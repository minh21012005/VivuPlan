package com.vivuplan.vivuplan_be.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WeatherServiceTest {

    @Test
    void parseDailyBlockToleratesNullDailyValues() throws Exception {
        WeatherService service = new WeatherService();
        Map<String, Object> daily = new HashMap<>();
        daily.put("time", List.of("2026-05-18", "2026-05-19"));
        daily.put("weathercode", Arrays.asList(63, null));
        daily.put("temperature_2m_max", Arrays.asList(31.5, null));
        daily.put("temperature_2m_min", Arrays.asList(null, 25.0));
        daily.put("precipitation_probability_max", Arrays.asList(null, 80));

        Map<String, Object> response = Map.of("daily", daily);

        List<WeatherService.DailyWeather> forecast = invokeParseDailyBlock(service, response);

        assertThat(forecast).hasSize(2);
        assertThat(forecast.get(0).getDate()).isEqualTo("2026-05-18");
        assertThat(forecast.get(0).getCode()).isEqualTo(63);
        assertThat(forecast.get(0).getMinTemp()).isEqualTo(31.5);
        assertThat(forecast.get(0).getPrecipitationProbability()).isZero();
        assertThat(forecast.get(1).getCode()).isZero();
        assertThat(forecast.get(1).getMaxTemp()).isEqualTo(25.0);
        assertThat(forecast.get(1).getMinTemp()).isEqualTo(25.0);
        assertThat(forecast.get(1).getPrecipitationProbability()).isEqualTo(80);
    }

    @Test
    void parseDailyBlockReturnsEmptyWhenTimeSeriesMissing() throws Exception {
        WeatherService service = new WeatherService();
        Map<String, Object> response = Map.of("daily", new HashMap<String, Object>());

        List<WeatherService.DailyWeather> forecast = invokeParseDailyBlock(service, response);

        assertThat(forecast).isEmpty();
    }

    @Test
    void parseDailyBlockBuildsHourlyOutdoorWindowsWhenAvailable() throws Exception {
        WeatherService service = new WeatherService();
        Map<String, Object> daily = new HashMap<>();
        daily.put("time", List.of("2026-05-18"));
        daily.put("weathercode", List.of(63));
        daily.put("temperature_2m_max", List.of(31.0));
        daily.put("temperature_2m_min", List.of(25.0));
        daily.put("precipitation_sum", List.of(4.0));
        daily.put("windspeed_10m_max", List.of(12.0));
        daily.put("precipitation_probability_max", List.of(80));
        Map<String, Object> hourly = new HashMap<>();
        hourly.put("time", List.of(
                "2026-05-18T06:00",
                "2026-05-18T09:00",
                "2026-05-18T13:00",
                "2026-05-18T19:00"));
        hourly.put("weathercode", List.of(1, 61, 80, 0));
        hourly.put("precipitation", List.of(0.0, 0.4, 2.0, 0.0));
        hourly.put("precipitation_probability", List.of(15, 35, 75, 20));
        hourly.put("windspeed_10m", List.of(8.0, 12.0, 10.0, 5.0));

        List<WeatherService.DailyWeather> forecast = invokeParseDailyBlock(service,
                Map.of("daily", daily, "hourly", hourly));

        assertThat(forecast).hasSize(1);
        assertThat(forecast.get(0).getTimeWindows()).hasSize(3);
        assertThat(forecast.get(0).getTimeWindows())
                .extracting(WeatherService.WeatherWindow::getLabel)
                .containsExactly("morning", "afternoon", "evening");
        WeatherService.WeatherWindow afternoon = forecast.get(0).getTimeWindows().get(1);
        assertThat(afternoon.getPrecipitationProbability()).isEqualTo(75);
        assertThat(afternoon.getPrecipitationMm()).isEqualTo(2.0);
        assertThat(afternoon.outdoorRiskLevel()).isEqualTo(1);
        assertThat(forecast.get(0).getTimeWindows().get(2).outdoorRiskLevel()).isZero();
    }

    @Test
    void outdoorRiskTreatsModerateRainChanceAsFlexibleNotSevere() {
        WeatherService.DailyWeather weather = WeatherService.DailyWeather.builder()
                .code(63)
                .precipitationProbability(80)
                .precipitationMm(4.0)
                .windspeedKmh(12)
                .build();

        assertThat(weather.outdoorRiskLevel()).isEqualTo(1);
    }

    @Test
    void outdoorRiskTreatsHighProbabilityTinyRainAsGoodWeather() {
        WeatherService.DailyWeather weather = WeatherService.DailyWeather.builder()
                .code(3)
                .precipitationProbability(55)
                .precipitationMm(0.4)
                .windspeedKmh(10)
                .build();

        assertThat(weather.outdoorRiskLevel()).isZero();
    }

    @Test
    void outdoorRiskTreatsNearCertainHeavyRainAsSevere() {
        WeatherService.DailyWeather weather = WeatherService.DailyWeather.builder()
                .code(61)
                .precipitationProbability(96)
                .precipitationMm(16.0)
                .windspeedKmh(20)
                .build();

        assertThat(weather.outdoorRiskLevel()).isEqualTo(2);
    }

    @Test
    void outdoorRiskTreatsThunderstormAsSevere() {
        WeatherService.DailyWeather weather = WeatherService.DailyWeather.builder()
                .code(95)
                .precipitationProbability(75)
                .precipitationMm(8.0)
                .windspeedKmh(20)
                .build();

        assertThat(weather.outdoorRiskLevel()).isEqualTo(2);
    }

    @Test
    void outdoorRiskTreatsLowProbabilityDryThunderstormSignalAsFlexible() {
        WeatherService.DailyWeather weather = WeatherService.DailyWeather.builder()
                .code(95)
                .precipitationProbability(8)
                .precipitationMm(0.0)
                .windspeedKmh(21)
                .build();

        assertThat(weather.outdoorRiskLevel()).isEqualTo(1);
    }

    @Test
    void outdoorRiskTreatsModerateProbabilityDryThunderstormSignalAsFlexible() {
        WeatherService.DailyWeather weather = WeatherService.DailyWeather.builder()
                .code(95)
                .precipitationProbability(45)
                .precipitationMm(0.0)
                .windspeedKmh(21)
                .build();

        assertThat(weather.outdoorRiskLevel()).isEqualTo(1);
    }

    @Test
    void outdoorRiskTreatsThunderstormWithLikelyRainAsSevere() {
        WeatherService.DailyWeather weather = WeatherService.DailyWeather.builder()
                .code(95)
                .precipitationProbability(50)
                .precipitationMm(1.0)
                .windspeedKmh(21)
                .build();

        assertThat(weather.outdoorRiskLevel()).isEqualTo(2);
    }

    @Test
    void outdoorRiskTreatsHailThunderstormAsSevereEvenWithLowRainAmount() {
        WeatherService.DailyWeather weather = WeatherService.DailyWeather.builder()
                .code(96)
                .precipitationProbability(8)
                .precipitationMm(0.0)
                .windspeedKmh(21)
                .build();

        assertThat(weather.outdoorRiskLevel()).isEqualTo(2);
    }

    @SuppressWarnings("unchecked")
    private List<WeatherService.DailyWeather> invokeParseDailyBlock(
            WeatherService service,
            Map<String, Object> response) throws Exception {
        Method method = WeatherService.class.getDeclaredMethod("parseDailyBlock", Map.class);
        method.setAccessible(true);
        return (List<WeatherService.DailyWeather>) method.invoke(service, response);
    }
}
