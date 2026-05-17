package com.vivuplan.vivuplan_be.service;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
 * - Per-coordinate in-memory cache with 30-minute TTL.
 * - Nominatim (OpenStreetMap) geocoding fallback: if a destination is not found in
 *   the local DB, coordinates are resolved on-the-fly and cached for 24h.
 * - Safe, type-checked parsing for numeric JSON fields.
 */
@Service
@Slf4j
public class WeatherService {

    private static final String OPEN_METEO_BASE   = "https://api.open-meteo.com/v1/forecast";
    private static final String NOMINATIM_BASE    = "https://nominatim.openstreetmap.org/search";
    private static final long   WEATHER_CACHE_TTL = 30  * 60 * 1000L;       // 30 minutes
    private static final long   GEOCODE_CACHE_TTL = 24  * 60 * 60 * 1000L;  // 24 hours

    public record LatLon(double lat, double lon) {}

    private record WeatherCacheEntry(List<DailyWeather> data, long fetchedAt) {}

    private final Map<String, WeatherCacheEntry> weatherCache    = new ConcurrentHashMap<>();
    private final Map<String, LatLon>            geocodeCache    = new ConcurrentHashMap<>();
    private final Map<String, Long>              geocodeFetchedAt = new ConcurrentHashMap<>();

    private final RestTemplate restTemplate = new RestTemplate();

    // ─── DailyWeather DTO ────────────────────────────────────────────────────

    @Data
    @Builder
    public static class DailyWeather {
        private String date;
        private int    code;
        private double maxTemp;
        private double minTemp;
        private int    precipitationProbability;

        /** Human-readable WMO weather label for AI prompts. */
        public String toWeatherLabel() {
            if (code == 0)            return "Clear sky";
            if (code <= 2)            return "Partly cloudy";
            if (code == 3)            return "Overcast";
            if (code <= 49)           return "Fog/mist";
            if (code <= 59)           return "Drizzle";
            if (code <= 69)           return "Rain";
            if (code <= 79)           return "Snow/sleet";
            if (code <= 82)           return "Rain showers";
            if (code <= 86)           return "Snow showers";
            if (code <= 99)           return "Thunderstorm";
            return "Unknown";
        }

        /** 0 = fine, 1 = light rain caution, 2 = high risk (heavy rain/storm) */
        public int outdoorRiskLevel() {
            if (code >= 80 && code <= 99)          return 2;
            if (code >= 61 && code <= 79)          return 2;
            if (code >= 51 && code <= 60)          return 1;
            if (precipitationProbability >= 70)    return 2;
            if (precipitationProbability >= 40)    return 1;
            return 0;
        }
    }

    // ─── Weather Forecast ────────────────────────────────────────────────────

    /**
     * Fetches a 16-day forecast for (lat, lon). Returns empty list on failure.
     * Cached per coordinate pair for 30 minutes.
     */
    public List<DailyWeather> getForecast(Double lat, Double lon) {
        if (lat == null || lon == null) return List.of();

        String cacheKey = String.format("%.4f,%.4f", lat, lon);
        WeatherCacheEntry cached = weatherCache.get(cacheKey);
        if (cached != null && (Instant.now().toEpochMilli() - cached.fetchedAt()) < WEATHER_CACHE_TTL) {
            log.debug("Weather cache hit for {}", cacheKey);
            return cached.data();
        }

        String url = UriComponentsBuilder.fromHttpUrl(OPEN_METEO_BASE)
                .queryParam("latitude",  String.format("%.4f", lat))
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
            weatherCache.put(cacheKey, new WeatherCacheEntry(result, Instant.now().toEpochMilli()));
            log.info("Fetched {}-day weather for {} successfully", result.size(), cacheKey);
            return result;
        } catch (Exception e) {
            log.warn("Failed to fetch weather for {}: {}", cacheKey, e.getMessage());
            return List.of();
        }
    }

    // ─── Geocoding Fallback (Nominatim / OpenStreetMap) ──────────────────────

    /**
     * Resolves lat/lon for a place name using Nominatim.
     * Results are cached for 24 hours. Returns null if unresolvable.
     */
    public LatLon geocodeDestination(String placeName) {
        if (placeName == null || placeName.isBlank()) return null;

        String key = placeName.trim().toLowerCase();
        Long fetchedAt = geocodeFetchedAt.get(key);
        if (fetchedAt != null && (Instant.now().toEpochMilli() - fetchedAt) < GEOCODE_CACHE_TTL) {
            return geocodeCache.get(key); // null means previously unresolvable — don't retry
        }

        String url = UriComponentsBuilder.fromHttpUrl(NOMINATIM_BASE)
                .queryParam("q", placeName.trim())
                .queryParam("format", "json")
                .queryParam("limit", 1)
                .queryParam("countrycodes", "vn")  // prefer Vietnam results
                .build(true)
                .toUriString();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "VivuPlan/1.0 (travel-planning-app)");
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = restTemplate.exchange(
                    url, HttpMethod.GET, entity,
                    (Class<List<Map<String, Object>>>) (Class<?>) List.class
            ).getBody();

            geocodeFetchedAt.put(key, Instant.now().toEpochMilli()); // always mark as attempted

            if (results != null && !results.isEmpty()) {
                Map<String, Object> first = results.get(0);
                double lat = Double.parseDouble(first.get("lat").toString());
                double lon = Double.parseDouble(first.get("lon").toString());
                LatLon latLon = new LatLon(lat, lon);
                geocodeCache.put(key, latLon);
                log.info("Geocoded '{}' → ({}, {})", placeName, lat, lon);
                return latLon;
            } else {
                log.warn("Nominatim could not resolve '{}'", placeName);
                return null;
            }

        } catch (Exception e) {
            log.warn("Nominatim geocoding failed for '{}': {}", placeName, e.getMessage());
            return null;
        }
    }

    /**
     * Convenience method: fetch forecast using pre-resolved coordinates (from DB),
     * falling back to Nominatim geocoding if coordinates are not available.
     */
    public List<DailyWeather> getForecastForDestination(String destinationName, Double lat, Double lon) {
        if (lat != null && lon != null) {
            return getForecast(lat, lon);
        }
        LatLon resolved = geocodeDestination(destinationName);
        if (resolved == null) return List.of();
        return getForecast(resolved.lat(), resolved.lon());
    }

    // ─── Parsing ─────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<DailyWeather> parseDailyBlock(Map<String, Object> response) {
        Map<String, Object> daily = (Map<String, Object>) response.get("daily");
        List<String> times    = (List<String>) daily.get("time");
        List<Number> codes    = (List<Number>) daily.get("weathercode");
        List<Number> maxTemps = (List<Number>) daily.get("temperature_2m_max");
        List<Number> minTemps = (List<Number>) daily.get("temperature_2m_min");
        List<Number> rainProbs = (List<Number>) daily.get("precipitation_probability_max");

        if (times == null || times.isEmpty()) {
            return List.of();
        }

        List<DailyWeather> result = new ArrayList<>(times.size());
        for (int i = 0; i < times.size(); i++) {
            String date = getString(times, i);
            if (date == null || date.isBlank()) {
                continue;
            }
            Double maxTemp = getDouble(maxTemps, i);
            Double minTemp = getDouble(minTemps, i);
            double resolvedMaxTemp = maxTemp != null ? maxTemp : minTemp != null ? minTemp : 0;
            double resolvedMinTemp = minTemp != null ? minTemp : maxTemp != null ? maxTemp : 0;
            result.add(DailyWeather.builder()
                    .date(date)
                    .code(getInt(codes, i, 0))
                    .maxTemp(resolvedMaxTemp)
                    .minTemp(resolvedMinTemp)
                    .precipitationProbability(getInt(rainProbs, i, 0))
                    .build());
        }
        return result;
    }

    private String getString(List<String> values, int index) {
        return values != null && index < values.size() ? values.get(index) : null;
    }

    private int getInt(List<Number> values, int index, int fallback) {
        Number value = getNumber(values, index);
        return value != null ? value.intValue() : fallback;
    }

    private Double getDouble(List<Number> values, int index) {
        Number value = getNumber(values, index);
        return value != null ? value.doubleValue() : null;
    }

    private Number getNumber(List<Number> values, int index) {
        return values != null && index < values.size() ? values.get(index) : null;
    }
}
