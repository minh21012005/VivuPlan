package com.vivuplan.vivuplan_be.service;

import com.vivuplan.vivuplan_be.dto.TripDto;
import com.vivuplan.vivuplan_be.entity.*;
import com.vivuplan.vivuplan_be.repository.DestinationRepository;
import com.vivuplan.vivuplan_be.repository.TripRepository;
import com.vivuplan.vivuplan_be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TripService {

    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final DestinationRepository destinationRepository;
    private final AiService aiService;
    private final WeatherService weatherService;
    private final Map<String, DayRegenerationProposal> dayRegenerationProposals = new ConcurrentHashMap<>();
    private static final int REGENERATION_PROPOSAL_TTL_MINUTES = 30;
    private static final String COST_REVIEW_STATUS = "NEEDS_REVIEW";
    private static final long REGENERATION_COST_INCREASE_WARNING_MIN_DELTA = 200_000L;
    private static final int MISSING_TRANSPORT_WARNING_MIN_ACTIVITIES = 4;
    private static final int MISSING_TRANSPORT_WARNING_MIN_DISTINCT_LOCATIONS = 3;
    private static final double MISSING_TRANSPORT_WARNING_DISTANCE_KM = 2.0;
    private static final String COST_REVIEW_MESSAGE =
            "Chi phí này cần được kiểm tra lại vì AI chưa đưa ra mức ước tính đáng tin cậy.";
    private static final String COST_REVIEW_NOTE =
            "Chi phí cần kiểm tra: hoạt động này có thể phát sinh phí, nhưng AI chưa đưa ra mức ước tính đáng tin cậy.";

    @Transactional
    public TripDto.TripResponse generateAndSave(Long userId, TripDto.GenerateRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Người dùng không tồn tại"));

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
        aiReq.setWeatherForecast(fetchWeatherContext(req));

        AiService.GeneratedItineraryResult generatedItinerary = aiService.generateItinerary(aiReq);
        List<TripDto.DayResponse> aiSchedule = generatedItinerary.days();
        TripDto.RequestFulfillment requestFulfillment = generatedItinerary.requestFulfillment();

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
                .outboundTransport(
                        parseEnum(Trip.TransportMode.class, req.getOutboundTransport(), Trip.TransportMode.MIXED))
                .localTransport(parseEnum(Trip.TransportMode.class, req.getLocalTransport(), Trip.TransportMode.MIXED))
                .destinationSuggested(Boolean.TRUE.equals(req.getDestinationSuggested()))
                .mustVisit(req.getMustVisit())
                .avoid(req.getAvoid())
                .notes(req.getNotes())
                .status(Trip.TripStatus.PLANNED)
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

        TripDto.BudgetBreakdown budget = calculateBudget(trip, aiSchedule);
        String requestText = buildGenerationRequestText(req);
        List<String> persistentWarnings = buildRequestFulfillmentWarnings(
                requestFulfillment,
                requestText,
                "lịch trình vừa tạo");
        List<String> warnings = buildGenerationWarnings(
                aiSchedule,
                persistentWarnings);
        trip.setAiWarnings(serializeWarnings(persistentWarnings));

        trip = tripRepository.saveAndFlush(trip);

        TripDto.TripResponse response = toTripResponse(trip);
        response.setBudget(budget);
        response.setRequestFulfillment(requestFulfillment);
        response.setWarnings(warnings);
        return response;
    }

    public List<TripDto.TripResponse> getUserTrips(Long userId) {
        return tripRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(t -> {
                    TripDto.TripResponse r = TripDto.TripResponse.from(t);
                    r.setSchedule(mapDays(t.getItineraryDays()));
                    r.setWarnings(filterPersistentWarnings(r.getWarnings()));
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

        return toTripResponse(trip);
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
    public TripDto.TripResponse addActivity(Long tripId, Long userId, Integer dayNumber,
            TripDto.UpdateActivityRequest req) {
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
    public TripDto.TripResponse updateActivity(Long tripId, Long userId, Long activityId,
            TripDto.UpdateActivityRequest req) {
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

    @Transactional(readOnly = true)
    public TripDto.RegenerateDayPreviewResponse previewRegenerateDay(
            Long tripId,
            Long userId,
            Integer dayNumber,
            TripDto.RegenerateDayRequest req) {
        cleanupExpiredRegenerationProposals();
        Trip trip = getOwnedTrip(tripId, userId);
        ItineraryDay existingDay = findDay(trip, dayNumber);
        List<TripDto.DayResponse> currentSchedule = mapDays(trip.getItineraryDays());
        TripDto.GenerateRequest aiReq = toGenerateRequest(trip);

        String instruction = req != null ? req.getInstruction() : null;
        AiService.RegeneratedDayResult regeneratedDay = aiService.regenerateDay(
                aiReq,
                currentSchedule,
                dayNumber,
                req != null ? req.getIntent() : "REGENERATE",
                instruction);
        TripDto.DayResponse proposedDay = regeneratedDay.day();
        TripDto.RequestFulfillment requestFulfillment = regeneratedDay.requestFulfillment();
        normalizeActivityCosts(List.of(proposedDay), trip);
        validateRegeneratedDayProposal(trip, proposedDay);

        long oldBudget = sumDayCost(existingDay);
        long newBudget = sumDayCost(proposedDay);
        List<String> persistentWarnings = buildRequestFulfillmentWarnings(
                requestFulfillment,
                instruction,
                "preview này");
        List<String> warnings = buildRegenerationWarnings(
                trip,
                existingDay,
                proposedDay,
                persistentWarnings);
        String proposalId = UUID.randomUUID().toString();
        dayRegenerationProposals.put(proposalId, new DayRegenerationProposal(
                proposalId,
                tripId,
                userId,
                dayNumber,
                proposedDay,
                oldBudget,
                newBudget,
                warnings,
                persistentWarnings,
                LocalDateTime.now().plusMinutes(REGENERATION_PROPOSAL_TTL_MINUTES)));

        TripDto.RegenerateDayPreviewResponse response = new TripDto.RegenerateDayPreviewResponse();
        response.setProposalId(proposalId);
        response.setDayNumber(dayNumber);
        response.setDay(proposedDay);
        response.setOldBudget(oldBudget);
        response.setNewBudget(newBudget);
        response.setWarnings(warnings);
        response.setRequestFulfillment(requestFulfillment);
        return response;
    }

    @Transactional
    public TripDto.TripResponse applyRegeneratedDay(
            Long tripId,
            Long userId,
            Integer dayNumber,
            TripDto.ApplyRegenerateDayRequest req) {
        cleanupExpiredRegenerationProposals();
        if (req == null || req.getProposalId() == null || req.getProposalId().isBlank()) {
            throw new IllegalArgumentException("Thiếu mã phương án chỉnh lịch trình");
        }

        DayRegenerationProposal proposal = dayRegenerationProposals.get(req.getProposalId());
        if (proposal == null || proposal.expiresAt().isBefore(LocalDateTime.now())) {
            dayRegenerationProposals.remove(req.getProposalId());
            throw new IllegalArgumentException("Phương án chỉnh lịch trình đã hết hạn. Vui lòng tạo lại.");
        }
        if (!proposal.tripId().equals(tripId) || !proposal.userId().equals(userId)
                || !proposal.dayNumber().equals(dayNumber)) {
            throw new RuntimeException("Không có quyền áp dụng phương án chỉnh lịch trình này");
        }

        Trip trip = getOwnedTrip(tripId, userId);
        ItineraryDay day = findDay(trip, dayNumber);
        TripDto.DayResponse proposedDay = proposal.day();
        TripDto.DayResponse dayToApply = mergeSelectedRegeneratedActivities(day, proposedDay,
                req.getSelectedActivityIndexes());
        validateRegeneratedDayProposal(trip, dayToApply);

        day.setTitle(dayToApply.getTitle());
        day.setSummary(dayToApply.getSummary());
        day.getActivities().clear();
        if (dayToApply.getActivities() != null) {
            for (TripDto.ActivityResponse activityResponse : dayToApply.getActivities()) {
                Activity activity = toActivity(activityResponse, day);
                day.getActivities().add(activity);
            }
        }
        resequenceActivities(day);
        trip.setAiWarnings(serializeWarnings(mergeWarnings(parsePersistentWarnings(trip.getAiWarnings()), proposal.persistentWarnings())));
        trip = tripRepository.saveAndFlush(trip);
        dayRegenerationProposals.remove(req.getProposalId());
        return toTripResponse(trip);
    }

    public Page<TripDto.TripResponse> getPublicTrips(int page, int size) {
        return tripRepository.findByIsPublicTrueOrderByViewCountDesc(PageRequest.of(page, size))
                .map(trip -> {
                    TripDto.TripResponse response = TripDto.TripResponse.from(trip);
                    response.setWarnings(filterPersistentWarnings(response.getWarnings()));
                    return response;
                });
    }

    public TripDto.TripResponse getByShareCode(String code) {
        Trip trip = tripRepository.findByShareCode(code)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy lịch trình"));
        trip.setViewCount(trip.getViewCount() + 1);
        tripRepository.save(trip);
        return toTripResponse(trip);
    }

    // ---- helpers ----

    private TripDto.GenerateRequest toGenerateRequest(Trip trip) {
        TripDto.GenerateRequest req = new TripDto.GenerateRequest();
        req.setDestination(trip.getDestination());
        req.setDeparture(trip.getDeparture());
        req.setStartDate(trip.getStartDate());
        req.setEndDate(trip.getEndDate());
        req.setDays(trip.getDays());
        req.setBudgetPerPerson(trip.getBudgetPerPerson());
        req.setBudgetTotal(trip.getBudgetTotal());
        req.setBudgetMode(trip.getBudgetMode().name());
        req.setTravelerCount(trip.getTravelerCount());
        req.setStyle(trip.getStyle().name());
        req.setGroupType(trip.getGroupType().name());
        req.setTransport(trip.getTransport().name());
        req.setOutboundTransport(trip.getOutboundTransport().name());
        req.setLocalTransport(trip.getLocalTransport().name());
        req.setDestinationSuggested(trip.getDestinationSuggested());
        req.setMustVisit(trip.getMustVisit());
        req.setAvoid(trip.getAvoid());
        req.setNotes(trip.getNotes());
        req.setWeatherForecast(fetchWeatherContext(req));
        return req;
    }

    private String buildGenerationRequestText(TripDto.GenerateRequest req) {
        if (req == null) {
            return "";
        }

        List<String> requestParts = new ArrayList<>();
        if (req.getMustVisit() != null && !req.getMustVisit().isBlank()) {
            requestParts.add("Nơi muốn ghé: " + req.getMustVisit().trim());
        }
        if (req.getAvoid() != null && !req.getAvoid().isBlank()) {
            requestParts.add("Điều muốn tránh: " + req.getAvoid().trim());
        }

        String userNotes = extractUserAuthoredNotes(req);
        if (!userNotes.isBlank()) {
            requestParts.add(userNotes);
        }
        return String.join("\n", requestParts).trim();
    }

    private String extractUserAuthoredNotes(TripDto.GenerateRequest req) {
        String notes = req.getNotes();
        if (notes == null || notes.isBlank()) {
            return "";
        }

        List<String> userLines = new ArrayList<>();
        for (String rawLine : notes.split("\\R")) {
            String line = rawLine.trim();
            if (line.isBlank() || isGeneratedPlanningNoteLine(line, req)) {
                continue;
            }
            userLines.add(line);
        }
        return String.join("\n", userLines).trim();
    }

    private boolean isGeneratedPlanningNoteLine(String line, TripDto.GenerateRequest req) {
        String normalized = normalizeText(line).replaceAll("\\s+", " ").trim();
        return normalized.startsWith("so nguoi:")
                || normalized.startsWith("ngan sach")
                || normalized.startsWith("thanh phan nhom:")
                || normalized.startsWith("di chuyen den diem den:")
                || normalized.startsWith("di chuyen trong chuyen di:")
                || (normalized.startsWith("noi muon ghe:") && req.getMustVisit() != null && !req.getMustVisit().isBlank())
                || (normalized.startsWith("dieu muon tranh:") && req.getAvoid() != null && !req.getAvoid().isBlank());
    }

    /**
     * Resolves weather forecast for the trip's destination and formats it as a
     * concise, human-readable summary for injection into the AI prompt.
     *
     * Optimizations vs. original:
     *  - Uses a targeted DB query (findByNameOrSlug) instead of findAll()
     *  - Falls back gracefully when no startDate is provided (uses "today")
     *  - Annotates each day with a weather label and outdoor risk level so the AI
     *    gets richer context than a raw WMO code integer
     */
    private String fetchWeatherContext(TripDto.GenerateRequest req) {
        String destName = req.getDestination();
        if (destName == null || destName.isBlank()) return "none";

        // 1. Try to get coordinates from local DB first (fastest path)
        Double lat = null, lon = null;
        var dbDest = destinationRepository.findByNameIgnoreCaseOrSlugIgnoreCase(destName, destName);
        if (dbDest.isPresent()) {
            lat = dbDest.get().getLatitude();
            lon = dbDest.get().getLongitude();
        }

        // 2. getForecastForDestination will automatically fall back to Nominatim
        //    geocoding if lat/lon are null (unknown destination)
        List<WeatherService.DailyWeather> forecast =
                weatherService.getForecastForDestination(destName, lat, lon);

        if (forecast.isEmpty()) return "none";

        LocalDate start = req.getStartDate() != null ? req.getStartDate() : LocalDate.now();
        LocalDate end   = req.getEndDate()   != null ? req.getEndDate()   : start.plusDays(Math.max(0, req.getDays() - 1));
        return formatWeatherForAi(forecast, start, end);
    }

    /**
     * Produces a concise day-by-day weather summary for the AI prompt.
     * Example line: "Day 1 (2025-06-10): Rain, 22–28°C, rain chance 75% – AVOID outdoor activities"
     */
    private String formatWeatherForAi(List<WeatherService.DailyWeather> forecast, LocalDate start, LocalDate end) {
        if (forecast == null || forecast.isEmpty() || start == null || end == null) return "none";

        StringBuilder sb = new StringBuilder();
        int dayNum = 1;
        for (WeatherService.DailyWeather dw : forecast) {
            LocalDate date = LocalDate.parse(dw.getDate());
            if (date.isBefore(start)) continue;
            if (date.isAfter(end)) break;

            String risk = switch (dw.outdoorRiskLevel()) {
                case 2 -> "HIGH RAIN RISK – prefer indoor activities";
                case 1 -> "LIGHT RAIN – mix indoor and outdoor";
                default -> "Good weather – outdoor activities recommended";
            };

            sb.append(String.format("Day %d (%s): %s, %.0f–%.0f°C, rain chance %d%% → %s%n",
                    dayNum++,
                    dw.getDate(),
                    dw.toWeatherLabel(),
                    dw.getMinTemp(),
                    dw.getMaxTemp(),
                    dw.getPrecipitationProbability(),
                    risk));
        }
        return sb.length() > 0 ? sb.toString().trim() : "none";
    }

    private void validateRegeneratedDayProposal(Trip trip, TripDto.DayResponse proposedDay) {
        if (proposedDay == null || proposedDay.getDay() < 1 || proposedDay.getDay() > trip.getDays()) {
            throw new IllegalArgumentException("Ngày được tạo lại không hợp lệ");
        }
        if (proposedDay.getTitle() == null || proposedDay.getTitle().isBlank()) {
            throw new IllegalArgumentException("Ngày được tạo lại thiếu tiêu đề");
        }
        int minActivities = minimumActivitiesForRegeneratedDay(trip, proposedDay);
        if (proposedDay.getActivities() == null || proposedDay.getActivities().size() < minActivities) {
            throw new IllegalArgumentException("Ngày được tạo lại cần ít nhất " + minActivities + " hoạt động");
        }
        if (proposedDay.getActivities().size() > 14) {
            throw new IllegalArgumentException("Ngày được tạo lại có quá nhiều hoạt động");
        }

        long nonLogisticsActivities = proposedDay.getActivities().stream()
                .filter(activity -> !isLogisticsActivityType(normalizeText(activity.getType())))
                .count();
        if (nonLogisticsActivities > 9) {
            throw new IllegalArgumentException("Ngày được tạo lại có quá nhiều điểm ăn/chơi/tham quan");
        }

        ItineraryDay tempDay = new ItineraryDay();
        tempDay.setDayNumber(proposedDay.getDay());
        tempDay.setActivities(new ArrayList<>());
        for (TripDto.ActivityResponse activityResponse : proposedDay.getActivities()) {
            Activity activity = toActivity(activityResponse, tempDay);
            validateNoTimeOverlap(tempDay, activity, null, true);
            tempDay.getActivities().add(activity);
        }
        resequenceActivities(tempDay);

    }

    private int minimumActivitiesForRegeneratedDay(Trip trip, TripDto.DayResponse proposedDay) {
        boolean edgeDay = proposedDay != null
                && (proposedDay.getDay() <= 1 || proposedDay.getDay() >= Math.max(1, trip.getDays()));
        boolean hasIntercityTransport = proposedDay != null
                && proposedDay.getActivities() != null
                && proposedDay.getActivities().stream().anyMatch(activity -> {
                    String type = normalizeText(activity.getType());
                    String combined = normalizeText(String.join(" ",
                            nullToBlank(activity.getName()),
                            nullToBlank(activity.getLocation()),
                            nullToBlank(activity.getNote())));
                    return isTripIntercityTransportActivity(type, combined, trip);
                });
        return edgeDay || hasIntercityTransport || isRelaxedPacing(trip) ? 2 : 3;
    }

    private boolean isLogisticsActivityType(String normalizedType) {
        return normalizedType.equals("transport") || normalizedType.equals("accommodation");
    }

    private boolean isRelaxedPacing(Trip trip) {
        String context = normalizeText(String.join(" ",
                trip.getStyle() != null ? trip.getStyle().name() : "",
                trip.getGroupType() != null ? trip.getGroupType().name() : "",
                nullToBlank(trip.getNotes())));
        return containsAny(context,
                "relaxing",
                "nghi duong",
                "family",
                "tre em",
                "nguoi lon tuoi",
                "nhe nhang",
                "thu gian");
    }

    private TripDto.DayResponse mergeSelectedRegeneratedActivities(
            ItineraryDay oldDay,
            TripDto.DayResponse proposedDay,
            List<Integer> selectedActivityIndexes) {
        List<TripDto.ActivityResponse> oldActivities = oldDay.getActivities() == null
                ? List.of()
                : oldDay.getActivities().stream().map(TripDto.ActivityResponse::from).collect(Collectors.toList());
        List<TripDto.ActivityResponse> newActivities = proposedDay.getActivities() == null ? List.of()
                : proposedDay.getActivities();

        if (selectedActivityIndexes != null && selectedActivityIndexes.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn ít nhất một mục mới để áp dụng");
        }
        boolean applyAll = selectedActivityIndexes == null;
        Set<Integer> selected = new HashSet<>(applyAll
                ? java.util.stream.IntStream.range(0, newActivities.size()).boxed().toList()
                : selectedActivityIndexes);

        for (Integer index : selected) {
            if (index == null || index < 0 || index >= newActivities.size()) {
                throw new IllegalArgumentException("Mục được chọn để áp dụng không hợp lệ");
            }
        }

        int maxSize = Math.max(oldActivities.size(), newActivities.size());
        List<TripDto.ActivityResponse> mergedActivities = new ArrayList<>();
        for (int i = 0; i < maxSize; i++) {
            if (selected.contains(i) && i < newActivities.size()) {
                mergedActivities.add(copyActivityResponse(newActivities.get(i)));
            } else if (i < oldActivities.size()) {
                mergedActivities.add(copyActivityResponse(oldActivities.get(i)));
            }
        }

        TripDto.DayResponse mergedDay = new TripDto.DayResponse();
        mergedDay.setDay(proposedDay.getDay());
        mergedDay.setTitle(selected.size() == newActivities.size() ? proposedDay.getTitle() : oldDay.getTitle());
        mergedDay.setSummary(selected.size() == newActivities.size() ? proposedDay.getSummary() : oldDay.getSummary());
        mergedActivities.sort((a, b) -> a.getTime().compareTo(b.getTime()));
        for (int i = 0; i < mergedActivities.size(); i++) {
            mergedActivities.get(i).setId(null);
            mergedActivities.get(i).setSortOrder(i);
        }
        mergedDay.setActivities(mergedActivities);
        return mergedDay;
    }

    private TripDto.ActivityResponse copyActivityResponse(TripDto.ActivityResponse source) {
        TripDto.ActivityResponse copy = new TripDto.ActivityResponse();
        copy.setTime(source.getTime());
        copy.setName(source.getName());
        copy.setType(source.getType());
        copy.setLocation(source.getLocation());
        copy.setDuration(source.getDuration());
        copy.setEstimatedCost(source.getEstimatedCost());
        copy.setNote(source.getNote());
        copy.setRating(source.getRating());
        copy.setLatitude(source.getLatitude());
        copy.setLongitude(source.getLongitude());
        copy.setGooglePlaceId(source.getGooglePlaceId());
        copy.setSortOrder(source.getSortOrder());
        return copy;
    }

    private Activity toActivity(TripDto.ActivityResponse response, ItineraryDay day) {
        if (response.getName() == null || response.getName().isBlank()) {
            throw new IllegalArgumentException("Hoạt động không được để trống tên");
        }
        parseTime(response.getTime());
        if (response.getEstimatedCost() < 0) {
            throw new IllegalArgumentException("Chi phí hoạt động không được âm");
        }

        Activity activity = new Activity();
        activity.setItineraryDay(day);
        activity.setName(response.getName().trim());
        activity.setTime(response.getTime().trim());
        activity.setType(parseEnum(Activity.ActivityType.class, response.getType(), Activity.ActivityType.ATTRACTION));
        activity.setLocation(response.getLocation());
        activity.setDuration(
                response.getDuration() != null && !response.getDuration().isBlank() ? response.getDuration().trim()
                        : "1 giờ");
        activity.setEstimatedCost(response.getEstimatedCost());
        activity.setNote(response.getNote());
        activity.setRating(response.getRating() > 0 ? response.getRating() : null);
        activity.setLatitude(response.getLatitude());
        activity.setLongitude(response.getLongitude());
        activity.setGooglePlaceId(response.getGooglePlaceId());
        activity.setSortOrder(response.getSortOrder());
        return activity;
    }

    private long sumDayCost(ItineraryDay day) {
        if (day.getActivities() == null)
            return 0;
        return day.getActivities().stream()
                .mapToLong(
                        activity -> Math.max(0, activity.getEstimatedCost() != null ? activity.getEstimatedCost() : 0))
                .sum();
    }

    private long sumDayCost(TripDto.DayResponse day) {
        if (day.getActivities() == null)
            return 0;
        return day.getActivities().stream()
                .mapToLong(activity -> Math.max(0, activity.getEstimatedCost()))
                .sum();
    }

    private long calculateBudgetWithReplacement(Trip trip, TripDto.DayResponse replacementDay) {
        List<TripDto.DayResponse> schedule = mapDays(trip.getItineraryDays()).stream()
                .map(day -> day.getDay() == replacementDay.getDay() ? replacementDay : day)
                .collect(Collectors.toList());
        return calculateBudget(trip, schedule).getTotal();
    }

    private List<String> buildGenerationWarnings(
            List<TripDto.DayResponse> schedule,
            List<String> persistentWarnings) {
        List<String> warnings = new ArrayList<>();
        warnings.addAll(persistentWarnings);
        warnings.addAll(buildCostReviewWarnings(schedule, "lịch trình vừa tạo"));
        return warnings;
    }

    private List<String> buildRegenerationWarnings(
            Trip trip,
            ItineraryDay oldDay,
            TripDto.DayResponse proposedDay,
            List<String> persistentWarnings) {
        List<String> warnings = new ArrayList<>();
        warnings.addAll(persistentWarnings);
        warnings.addAll(buildCostReviewWarnings(List.of(proposedDay), "preview này"));
        long oldBudget = sumDayCost(oldDay);
        long newBudget = sumDayCost(proposedDay);
        if (shouldWarnSignificantCostIncrease(oldBudget, newBudget)) {
            warnings.add("Chi phí ngày mới cao hơn đáng kể so với ngày cũ.");
        }
        long proposedTripTotal = calculateBudgetWithReplacement(trip, proposedDay);
        long budgetCeiling = resolveGroupBudget(trip);
        if (budgetCeiling > 0 && proposedTripTotal > budgetCeiling) {
            warnings.add("Tổng chi phí sau khi áp dụng có thể vượt ngân sách bạn đã nhập.");
        }
        if (shouldWarnMissingTransport(proposedDay)) {
            warnings.add("Ngày mới chưa có chặng di chuyển riêng, hãy kiểm tra lại nếu các điểm ở xa nhau.");
        }
        return warnings;
    }

    private boolean shouldWarnSignificantCostIncrease(long oldBudget, long newBudget) {
        long delta = newBudget - oldBudget;
        return oldBudget > 0
                && delta >= REGENERATION_COST_INCREASE_WARNING_MIN_DELTA
                && newBudget > Math.round(oldBudget * 1.25);
    }

    private boolean shouldWarnMissingTransport(TripDto.DayResponse proposedDay) {
        if (proposedDay == null || proposedDay.getActivities() == null || proposedDay.getActivities().isEmpty()) {
            return false;
        }

        boolean hasTransport = proposedDay.getActivities().stream()
                .anyMatch(activity -> "TRANSPORT".equalsIgnoreCase(activity.getType()));
        if (hasTransport) {
            return false;
        }

        List<TripDto.ActivityResponse> placeActivities = proposedDay.getActivities().stream()
                .filter(activity -> activity.getType() == null
                        || (!"ACCOMMODATION".equalsIgnoreCase(activity.getType())
                                && !"TRANSPORT".equalsIgnoreCase(activity.getType())))
                .toList();
        if (placeActivities.size() < MISSING_TRANSPORT_WARNING_MIN_ACTIVITIES) {
            return false;
        }

        if (hasDistantCoordinates(placeActivities)) {
            return true;
        }

        long distinctLocations = placeActivities.stream()
                .map(activity -> normalizeText(activity.getLocation()).replaceAll("\\s+", " ").trim())
                .filter(location -> !location.isBlank())
                .distinct()
                .count();
        return distinctLocations >= MISSING_TRANSPORT_WARNING_MIN_DISTINCT_LOCATIONS;
    }

    private boolean hasDistantCoordinates(List<TripDto.ActivityResponse> activities) {
        for (int i = 0; i < activities.size(); i++) {
            TripDto.ActivityResponse first = activities.get(i);
            if (first.getLatitude() == null || first.getLongitude() == null) {
                continue;
            }
            for (int j = i + 1; j < activities.size(); j++) {
                TripDto.ActivityResponse second = activities.get(j);
                if (second.getLatitude() == null || second.getLongitude() == null) {
                    continue;
                }
                if (distanceKm(first.getLatitude(), first.getLongitude(), second.getLatitude(), second.getLongitude())
                        >= MISSING_TRANSPORT_WARNING_DISTANCE_KM) {
                    return true;
                }
            }
        }
        return false;
    }

    private double distanceKm(double firstLat, double firstLng, double secondLat, double secondLng) {
        double earthRadiusKm = 6371.0;
        double latDistance = Math.toRadians(secondLat - firstLat);
        double lngDistance = Math.toRadians(secondLng - firstLng);
        double firstLatRad = Math.toRadians(firstLat);
        double secondLatRad = Math.toRadians(secondLat);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(firstLatRad) * Math.cos(secondLatRad)
                        * Math.sin(lngDistance / 2) * Math.sin(lngDistance / 2);
        return earthRadiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private List<String> buildCostReviewWarnings(List<TripDto.DayResponse> schedule, String contextLabel) {
        if (schedule == null || schedule.isEmpty()) {
            return List.of();
        }
        boolean hasCostReview = schedule.stream()
                .filter(day -> day.getActivities() != null)
                .flatMap(day -> day.getActivities().stream())
                .anyMatch(activity -> COST_REVIEW_STATUS.equals(activity.getCostEstimateStatus()));
        if (!hasCostReview) {
            return List.of();
        }
        return List.of("Một số chi phí bắt buộc trong " + contextLabel
                + " chưa có ước tính đáng tin cậy. Các mục này sẽ hiển thị là \"Cần kiểm tra\" để bạn rà soát trước khi chốt lịch.");
    }

    private String serializeWarnings(List<String> warnings) {
        List<String> normalized = mergeWarnings(warnings);
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.stream()
                .map(warning -> warning.replaceAll("\\R+", " ").trim())
                .collect(Collectors.joining("\n"));
    }

    private List<String> parseWarnings(String rawWarnings) {
        if (rawWarnings == null || rawWarnings.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(rawWarnings.split("\\R"))
                .map(String::trim)
                .filter(warning -> !warning.isBlank())
                .toList();
    }

    private List<String> parsePersistentWarnings(String rawWarnings) {
        return filterPersistentWarnings(parseWarnings(rawWarnings));
    }

    private List<String> filterPersistentWarnings(List<String> warnings) {
        if (warnings == null || warnings.isEmpty()) {
            return List.of();
        }
        return warnings.stream()
                .filter(this::isPersistentAiWarning)
                .toList();
    }

    private boolean isPersistentAiWarning(String warning) {
        return normalizeText(warning).replaceAll("\\s+", " ").trim().startsWith("yeu cau");
    }

    @SafeVarargs
    private static List<String> mergeWarnings(List<String>... warningGroups) {
        if (warningGroups == null || warningGroups.length == 0) {
            return List.of();
        }
        List<String> merged = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (List<String> warnings : warningGroups) {
            if (warnings == null) {
                continue;
            }
            for (String warning : warnings) {
                if (warning == null || warning.isBlank()) {
                    continue;
                }
                String normalized = warning.replaceAll("\\R+", " ").trim();
                if (seen.add(normalized)) {
                    merged.add(normalized);
                }
            }
        }
        return merged;
    }

    private List<String> buildRequestFulfillmentWarnings(
            TripDto.RequestFulfillment requestFulfillment,
            String instruction,
            String contextLabel) {
        if (instruction == null || instruction.isBlank()) {
            return List.of();
        }
        if (requestFulfillment == null) {
            return List.of(unverifiedRequestWarning(contextLabel));
        }

        List<TripDto.RequestFulfillmentItem> items = requestFulfillment.getItems() != null
                ? requestFulfillment.getItems()
                : List.of();
        if (items.isEmpty()) {
            return isUnfulfilledOverallStatus(requestFulfillment.getOverallStatus())
                    ? List.of(unverifiedRequestWarning(contextLabel))
                    : List.of();
        }

        List<String> warnings = new ArrayList<>();
        for (TripDto.RequestFulfillmentItem item : items) {
            String status = normalizeFulfillmentToken(item.getStatus());
            String reasonCode = normalizeFulfillmentToken(item.getReasonCode());
            if ("FULFILLED".equals(status)
                    || "APPLIED".equals(status)
                    || (status.isBlank() && "APPLIED".equals(reasonCode))) {
                continue;
            }

            String message = item.getUserMessage();
            if (message == null || message.isBlank()) {
                String requestedText = item.getRequestedText() != null && !item.getRequestedText().isBlank()
                        ? item.getRequestedText().trim()
                        : instruction.trim();
                message = String.format(
                        "Yêu cầu \"%s\" chưa được phản ánh đầy đủ trong %s. Hãy kiểm tra lại trước khi sử dụng.",
                        requestedText,
                        contextLabel);
            }
            warnings.add(toRequestWarning(message));
        }

        if (warnings.isEmpty() && isUnfulfilledOverallStatus(requestFulfillment.getOverallStatus())) {
            warnings.add(unverifiedRequestWarning(contextLabel));
        }
        return warnings;
    }

    private boolean isUnfulfilledOverallStatus(String status) {
        String normalizedStatus = normalizeFulfillmentToken(status);
        return normalizedStatus.isBlank()
                || "PARTIAL".equals(normalizedStatus)
                || "NOT_FULFILLED".equals(normalizedStatus)
                || "UNCLEAR".equals(normalizedStatus);
    }

    private String normalizeFulfillmentToken(String value) {
        return value == null
                ? ""
                : value.trim()
                        .toUpperCase(java.util.Locale.ROOT)
                        .replace('-', '_')
                        .replace(' ', '_');
    }

    private String toRequestWarning(String message) {
        String trimmed = message == null ? "" : message.trim();
        if (trimmed.startsWith("Yêu cầu")) {
            return trimmed;
        }
        return "Yêu cầu: " + trimmed;
    }

    private String unverifiedRequestWarning(String contextLabel) {
        return "Yêu cầu của bạn chưa được VivuPlan xác minh đầy đủ trong " + contextLabel + ". Hãy kiểm tra lại trước khi sử dụng.";
    }

    private void cleanupExpiredRegenerationProposals() {
        LocalDateTime now = LocalDateTime.now();
        dayRegenerationProposals.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

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
        List<String> persistentWarnings = filterPersistentWarnings(response.getWarnings());
        response.setWarnings(mergeWarnings(
                persistentWarnings,
                buildCostReviewWarnings(response.getSchedule(), "lịch trình này")));
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
                .flatMap(day -> day.getActivities() == null ? java.util.stream.Stream.empty()
                        : day.getActivities().stream())
                .filter(activity -> activityId.equals(activity.getId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Hoạt động không tồn tại"));
    }

    private ItineraryDay findActivityDay(Trip trip, Long activityId) {
        return trip.getItineraryDays().stream()
                .filter(day -> day.getActivities() != null
                        && day.getActivities().stream().anyMatch(activity -> activityId.equals(activity.getId())))
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
        activity.setType(parseEnum(Activity.ActivityType.class, req.getType(),
                activity.getType() != null ? activity.getType() : Activity.ActivityType.ATTRACTION));
        activity.setLocation(req.getLocation());
        activity.setDuration(
                req.getDuration() != null && !req.getDuration().isBlank() ? req.getDuration().trim() : "1 giờ");
        long estimatedCost = req.getEstimatedCost() != null ? req.getEstimatedCost() : 0;
        activity.setEstimatedCost(estimatedCost);
        activity.setNote(estimatedCost > 0 ? removeCostReviewNote(req.getNote()) : req.getNote());
        activity.setLatitude(req.getLatitude());
        activity.setLongitude(req.getLongitude());
        activity.setGooglePlaceId(req.getGooglePlaceId());
        activity.setSortOrder(req.getSortOrder() > 0 ? req.getSortOrder() : defaultSortOrder);
    }

    private String removeCostReviewNote(String note) {
        if (note == null || note.isBlank()) {
            return note;
        }
        String cleaned = note.replace(COST_REVIEW_NOTE, "").replaceAll("\\s{2,}", " ").trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private void validateNoTimeOverlap(ItineraryDay day, Activity candidate, Long ignoreActivityId) {
        validateNoTimeOverlap(day, candidate, ignoreActivityId, false);
    }

    private void validateNoTimeOverlap(ItineraryDay day, Activity candidate, Long ignoreActivityId, boolean relaxedForAiProposal) {
        LocalTime candidateStart = parseTime(candidate.getTime());
        LocalTime candidateEnd = candidateStart.plusMinutes(parseDurationMinutes(candidate.getDuration()));
        if (day.getActivities() == null)
            return;
        for (Activity other : day.getActivities()) {
            if (ignoreActivityId != null && ignoreActivityId.equals(other.getId()))
                continue;
            LocalTime otherStart = parseTime(other.getTime());
            LocalTime otherEnd = otherStart.plusMinutes(parseDurationMinutes(other.getDuration()));
            if (candidateStart.isBefore(otherEnd) && otherStart.isBefore(candidateEnd)) {
                if (relaxedForAiProposal) {
                    if (candidate.getType() == Activity.ActivityType.TRANSPORT
                            || other.getType() == Activity.ActivityType.TRANSPORT
                            || candidate.getType() == Activity.ActivityType.ACCOMMODATION
                            || other.getType() == Activity.ActivityType.ACCOMMODATION) {
                        continue;
                    }
                    LocalTime overlapStart = candidateStart.isAfter(otherStart) ? candidateStart : otherStart;
                    LocalTime overlapEnd = candidateEnd.isBefore(otherEnd) ? candidateEnd : otherEnd;
                    long overlapMinutes = ChronoUnit.MINUTES.between(overlapStart, overlapEnd);
                    if (overlapMinutes <= 30) {
                        continue;
                    }
                }
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
        if (duration == null || duration.isBlank())
            return 60;
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
            if (byTime != 0)
                return byTime;
            return Integer.compare(a.getSortOrder() != null ? a.getSortOrder() : 0,
                    b.getSortOrder() != null ? b.getSortOrder() : 0);
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
        if (value == null || value.isBlank())
            return fallback;
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private int resolveTravelerCount(TripDto.GenerateRequest req) {
        Integer travelers = req.getTravelerCount();
        if (travelers == null)
            return 1;
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
        if (days == null)
            return List.of();
        return days.stream().map(d -> {
            TripDto.DayResponse dr = new TripDto.DayResponse();
            dr.setDay(d.getDayNumber());
            dr.setTitle(d.getTitle());
            dr.setSummary(d.getSummary());
            dr.setActivities(d.getActivities() == null ? List.of()
                    : d.getActivities().stream().map(TripDto.ActivityResponse::from).collect(Collectors.toList()));
            return dr;
        }).collect(Collectors.toList());
    }

    private TripDto.BudgetBreakdown calculateBudget(Trip trip, List<TripDto.DayResponse> schedule) {
        TripDto.BudgetBreakdown b = new TripDto.BudgetBreakdown();
        long food = 0, transport = 0, accommodation = 0, activities = 0;
        if (schedule != null) {
            for (TripDto.DayResponse day : schedule) {
                if (day.getActivities() == null)
                    continue;
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
        b.setTotal(total);
        b.setTransport(transport);
        b.setAccommodation(accommodation);
        b.setFood(food);
        b.setActivities(activities);
        return b;
    }

    private void normalizeActivityCosts(List<TripDto.DayResponse> schedule, Trip trip) {
        if (schedule == null || schedule.isEmpty())
            return;
        boolean bundledIntercityTransportCost = hasBundledIntercityTransportCost(schedule, trip);
        for (TripDto.DayResponse day : schedule) {
            if (day.getActivities() == null)
                continue;
            for (TripDto.ActivityResponse activity : day.getActivities()) {
                long originalCost = Math.max(0, activity.getEstimatedCost());
                long normalizedCost = normalizeActivityCost(activity, trip, bundledIntercityTransportCost);
                activity.setEstimatedCost(normalizedCost);
                if (normalizedCost > originalCost) {
                    activity.setNote(normalizeIncludedCostNote(activity.getNote()));
                }
                if (normalizedCost == 0 && isRequiredPaidActivityWithoutCost(activity, trip, bundledIntercityTransportCost)) {
                    markCostNeedsReview(activity);
                }
            }
        }
    }

    private long normalizeActivityCost(TripDto.ActivityResponse activity, Trip trip, boolean bundledIntercityTransportCost) {
        long current = Math.max(0, activity.getEstimatedCost());
        boolean zeroCostBundledIntercity = isZeroCostBundledIntercityActivity(activity, trip, bundledIntercityTransportCost);
        if (!zeroCostBundledIntercity) {
            Long perPersonCost = extractPerPersonCost(activity.getNote());
            if (perPersonCost != null) {
                long expected = roundToNearest(perPersonCost * Math.max(1, trip.getTravelerCount()), 10_000);
                current = reconcileRequiredCost(current, expected, 0.10);
            }

            Long requiredCost = inferRequiredActivityCost(activity, trip, bundledIntercityTransportCost);
            if (requiredCost != null) {
                current = reconcileRequiredCost(current, roundToNearest(requiredCost, 10_000), 0.25);
            }
        }

        return current;
    }

    private boolean isZeroCostBundledIntercityActivity(
            TripDto.ActivityResponse activity,
            Trip trip,
            boolean bundledIntercityTransportCost) {
        if (!bundledIntercityTransportCost || Math.max(0, activity.getEstimatedCost()) != 0) {
            return false;
        }
        String type = normalizeText(activity.getType());
        String combined = normalizeText(String.join(" ",
                nullToBlank(activity.getName()),
                nullToBlank(activity.getLocation()),
                nullToBlank(activity.getNote())));
        return isTripIntercityTransportActivity(type, combined, trip);
    }

    private long reconcileRequiredCost(long current, long expected, double allowedUnderrun) {
        if (expected <= 0) {
            return current;
        }
        if (current == 0) {
            return expected;
        }
        return current < Math.round(expected * (1 - allowedUnderrun)) ? expected : current;
    }

    private Long inferRequiredActivityCost(TripDto.ActivityResponse activity, Trip trip, boolean bundledIntercityTransportCost) {
        long current = Math.max(0, activity.getEstimatedCost());
        String type = normalizeText(activity.getType());
        String combined = normalizeText(String.join(" ",
                nullToBlank(activity.getName()),
                nullToBlank(activity.getLocation()),
                nullToBlank(activity.getNote())));

        Long mentionedCost = extractLargestMentionedCost(activity.getNote());
        if (isTripIntercityTransportActivity(type, combined, trip) && mentionedCost != null) {
            if (current == 0 && bundledIntercityTransportCost) {
                return null;
            }
            return mentionedCost;
        }
        if (isVehicleRentalStartActivity(type, combined) && mentionedCost != null) {
            return estimateVehicleRentalCost(combined, trip, mentionedCost);
        }
        if (mentionsExcludedRequiredCost(combined) && mentionedCost != null) {
            return mentionedCost;
        }
        return null;
    }

    private boolean isRequiredPaidActivityWithoutCost(TripDto.ActivityResponse activity, Trip trip, boolean bundledIntercityTransportCost) {
        String type = normalizeText(activity.getType());
        String combined = normalizeText(String.join(" ",
                nullToBlank(activity.getName()),
                nullToBlank(activity.getLocation()),
                nullToBlank(activity.getNote())));

        return (isTripIntercityTransportActivity(type, combined, trip) && !bundledIntercityTransportCost)
                || isVehicleRentalStartActivity(type, combined)
                || mentionsExcludedRequiredCost(combined);
    }

    private boolean hasBundledIntercityTransportCost(List<TripDto.DayResponse> schedule, Trip trip) {
        if (schedule == null || schedule.isEmpty()) {
            return false;
        }
        for (TripDto.DayResponse day : schedule) {
            if (day == null || day.getActivities() == null) {
                continue;
            }
            for (TripDto.ActivityResponse activity : day.getActivities()) {
                String type = normalizeText(activity.getType());
                if (!type.equals("transport") || Math.max(0, activity.getEstimatedCost()) == 0) {
                    continue;
                }
                String combined = normalizeText(String.join(" ",
                        nullToBlank(activity.getName()),
                        nullToBlank(activity.getLocation()),
                        nullToBlank(activity.getNote())));
                if (isTripIntercityTransportActivity(type, combined, trip)
                        && mentionsBundledIntercityTransportCost(combined)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isTripIntercityTransportActivity(String normalizedType, String normalizedText, Trip trip) {
        if (!normalizedType.equals("transport")) {
            return false;
        }
        String departure = normalizeText(trip.getDeparture());
        String destination = normalizeText(trip.getDestination());
        return !departure.isBlank()
                && !destination.isBlank()
                && normalizedText.contains(departure)
                && normalizedText.contains(destination);
    }

    private boolean isVehicleRentalStartActivity(String normalizedType, String normalizedText) {
        if (!normalizedType.equals("transport")) {
            return false;
        }
        return containsAny(normalizedText,
                "thue xe may",
                "nhan xe may",
                "lay xe may",
                "thue xe dap",
                "nhan xe dap",
                "thue o to",
                "thue oto",
                "nhan o to",
                "nhan oto",
                "lay o to",
                "lay oto");
    }

    private long estimateVehicleRentalCost(String normalizedText, Trip trip, Long mentionedCost) {
        if (mentionedCost == null || mentionedCost <= 0) {
            return 0;
        }
        int travelers = Math.max(1, trip.getTravelerCount());
        if (containsAny(normalizedText, "xe may")) {
            int bikeCount = Math.max(1, (int) Math.ceil(travelers / 2.0));
            return mentionedCost * bikeCount;
        }
        if (containsAny(normalizedText, "xe dap")) {
            return mentionedCost * travelers;
        }
        return mentionedCost;
    }

    private void markCostNeedsReview(TripDto.ActivityResponse activity) {
        activity.setCostEstimateStatus(COST_REVIEW_STATUS);
        activity.setCostEstimateMessage(COST_REVIEW_MESSAGE);
        activity.setNote(appendCostReviewNote(activity.getNote()));
    }

    private String appendCostReviewNote(String note) {
        if (note != null && note.contains("Chi phí cần kiểm tra")) {
            return note;
        }
        if (note == null || note.isBlank()) {
            return COST_REVIEW_NOTE;
        }
        return note + " " + COST_REVIEW_NOTE;
    }

    private boolean mentionsExcludedRequiredCost(String normalizedText) {
        return containsAny(normalizedText,
                "khong bao gom trong chi phi nay",
                "chua bao gom trong chi phi nay",
                "khong bao gom vao chi phi",
                "chua bao gom vao chi phi",
                "not included");
    }

    private boolean mentionsBundledIntercityTransportCost(String normalizedText) {
        return containsAny(normalizedText,
                "khu hoi",
                "hai chieu",
                "2 chieu",
                "ca di va ve",
                "di va ve",
                "ca di ca ve",
                "round trip",
                "round-trip",
                "return ticket",
                "bao gom chieu ve",
                "bao gom ca chieu ve",
                "bao gom chuyen bay ve",
                "bao gom ve ve",
                "bao gom ca tien ve");
    }

    private String normalizeIncludedCostNote(String note) {
        if (note == null || note.isBlank()) {
            return note;
        }
        if (!mentionsExcludedRequiredCost(normalizeText(note))) {
            return note;
        }
        return note + " Khoản chi phí bắt buộc này đã được cộng vào ước tính.";
    }

    private boolean containsAny(String text, String... terms) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private Long extractPerPersonCost(String note) {
        String normalized = normalizeText(note)
                .replace("vnđ", " vnd")
                .replace("₫", " vnd");
        if (!normalized.contains("/nguoi") && !normalized.contains("/khach")) {
            return null;
        }

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "(\\d[\\d\\.,]*)\\s*(trieu|tr|k|nghin|ngan|vnd|d)?\\s*/\\s*(nguoi|khach)");
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

    private Long extractLargestMentionedCost(String text) {
        String normalized = normalizeText(text)
                .replace("vnÄ‘", " vnd")
                .replace("â‚«", " vnd");
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "(\\d+(?:[\\.,]\\d+)*)\\s*(trieu|tr|k|nghin|ngan|vnd|d)");
        java.util.regex.Matcher matcher = pattern.matcher(normalized);
        Long result = null;
        while (matcher.find()) {
            Long parsed = parseMoney(matcher.group(1), matcher.group(2));
            if (parsed != null && parsed > 0 && (result == null || parsed > result)) {
                result = parsed;
            }
        }
        return result;
    }

    private Long parseMoney(String rawValue, String rawUnit) {
        if (rawValue == null || rawValue.isBlank())
            return null;
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
        if (trip.getBudgetMode() == Trip.BudgetMode.TOTAL && trip.getBudgetTotal() != null
                && trip.getBudgetTotal() > 0) {
            return trip.getBudgetTotal();
        }
        return Math.max(0, trip.getBudgetPerPerson()) * Math.max(1, trip.getTravelerCount());
    }

    private long roundToNearest(long value, long unit) {
        if (unit <= 0)
            return value;
        return Math.round((double) value / unit) * unit;
    }

    private String normalizeText(String value) {
        if (value == null)
            return "";
        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace("đ", "d")
                .replace("Đ", "D")
                .toLowerCase(java.util.Locale.ROOT);
    }

    private record DayRegenerationProposal(
            String proposalId,
            Long tripId,
            Long userId,
            Integer dayNumber,
            TripDto.DayResponse day,
            long oldBudget,
            long newBudget,
            List<String> warnings,
            List<String> persistentWarnings,
            LocalDateTime expiresAt) {
    }
}
