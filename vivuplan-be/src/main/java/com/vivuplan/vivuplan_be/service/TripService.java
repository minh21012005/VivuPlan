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
        aiReq.setStyle(req.getStyle());
        aiReq.setGroupType(req.getGroupType());
        aiReq.setTransport(req.getTransport());
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
                .style(parseEnum(Trip.TravelStyle.class, req.getStyle(), Trip.TravelStyle.RELAXING))
                .groupType(parseEnum(Trip.GroupType.class, req.getGroupType(), Trip.GroupType.FRIENDS))
                .transport(parseEnum(Trip.TransportMode.class, req.getTransport(), Trip.TransportMode.MIXED))
                .notes(req.getNotes())
                .status(Trip.TripStatus.DRAFT)
                .shareCode(UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .build();

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
                    act.setSortOrder(ar.getSortOrder());
                    activities.add(act);
                }
            }
            day.setActivities(activities);
            days.add(day);
        }
        trip.setItineraryDays(days);

        trip = tripRepository.save(trip);

        TripDto.TripResponse response = TripDto.TripResponse.from(trip);
        response.setSchedule(aiSchedule);
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
        b.setTotal(trip.getBudgetPerPerson());

        long food = 0, transport = 0, accommodation = 0, activities = 0;
        if (schedule != null) {
            for (TripDto.DayResponse day : schedule) {
                if (day.getActivities() == null) continue;
                for (TripDto.ActivityResponse act : day.getActivities()) {
                    switch (act.getType()) {
                        case "FOOD", "CAFE" -> food += act.getEstimatedCost();
                        case "TRANSPORT" -> transport += act.getEstimatedCost();
                        case "ACCOMMODATION" -> accommodation += act.getEstimatedCost();
                        default -> activities += act.getEstimatedCost();
                    }
                }
            }
        }

        // Fill remaining with estimates if not set
        if (accommodation == 0) accommodation = (long)(trip.getBudgetPerPerson() * 0.30);
        if (transport == 0)     transport     = (long)(trip.getBudgetPerPerson() * 0.20);

        b.setFood(food);
        b.setTransport(transport);
        b.setAccommodation(accommodation);
        b.setActivities(activities);
        return b;
    }
}
