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

    @SuppressWarnings("unchecked")
    private List<WeatherService.DailyWeather> invokeParseDailyBlock(
            WeatherService service,
            Map<String, Object> response) throws Exception {
        Method method = WeatherService.class.getDeclaredMethod("parseDailyBlock", Map.class);
        method.setAccessible(true);
        return (List<WeatherService.DailyWeather>) method.invoke(service, response);
    }
}
