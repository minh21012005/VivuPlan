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
    private final AiService aiService;
    private final Map<String, DayRegenerationProposal> dayRegenerationProposals = new ConcurrentHashMap<>();
    private static final int REGENERATION_PROPOSAL_TTL_MINUTES = 30;

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

        TripDto.DayResponse proposedDay = aiService.regenerateDay(
                aiReq,
                currentSchedule,
                dayNumber,
                req != null ? req.getIntent() : "REGENERATE",
                req != null ? req.getInstruction() : null);
        normalizeActivityCosts(List.of(proposedDay), trip);
        validateRegeneratedDayProposal(trip, proposedDay);

        long oldBudget = sumDayCost(existingDay);
        long newBudget = sumDayCost(proposedDay);
        List<String> warnings = buildRegenerationWarnings(trip, existingDay, proposedDay);
        String proposalId = UUID.randomUUID().toString();
        dayRegenerationProposals.put(proposalId, new DayRegenerationProposal(
                proposalId,
                tripId,
                userId,
                dayNumber,
                proposedDay,
                oldBudget,
                newBudget,
                LocalDateTime.now().plusMinutes(REGENERATION_PROPOSAL_TTL_MINUTES)));

        TripDto.RegenerateDayPreviewResponse response = new TripDto.RegenerateDayPreviewResponse();
        response.setProposalId(proposalId);
        response.setDayNumber(dayNumber);
        response.setDay(proposedDay);
        response.setOldBudget(oldBudget);
        response.setNewBudget(newBudget);
        response.setWarnings(warnings);
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
        trip = tripRepository.saveAndFlush(trip);
        dayRegenerationProposals.remove(req.getProposalId());
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
        return req;
    }

    private void validateRegeneratedDayProposal(Trip trip, TripDto.DayResponse proposedDay) {
        if (proposedDay == null || proposedDay.getDay() < 1 || proposedDay.getDay() > trip.getDays()) {
            throw new IllegalArgumentException("Ngày được tạo lại không hợp lệ");
        }
        if (proposedDay.getTitle() == null || proposedDay.getTitle().isBlank()) {
            throw new IllegalArgumentException("Ngày được tạo lại thiếu tiêu đề");
        }
        if (proposedDay.getActivities() == null || proposedDay.getActivities().size() < 4) {
            throw new IllegalArgumentException("Ngày được tạo lại cần ít nhất 4 hoạt động");
        }
        if (proposedDay.getActivities().size() > 8) {
            throw new IllegalArgumentException("Ngày được tạo lại có quá nhiều hoạt động");
        }

        ItineraryDay tempDay = new ItineraryDay();
        tempDay.setDayNumber(proposedDay.getDay());
        tempDay.setActivities(new ArrayList<>());
        for (TripDto.ActivityResponse activityResponse : proposedDay.getActivities()) {
            Activity activity = toActivity(activityResponse, tempDay);
            validateNoTimeOverlap(tempDay, activity, null);
            tempDay.getActivities().add(activity);
        }
        resequenceActivities(tempDay);

        long proposedTripTotal = calculateBudgetWithReplacement(trip, proposedDay);
        long budgetCeiling = resolveGroupBudget(trip);
        if (budgetCeiling > 0 && proposedTripTotal > Math.round(budgetCeiling * 1.15)) {
            throw new IllegalArgumentException(
                    "Phương án mới vượt ngân sách quá nhiều. Vui lòng thử yêu cầu giảm chi phí.");
        }
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

    private List<String> buildRegenerationWarnings(Trip trip, ItineraryDay oldDay, TripDto.DayResponse proposedDay) {
        List<String> warnings = new ArrayList<>();
        long oldBudget = sumDayCost(oldDay);
        long newBudget = sumDayCost(proposedDay);
        if (oldBudget > 0 && newBudget > Math.round(oldBudget * 1.25)) {
            warnings.add("Chi phí ngày mới cao hơn đáng kể so với ngày cũ.");
        }
        long proposedTripTotal = calculateBudgetWithReplacement(trip, proposedDay);
        long budgetCeiling = resolveGroupBudget(trip);
        if (budgetCeiling > 0 && proposedTripTotal > budgetCeiling) {
            warnings.add("Tổng chi phí sau khi áp dụng có thể vượt ngân sách bạn đã nhập.");
        }
        long transportCount = proposedDay.getActivities() == null ? 0
                : proposedDay.getActivities().stream()
                        .filter(activity -> "TRANSPORT".equalsIgnoreCase(activity.getType()))
                        .count();
        if (transportCount == 0) {
            warnings.add("Ngày mới chưa có chặng di chuyển riêng, hãy kiểm tra lại nếu các điểm ở xa nhau.");
        }
        return warnings;
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
        if (day.getActivities() == null)
            return;
        for (Activity other : day.getActivities()) {
            if (ignoreActivityId != null && ignoreActivityId.equals(other.getId()))
                continue;
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
        long budgetCeiling = resolveGroupBudget(trip);
        if (budgetCeiling > 0 && total > budgetCeiling) {
            double overrun = (total - budgetCeiling) / (double) budgetCeiling;
            if (overrun > 0.10) {
                log.warn("Estimated trip cost exceeds user budget by {}%: estimated={}, budgetCeiling={}",
                        Math.round(overrun * 100), total, budgetCeiling);
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
        if (schedule == null || schedule.isEmpty())
            return;
        for (TripDto.DayResponse day : schedule) {
            if (day.getActivities() == null)
                continue;
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
            LocalDateTime expiresAt) {
    }
}
