package com.vivuplan.vivuplan_be.service;

import com.vivuplan.vivuplan_be.dto.TripDto;
import com.vivuplan.vivuplan_be.entity.*;
import com.vivuplan.vivuplan_be.repository.TripRepository;
import com.vivuplan.vivuplan_be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TripService {

    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final AiService aiService;

    @Transactional
    public TripDto.TripResponse generateAndSave(Long userId, TripDto.GenerateRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Người dùng không tồn tại"));
        int tripDays = resolveTripDays(req);

        // 1. Call AI to generate itinerary
        TripDto.GenerateRequest aiReq = new TripDto.GenerateRequest();
        aiReq.setDestination(req.getDestination());
        aiReq.setDeparture(req.getDeparture());
        aiReq.setStartDate(req.getStartDate());
        aiReq.setEndDate(req.getEndDate());
        aiReq.setDays(tripDays);
        aiReq.setBudgetPerPerson(req.getBudgetPerPerson());
        aiReq.setBudgetTotal(req.getBudgetTotal());
        aiReq.setBudgetMode(req.getBudgetMode());
        aiReq.setTravelerCount(resolveTravelerCount(req));
        aiReq.setStyle(req.getStyle());
        aiReq.setGroupType(req.getGroupType());
        aiReq.setTransport(req.getTransport());
        aiReq.setOutboundTransport(req.getOutboundTransport());
        aiReq.setLocalTransport(req.getLocalTransport());
        aiReq.setDestinationSuggested(req.getDestinationSuggested());
        aiReq.setMustVisit(req.getMustVisit());
        aiReq.setAvoid(req.getAvoid());
        aiReq.setNotes(req.getNotes());

        List<TripDto.DayResponse> aiSchedule = aiService.generateItinerary(aiReq);

        // 2. Build and save Trip entity
        Trip trip = Trip.builder()
                .user(user)
                .destination(req.getDestination())
                .departure(req.getDeparture())
                .days(tripDays)
                .startDate(req.getStartDate())
                .endDate(req.getEndDate())
                .budgetPerPerson(req.getBudgetPerPerson())
                .budgetTotal(req.getBudgetTotal())
                .budgetMode(parseEnum(Trip.BudgetMode.class, req.getBudgetMode(), Trip.BudgetMode.PER_PERSON))
                .travelerCount(resolveTravelerCount(req))
                .style(parseEnum(Trip.TravelStyle.class, req.getStyle(), Trip.TravelStyle.RELAXING))
                .groupType(parseEnum(Trip.GroupType.class, req.getGroupType(), Trip.GroupType.FRIENDS))
                .transport(parseEnum(Trip.TransportMode.class, req.getTransport(), Trip.TransportMode.MIXED))
                .outboundTransport(parseEnum(Trip.TransportMode.class, req.getOutboundTransport(), Trip.TransportMode.MIXED))
                .localTransport(parseEnum(Trip.TransportMode.class, req.getLocalTransport(), Trip.TransportMode.MIXED))
                .destinationSuggested(Boolean.TRUE.equals(req.getDestinationSuggested()))
                .mustVisit(req.getMustVisit())
                .avoid(req.getAvoid())
                .notes(req.getNotes())
                .status(Trip.TripStatus.DRAFT)
                .shareCode(generateUniqueShareCode())
                .build();

        normalizeActivityCosts(aiSchedule, trip);

        // 3. Attach AI-generated days
        List<ItineraryDay> days = new ArrayList<>();
        for (TripDto.DayResponse dr : aiSchedule) {
            ItineraryDay day = ItineraryDay.builder()
                    .trip(trip)
                    .dayNumber(dr.getDay())
                    .title(dr.getTitle())
                    .summary(dr.getSummary())
                    .build();

            List<Activity> activities = new ArrayList<>();
            if (dr.getActivities() != null) {
                for (TripDto.ActivityResponse ar : dr.getActivities()) {
                    Activity act = new Activity();
                    act.setItineraryDay(day);
                    act.setName(ar.getName());
                    act.setTime(ar.getTime());
                    act.setType(parseEnum(Activity.ActivityType.class, ar.getType(), Activity.ActivityType.ATTRACTION));
                    act.setLocation(ar.getLocation());
                    act.setDuration(ar.getDuration());
                    act.setEstimatedCost(ar.getEstimatedCost());
                    act.setNote(ar.getNote());
                    act.setRating(ar.getRating() > 0 ? ar.getRating() : null);
                    act.setLatitude(ar.getLatitude());
                    act.setLongitude(ar.getLongitude());
                    act.setGooglePlaceId(ar.getGooglePlaceId());
                    act.setSortOrder(ar.getSortOrder());
                    activities.add(act);
                }
            }
            day.setActivities(activities);
            days.add(day);
        }
        trip.setItineraryDays(days);

        trip = tripRepository.saveAndFlush(trip);

        TripDto.TripResponse response = TripDto.TripResponse.from(trip);
        response.setSchedule(mapDays(trip.getItineraryDays()));
        response.setBudget(calculateBudget(trip, aiSchedule));
        return response;
    }

    public List<TripDto.TripResponse> getUserTrips(Long userId) {
        return tripRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(t -> {
                    TripDto.TripResponse r = TripDto.TripResponse.from(t);
                    r.setSchedule(mapDays(t.getItineraryDays()));
                    return r;
                })
                .collect(Collectors.toList());
    }

    public TripDto.TripResponse getTrip(Long tripId, Long userId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Lịch trình không tồn tại"));

        if (!trip.getIsPublic() && (userId == null || !trip.getUser().getId().equals(userId))) {
            throw new RuntimeException("Bạn không có quyền xem lịch trình này");
        }

        // Increment view count for public trips
        if (trip.getIsPublic() && (userId == null || !trip.getUser().getId().equals(userId))) {
            trip.setViewCount(trip.getViewCount() + 1);
            tripRepository.save(trip);
        }

        TripDto.TripResponse response = TripDto.TripResponse.from(trip);
        response.setSchedule(mapDays(trip.getItineraryDays()));
        response.setBudget(calculateBudget(trip, response.getSchedule()));
        return response;
    }

    @Transactional
    public TripDto.TripResponse updateTripStatus(Long tripId, Long userId, String status) {
        Trip trip = getOwnedTrip(tripId, userId);
        trip.setStatus(Trip.TripStatus.valueOf(status));
        return TripDto.TripResponse.from(tripRepository.save(trip));
    }

    @Transactional
    public TripDto.TripResponse togglePublic(Long tripId, Long userId) {
        Trip trip = getOwnedTrip(tripId, userId);
        trip.setIsPublic(!trip.getIsPublic());
        return TripDto.TripResponse.from(tripRepository.save(trip));
    }

    @Transactional
    public void deleteTrip(Long tripId, Long userId) {
        Trip trip = getOwnedTrip(tripId, userId);
        tripRepository.delete(trip);
    }

    @Transactional
    public TripDto.TripResponse addActivity(Long tripId, Long userId, Integer dayNumber, TripDto.UpdateActivityRequest req) {
        Trip trip = getOwnedTrip(tripId, userId);
        ItineraryDay day = findDay(trip, dayNumber);
        if (day.getActivities() == null) {
            day.setActivities(new ArrayList<>());
        }
        Activity activity = new Activity();
        activity.setItineraryDay(day);
        applyActivityRequest(activity, req, day.getActivities().size());
        validateNoTimeOverlap(day, activity, null);
        day.getActivities().add(activity);
        resequenceActivities(day);
        trip = tripRepository.saveAndFlush(trip);
        return toTripResponse(trip);
    }

    @Transactional
    public TripDto.TripResponse updateActivity(Long tripId, Long userId, Long activityId, TripDto.UpdateActivityRequest req) {
        Trip trip = getOwnedTrip(tripId, userId);
        Activity activity = findActivity(trip, activityId);
        applyActivityRequest(activity, req, activity.getSortOrder() != null ? activity.getSortOrder() : 0);
        validateNoTimeOverlap(activity.getItineraryDay(), activity, activityId);
        resequenceActivities(activity.getItineraryDay());
        trip = tripRepository.saveAndFlush(trip);
        return toTripResponse(trip);
    }

    @Transactional
    public TripDto.TripResponse deleteActivity(Long tripId, Long userId, Long activityId) {
        Trip trip = getOwnedTrip(tripId, userId);
        ItineraryDay day = findActivityDay(trip, activityId);
        boolean removed = day.getActivities().removeIf(activity -> activityId.equals(activity.getId()));
        if (!removed) {
            throw new RuntimeException("Hoạt động không tồn tại");
        }
        resequenceActivities(day);
        trip = tripRepository.saveAndFlush(trip);
        return toTripResponse(trip);
    }

    public Page<TripDto.TripResponse> getPublicTrips(int page, int size) {
        return tripRepository.findByIsPublicTrueOrderByViewCountDesc(PageRequest.of(page, size))
                .map(TripDto.TripResponse::from);
    }

    public TripDto.TripResponse getByShareCode(String code) {
        Trip trip = tripRepository.findByShareCode(code)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy lịch trình"));
        trip.setViewCount(trip.getViewCount() + 1);
        tripRepository.save(trip);
        TripDto.TripResponse r = TripDto.TripResponse.from(trip);
        r.setSchedule(mapDays(trip.getItineraryDays()));
        r.setBudget(calculateBudget(trip, r.getSchedule()));
        return r;
    }

    // ---- helpers ----

    private Trip getOwnedTrip(Long tripId, Long userId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Lịch trình không tồn tại"));
        if (!trip.getUser().getId().equals(userId))
            throw new RuntimeException("Không có quyền thực hiện thao tác này");
        return trip;
    }

    private TripDto.TripResponse toTripResponse(Trip trip) {
        TripDto.TripResponse response = TripDto.TripResponse.from(trip);
        response.setSchedule(mapDays(trip.getItineraryDays()));
        response.setBudget(calculateBudget(trip, response.getSchedule()));
        return response;
    }

    private ItineraryDay findDay(Trip trip, Integer dayNumber) {
        if (dayNumber == null) {
            throw new IllegalArgumentException("Ngày không hợp lệ");
        }
        return trip.getItineraryDays().stream()
                .filter(day -> day.getDayNumber().equals(dayNumber))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Không tìm thấy ngày trong lịch trình"));
    }

    private Activity findActivity(Trip trip, Long activityId) {
        return trip.getItineraryDays().stream()
                .flatMap(day -> day.getActivities() == null ? java.util.stream.Stream.empty() : day.getActivities().stream())
                .filter(activity -> activityId.equals(activity.getId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Hoạt động không tồn tại"));
    }

    private ItineraryDay findActivityDay(Trip trip, Long activityId) {
        return trip.getItineraryDays().stream()
                .filter(day -> day.getActivities() != null && day.getActivities().stream().anyMatch(activity -> activityId.equals(activity.getId())))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Hoạt động không tồn tại"));
    }

    private void applyActivityRequest(Activity activity, TripDto.UpdateActivityRequest req, int defaultSortOrder) {
        if (req.getName() == null || req.getName().isBlank()) {
            throw new IllegalArgumentException("Tên hoạt động không được để trống");
        }
        if (req.getTime() == null || req.getTime().isBlank()) {
            throw new IllegalArgumentException("Thời gian hoạt động không được để trống");
        }
        parseTime(req.getTime());
        if (req.getEstimatedCost() != null && req.getEstimatedCost() < 0) {
            throw new IllegalArgumentException("Chi phí không được âm");
        }

        activity.setName(req.getName().trim());
        activity.setTime(req.getTime().trim());
        activity.setType(parseEnum(Activity.ActivityType.class, req.getType(), activity.getType() != null ? activity.getType() : Activity.ActivityType.ATTRACTION));
        activity.setLocation(req.getLocation());
        activity.setDuration(req.getDuration() != null && !req.getDuration().isBlank() ? req.getDuration().trim() : "1 giờ");
        activity.setEstimatedCost(req.getEstimatedCost() != null ? req.getEstimatedCost() : 0);
        activity.setNote(req.getNote());
        activity.setLatitude(req.getLatitude());
        activity.setLongitude(req.getLongitude());
        activity.setGooglePlaceId(req.getGooglePlaceId());
        activity.setSortOrder(req.getSortOrder() > 0 ? req.getSortOrder() : defaultSortOrder);
    }

    private void validateNoTimeOverlap(ItineraryDay day, Activity candidate, Long ignoreActivityId) {
        LocalTime candidateStart = parseTime(candidate.getTime());
        LocalTime candidateEnd = candidateStart.plusMinutes(parseDurationMinutes(candidate.getDuration()));
        if (day.getActivities() == null) return;
        for (Activity other : day.getActivities()) {
            if (ignoreActivityId != null && ignoreActivityId.equals(other.getId())) continue;
            LocalTime otherStart = parseTime(other.getTime());
            LocalTime otherEnd = otherStart.plusMinutes(parseDurationMinutes(other.getDuration()));
            if (candidateStart.isBefore(otherEnd) && otherStart.isBefore(candidateEnd)) {
                throw new IllegalArgumentException("Thời gian hoạt động bị trùng với: " + other.getName());
            }
        }
    }

    private LocalTime parseTime(String time) {
        try {
            if (time == null || !time.matches("([01]\\d|2[0-3]):[0-5]\\d")) {
                throw new IllegalArgumentException();
            }
            return LocalTime.parse(time);
        } catch (Exception e) {
            throw new IllegalArgumentException("Thời gian phải có định dạng 24h HH:mm, từ 00:00 đến 23:59");
        }
    }

    private int parseDurationMinutes(String duration) {
        if (duration == null || duration.isBlank()) return 60;
        String normalized = normalizeText(duration);
        int minutes = 0;

        java.util.regex.Matcher hourMatcher = java.util.regex.Pattern
                .compile("(\\d+(?:[\\.,]\\d+)?)\\s*(gio|h)")
                .matcher(normalized);
        if (hourMatcher.find()) {
            minutes += Math.round(Float.parseFloat(hourMatcher.group(1).replace(",", ".")) * 60);
        }

        java.util.regex.Matcher minuteMatcher = java.util.regex.Pattern
                .compile("(\\d+)\\s*(phut|p|min)")
                .matcher(normalized);
        if (minuteMatcher.find()) {
            minutes += Integer.parseInt(minuteMatcher.group(1));
        }

        return minutes > 0 ? minutes : 60;
    }

    private void resequenceActivities(ItineraryDay day) {
        List<Activity> activities = day.getActivities();
        activities.sort((a, b) -> {
            int byTime = a.getTime().compareTo(b.getTime());
            if (byTime != 0) return byTime;
            return Integer.compare(a.getSortOrder() != null ? a.getSortOrder() : 0, b.getSortOrder() != null ? b.getSortOrder() : 0);
        });
        for (int i = 0; i < activities.size(); i++) {
            activities.get(i).setSortOrder(i);
        }
    }

    private int resolveTripDays(TripDto.GenerateRequest req) {
        if (req.getStartDate() != null && req.getEndDate() != null) {
            if (req.getEndDate().isBefore(req.getStartDate())) {
                throw new IllegalArgumentException("Ngày về phải sau hoặc bằng ngày đi");
            }
            long days = ChronoUnit.DAYS.between(req.getStartDate(), req.getEndDate()) + 1;
            if (days < 1 || days > 30) {
                throw new IllegalArgumentException("Thời gian chuyến đi phải từ 1 đến 30 ngày");
            }
            return (int) days;
        }
        if (req.getDays() < 1 || req.getDays() > 30) {
            throw new IllegalArgumentException("Thời gian chuyến đi phải từ 1 đến 30 ngày");
        }
        return req.getDays();
    }

    private <E extends Enum<E>> E parseEnum(Class<E> enumType, String value, E fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private int resolveTravelerCount(TripDto.GenerateRequest req) {
        Integer travelers = req.getTravelerCount();
        if (travelers == null) return 1;
        if (travelers < 1 || travelers > 30) {
            throw new IllegalArgumentException("Số người phải từ 1 đến 30");
        }
        return travelers;
    }

    private String generateUniqueShareCode() {
        for (int i = 0; i < 5; i++) {
            String code = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
            if (!tripRepository.existsByShareCode(code)) {
                return code;
            }
        }
        throw new RuntimeException("Không thể tạo mã chia sẻ lịch trình");
    }

    private List<TripDto.DayResponse> mapDays(List<ItineraryDay> days) {
        if (days == null) return List.of();
        return days.stream().map(d -> {
            TripDto.DayResponse dr = new TripDto.DayResponse();
            dr.setDay(d.getDayNumber());
            dr.setTitle(d.getTitle());
            dr.setSummary(d.getSummary());
            dr.setActivities(d.getActivities() == null ? List.of() :
                    d.getActivities().stream().map(TripDto.ActivityResponse::from).collect(Collectors.toList()));
            return dr;
        }).collect(Collectors.toList());
    }

    private TripDto.BudgetBreakdown calculateBudget(Trip trip, List<TripDto.DayResponse> schedule) {
        TripDto.BudgetBreakdown b = new TripDto.BudgetBreakdown();
        long food = 0, transport = 0, accommodation = 0, activities = 0;
        if (schedule != null) {
            for (TripDto.DayResponse day : schedule) {
                if (day.getActivities() == null) continue;
                for (TripDto.ActivityResponse act : day.getActivities()) {
                    long cost = Math.max(0, act.getEstimatedCost());
                    switch (act.getType()) {
                        case "FOOD", "CAFE" -> food += cost;
                        case "TRANSPORT" -> transport += cost;
                        case "ACCOMMODATION" -> accommodation += cost;
                        default -> activities += cost;
                    }
                }
            }
        }

        long total = food + transport + accommodation + activities;
        long target = resolveGroupBudget(trip);
        if (target > 0 && total > 0) {
            double variance = Math.abs(total - target) / (double) target;
            if (variance > 0.15) {
                log.warn("Estimated trip cost differs from user budget by {}%: estimated={}, target={}",
                        Math.round(variance * 100), total, target);
            }
        }

        b.setTotal(total);
        b.setTransport(transport);
        b.setAccommodation(accommodation);
        b.setFood(food);
        b.setActivities(activities);
        return b;
    }

    private void normalizeActivityCosts(List<TripDto.DayResponse> schedule, Trip trip) {
        if (schedule == null || schedule.isEmpty()) return;
        for (TripDto.DayResponse day : schedule) {
            if (day.getActivities() == null) continue;
            for (TripDto.ActivityResponse activity : day.getActivities()) {
                activity.setEstimatedCost(normalizeActivityCost(activity, trip));
            }
        }
    }

    private long normalizeActivityCost(TripDto.ActivityResponse activity, Trip trip) {
        long current = Math.max(0, activity.getEstimatedCost());
        Long perPersonCost = extractPerPersonCost(activity.getNote());
        if (perPersonCost == null) {
            return current;
        }

        long expected = roundToNearest(perPersonCost * Math.max(1, trip.getTravelerCount()), 10_000);
        if (current == 0) {
            return expected;
        }

        double variance = Math.abs(current - expected) / (double) Math.max(1, expected);
        return variance > 0.10 ? expected : current;
    }

    private Long extractPerPersonCost(String note) {
        String normalized = normalizeText(note)
                .replace("vnđ", " vnd")
                .replace("₫", " vnd");
        if (!normalized.contains("/nguoi") && !normalized.contains("/khach")) {
            return null;
        }

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "(\\d[\\d\\.,]*)\\s*(trieu|tr|k|nghin|ngan|vnd|d)?\\s*/\\s*(nguoi|khach)"
        );
        java.util.regex.Matcher matcher = pattern.matcher(normalized);
        Long result = null;
        while (matcher.find()) {
            Long parsed = parseMoney(matcher.group(1), matcher.group(2));
            if (parsed != null && parsed > 0) {
                result = parsed;
            }
        }
        return result;
    }

    private Long parseMoney(String rawValue, String rawUnit) {
        if (rawValue == null || rawValue.isBlank()) return null;
        String unit = rawUnit == null ? "" : rawUnit;
        String value = rawValue.trim();
        try {
            if (unit.equals("tr") || unit.equals("trieu")) {
                return Math.round(Double.parseDouble(value.replace(",", ".")) * 1_000_000);
            }
            if (unit.equals("k") || unit.equals("nghin") || unit.equals("ngan")) {
                return Math.round(Double.parseDouble(value.replace(",", ".")) * 1_000);
            }
            String digits = value.replaceAll("[^0-9]", "");
            return digits.isBlank() ? null : Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private long resolveGroupBudget(Trip trip) {
        if (trip.getBudgetMode() == Trip.BudgetMode.TOTAL && trip.getBudgetTotal() != null && trip.getBudgetTotal() > 0) {
            return trip.getBudgetTotal();
        }
        return Math.max(0, trip.getBudgetPerPerson()) * Math.max(1, trip.getTravelerCount());
    }

    private long roundToNearest(long value, long unit) {
        if (unit <= 0) return value;
        return Math.round((double) value / unit) * unit;
    }

    private String normalizeText(String value) {
        if (value == null) return "";
        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace("đ", "d")
                .replace("Đ", "D")
                .toLowerCase(java.util.Locale.ROOT);
    }

}
