package com.vivuplan.vivuplan_be.service;

import com.vivuplan.vivuplan_be.dto.TripDto;
import com.vivuplan.vivuplan_be.entity.Activity;
import com.vivuplan.vivuplan_be.entity.ItineraryDay;
import com.vivuplan.vivuplan_be.entity.Trip;
import com.vivuplan.vivuplan_be.entity.User;
import com.vivuplan.vivuplan_be.repository.TripRepository;
import com.vivuplan.vivuplan_be.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripServiceTest {

    @Mock
    private TripRepository tripRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AiService aiService;

    @Test
    void addActivitySortsByTimeAndRecalculatesBudget() {
        Trip trip = sampleTrip();
        TripService service = new TripService(tripRepository, userRepository, aiService);
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));
        when(tripRepository.saveAndFlush(any(Trip.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TripDto.UpdateActivityRequest req = new TripDto.UpdateActivityRequest();
        req.setTime("08:00");
        req.setName("Ăn sáng");
        req.setType("FOOD");
        req.setLocation("Trung tâm");
        req.setDuration("45 phút");
        req.setEstimatedCost(50_000L);

        TripDto.TripResponse response = service.addActivity(1L, 7L, 1, req);

        assertThat(response.getSchedule().get(0).getActivities())
                .extracting(TripDto.ActivityResponse::getName)
                .containsExactly("Ăn sáng", "Hoạt động buổi sáng", "Ăn trưa");
        assertThat(response.getBudget().getTotal()).isEqualTo(230_000L);
        assertThat(response.getBudget().getFood()).isEqualTo(130_000L);
        assertThat(response.getBudget().getActivities()).isEqualTo(100_000L);
    }

    @Test
    void updateActivityRejectsOverlappingTimeWindow() {
        Trip trip = sampleTrip();
        TripService service = new TripService(tripRepository, userRepository, aiService);
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));

        TripDto.UpdateActivityRequest req = new TripDto.UpdateActivityRequest();
        req.setTime("10:00");
        req.setName("Ăn trưa");
        req.setType("FOOD");
        req.setLocation("Trung tâm");
        req.setDuration("1 giờ");
        req.setEstimatedCost(80_000L);

        assertThatThrownBy(() -> service.updateActivity(1L, 7L, 101L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trùng");
    }

    private Trip sampleTrip() {
        User user = User.builder()
                .id(7L)
                .name("Test User")
                .email("test@example.com")
                .build();

        Trip trip = Trip.builder()
                .id(1L)
                .user(user)
                .destination("Đà Lạt")
                .departure("Hà Nội")
                .days(1)
                .budgetPerPerson(230_000L)
                .budgetMode(Trip.BudgetMode.PER_PERSON)
                .travelerCount(1)
                .style(Trip.TravelStyle.RELAXING)
                .groupType(Trip.GroupType.SOLO)
                .transport(Trip.TransportMode.MIXED)
                .outboundTransport(Trip.TransportMode.MIXED)
                .localTransport(Trip.TransportMode.MIXED)
                .status(Trip.TripStatus.DRAFT)
                .isPublic(false)
                .shareCode("TESTCODE")
                .build();

        ItineraryDay day = ItineraryDay.builder()
                .id(10L)
                .trip(trip)
                .dayNumber(1)
                .title("Ngày 1")
                .summary("Test")
                .activities(new ArrayList<>())
                .build();

        Activity morning = Activity.builder()
                .id(100L)
                .itineraryDay(day)
                .time("09:00")
                .name("Hoạt động buổi sáng")
                .type(Activity.ActivityType.ACTIVITY)
                .location("Trung tâm")
                .duration("2 giờ")
                .estimatedCost(100_000L)
                .sortOrder(0)
                .build();

        Activity lunch = Activity.builder()
                .id(101L)
                .itineraryDay(day)
                .time("12:30")
                .name("Ăn trưa")
                .type(Activity.ActivityType.FOOD)
                .location("Trung tâm")
                .duration("1 giờ")
                .estimatedCost(80_000L)
                .sortOrder(1)
                .build();

        day.setActivities(new ArrayList<>(List.of(morning, lunch)));
        trip.setItineraryDays(new ArrayList<>(List.of(day)));
        return trip;
    }
}
