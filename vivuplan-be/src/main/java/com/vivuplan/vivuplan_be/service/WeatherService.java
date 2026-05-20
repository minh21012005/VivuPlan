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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
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
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter OPEN_METEO_HOUR_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    public record LatLon(double lat, double lon) {}

    private record WeatherCacheEntry(List<DailyWeather> data, long fetchedAt) {}
    private record CurrentWeatherCacheEntry(CurrentWeather data, long fetchedAt) {}

    private final Map<String, WeatherCacheEntry> weatherCache    = new ConcurrentHashMap<>();
    private final Map<String, CurrentWeatherCacheEntry> currentWeatherCache = new ConcurrentHashMap<>();
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
        private double precipitationMm;
        private int    precipitationProbability;
        private double windspeedKmh;
        @Builder.Default
        private List<WeatherWindow> timeWindows = List.of();

        /** Human-readable WMO weather label for AI prompts. */
        public String toWeatherLabel() {
            return weatherLabel(code);
        }

        /** 0 = fine, 1 = flexible rain plan, 2 = severe weather safety risk */
        public int outdoorRiskLevel() {
            return resolveOutdoorRiskLevel(code, precipitationMm, precipitationProbability, windspeedKmh);
        }
    }

    @Data
    @Builder
    public static class WeatherWindow {
        private String label;
        private int startHour;
        private int endHour;
        private int code;
        private double temperatureC;
        private double precipitationMm;
        private int precipitationProbability;
        private double windspeedKmh;

        public String toWeatherLabel() {
            return weatherLabel(code);
        }

        public int outdoorRiskLevel() {
            return resolveOutdoorRiskLevel(code, precipitationMm, precipitationProbability, windspeedKmh);
        }
    }

    @Data
    @Builder
    public static class CurrentWeather {
        private String time;
        private int code;
        private double temperatureC;
        private double precipitationMm;
        private int precipitationProbability;
        private double windspeedKmh;

        public String toWeatherLabel() {
            return weatherLabel(code);
        }

        public int outdoorRiskLevel() {
            return resolveOutdoorRiskLevel(code, precipitationMm, precipitationProbability, windspeedKmh);
        }
    }

    private static String weatherLabel(int code) {
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

    private static int resolveOutdoorRiskLevel(
            int code,
            double precipitationMm,
            int precipitationProbability,
            double windspeedKmh) {
        if (code >= 96 && code <= 99)          return 2;
        if (code == 95 && (precipitationProbability >= 60
                || precipitationMm >= 3
                || windspeedKmh >= 40
                || (precipitationProbability >= 50 && precipitationMm >= 1))) return 2;
        if (code == 65 || code == 67 || code == 82 || code == 86) return 2;
        if (precipitationMm >= 25)             return 2;
        if (windspeedKmh >= 50 && precipitationProbability >= 70) return 2;
        if (precipitationProbability >= 95 && precipitationMm >= 15) return 2;
        if (code == 95)                        return 1;
        if ((code >= 51 && code <= 64) || (code >= 80 && code <= 81)) return 1;
        if (precipitationMm >= 1)              return 1;
        if (precipitationProbability >= 60)    return 1;
        return 0;
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

        java.net.URI uri = UriComponentsBuilder.fromHttpUrl(OPEN_METEO_BASE)
                .queryParam("latitude",  String.format("%.4f", lat))
                .queryParam("longitude", String.format("%.4f", lon))
                .queryParam("daily", "weathercode,temperature_2m_max,temperature_2m_min,precipitation_sum,windspeed_10m_max,precipitation_probability_max")
                .queryParam("hourly", "weathercode,temperature_2m,precipitation,precipitation_probability,windspeed_10m")
                .queryParam("timezone", "Asia/Ho_Chi_Minh")
                .queryParam("forecast_days", 16)
                .build()
                .encode()
                .toUri();

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(uri, Map.class);
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

    /**
     * Fetches the current hourly weather sample for destination cards.
     * The cache key includes the current local hour, so cards move to the next
     * hourly sample immediately when the displayed hour changes.
     */
    public CurrentWeather getCurrentWeather(Double lat, Double lon) {
        if (lat == null || lon == null) return null;

        String coordKey = String.format("%.4f,%.4f", lat, lon);
        LocalDateTime currentHour = LocalDateTime.now(VIETNAM_ZONE)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
        String currentHourKey = currentHour.format(OPEN_METEO_HOUR_FORMAT);
        String cacheKey = coordKey + "@" + currentHourKey;
        CurrentWeatherCacheEntry cached = currentWeatherCache.get(cacheKey);
        if (cached != null && (Instant.now().toEpochMilli() - cached.fetchedAt()) < WEATHER_CACHE_TTL) {
            log.debug("Current weather cache hit for {}", cacheKey);
            return cached.data();
        }

        java.net.URI uri = UriComponentsBuilder.fromHttpUrl(OPEN_METEO_BASE)
                .queryParam("latitude", String.format("%.4f", lat))
                .queryParam("longitude", String.format("%.4f", lon))
                .queryParam("hourly", "weathercode,temperature_2m,precipitation,precipitation_probability,windspeed_10m")
                .queryParam("timezone", "Asia/Ho_Chi_Minh")
                .queryParam("forecast_days", 2)
                .build()
                .encode()
                .toUri();

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(uri, Map.class);
            CurrentWeather current = parseCurrentHourly(response, currentHourKey);
            if (current == null) {
                log.warn("Open-Meteo returned no hourly current weather for {}", cacheKey);
                return null;
            }
            currentWeatherCache.put(cacheKey, new CurrentWeatherCacheEntry(current, Instant.now().toEpochMilli()));
            log.debug("Fetched current hourly weather for {} at {}", coordKey, current.getTime());
            return current;
        } catch (Exception e) {
            log.warn("Failed to fetch current hourly weather for {}: {}", coordKey, e.getMessage());
            return null;
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

        java.net.URI uri = UriComponentsBuilder.fromHttpUrl(NOMINATIM_BASE)
                .queryParam("q", placeName.trim())
                .queryParam("format", "json")
                .queryParam("limit", 1)
                .queryParam("countrycodes", "vn")  // prefer Vietnam results
                .build()
                .encode()
                .toUri();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "VivuPlan/1.0 (travel-planning-app)");
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = restTemplate.exchange(
                    uri, HttpMethod.GET, entity,
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

    public CurrentWeather getCurrentWeatherForDestination(String destinationName, Double lat, Double lon) {
        if (lat != null && lon != null) {
            return getCurrentWeather(lat, lon);
        }
        LatLon resolved = geocodeDestination(destinationName);
        if (resolved == null) return null;
        return getCurrentWeather(resolved.lat(), resolved.lon());
    }

    // ─── Parsing ─────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private CurrentWeather parseCurrentHourly(Map<String, Object> response, String targetTime) {
        if (response == null || !(response.get("hourly") instanceof Map<?, ?>)) {
            return null;
        }
        Map<String, Object> hourly = (Map<String, Object>) response.get("hourly");
        List<String> times = (List<String>) hourly.get("time");
        if (times == null || times.isEmpty()) {
            return null;
        }

        int index = findHourlyIndex(times, targetTime);
        if (index < 0) {
            return null;
        }

        List<Number> codes = (List<Number>) hourly.get("weathercode");
        List<Number> temperatures = (List<Number>) hourly.get("temperature_2m");
        List<Number> precipitation = (List<Number>) hourly.get("precipitation");
        List<Number> rainProbs = (List<Number>) hourly.get("precipitation_probability");
        List<Number> windSpeeds = (List<Number>) hourly.get("windspeed_10m");

        return CurrentWeather.builder()
                .time(getString(times, index))
                .code(getInt(codes, index, 0))
                .temperatureC(getDouble(temperatures, index, 0))
                .precipitationMm(getDouble(precipitation, index, 0))
                .precipitationProbability(getInt(rainProbs, index, 0))
                .windspeedKmh(getDouble(windSpeeds, index, 0))
                .build();
    }

    private int findHourlyIndex(List<String> times, String targetTime) {
        int nearestIndex = -1;
        long nearestDistanceSeconds = Long.MAX_VALUE;
        LocalDateTime target = LocalDateTime.parse(targetTime, OPEN_METEO_HOUR_FORMAT);

        for (int i = 0; i < times.size(); i++) {
            String time = times.get(i);
            if (targetTime.equals(time)) {
                return i;
            }
            try {
                LocalDateTime candidate = LocalDateTime.parse(time, OPEN_METEO_HOUR_FORMAT);
                long distance = Math.abs(java.time.Duration.between(target, candidate).getSeconds());
                if (distance < nearestDistanceSeconds) {
                    nearestDistanceSeconds = distance;
                    nearestIndex = i;
                }
            } catch (RuntimeException ignored) {
                // Ignore malformed timestamps from upstream.
            }
        }

        return nearestDistanceSeconds <= 60 * 60 ? nearestIndex : -1;
    }

    @SuppressWarnings("unchecked")
    private List<DailyWeather> parseDailyBlock(Map<String, Object> response) {
        Map<String, Object> daily = (Map<String, Object>) response.get("daily");
        List<String> times    = (List<String>) daily.get("time");
        List<Number> codes    = (List<Number>) daily.get("weathercode");
        List<Number> maxTemps = (List<Number>) daily.get("temperature_2m_max");
        List<Number> minTemps = (List<Number>) daily.get("temperature_2m_min");
        List<Number> precipitationSums = (List<Number>) daily.get("precipitation_sum");
        List<Number> windSpeeds = (List<Number>) daily.get("windspeed_10m_max");
        List<Number> rainProbs = (List<Number>) daily.get("precipitation_probability_max");
        Map<String, List<WeatherWindow>> windowsByDate = parseHourlyWindows(response);

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
                    .precipitationMm(getDouble(precipitationSums, i, 0))
                    .precipitationProbability(getInt(rainProbs, i, 0))
                    .windspeedKmh(getDouble(windSpeeds, i, 0))
                    .timeWindows(windowsByDate.getOrDefault(date, List.of()))
                    .build());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<WeatherWindow>> parseHourlyWindows(Map<String, Object> response) {
        Object hourlyObject = response.get("hourly");
        if (!(hourlyObject instanceof Map<?, ?>)) {
            return Map.of();
        }
        Map<String, Object> hourly = (Map<String, Object>) hourlyObject;
        List<String> times = (List<String>) hourly.get("time");
        if (times == null || times.isEmpty()) {
            return Map.of();
        }

        List<Number> codes = (List<Number>) hourly.get("weathercode");
        List<Number> temperatures = (List<Number>) hourly.get("temperature_2m");
        List<Number> precipitation = (List<Number>) hourly.get("precipitation");
        List<Number> rainProbs = (List<Number>) hourly.get("precipitation_probability");
        List<Number> windSpeeds = (List<Number>) hourly.get("windspeed_10m");
        Map<String, WindowAccumulator[]> grouped = new HashMap<>();

        for (int i = 0; i < times.size(); i++) {
            String time = getString(times, i);
            if (time == null || time.length() < 13) {
                continue;
            }
            int hour = parseHour(time);
            int windowIndex = windowIndex(hour);
            if (windowIndex < 0) {
                continue;
            }
            String date = time.substring(0, 10);
            WindowAccumulator[] accumulators = grouped.computeIfAbsent(date, ignored -> new WindowAccumulator[] {
                    new WindowAccumulator("morning", 6, 11),
                    new WindowAccumulator("afternoon", 12, 17),
                    new WindowAccumulator("evening", 18, 22)
            });
            accumulators[windowIndex].add(
                    getInt(codes, i, 0),
                    getDouble(temperatures, i, 0),
                    getDouble(precipitation, i, 0),
                    getInt(rainProbs, i, 0),
                    getDouble(windSpeeds, i, 0));
        }

        Map<String, List<WeatherWindow>> result = new HashMap<>();
        for (Map.Entry<String, WindowAccumulator[]> entry : grouped.entrySet()) {
            List<WeatherWindow> windows = new ArrayList<>();
            for (WindowAccumulator accumulator : entry.getValue()) {
                if (accumulator.hasData()) {
                    windows.add(accumulator.toWindow());
                }
            }
            if (!windows.isEmpty()) {
                result.put(entry.getKey(), windows);
            }
        }
        return result;
    }

    private int parseHour(String time) {
        try {
            return Integer.parseInt(time.substring(11, 13));
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private int windowIndex(int hour) {
        if (hour >= 6 && hour <= 11) {
            return 0;
        }
        if (hour >= 12 && hour <= 17) {
            return 1;
        }
        if (hour >= 18 && hour <= 22) {
            return 2;
        }
        return -1;
    }

    private static int weatherCodeRank(int code) {
        if (code >= 95 && code <= 99) return 5;
        if (code == 65 || code == 67 || code == 82 || code == 86) return 4;
        if ((code >= 61 && code <= 64) || (code >= 80 && code <= 81)) return 3;
        if (code >= 51 && code <= 60) return 2;
        if (code >= 1 && code <= 49) return 1;
        return 0;
    }

    private static class WindowAccumulator {
        private final String label;
        private final int startHour;
        private final int endHour;
        private int samples;
        private int code;
        private double temperatureSum;
        private double precipitationMm;
        private int precipitationProbability;
        private double windspeedKmh;

        private WindowAccumulator(String label, int startHour, int endHour) {
            this.label = label;
            this.startHour = startHour;
            this.endHour = endHour;
        }

        private void add(int code, double temperatureC, double precipitationMm, int precipitationProbability, double windspeedKmh) {
            samples++;
            if (weatherCodeRank(code) > weatherCodeRank(this.code)) {
                this.code = code;
            }
            this.temperatureSum += temperatureC;
            this.precipitationMm += Math.max(0, precipitationMm);
            this.precipitationProbability = Math.max(this.precipitationProbability, precipitationProbability);
            this.windspeedKmh = Math.max(this.windspeedKmh, windspeedKmh);
        }

        private boolean hasData() {
            return samples > 0;
        }

        private WeatherWindow toWindow() {
            return WeatherWindow.builder()
                    .label(label)
                    .startHour(startHour)
                    .endHour(endHour)
                    .code(code)
                    .temperatureC(samples > 0 ? temperatureSum / samples : 0)
                    .precipitationMm(precipitationMm)
                    .precipitationProbability(precipitationProbability)
                    .windspeedKmh(windspeedKmh)
                    .build();
        }
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

    private double getDouble(List<Number> values, int index, double fallback) {
        Number value = getNumber(values, index);
        return value != null ? value.doubleValue() : fallback;
    }

    private Number getNumber(List<Number> values, int index) {
        return values != null && index < values.size() ? values.get(index) : null;
    }
}
