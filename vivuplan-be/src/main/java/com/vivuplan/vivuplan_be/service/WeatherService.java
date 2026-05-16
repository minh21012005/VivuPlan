package com.vivuplan.vivuplan_be.service;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetches 16-day daily forecasts from Open-Meteo (free, no API key required).
 *
 * Optimizations:
 * - Per-coordinate in-memory cache with 30-minute TTL to avoid hammering the API
 *   when multiple trips to the same destination are generated in a short window.
 * - Safe, type-checked parsing that handles both Integer and Double values returned
 *   by Jackson for numeric JSON fields.
 * - Constructed via UriComponentsBuilder (no raw String.format for URL encoding).
 */
@Service
@Slf4j
public class WeatherService {

    private static final String OPEN_METEO_BASE = "https://api.open-meteo.com/v1/forecast";
    private static final long CACHE_TTL_MS = 30 * 60 * 1000L; // 30 minutes

    private record CacheEntry(List<DailyWeather> data, long fetchedAt) {}

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final RestTemplate restTemplate = new RestTemplate();

    @Data
    @Builder
    public static class DailyWeather {
        private String date;
        private int code;
        private double maxTemp;
        private double minTemp;
        private int precipitationProbability;

        /**
         * Returns a human-readable WMO weather label for use in AI prompts.
         */
        public String toWeatherLabel() {
            int c = code;
            String condition;
            if (c == 0)             condition = "Clear sky";
            else if (c <= 2)        condition = "Partly cloudy";
            else if (c == 3)        condition = "Overcast";
            else if (c <= 49)       condition = "Fog/mist";
            else if (c <= 59)       condition = "Drizzle";
            else if (c <= 69)       condition = "Rain";
            else if (c <= 79)       condition = "Snow/sleet";
            else if (c <= 82)       condition = "Rain showers";
            else if (c <= 86)       condition = "Snow showers";
            else if (c <= 99)       condition = "Thunderstorm";
            else                    condition = "Unknown";
            return condition;
        }

        /**
         * Returns a risk level to guide the AI:
         * 0 = fine, 1 = caution (light rain), 2 = bad (heavy rain/storm)
         */
        public int outdoorRiskLevel() {
            if (code >= 80 && code <= 99) return 2; // showers/thunderstorm
            if (code >= 61 && code <= 79) return 2; // moderate-heavy rain/snow
            if (code >= 51 && code <= 60) return 1; // drizzle
            if (precipitationProbability >= 70) return 2;
            if (precipitationProbability >= 40) return 1;
            return 0;
        }
    }

    /**
     * Fetches forecast for (lat, lon). Returns an empty list on any failure.
     * Results are cached per coordinate pair for 30 minutes.
     */
    public List<DailyWeather> getForecast(Double lat, Double lon) {
        if (lat == null || lon == null) return List.of();

        String cacheKey = String.format("%.4f,%.4f", lat, lon);
        CacheEntry cached = cache.get(cacheKey);
        if (cached != null && (Instant.now().toEpochMilli() - cached.fetchedAt()) < CACHE_TTL_MS) {
            log.debug("Weather cache hit for {}", cacheKey);
            return cached.data();
        }

        String url = UriComponentsBuilder.fromHttpUrl(OPEN_METEO_BASE)
                .queryParam("latitude", String.format("%.4f", lat))
                .queryParam("longitude", String.format("%.4f", lon))
                .queryParam("daily", "weathercode,temperature_2m_max,temperature_2m_min,precipitation_probability_max")
                .queryParam("timezone", "Asia/Ho_Chi_Minh")
                .queryParam("forecast_days", 16)
                .build(true)
                .toUriString();

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response == null || !response.containsKey("daily")) {
                log.warn("Open-Meteo returned no 'daily' block for {}", cacheKey);
                return List.of();
            }

            List<DailyWeather> result = parseDailyBlock(response);
            cache.put(cacheKey, new CacheEntry(result, Instant.now().toEpochMilli()));
            log.info("Fetched {}-day weather forecast for {} successfully", result.size(), cacheKey);
            return result;

        } catch (Exception e) {
            log.warn("Failed to fetch weather from Open-Meteo for {}: {}", cacheKey, e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<DailyWeather> parseDailyBlock(Map<String, Object> response) {
        Map<String, Object> daily = (Map<String, Object>) response.get("daily");
        List<String> times     = (List<String>)  daily.get("time");
        List<Number> codes     = (List<Number>)  daily.get("weathercode");
        List<Number> maxTemps  = (List<Number>)  daily.get("temperature_2m_max");
        List<Number> minTemps  = (List<Number>)  daily.get("temperature_2m_min");
        List<Number> rainProbs = (List<Number>)  daily.get("precipitation_probability_max");

        List<DailyWeather> result = new ArrayList<>(times.size());
        for (int i = 0; i < times.size(); i++) {
            result.add(DailyWeather.builder()
                    .date(times.get(i))
                    .code(codes.get(i).intValue())
                    .maxTemp(maxTemps.get(i).doubleValue())
                    .minTemp(minTemps.get(i).doubleValue())
                    .precipitationProbability(rainProbs.get(i) != null ? rainProbs.get(i).intValue() : 0)
                    .build());
        }
        return result;
    }
}
