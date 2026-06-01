package com.vivuplan.vivuplan_be.service;

import com.vivuplan.vivuplan_be.dto.TripDto;
import com.vivuplan.vivuplan_be.entity.Destination;
import com.vivuplan.vivuplan_be.exception.AiGenerationException;
import com.vivuplan.vivuplan_be.repository.DestinationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DestinationSuggestionService {

    private static final String RATE_LIMIT_MESSAGE =
            "Bạn đã yêu cầu gợi ý quá nhiều lần. Vui lòng thử lại sau ít phút.";
    private static final String COOLDOWN_MESSAGE =
            "Vui lòng chờ một chút trước khi yêu cầu gợi ý điểm đến mới.";
    private static final String AI_SUGGESTION_ERROR =
            "AI chưa gợi ý được điểm đến phù hợp. Vui lòng thử lại.";
    private static final int MAX_CATALOG_ITEMS = 20;
    private static final int MAX_CACHE_ENTRIES = 300;
    private static final int MAX_DESTINATION_NAME_LENGTH = 80;
    private static final int MAX_REASON_LENGTH = 180;
    private static final Set<String> BUDGET_DURATION_FITS = Set.of("Phù hợp", "Khá phù hợp", "Cần cân nhắc");
    private static final Set<String> STYLE_FITS = Set.of("Rất hợp", "Phù hợp", "Khá phù hợp");

    private final DestinationRepository destinationRepository;
    private final AiService aiService;
    private final BillingService billingService;
    private final UserPromptGuardService userPromptGuardService;
    private final Map<Long, SuggestionRateWindow> rateWindows = new ConcurrentHashMap<>();
    private final Map<String, SuggestionCacheEntry> suggestionCache = new ConcurrentHashMap<>();

    @Value("${app.ai.destination-suggest.limit:2}")
    private int suggestionLimit;

    @Value("${app.ai.destination-suggest.window-minutes:1440}")
    private int suggestionWindowMinutes;

    @Value("${app.ai.destination-suggest.cooldown-seconds:60}")
    private int suggestionCooldownSeconds;

    @Value("${app.ai.destination-suggest.cache-ttl-hours:24}")
    private int suggestionCacheTtlHours;

    public TripDto.DestinationSuggestionResponse suggest(Long userId, TripDto.DestinationSuggestionRequest req) {
        userPromptGuardService.validateAndSanitizeDestinationSuggestionRequest(req);
        validatePlanningContext(req);
        billingService.requirePlanCredit(userId);

        long now = System.currentTimeMillis();
        String cacheKey = buildCacheKey(userId, req);
        TripDto.DestinationSuggestionResponse cached = getCached(cacheKey, now);
        if (cached != null) {
            return cached;
        }

        enforceRateLimit(userId, now);

        List<Destination> catalogDestinations = destinationRepository.findByActiveTrueOrderByDisplayOrderAscNameAsc();
        Set<String> catalogNames = catalogDestinations.stream()
                .map(Destination::getName)
                .map(this::normalize)
                .collect(Collectors.toSet());
        String catalogContext = buildCatalogContext(req, catalogDestinations);

        List<TripDto.DestinationSuggestion> suggestions = aiService.suggestDestinations(req, catalogContext);
        List<TripDto.DestinationSuggestion> cleaned = validateAndCleanSuggestions(suggestions, catalogNames);

        TripDto.DestinationSuggestionResponse response = new TripDto.DestinationSuggestionResponse();
        response.setSuggestions(cleaned);
        putCached(cacheKey, response, now);
        return response;
    }

    private void enforceRateLimit(Long userId, long now) {
        if (suggestionLimit <= 0 && suggestionCooldownSeconds <= 0) {
            return;
        }

        long windowMillis = Math.max(1, suggestionWindowMinutes) * 60_000L;
        long cooldownMillis = Math.max(0, suggestionCooldownSeconds) * 1_000L;
        rateWindows.compute(userId, (key, window) -> {
            if (window == null || now - window.startedAt() >= windowMillis) {
                return new SuggestionRateWindow(now, 1, now);
            }
            if (cooldownMillis > 0 && now - window.lastSuggestedAt() < cooldownMillis) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, COOLDOWN_MESSAGE);
            }
            if (suggestionLimit > 0 && window.count() >= suggestionLimit) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, RATE_LIMIT_MESSAGE);
            }
            return new SuggestionRateWindow(window.startedAt(), window.count() + 1, now);
        });
    }

    private void validatePlanningContext(TripDto.DestinationSuggestionRequest req) {
        if (req.getStartDate() == null || req.getEndDate() == null) {
            throw new IllegalArgumentException("Ngày đi và ngày về không được để trống");
        }
        if (req.getStartDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Ngày đi không được ở trong quá khứ");
        }
        if (req.getStartDate().isAfter(LocalDate.now().plusYears(1))) {
            throw new IllegalArgumentException("Ngày đi không được quá 1 năm kể từ hôm nay");
        }

        int tripDays = resolveTripDays(req);
        if (tripDays <= 0) {
            throw new IllegalArgumentException("Thời gian chuyến đi không hợp lệ");
        }
        if (tripDays > 30) {
            throw new IllegalArgumentException("Thời gian chuyến đi tối đa là 30 ngày");
        }

        if (req.getBudgetPerPerson() <= 0) {
            throw new IllegalArgumentException("Vui lòng nhập ngân sách.");
        }
        validateBudgetPerPerson(req.getBudgetPerPerson(), tripDays);

        int travelers = req.getTravelerCount() != null ? req.getTravelerCount() : 1;
        if (travelers < 1 || travelers > 30) {
            throw new IllegalArgumentException("Số người phải từ 1 đến 30");
        }
        req.setDays(tripDays);
        req.setTravelerCount(travelers);
    }

    private int resolveTripDays(TripDto.DestinationSuggestionRequest req) {
        if (req.getStartDate() != null && req.getEndDate() != null) {
            long days = ChronoUnit.DAYS.between(req.getStartDate(), req.getEndDate()) + 1;
            if (days < Integer.MIN_VALUE || days > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Thời gian chuyến đi không hợp lệ");
            }
            return (int) days;
        }
        return req.getDays();
    }

    private void validateBudgetPerPerson(long budgetPerPerson, int days) {
        long absurdMaximum = Math.max(200_000_000L, days * 50_000_000L);
        long unrealisticDailyMinimum = days >= 4 ? 350_000L : 300_000L;
        long absoluteMinimum = days <= 1 ? 300_000L : 500_000L;
        long minimum = Math.max(absoluteMinimum, unrealisticDailyMinimum * Math.max(1, days));

        if (budgetPerPerson < minimum) {
            throw new IllegalArgumentException("Ngân sách quá thấp cho thời gian chuyến đi. Vui lòng kiểm tra lại.");
        }
        if (budgetPerPerson > absurdMaximum) {
            throw new IllegalArgumentException("Ngân sách đang quá cao so với thời gian chuyến đi. Vui lòng kiểm tra lại.");
        }
    }

    private String buildCatalogContext(TripDto.DestinationSuggestionRequest req, List<Destination> destinations) {
        if (destinations.isEmpty()) {
            return "[]";
        }

        List<String> rows = new ArrayList<>();
        destinations.stream()
                .sorted(Comparator.comparingInt((Destination destination) -> scoreDestination(destination, req)).reversed())
                .limit(MAX_CATALOG_ITEMS)
                .forEach(destination -> rows.add(String.format(Locale.ROOT,
                        "- %s | region=%s | category=%s | tags=%s | recommendedDays=%s | budget=%s-%s | summary=%s",
                        nullToBlank(destination.getName()),
                        destination.getRegion() != null ? destination.getRegion().getLabel() : "",
                        destination.getCategory() != null ? destination.getCategory().name() : "",
                        String.join(", ", destination.getTags() != null ? destination.getTags() : List.of()),
                        nullToBlank(destination.getRecommendedDays()),
                        destination.getEstimatedBudgetMin() != null ? destination.getEstimatedBudgetMin() : "",
                        destination.getEstimatedBudgetMax() != null ? destination.getEstimatedBudgetMax() : "",
                        truncate(nullToBlank(destination.getSummary()), 180))));
        return String.join("\n", rows);
    }

    private int scoreDestination(Destination destination, TripDto.DestinationSuggestionRequest req) {
        String searchable = normalize(String.join(" ",
                nullToBlank(destination.getName()),
                nullToBlank(destination.getProvince()),
                nullToBlank(destination.getTourismRegion()),
                destination.getCategory() != null ? destination.getCategory().name() : "",
                nullToBlank(destination.getTag()),
                nullToBlank(destination.getSummary()),
                nullToBlank(destination.getDescription()),
                String.join(" ", destination.getTags() != null ? destination.getTags() : List.of())));
        String tags = normalize(String.join(" ", destination.getTags() != null ? destination.getTags() : List.of()));
        String style = normalize(req.getStyle());
        String departure = normalize(req.getDeparture());
        String preferences = normalize(String.join(" ", nullToBlank(req.getMustVisit()), nullToBlank(req.getNotes())));
        String avoid = normalize(req.getAvoid());

        int score = (int) Math.round((destination.getRating() != null ? destination.getRating() : 4.0) * 10);
        if (Boolean.TRUE.equals(destination.getFeatured())) score += 10;

        if ((style.contains("foodie") || style.contains("cultural"))
                && containsAny(searchable, "food", "culture", "heritage", "old-town", "unesco", "am thuc", "van hoa")) {
            score += 22;
        }
        if (style.contains("adventure")
                && containsAny(searchable, "mountain", "adventure", "cave", "roadtrip", "trekking", "national-park", "nui")) {
            score += 24;
        }
        if (style.contains("relaxing")
                && containsAny(searchable, "beach", "island", "resort", "quiet", "cool-weather", "bien", "dao")) {
            score += 22;
        }

        long budget = req.getBudgetPerPerson();
        if (destination.getEstimatedBudgetMin() != null && budget >= destination.getEstimatedBudgetMin()) score += 8;
        if (destination.getEstimatedBudgetMax() != null && budget <= destination.getEstimatedBudgetMax()) score += 6;
        if (budget < 2_000_000 && destination.getEstimatedBudgetMin() != null
                && destination.getEstimatedBudgetMin() <= 1_500_000) score += 10;
        if (budget >= 6_000_000 && containsAny(tags, "island", "resort", "beach", "dao", "bien")) score += 8;

        int days = req.getDays();
        String recommendedDays = normalize(destination.getRecommendedDays());
        if (days <= 3 && recommendedDays.contains("1-2")) score += 8;
        if (days <= 4 && recommendedDays.contains("2-3")) score += 6;

        if (departure.contains("ha noi") && destination.getRegion() == Destination.Region.MIEN_BAC) score += 8;
        if ((departure.contains("tp.hcm") || departure.contains("ho chi minh") || departure.contains("sai gon"))
                && destination.getRegion() == Destination.Region.MIEN_NAM) score += 8;
        if (departure.contains("da nang") && destination.getRegion() == Destination.Region.MIEN_TRUNG) score += 8;

        if (!preferences.isBlank()) {
            for (String term : preferences.split("\\s+")) {
                if (term.length() >= 4 && searchable.contains(term)) score += 2;
            }
        }
        if (!avoid.isBlank()) {
            for (String term : avoid.split("\\s+")) {
                if (term.length() >= 4 && searchable.contains(term)) score -= 12;
            }
        }
        return score;
    }

    private List<TripDto.DestinationSuggestion> validateAndCleanSuggestions(
            List<TripDto.DestinationSuggestion> suggestions,
            Set<String> catalogNames) {
        if (suggestions == null || suggestions.isEmpty()) {
            throw new AiGenerationException(AI_SUGGESTION_ERROR);
        }

        List<TripDto.DestinationSuggestion> cleaned = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (TripDto.DestinationSuggestion suggestion : suggestions) {
            if (suggestion == null) {
                continue;
            }
            String name = blankToNull(suggestion.getName());
            String reason = blankToNull(suggestion.getReason());
            if (name == null || reason == null) {
                continue;
            }
            if (name.length() > MAX_DESTINATION_NAME_LENGTH || reason.length() > MAX_REASON_LENGTH) {
                continue;
            }
            String normalizedName = normalize(name);
            if (!names.add(normalizedName)) {
                continue;
            }
            suggestion.setName(name);
            suggestion.setReason(reason);
            suggestion.setRegion(defaultText(suggestion.getRegion(), "Việt Nam"));
            suggestion.setBudgetFit(validFitLabel(suggestion.getBudgetFit(), BUDGET_DURATION_FITS));
            suggestion.setDurationFit(validFitLabel(suggestion.getDurationFit(), BUDGET_DURATION_FITS));
            suggestion.setStyleFit(validFitLabel(suggestion.getStyleFit(), STYLE_FITS));
            suggestion.setFromCatalog(catalogNames.contains(normalizedName));
            cleaned.add(suggestion);
            if (cleaned.size() == 3) {
                break;
            }
        }

        if (cleaned.size() != 3) {
            throw new AiGenerationException(AI_SUGGESTION_ERROR);
        }
        return cleaned;
    }

    private TripDto.DestinationSuggestionResponse getCached(String cacheKey, long now) {
        SuggestionCacheEntry cached = suggestionCache.get(cacheKey);
        if (cached == null) {
            return null;
        }
        if (cached.expiresAt() <= now) {
            suggestionCache.remove(cacheKey);
            return null;
        }
        return responseFrom(cached.suggestions());
    }

    private void putCached(String cacheKey, TripDto.DestinationSuggestionResponse response, long now) {
        if (suggestionCache.size() > MAX_CACHE_ENTRIES) {
            suggestionCache.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        }
        long ttlMillis = Math.max(1, suggestionCacheTtlHours) * 3_600_000L;
        suggestionCache.put(cacheKey, new SuggestionCacheEntry(copySuggestions(response.getSuggestions()), now + ttlMillis));
    }

    private TripDto.DestinationSuggestionResponse responseFrom(List<TripDto.DestinationSuggestion> suggestions) {
        TripDto.DestinationSuggestionResponse response = new TripDto.DestinationSuggestionResponse();
        response.setSuggestions(copySuggestions(suggestions));
        return response;
    }

    private List<TripDto.DestinationSuggestion> copySuggestions(List<TripDto.DestinationSuggestion> source) {
        if (source == null) {
            return List.of();
        }
        return source.stream().map(suggestion -> {
            TripDto.DestinationSuggestion copy = new TripDto.DestinationSuggestion();
            copy.setName(suggestion.getName());
            copy.setRegion(suggestion.getRegion());
            copy.setReason(suggestion.getReason());
            copy.setBudgetFit(suggestion.getBudgetFit());
            copy.setDurationFit(suggestion.getDurationFit());
            copy.setStyleFit(suggestion.getStyleFit());
            copy.setFromCatalog(suggestion.getFromCatalog());
            return copy;
        }).toList();
    }

    private String buildCacheKey(Long userId, TripDto.DestinationSuggestionRequest req) {
        return String.join("|",
                String.valueOf(userId),
                normalizeForCache(req.getDeparture()),
                String.valueOf(req.getStartDate()),
                String.valueOf(req.getEndDate()),
                String.valueOf(req.getDays()),
                String.valueOf(req.getBudgetPerPerson()),
                String.valueOf(req.getBudgetTotal()),
                normalizeForCache(req.getBudgetMode()),
                String.valueOf(req.getTravelerCount()),
                normalizeForCache(req.getStyle()),
                normalizeForCache(req.getGroupType()),
                normalizeForCache(req.getOutboundTransport()),
                normalizeForCache(req.getLocalTransport()),
                normalizeForCache(req.getMustVisit()),
                normalizeForCache(req.getAvoid()),
                normalizeForCache(req.getNotes()));
    }

    private String validFitLabel(String value, Set<String> allowed) {
        String normalized = defaultText(value, "");
        if (!allowed.contains(normalized)) {
            throw new AiGenerationException(AI_SUGGESTION_ERROR);
        }
        return normalized;
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 1).trim() + "...";
    }

    private String normalizeForCache(String value) {
        return normalize(value).replaceAll("\\s+", " ").trim();
    }

    private String normalize(String value) {
        if (value == null) return "";
        String lower = value.toLowerCase(Locale.ROOT);
        String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "").replace('đ', 'd').trim();
    }

    private record SuggestionRateWindow(long startedAt, int count, long lastSuggestedAt) {
    }

    private record SuggestionCacheEntry(List<TripDto.DestinationSuggestion> suggestions, long expiresAt) {
    }
}
