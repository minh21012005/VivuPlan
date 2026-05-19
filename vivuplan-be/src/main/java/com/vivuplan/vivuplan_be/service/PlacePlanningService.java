package com.vivuplan.vivuplan_be.service;

import com.vivuplan.vivuplan_be.dto.TripDto;
import com.vivuplan.vivuplan_be.entity.Activity;
import com.vivuplan.vivuplan_be.entity.Destination;
import com.vivuplan.vivuplan_be.entity.Place;
import com.vivuplan.vivuplan_be.repository.DestinationRepository;
import com.vivuplan.vivuplan_be.repository.PlaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlacePlanningService {

    private static final int MIN_CONTEXT_PLACES = 10;
    private static final int MAX_CONTEXT_PLACES = 30;
    private static final double MAX_NEARBY_CONTEXT_RATIO = 0.20;
    private static final int REQUEST_CONTEXT_BOOST = 4;
    private static final int REQUEST_MATCH_SCORE = 60;
    private static final int WEATHER_FRIENDLY_SCORE = 18;
    private static final Map<String, List<String>> NEARBY_DESTINATIONS = Map.of(
            "da nang", List.of("Hội An", "Mỹ Sơn"),
            "hoi an", List.of("Đà Nẵng", "Mỹ Sơn"),
            "my son", List.of("Hội An", "Đà Nẵng"),
            "tp ho chi minh", List.of("Vũng Tàu", "Tây Ninh", "Bến Tre"),
            "can tho", List.of("Châu Đốc - An Giang", "Đồng Tháp", "Bến Tre"));

    private final PlaceRepository placeRepository;
    private final DestinationRepository destinationRepository;

    public String buildVerifiedPlacesContext(TripDto.GenerateRequest req) {
        if (req == null) {
            return "none";
        }
        List<Place> places = selectPromptPlaces(req);
        if (places.isEmpty()) {
            return "none";
        }
        return places.stream()
                .map(this::formatPlaceForPrompt)
                .collect(Collectors.joining("\n"));
    }

    public void enrichScheduleWithVerifiedPlaces(List<TripDto.DayResponse> schedule, String destination) {
        if (schedule == null || schedule.isEmpty()) {
            return;
        }
        List<Place> places = loadVerifiedPlacesForDestination(destination);
        if (places.isEmpty()) {
            return;
        }
        for (TripDto.DayResponse day : schedule) {
            if (day.getActivities() == null) {
                continue;
            }
            for (TripDto.ActivityResponse activity : day.getActivities()) {
                findBestMatchingPlace(activity, places).ifPresent(place -> applyPlace(activity, place));
            }
        }
    }

    public void attachVerifiedPlace(Activity activity, Long placeId) {
        if (activity == null || placeId == null) {
            return;
        }
        placeRepository.findById(placeId).ifPresent(activity::setPlace);
    }

    List<Place> selectPromptPlaces(TripDto.GenerateRequest req) {
        List<CandidatePlace> candidates = loadCandidatePlacesForRequest(req);
        if (candidates.isEmpty()) {
            return List.of();
        }

        int limit = resolvePromptPlaceLimit(req);
        List<ScoredPlace> ranked = candidates.stream()
                .map(candidate -> new ScoredPlace(candidate.place(), scorePlace(candidate.place(), req), candidate.nearby()))
                .sorted(Comparator
                        .comparingInt(ScoredPlace::score).reversed()
                        .thenComparing(scored -> Optional.ofNullable(scored.place().getRating()).orElse(0.0), Comparator.reverseOrder())
                        .thenComparing(scored -> scored.place().getName(), String.CASE_INSENSITIVE_ORDER))
                .toList();

        return diversify(ranked, limit, resolveNearbyLimit(limit));
    }

    private List<Place> diversify(List<ScoredPlace> ranked, int limit, int nearbyLimit) {
        List<Place> selected = new ArrayList<>();
        Set<String> selectedKeys = new HashSet<>();
        Map<Place.PlaceType, Integer> typeCounts = new EnumMap<>(Place.PlaceType.class);
        int selectedNearby = 0;

        for (ScoredPlace scored : ranked) {
            if (selected.size() >= limit) {
                break;
            }
            if (scored.nearby() && selectedNearby >= nearbyLimit) {
                continue;
            }
            Place place = scored.place();
            int sameTypeCount = typeCounts.getOrDefault(place.getType(), 0);
            if (sameTypeCount >= Math.max(3, limit / 4) && selected.size() < limit - 3) {
                continue;
            }
            selected.add(place);
            selectedKeys.add(placeKey(place));
            typeCounts.merge(place.getType(), 1, Integer::sum);
            if (scored.nearby()) {
                selectedNearby++;
            }
        }

        if (selected.size() < Math.min(limit, ranked.size())) {
            for (ScoredPlace scored : ranked) {
                if (selected.size() >= limit) {
                    break;
                }
                if (scored.nearby() && selectedNearby >= nearbyLimit) {
                    continue;
                }
                Place place = scored.place();
                if (selectedKeys.add(placeKey(place))) {
                    selected.add(place);
                    if (scored.nearby()) {
                        selectedNearby++;
                    }
                }
            }
        }
        return selected;
    }

    private int scorePlace(Place place, TripDto.GenerateRequest req) {
        int score = 0;
        if (Boolean.TRUE.equals(place.getVerified())) {
            score += 20;
        }
        if (place.getRating() != null) {
            score += Math.round(place.getRating().floatValue() * 8);
        }
        if (!sameDestination(place.getDestination(), req.getDestination())) {
            score -= 18;
        }

        score += scoreByStyle(place, normalizeText(req.getStyle()));
        score += scoreByGroup(place, normalizeText(req.getGroupType()));
        score += scoreByBudget(place, req);
        score += scoreByUserRequest(place, req);
        score += scoreByWeather(place, req.getWeatherForecast());
        return score;
    }

    private int scoreByStyle(Place place, String style) {
        if (style.isBlank()) {
            return 0;
        }
        Place.PlaceType type = place.getType();
        String text = normalizedPlaceText(place);
        String tags = normalizedTags(place);
        return switch (style) {
            case "foodie" -> type == Place.PlaceType.FOOD || type == Place.PlaceType.CAFE || tags.contains("food") ? 34 : 0;
            case "cultural" -> type == Place.PlaceType.ATTRACTION && (containsAny(text,
                    "bao tang", "di tich", "chua", "den", "thap", "pho co", "lang nghe", "unesco") || containsAny(tags, "museum", "heritage", "spiritual")) ? 32 : 8;
            case "adventure" -> type == Place.PlaceType.ACTIVITY || containsAny(text,
                    "trekking", "thuyen", "kayak", "zipline", "deo", "thac", "nui", "hang", "dao") || tags.contains("adventure") ? 32 : 0;
            case "relaxing" -> type == Place.PlaceType.CAFE || containsAny(text,
                    "bien", "ho", "spa", "dao", "vuon", "dao nhe", "di dao", "ngam canh") || containsAny(tags, "couple", "beach", "island") ? 22 : 0;
            default -> 0;
        };
    }

    private int scoreByGroup(Place place, String groupType) {
        String text = normalizedPlaceText(place);
        String tags = normalizedTags(place);
        Place.PlaceType type = place.getType();
        return switch (groupType) {
            case "family" -> type == Place.PlaceType.ATTRACTION || containsAny(text, "bao tang", "cong vien", "cho", "vuon") || tags.contains("family") ? 18 : 0;
            case "couple" -> type == Place.PlaceType.CAFE || containsAny(text, "hoang hon", "bien", "view", "pho co", "ngam canh") || tags.contains("couple") ? 16 : 0;
            case "solo" -> type == Place.PlaceType.CAFE || type == Place.PlaceType.ATTRACTION ? 10 : 0;
            default -> 0;
        };
    }

    private int scoreByBudget(Place place, TripDto.GenerateRequest req) {
        long perPersonPerDay = resolvePerPersonPerDayBudget(req);
        if (perPersonPerDay <= 0 || place.getEstimatedCostMax() == null) {
            return 0;
        }
        long maxCost = Math.max(0, place.getEstimatedCostMax());
        if (maxCost == 0) {
            return 18;
        }
        if (perPersonPerDay < 500_000) {
            return maxCost <= 150_000 ? 18 : maxCost <= 400_000 ? 6 : -16;
        }
        if (perPersonPerDay < 900_000) {
            return maxCost <= 400_000 ? 14 : maxCost <= 800_000 ? 4 : -8;
        }
        return maxCost >= 300_000 ? 6 : 2;
    }

    private int scoreByUserRequest(Place place, TripDto.GenerateRequest req) {
        String requestText = normalizeText(String.join(" ",
                nullToBlank(req.getMustVisit()),
                nullToBlank(req.getAvoid()),
                extractUserNotes(req.getNotes())));
        if (requestText.isBlank()) {
            return 0;
        }
        String placeText = normalizedPlaceText(place);
        int score = 0;
        for (String token : requestTokens(requestText)) {
            if (token.length() >= 3 && placeText.contains(token)) {
                score += REQUEST_MATCH_SCORE;
            }
        }
        return Math.min(score, REQUEST_MATCH_SCORE * 2);
    }

    private int scoreByWeather(Place place, String weatherForecast) {
        if (weatherForecast == null || !normalizeText(weatherForecast).contains("high rain risk")) {
            return 0;
        }
        if (place.getIndoorOutdoor() == Place.IndoorOutdoor.INDOOR
                || place.getWeatherSensitivity() == Place.WeatherSensitivity.LOW
                || place.getType() == Place.PlaceType.FOOD
                || place.getType() == Place.PlaceType.CAFE) {
            return WEATHER_FRIENDLY_SCORE;
        }
        if (place.getWeatherSensitivity() == Place.WeatherSensitivity.HIGH
                || place.getIndoorOutdoor() == Place.IndoorOutdoor.OUTDOOR) {
            return -WEATHER_FRIENDLY_SCORE;
        }
        String text = normalizedPlaceText(place);
        if (containsAny(text, "bao tang", "cho", "trung tam", "nha co", "dinh", "chua", "nha tho")) {
            return WEATHER_FRIENDLY_SCORE;
        }
        if (containsAny(text, "bien", "thuyen", "dao", "sup", "kayak", "trekking", "deo", "thac", "nui")) {
            return -WEATHER_FRIENDLY_SCORE;
        }
        return 0;
    }

    private int resolvePromptPlaceLimit(TripDto.GenerateRequest req) {
        int days = Math.max(1, req.getDays());
        int base = days <= 1 ? 14 : days <= 2 ? 20 : days <= 4 ? 26 : 30;
        int boost = hasSpecificRequest(req) ? REQUEST_CONTEXT_BOOST : 0;
        return Math.max(MIN_CONTEXT_PLACES, Math.min(MAX_CONTEXT_PLACES, base + boost));
    }

    private int resolveNearbyLimit(int promptPlaceLimit) {
        return Math.max(0, (int) Math.floor(promptPlaceLimit * MAX_NEARBY_CONTEXT_RATIO));
    }

    private long resolvePerPersonPerDayBudget(TripDto.GenerateRequest req) {
        int travelers = req.getTravelerCount() != null ? Math.max(1, req.getTravelerCount()) : 1;
        long totalBudget = "TOTAL".equalsIgnoreCase(req.getBudgetMode()) && req.getBudgetTotal() != null
                ? Math.max(0, req.getBudgetTotal())
                : Math.max(0, req.getBudgetPerPerson()) * travelers;
        int days = Math.max(1, req.getDays());
        return totalBudget / travelers / days;
    }

    private String formatPlaceForPrompt(Place place) {
        String cost = "unknown";
        if (place.getEstimatedCostMin() != null || place.getEstimatedCostMax() != null) {
            long min = place.getEstimatedCostMin() != null ? place.getEstimatedCostMin() : 0;
            long max = place.getEstimatedCostMax() != null ? place.getEstimatedCostMax() : min;
            cost = min == max ? min + " VND" : min + "-" + max + " VND";
        }
        String coords = place.getLatitude() != null && place.getLongitude() != null
                ? String.format(Locale.ROOT, "%.5f,%.5f", place.getLatitude(), place.getLongitude())
                : "unknown";
        List<String> parts = new ArrayList<>();
        parts.add("- " + place.getName());
        parts.add("type=" + place.getType());
        parts.add("indoor=" + (place.getIndoorOutdoor() != null ? place.getIndoorOutdoor() : "unknown"));
        parts.add("weather=" + (place.getWeatherSensitivity() != null ? place.getWeatherSensitivity() : "unknown"));
        parts.add("cost=" + cost);
        parts.add("costBasis=" + (place.getCostBasis() != null ? place.getCostBasis() : "unknown"));
        parts.add("rating=" + (place.getRating() != null ? String.format(Locale.ROOT, "%.1f", place.getRating()) : "unknown"));
        parts.add("coords=" + coords);
        parts.add("address=" + nullToBlank(place.getAddress()));
        parts.add("tags=" + compactList(place.getTags(), 6));
        parts.add("note=" + truncate(nullToBlank(place.getDescription()), 160));
        return String.join(" | ", parts);
    }

    private Optional<Place> findBestMatchingPlace(TripDto.ActivityResponse activity, List<Place> places) {
        if (activity == null || places == null || places.isEmpty()) {
            return Optional.empty();
        }
        if (activity.getGooglePlaceId() != null && !activity.getGooglePlaceId().isBlank()) {
            Optional<Place> byGooglePlaceId = places.stream()
                    .filter(place -> activity.getGooglePlaceId().equals(place.getGooglePlaceId()))
                    .findFirst();
            if (byGooglePlaceId.isPresent()) {
                return byGooglePlaceId;
            }
        }

        String activityName = normalizeText(activity.getName());
        String activityLocation = normalizeText(activity.getLocation());
        String activityType = normalizeText(activity.getType());
        Place best = null;
        int bestScore = 0;
        for (Place place : places) {
            List<String> placeNames = matchingNames(place);
            String placeAddress = normalizeText(place.getAddress());
            int score = 0;
            if (!activityName.isBlank() && placeNames.contains(activityName)) {
                score += 100;
            } else if (!activityName.isBlank() && placeNames.stream()
                    .anyMatch(placeName -> activityName.contains(placeName) || placeName.contains(activityName))) {
                score += 75;
            }
            if (!activityLocation.isBlank() && placeNames.stream()
                    .anyMatch(placeName -> activityLocation.contains(placeName) || placeName.contains(activityLocation))) {
                score += 55;
            }
            if (!activityLocation.isBlank() && !placeAddress.isBlank()
                    && (activityLocation.contains(placeAddress) || placeAddress.contains(activityLocation))) {
                score += 35;
            }
            if (isCompatiblePlaceType(activityType, place.getType())) {
                score += 15;
            }
            if (score > bestScore) {
                bestScore = score;
                best = place;
            }
        }
        return bestScore >= 75 ? Optional.of(best) : Optional.empty();
    }

    private boolean isCompatiblePlaceType(String activityType, Place.PlaceType placeType) {
        if (placeType == null) {
            return false;
        }
        String type = normalizeText(placeType.name());
        return activityType.equals(type)
                || (activityType.equals("attraction") && type.equals("activity"))
                || (activityType.equals("activity") && type.equals("attraction"));
    }

    private void applyPlace(TripDto.ActivityResponse activity, Place place) {
        activity.setPlaceId(place.getId());
        if (activity.getGooglePlaceId() == null || activity.getGooglePlaceId().isBlank()) {
            activity.setGooglePlaceId(place.getGooglePlaceId());
        }
        if (activity.getLatitude() == null) {
            activity.setLatitude(place.getLatitude());
        }
        if (activity.getLongitude() == null) {
            activity.setLongitude(place.getLongitude());
        }
        if ((activity.getLocation() == null || activity.getLocation().isBlank()) && place.getAddress() != null) {
            activity.setLocation(place.getAddress());
        }
        if (activity.getRating() <= 0 && place.getRating() != null) {
            activity.setRating(place.getRating());
        }
    }

    private List<Place> loadVerifiedPlacesForDestination(String destination) {
        if (destination == null || destination.isBlank()) {
            return List.of();
        }
        String canonicalDestination = resolveCanonicalDestinationName(destination);
        List<Place> places = placeRepository.findByDestinationIgnoreCaseAndVerifiedTrueOrderByRatingDesc(canonicalDestination);
        if (places == null) {
            places = List.of();
        }
        if (places.isEmpty() && !canonicalDestination.equalsIgnoreCase(destination.trim())) {
            places = placeRepository.findByDestinationIgnoreCaseAndVerifiedTrueOrderByRatingDesc(destination.trim());
            if (places == null) {
                places = List.of();
            }
        }
        return places;
    }

    private List<CandidatePlace> loadCandidatePlacesForRequest(TripDto.GenerateRequest req) {
        List<CandidatePlace> candidates = new ArrayList<>();
        loadVerifiedPlacesForDestination(req.getDestination()).forEach(place -> candidates.add(new CandidatePlace(place, false)));
        if (shouldIncludeNearbyDestinations(req)) {
            for (String nearbyDestination : NEARBY_DESTINATIONS.getOrDefault(normalizeText(req.getDestination()), List.of())) {
                loadVerifiedPlacesForDestination(nearbyDestination).forEach(place -> candidates.add(new CandidatePlace(place, true)));
            }
        }
        Set<String> seen = new HashSet<>();
        return candidates.stream()
                .filter(candidate -> seen.add(placeKey(candidate.place())))
                .toList();
    }

    private boolean shouldIncludeNearbyDestinations(TripDto.GenerateRequest req) {
        if (req == null || req.getDestination() == null) {
            return false;
        }
        return Math.max(1, req.getDays()) >= 3
                && NEARBY_DESTINATIONS.containsKey(normalizeText(req.getDestination()));
    }

    private String resolveCanonicalDestinationName(String destination) {
        if (destination == null || destination.isBlank()) {
            return "";
        }
        String trimmed = destination.trim();
        return destinationRepository.findByNameIgnoreCaseOrSlugIgnoreCase(trimmed, toDestinationSlug(trimmed))
                .map(Destination::getName)
                .orElse(trimmed);
    }

    private List<String> requestTokens(String requestText) {
        List<String> tokens = new ArrayList<>();
        for (String token : requestText.split("[^a-z0-9]+")) {
            if (token.length() >= 3 && !isWeakToken(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private boolean hasSpecificRequest(TripDto.GenerateRequest req) {
        if (req == null) {
            return false;
        }
        String requestText = normalizeText(String.join(" ",
                nullToBlank(req.getMustVisit()),
                nullToBlank(req.getAvoid()),
                extractUserNotes(req.getNotes())));
        return requestTokens(requestText).size() >= 2;
    }

    private boolean isWeakToken(String token) {
        return Set.of(
                "toi", "muon", "hay", "qua", "cho", "voi", "cac", "mot", "ngay", "lich",
                "trinh", "diem", "den", "them", "hoat", "dong", "khoang", "gan", "trong"
        ).contains(token);
    }

    private String normalizedPlaceText(Place place) {
        return normalizeText(String.join(" ",
                nullToBlank(place.getName()),
                nullToBlank(place.getNormalizedName()),
                place.getType() != null ? place.getType().name() : "",
                nullToBlank(place.getAddress()),
                nullToBlank(place.getDescription()),
                nullToBlank(place.getOpeningHours()),
                String.join(" ", place.getTags() == null ? List.of() : place.getTags()),
                String.join(" ", place.getAliases() == null ? List.of() : place.getAliases())));
    }

    private String normalizedTags(Place place) {
        return normalizeText(String.join(" ", place.getTags() == null ? List.of() : place.getTags()));
    }

    private List<String> matchingNames(Place place) {
        List<String> names = new ArrayList<>();
        names.add(normalizeText(place.getName()));
        names.add(normalizeText(place.getNormalizedName()));
        if (place.getAliases() != null) {
            for (String alias : place.getAliases()) {
                names.add(normalizeText(alias));
            }
        }
        return names.stream().filter(name -> !name.isBlank()).distinct().toList();
    }

    private boolean sameDestination(String first, String second) {
        return normalizeText(first).equals(normalizeText(second));
    }

    private String placeKey(Place place) {
        if (place.getId() != null) {
            return "id:" + place.getId();
        }
        return normalizeText(place.getDestination()) + "::" + normalizeText(place.getName());
    }

    private String extractUserNotes(String notes) {
        if (notes == null || notes.isBlank()) {
            return "";
        }
        return notes.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .filter(line -> {
                    String normalized = normalizeText(line);
                    return !normalized.startsWith("so nguoi:")
                            && !normalized.startsWith("ngan sach")
                            && !normalized.startsWith("thanh phan nhom:")
                            && !normalized.startsWith("di chuyen den diem den:")
                            && !normalized.startsWith("di chuyen trong chuyen di:");
                })
                .collect(Collectors.joining(" "));
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String toDestinationSlug(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String ascii = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace("đ", "d")
                .replace("Đ", "D")
                .toLowerCase(Locale.ROOT);
        return ascii.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
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

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private String compactList(List<String> values, int limit) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .limit(limit)
                .collect(Collectors.joining(","));
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)).trim() + "...";
    }

    private record CandidatePlace(Place place, boolean nearby) {}

    private record ScoredPlace(Place place, int score, boolean nearby) {}
}
