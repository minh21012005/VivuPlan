package com.vivuplan.vivuplan_be.service;

import com.vivuplan.vivuplan_be.dto.TripDto;
import com.vivuplan.vivuplan_be.entity.Activity;
import com.vivuplan.vivuplan_be.entity.ItineraryDay;
import com.vivuplan.vivuplan_be.entity.LocationResolutionCache;
import com.vivuplan.vivuplan_be.entity.Trip;
import com.vivuplan.vivuplan_be.repository.LocationResolutionCacheRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ActivityCoordinateResolverService {

    private static final String PROVIDER_NOMINATIM = "NOMINATIM";
    private static final double MIN_VN_LAT = 8.0;
    private static final double MAX_VN_LAT = 24.5;
    private static final double MIN_VN_LON = 102.0;
    private static final double MAX_VN_LON = 110.5;
    private static final int HIGH_CONFIDENCE_SCORE = 70;
    private static final int MEDIUM_CONFIDENCE_SCORE = 50;
    private static final Set<String> WEAK_TOKENS = Set.of(
            "tai", "gan", "khu", "vuc", "trung", "tam", "nha", "hang", "quan", "an",
            "cafe", "ca", "phe", "khach", "san", "homestay", "dia", "phuong", "cho",
            "diem", "den", "noi", "luu", "tru", "da", "chon", "ven");
    private static final List<String> GENERIC_DESTINATION_PREFIXES = List.of(
            "khu du lich sinh thai ",
            "khu du lich ",
            "diem du lich ",
            "khu nghi duong ",
            "resort ");

    private final LocationResolutionCacheRepository cacheRepository;
    private final RestTemplate restTemplate;
    private final Object rateLimitLock = new Object();

    private final boolean enabled;
    private final String nominatimBaseUrl;
    private final int maxPerTrip;
    private final long minRequestIntervalMs;

    private long lastExternalRequestAt = 0L;

    @Autowired
    public ActivityCoordinateResolverService(
            LocationResolutionCacheRepository cacheRepository,
            RestTemplateBuilder restTemplateBuilder,
            @Value("${app.geocoding.activity.enabled:true}") boolean enabled,
            @Value("${app.geocoding.activity.nominatim-base-url:https://nominatim.openstreetmap.org/search}") String nominatimBaseUrl,
            @Value("${app.geocoding.activity.timeout-ms:3000}") int timeoutMs,
            @Value("${app.geocoding.activity.max-per-trip:20}") int maxPerTrip,
            @Value("${app.geocoding.activity.min-request-interval-ms:1000}") long minRequestIntervalMs) {
        this(
                cacheRepository,
                restTemplateBuilder
                        .setConnectTimeout(Duration.ofMillis(timeoutMs))
                        .setReadTimeout(Duration.ofMillis(timeoutMs))
                        .build(),
                enabled,
                nominatimBaseUrl,
                maxPerTrip,
                minRequestIntervalMs);
    }

    ActivityCoordinateResolverService(
            LocationResolutionCacheRepository cacheRepository,
            RestTemplate restTemplate,
            boolean enabled,
            String nominatimBaseUrl,
            int maxPerTrip,
            long minRequestIntervalMs) {
        this.cacheRepository = cacheRepository;
        this.restTemplate = restTemplate;
        this.enabled = enabled;
        this.nominatimBaseUrl = nominatimBaseUrl;
        this.maxPerTrip = Math.max(0, maxPerTrip);
        this.minRequestIntervalMs = Math.max(0, minRequestIntervalMs);
    }

    public BatchResult resolveSchedule(List<TripDto.DayResponse> schedule, String destination) {
        if (schedule == null || schedule.isEmpty()) {
            return new BatchResult(List.of());
        }
        List<Target> targets = new ArrayList<>();
        for (TripDto.DayResponse day : schedule) {
            if (day.getActivities() == null) {
                continue;
            }
            for (TripDto.ActivityResponse activity : day.getActivities()) {
                targets.add(Target.forResponse(day.getDay(), activity));
            }
        }
        return resolveTargets(targets, destination, false);
    }

    public BatchResult resolveTrip(Trip trip, boolean dryRun) {
        if (trip == null || trip.getItineraryDays() == null) {
            return new BatchResult(List.of());
        }
        List<Target> targets = new ArrayList<>();
        for (ItineraryDay day : trip.getItineraryDays()) {
            if (day.getActivities() == null) {
                continue;
            }
            for (Activity activity : day.getActivities()) {
                targets.add(Target.forEntity(day.getDayNumber(), activity));
            }
        }
        return resolveTargets(targets, trip.getDestination(), dryRun);
    }

    private BatchResult resolveTargets(List<Target> targets, String destination, boolean dryRun) {
        long startedAt = System.nanoTime();
        List<ItemResult> results = new ArrayList<>();
        int externalAttempts = 0;
        log.info(
                "Resolving activity coordinates for destination='{}', targets={}, dryRun={}, enabled={}, maxPerTrip={}, minIntervalMs={}",
                destination,
                targets.size(),
                dryRun,
                enabled,
                maxPerTrip,
                minRequestIntervalMs);
        for (Target target : targets) {
            normalizeExistingCoordinateMetadata(target, dryRun);
            if (!shouldResolve(target)) {
                results.add(skipResult(target));
                continue;
            }
            if (!enabled) {
                results.add(ItemResult.skipped(target, "DISABLED", null, null, "Activity coordinate resolver is disabled"));
                continue;
            }
            if (externalAttempts >= maxPerTrip) {
                results.add(ItemResult.skipped(target, "LIMIT_REACHED", null, null, "Trip geocode limit reached"));
                continue;
            }

            List<String> queries = buildQueries(target.location(), destination);
            if (queries.isEmpty()) {
                results.add(ItemResult.skipped(target, "SKIPPED", null, null, "Location is too generic"));
                continue;
            }

            ItemResult resolved = null;
            for (String query : queries) {
                ResolveOutcome outcome = resolveQuery(query, target.location(), destination, !dryRun);
                if (outcome.externalAttempt()) {
                    externalAttempts++;
                }
                resolved = outcome.result().toItemResult(target, query, !dryRun);
                if (outcome.result().status() == LocationResolutionCache.Status.SUCCESS) {
                    if (!dryRun) {
                        applyResolvedCoordinate(target, outcome.result());
                    }
                    break;
                }
                if (externalAttempts >= maxPerTrip) {
                    break;
                }
            }
            results.add(resolved != null ? resolved : ItemResult.skipped(target, "NO_QUERY", null, null, "No usable query"));
        }
        BatchResult batch = new BatchResult(results);
        long durationMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        long cacheHits = results.stream().filter(ItemResult::cacheHit).count();
        long success = results.stream().filter(item -> "SUCCESS".equals(item.status())).count();
        long skipped = results.stream().filter(item -> "SKIPPED".equals(item.status())).count();
        long hasCoordinates = results.stream().filter(item -> "HAS_COORDINATES".equals(item.status())).count();
        long invalidCoordinates = results.stream().filter(item -> "INVALID_COORDINATES".equals(item.status())).count();
        long disabled = results.stream().filter(item -> "DISABLED".equals(item.status())).count();
        long limitReached = results.stream().filter(item -> "LIMIT_REACHED".equals(item.status())).count();
        long lowConfidence = results.stream().filter(item -> "LOW_CONFIDENCE".equals(item.status())).count();
        long noResult = results.stream().filter(item -> "NO_RESULT".equals(item.status())).count();
        long errors = results.stream().filter(item -> "ERROR".equals(item.status())).count();
        log.info(
                "Resolved activity coordinates destination='{}', durationMs={}, targets={}, applied={}, success={}, externalAttempts={}, cacheHits={}, hasCoordinates={}, skipped={}, invalidCoordinates={}, disabled={}, limitReached={}, lowConfidence={}, noResult={}, errors={}, dryRun={}",
                destination,
                durationMs,
                targets.size(),
                batch.appliedCount(),
                success,
                externalAttempts,
                cacheHits,
                hasCoordinates,
                skipped,
                invalidCoordinates,
                disabled,
                limitReached,
                lowConfidence,
                noResult,
                errors,
                dryRun);
        return batch;
    }

    private void normalizeExistingCoordinateMetadata(Target target, boolean dryRun) {
        Double lat = target.latitude();
        Double lon = target.longitude();
        if (lat == null && lon == null) {
            return;
        }
        if (!isValidVietnamCoordinate(lat, lon)) {
            if (!dryRun) {
                target.setLatitude(null);
                target.setLongitude(null);
                target.setCoordinateSource(null);
                target.setCoordinateConfidence(null);
            }
            return;
        }
        if (target.coordinateSource() != null) {
            return;
        }
        Activity.CoordinateSource source = target.placeId() != null
                ? Activity.CoordinateSource.VERIFIED_PLACE
                : Activity.CoordinateSource.AI_PROVIDED;
        Activity.CoordinateConfidence confidence = target.placeId() != null
                ? Activity.CoordinateConfidence.HIGH
                : Activity.CoordinateConfidence.MEDIUM;
        if (!dryRun) {
            target.setCoordinateSource(source);
            target.setCoordinateConfidence(confidence);
        }
    }

    private boolean shouldResolve(Target target) {
        if (target == null) {
            return false;
        }
        if (target.latitude() != null || target.longitude() != null) {
            return false;
        }
        if ("TRANSPORT".equalsIgnoreCase(target.type())) {
            return false;
        }
        String location = target.location();
        if (location == null || location.isBlank()) {
            return false;
        }
        return !isTooGenericLocation(location, target.type());
    }

    private ItemResult skipResult(Target target) {
        if (target.latitude() != null && target.longitude() != null) {
            return ItemResult.skipped(target, "HAS_COORDINATES", null, null, "Activity already has coordinates");
        }
        if (target.latitude() != null || target.longitude() != null) {
            return ItemResult.skipped(target, "INVALID_COORDINATES", null, null, "Activity has incomplete coordinates");
        }
        if ("TRANSPORT".equalsIgnoreCase(target.type())) {
            return ItemResult.skipped(target, "SKIPPED", null, null, "Transport activity is not geocoded");
        }
        return ItemResult.skipped(target, "SKIPPED", null, null, "Activity location is not specific enough");
    }

    private List<String> buildQueries(String location, String destination) {
        String cleanLocation = compact(location);
        if (cleanLocation.isBlank()) {
            return List.of();
        }
        List<String> queries = new ArrayList<>();
        String cleanDestination = compact(destination);
        if (!cleanDestination.isBlank() && !locationAlreadyContainsDestination(cleanLocation, cleanDestination)) {
            queries.add(cleanLocation + ", " + cleanDestination + ", Việt Nam");

            String shortDestination = shorterDestinationContext(cleanDestination);
            if (!shortDestination.isBlank()
                    && !shortDestination.equals(cleanDestination)
                    && !locationAlreadyContainsDestination(cleanLocation, shortDestination)) {
                queries.add(cleanLocation + ", " + shortDestination + ", Việt Nam");
            }
        }
        queries.add(cleanLocation + ", Việt Nam");
        return queries.stream().distinct().toList();
    }

    private boolean locationAlreadyContainsDestination(String location, String destination) {
        String normalizedLocation = normalizeComparableText(location);
        if (normalizedLocation.isBlank()) {
            return false;
        }
        return destinationContextVariants(destination).stream()
                .map(this::normalizeComparableText)
                .filter(variant -> variant.replaceAll("\\s+", "").length() >= 2)
                .anyMatch(variant -> containsComparablePhrase(normalizedLocation, variant));
    }

    private boolean containsComparablePhrase(String normalizedText, String normalizedPhrase) {
        String textWithBoundaries = " " + normalizedText + " ";
        String phraseWithBoundaries = " " + normalizedPhrase + " ";
        if (textWithBoundaries.contains(phraseWithBoundaries)) {
            return true;
        }
        String phraseWithoutSpaces = normalizedPhrase.replaceAll("\\s+", "");
        if (normalizedPhrase.contains(" ") || phraseWithoutSpaces.length() >= 4) {
            return normalizedText.replaceAll("\\s+", "").contains(phraseWithoutSpaces);
        }
        return false;
    }

    private List<String> destinationContextVariants(String destination) {
        String cleanDestination = compact(destination);
        if (cleanDestination.isBlank()) {
            return List.of();
        }
        String firstSegment = firstCommaSegment(cleanDestination);
        return List.of(
                        cleanDestination,
                        firstSegment,
                        stripGenericDestinationPrefix(cleanDestination),
                        stripGenericDestinationPrefix(firstSegment))
                .stream()
                .map(this::compact)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private String shorterDestinationContext(String destination) {
        String stripped = stripGenericDestinationPrefix(destination);
        if (!stripped.equals(destination)) {
            return stripped;
        }
        String firstSegment = firstCommaSegment(destination);
        String strippedFirstSegment = stripGenericDestinationPrefix(firstSegment);
        if (!strippedFirstSegment.equals(firstSegment)) {
            return strippedFirstSegment;
        }
        return "";
    }

    private String firstCommaSegment(String value) {
        String cleanValue = compact(value);
        int commaIndex = cleanValue.indexOf(',');
        return commaIndex >= 0 ? compact(cleanValue.substring(0, commaIndex)) : cleanValue;
    }

    private String stripGenericDestinationPrefix(String value) {
        String cleanValue = compact(value);
        String normalized = normalizeText(cleanValue);
        for (String prefix : GENERIC_DESTINATION_PREFIXES) {
            if (normalized.startsWith(prefix) && cleanValue.length() > prefix.length()) {
                return compact(cleanValue.substring(prefix.length()));
            }
        }
        return cleanValue;
    }

    private ResolveOutcome resolveQuery(String query, String rawLocation, String destination, boolean persistCache) {
        String normalizedQuery = normalizeText(query);
        Optional<LocationResolutionCache> cached = cacheRepository.findByProviderAndNormalizedQuery(
                PROVIDER_NOMINATIM,
                normalizedQuery);
        if (cached.isPresent() && cached.get().getStatus() != LocationResolutionCache.Status.ERROR) {
            LocationResolutionCache cache = cached.get();
            cache.setLastUsedAt(LocalDateTime.now());
            if (persistCache) {
                cacheRepository.save(cache);
            }
            log.debug(
                    "Activity geocode cache hit query='{}', status={}, confidence={}, lat={}, lon={}",
                    query,
                    cache.getStatus(),
                    cache.getConfidence(),
                    cache.getLatitude(),
                    cache.getLongitude());
            return new ResolveOutcome(ResolutionResult.fromCache(cache), false);
        }

        waitForRateLimit();
        long startedAt = System.nanoTime();
        ResolutionResult result;
        try {
            List<NominatimCandidate> candidates = fetchCandidates(query);
            result = selectBestCandidate(query, rawLocation, destination, candidates);
        } catch (RuntimeException e) {
            log.warn("Activity geocoding failed for '{}': {}", query, e.getMessage());
            result = ResolutionResult.error(shortMessage(e));
        }
        long durationMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        log.debug(
                "Activity geocode external query='{}', status={}, confidence={}, durationMs={}, lat={}, lon={}, displayName='{}'",
                query,
                result.status(),
                result.score(),
                durationMs,
                result.latitude(),
                result.longitude(),
                result.displayName());

        if (persistCache) {
            upsertCache(normalizedQuery, result);
        }
        return new ResolveOutcome(result, true);
    }

    @SuppressWarnings("unchecked")
    private List<NominatimCandidate> fetchCandidates(String query) {
        java.net.URI uri = UriComponentsBuilder.fromHttpUrl(nominatimBaseUrl)
                .queryParam("q", query)
                .queryParam("format", "jsonv2")
                .queryParam("limit", 3)
                .queryParam("countrycodes", "vn")
                .queryParam("accept-language", "vi")
                .build()
                .encode()
                .toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "VivuPlan/1.0 (activity-coordinate-resolver)");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        List<Map<String, Object>> body = restTemplate.exchange(
                uri,
                HttpMethod.GET,
                entity,
                (Class<List<Map<String, Object>>>) (Class<?>) List.class).getBody();
        if (body == null || body.isEmpty()) {
            return List.of();
        }
        return body.stream()
                .map(this::toCandidate)
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<NominatimCandidate> toCandidate(Map<String, Object> raw) {
        try {
            double lat = Double.parseDouble(String.valueOf(raw.get("lat")));
            double lon = Double.parseDouble(String.valueOf(raw.get("lon")));
            if (!isValidVietnamCoordinate(lat, lon)) {
                return Optional.empty();
            }
            double importance = 0.0;
            if (raw.get("importance") != null) {
                importance = Double.parseDouble(String.valueOf(raw.get("importance")));
            }
            return Optional.of(new NominatimCandidate(
                    lat,
                    lon,
                    stringValue(raw.get("display_name")),
                    stringValue(raw.get("category")),
                    stringValue(raw.get("type")),
                    importance));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private ResolutionResult selectBestCandidate(
            String query,
            String rawLocation,
            String destination,
            List<NominatimCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return ResolutionResult.noResult();
        }
        List<ScoredCandidate> scored = candidates.stream()
                .map(candidate -> new ScoredCandidate(candidate, scoreCandidate(candidate, rawLocation, destination)))
                .sorted(Comparator.comparingInt(ScoredCandidate::score).reversed())
                .toList();
        ScoredCandidate best = scored.get(0);
        if (best.score() < MEDIUM_CONFIDENCE_SCORE) {
            return ResolutionResult.lowConfidence(best.candidate(), best.score());
        }
        return ResolutionResult.success(
                best.candidate(),
                best.score(),
                best.score() >= HIGH_CONFIDENCE_SCORE
                        ? Activity.CoordinateConfidence.HIGH
                        : Activity.CoordinateConfidence.MEDIUM,
                "Resolved from query: " + query);
    }

    private int scoreCandidate(NominatimCandidate candidate, String rawLocation, String destination) {
        String display = normalizeText(candidate.displayName());
        String location = normalizeText(rawLocation);
        String destinationText = normalizeText(destination);
        List<String> tokens = significantTokens(location);
        long matchedTokens = tokens.stream().filter(display::contains).count();

        int score = (int) Math.min(60, matchedTokens * 22);
        if (!location.isBlank() && display.contains(location)) {
            score += 35;
        }
        if (!destinationText.isBlank() && display.contains(destinationText)) {
            score += 15;
        }
        if (isUsefulNominatimCategory(candidate.category(), candidate.type())) {
            score += 8;
        }
        if (candidate.importance() > 0) {
            score += Math.min(10, (int) Math.round(candidate.importance() * 10));
        }
        return Math.min(100, score);
    }

    private void applyResolvedCoordinate(Target target, ResolutionResult result) {
        target.setLatitude(result.latitude());
        target.setLongitude(result.longitude());
        target.setCoordinateSource(Activity.CoordinateSource.GEOCODED_LOCATION);
        target.setCoordinateConfidence(result.confidence());
    }

    private void upsertCache(String normalizedQuery, ResolutionResult result) {
        LocationResolutionCache cache = cacheRepository
                .findByProviderAndNormalizedQuery(PROVIDER_NOMINATIM, normalizedQuery)
                .orElseGet(LocationResolutionCache::new);
        cache.setProvider(PROVIDER_NOMINATIM);
        cache.setNormalizedQuery(normalizedQuery);
        cache.setStatus(result.status());
        cache.setLatitude(result.latitude());
        cache.setLongitude(result.longitude());
        cache.setDisplayName(truncate(result.displayName(), 500));
        cache.setConfidence(result.score());
        cache.setErrorMessage(truncate(result.message(), 160));
        cache.setLastUsedAt(LocalDateTime.now());
        cacheRepository.save(cache);
    }

    private void waitForRateLimit() {
        if (minRequestIntervalMs <= 0) {
            return;
        }
        synchronized (rateLimitLock) {
            long now = System.currentTimeMillis();
            long waitMs = minRequestIntervalMs - (now - lastExternalRequestAt);
            if (waitMs > 0) {
                try {
                    Thread.sleep(waitMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            lastExternalRequestAt = System.currentTimeMillis();
        }
    }

    private boolean isTooGenericLocation(String rawLocation, String activityType) {
        String location = normalizeText(rawLocation);
        List<String> significantTokens = significantTokens(location);
        if ("ACCOMMODATION".equalsIgnoreCase(activityType)
                && containsAny(location, "khach san", "homestay", "resort", "luu tru", "da chon")) {
            return true;
        }
        if (isBroadAreaDescription(location)) {
            return true;
        }
        if (significantTokens.size() >= 2) {
            return false;
        }
        return location.equals("dia phuong")
                || location.equals("gan khach san")
                || location.equals("nha hang gan do")
                || location.equals("quan an gan do")
                || location.equals("khach san")
                || location.equals("homestay")
                || location.equals("resort")
                || location.equals("trung tam")
                || location.length() < 4;
    }

    private boolean isBroadAreaDescription(String location) {
        return location.equals("trung tam thanh pho")
                || location.equals("khu trung tam")
                || location.equals("khu vuc trung tam")
                || location.equals("gan ben xe")
                || location.equals("gan san bay")
                || location.equals("gan khach san")
                || location.equals("gan cho")
                || location.equals("ven bien")
                || location.equals("gan bien")
                || location.equals("khu an uong")
                || location.equals("khu am thuc")
                || location.equals("khu vui choi")
                || location.equals("khu du lich")
                || location.equals("bai bien")
                || location.matches("^(nha hang|quan an|cafe|ca phe)\\s+(gan|quanh|tai|trong).+")
                || location.matches("^(khach san|homestay|resort)\\s+(gan|quanh|tai|trong).+");
    }

    private List<String> significantTokens(String normalizedText) {
        if (normalizedText == null || normalizedText.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(normalizedText.split("[^a-z0-9]+"))
                .map(String::trim)
                .filter(token -> token.length() >= 3)
                .filter(token -> !WEAK_TOKENS.contains(token))
                .distinct()
                .collect(Collectors.toList());
    }

    private boolean isUsefulNominatimCategory(String category, String type) {
        String normalized = normalizeText(category + " " + type);
        return containsAny(normalized,
                "tourism", "amenity", "historic", "leisure", "natural", "place", "shop", "building");
    }

    private boolean isValidVietnamCoordinate(Double lat, Double lon) {
        return lat != null
                && lon != null
                && Double.isFinite(lat)
                && Double.isFinite(lon)
                && lat >= MIN_VN_LAT
                && lat <= MAX_VN_LAT
                && lon >= MIN_VN_LON
                && lon <= MAX_VN_LON;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace("đ", "d")
                .replace("Đ", "D")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private String normalizeComparableText(String value) {
        return normalizeText(value)
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String compact(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String shortMessage(RuntimeException e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            message = e.getClass().getSimpleName();
        }
        return truncate(message, 160);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)).trim() + "...";
    }

    public record BatchResult(List<ItemResult> items) {
        public long appliedCount() {
            return items.stream().filter(ItemResult::applied).count();
        }
    }

    public record ItemResult(
            Integer dayNumber,
            Long activityId,
            Integer sortOrder,
            String name,
            String type,
            String location,
            String status,
            String query,
            String displayName,
            Double latitude,
            Double longitude,
            Integer confidenceScore,
            String coordinateConfidence,
            String coordinateSource,
            boolean cacheHit,
            boolean applied,
            String message) {

        private static ItemResult skipped(Target target, String status, String query, String displayName, String message) {
            return new ItemResult(
                    target.dayNumber(),
                    target.id(),
                    target.sortOrder(),
                    target.name(),
                    target.type(),
                    target.location(),
                    status,
                    query,
                    displayName,
                    target.latitude(),
                    target.longitude(),
                    null,
                    target.coordinateConfidence() != null ? target.coordinateConfidence().name() : null,
                    target.coordinateSource() != null ? target.coordinateSource().name() : null,
                    false,
                    false,
                    message);
        }
    }

    private record ResolveOutcome(ResolutionResult result, boolean externalAttempt) {}

    private record ResolutionResult(
            LocationResolutionCache.Status status,
            Double latitude,
            Double longitude,
            String displayName,
            Integer score,
            Activity.CoordinateConfidence confidence,
            boolean cacheHit,
            String message) {

        private static ResolutionResult fromCache(LocationResolutionCache cache) {
            return new ResolutionResult(
                    cache.getStatus(),
                    cache.getLatitude(),
                    cache.getLongitude(),
                    cache.getDisplayName(),
                    cache.getConfidence(),
                    confidenceFromScore(cache.getConfidence()),
                    true,
                    cache.getErrorMessage());
        }

        private static ResolutionResult success(
                NominatimCandidate candidate,
                int score,
                Activity.CoordinateConfidence confidence,
                String message) {
            return new ResolutionResult(
                    LocationResolutionCache.Status.SUCCESS,
                    candidate.lat(),
                    candidate.lon(),
                    candidate.displayName(),
                    score,
                    confidence,
                    false,
                    message);
        }

        private static ResolutionResult noResult() {
            return new ResolutionResult(LocationResolutionCache.Status.NO_RESULT, null, null, null, null, null, false, "No result");
        }

        private static ResolutionResult lowConfidence(NominatimCandidate candidate, int score) {
            return new ResolutionResult(
                    LocationResolutionCache.Status.LOW_CONFIDENCE,
                    candidate.lat(),
                    candidate.lon(),
                    candidate.displayName(),
                    score,
                    Activity.CoordinateConfidence.LOW,
                    false,
                    "Candidate confidence is too low");
        }

        private static ResolutionResult error(String message) {
            return new ResolutionResult(LocationResolutionCache.Status.ERROR, null, null, null, null, null, false, message);
        }

        private ItemResult toItemResult(Target target, String query, boolean canApply) {
            boolean success = status == LocationResolutionCache.Status.SUCCESS;
            return new ItemResult(
                    target.dayNumber(),
                    target.id(),
                    target.sortOrder(),
                    target.name(),
                    target.type(),
                    target.location(),
                    status.name(),
                    query,
                    displayName,
                    latitude != null ? latitude : target.latitude(),
                    longitude != null ? longitude : target.longitude(),
                    score,
                    confidence != null ? confidence.name() : null,
                    success ? Activity.CoordinateSource.GEOCODED_LOCATION.name()
                            : target.coordinateSource() != null ? target.coordinateSource().name() : null,
                    cacheHit,
                    success && canApply,
                    message);
        }

        private static Activity.CoordinateConfidence confidenceFromScore(Integer score) {
            if (score == null) {
                return null;
            }
            if (score >= HIGH_CONFIDENCE_SCORE) {
                return Activity.CoordinateConfidence.HIGH;
            }
            if (score >= MEDIUM_CONFIDENCE_SCORE) {
                return Activity.CoordinateConfidence.MEDIUM;
            }
            return Activity.CoordinateConfidence.LOW;
        }
    }

    private record NominatimCandidate(
            double lat,
            double lon,
            String displayName,
            String category,
            String type,
            double importance) {}

    private record ScoredCandidate(NominatimCandidate candidate, int score) {}

    private interface Target {
        Integer dayNumber();
        Long id();
        Integer sortOrder();
        String name();
        String type();
        String location();
        Long placeId();
        Double latitude();
        Double longitude();
        Activity.CoordinateSource coordinateSource();
        Activity.CoordinateConfidence coordinateConfidence();
        void setLatitude(Double latitude);
        void setLongitude(Double longitude);
        void setCoordinateSource(Activity.CoordinateSource source);
        void setCoordinateConfidence(Activity.CoordinateConfidence confidence);

        static Target forResponse(Integer dayNumber, TripDto.ActivityResponse activity) {
            return new Target() {
                @Override public Integer dayNumber() { return dayNumber; }
                @Override public Long id() { return activity.getId(); }
                @Override public Integer sortOrder() { return activity.getSortOrder(); }
                @Override public String name() { return activity.getName(); }
                @Override public String type() { return activity.getType(); }
                @Override public String location() { return activity.getLocation(); }
                @Override public Long placeId() { return activity.getPlaceId(); }
                @Override public Double latitude() { return activity.getLatitude(); }
                @Override public Double longitude() { return activity.getLongitude(); }
                @Override public Activity.CoordinateSource coordinateSource() { return parseSource(activity.getCoordinateSource()); }
                @Override public Activity.CoordinateConfidence coordinateConfidence() { return parseConfidence(activity.getCoordinateConfidence()); }
                @Override public void setLatitude(Double latitude) { activity.setLatitude(latitude); }
                @Override public void setLongitude(Double longitude) { activity.setLongitude(longitude); }
                @Override public void setCoordinateSource(Activity.CoordinateSource source) { activity.setCoordinateSource(source != null ? source.name() : null); }
                @Override public void setCoordinateConfidence(Activity.CoordinateConfidence confidence) { activity.setCoordinateConfidence(confidence != null ? confidence.name() : null); }
            };
        }

        static Target forEntity(Integer dayNumber, Activity activity) {
            return new Target() {
                @Override public Integer dayNumber() { return dayNumber; }
                @Override public Long id() { return activity.getId(); }
                @Override public Integer sortOrder() { return activity.getSortOrder(); }
                @Override public String name() { return activity.getName(); }
                @Override public String type() { return activity.getType() != null ? activity.getType().name() : null; }
                @Override public String location() { return activity.getLocation(); }
                @Override public Long placeId() { return activity.getPlace() != null ? activity.getPlace().getId() : null; }
                @Override public Double latitude() { return activity.getLatitude(); }
                @Override public Double longitude() { return activity.getLongitude(); }
                @Override public Activity.CoordinateSource coordinateSource() { return activity.getCoordinateSource(); }
                @Override public Activity.CoordinateConfidence coordinateConfidence() { return activity.getCoordinateConfidence(); }
                @Override public void setLatitude(Double latitude) { activity.setLatitude(latitude); }
                @Override public void setLongitude(Double longitude) { activity.setLongitude(longitude); }
                @Override public void setCoordinateSource(Activity.CoordinateSource source) { activity.setCoordinateSource(source); }
                @Override public void setCoordinateConfidence(Activity.CoordinateConfidence confidence) { activity.setCoordinateConfidence(confidence); }
            };
        }

        private static Activity.CoordinateSource parseSource(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            try {
                return Activity.CoordinateSource.valueOf(value);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        private static Activity.CoordinateConfidence parseConfidence(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            try {
                return Activity.CoordinateConfidence.valueOf(value);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }
}
