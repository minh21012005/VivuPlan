package com.vivuplan.vivuplan_be.service;

import com.vivuplan.vivuplan_be.dto.TripDto;
import com.vivuplan.vivuplan_be.entity.Activity;
import com.vivuplan.vivuplan_be.entity.ItineraryDay;
import com.vivuplan.vivuplan_be.entity.Trip;
import com.vivuplan.vivuplan_be.entity.User;
import com.vivuplan.vivuplan_be.repository.DestinationRepository;
import com.vivuplan.vivuplan_be.repository.TripRepository;
import com.vivuplan.vivuplan_be.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripServiceTest {

    @Mock
    private TripRepository tripRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DestinationRepository destinationRepository;

    @Mock
    private AiService aiService;

    @Mock
    private WeatherService weatherService;

    @Test
    void addActivitySortsByTimeAndRecalculatesBudget() {
        Trip trip = sampleTrip();
        TripService service = new TripService(tripRepository, userRepository, destinationRepository, aiService, weatherService);
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
        TripService service = new TripService(tripRepository, userRepository, destinationRepository, aiService, weatherService);
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

    @Test
    void generateAndSaveWarnsFromAiRequestFulfillmentReport() {
        User user = sampleUser();
        TripService service = new TripService(tripRepository, userRepository, destinationRepository, aiService, weatherService);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(destinationRepository.findByNameIgnoreCaseOrSlugIgnoreCase(anyString(), anyString()))
                .thenReturn(Optional.empty());
        mockRainForecast();
        when(tripRepository.existsByShareCode(anyString())).thenReturn(false);
        when(tripRepository.saveAndFlush(any(Trip.class))).thenAnswer(invocation -> {
            Trip saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        String userMessage = "Yêu cầu \"Nhảy dù ở Đà Nẵng\" chưa được áp dụng vì các ngày đi có mưa bão, hoạt động này không an toàn.";
        TripDto.RequestFulfillment requestFulfillment = requestFulfillment(
                "NOT_FULFILLED",
                "Nhảy dù ở Đà Nẵng",
                "NOT_APPLIED",
                "WEATHER_SAFETY",
                userMessage);
        when(aiService.generateItinerary(any(TripDto.GenerateRequest.class)))
                .thenReturn(new AiService.GeneratedItineraryResult(List.of(proposedDayWithoutRequestedActivity()), requestFulfillment));

        TripDto.GenerateRequest req = generateRequest("Nhảy dù ở Đà Nẵng", "");

        TripDto.TripResponse response = service.generateAndSave(7L, req);

        assertThat(response.getRequestFulfillment()).isSameAs(requestFulfillment);
        assertThat(response.getWarnings()).contains(userMessage);
    }

    @Test
    void generateAndSaveWarnsWhenAiDoesNotReturnRequestFulfillmentReport() {
        User user = sampleUser();
        TripService service = new TripService(tripRepository, userRepository, destinationRepository, aiService, weatherService);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(destinationRepository.findByNameIgnoreCaseOrSlugIgnoreCase(anyString(), anyString()))
                .thenReturn(Optional.empty());
        mockRainForecast();
        when(tripRepository.existsByShareCode(anyString())).thenReturn(false);
        when(tripRepository.saveAndFlush(any(Trip.class))).thenAnswer(invocation -> {
            Trip saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        when(aiService.generateItinerary(any(TripDto.GenerateRequest.class)))
                .thenReturn(new AiService.GeneratedItineraryResult(List.of(proposedDayWithoutRequestedActivity()), null));

        TripDto.GenerateRequest req = generateRequest("", "Nhảy dù ở Đà Nẵng cũng hay mà");

        TripDto.TripResponse response = service.generateAndSave(7L, req);

        assertThat(response.getWarnings())
                .contains("Yêu cầu của bạn chưa được VivuPlan xác minh đầy đủ trong lịch trình vừa tạo. Hãy kiểm tra lại trước khi sử dụng.");
    }

    @Test
    void generateAndSaveNormalizesRequiredTransportCosts() {
        User user = sampleUser();
        TripService service = new TripService(tripRepository, userRepository, destinationRepository, aiService, weatherService);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(destinationRepository.findByNameIgnoreCaseOrSlugIgnoreCase(anyString(), anyString()))
                .thenReturn(Optional.empty());
        mockRainForecast();
        when(tripRepository.existsByShareCode(anyString())).thenReturn(false);
        when(tripRepository.saveAndFlush(any(Trip.class))).thenAnswer(invocation -> {
            Trip saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        when(aiService.generateItinerary(any(TripDto.GenerateRequest.class)))
                .thenReturn(new AiService.GeneratedItineraryResult(
                        List.of(proposedDayWithUnrealisticRequiredCosts()),
                        noRequestFulfillment()));

        TripDto.GenerateRequest req = generateRequest("", "");

        TripDto.TripResponse response = service.generateAndSave(7L, req);

        List<TripDto.ActivityResponse> activities = response.getSchedule().get(0).getActivities();
        assertThat(activities.get(0).getEstimatedCost()).isGreaterThanOrEqualTo(1_500_000L);
        assertThat(activities.get(1).getEstimatedCost()).isGreaterThanOrEqualTo(200_000L);
        assertThat(response.getBudget().getTransport()).isGreaterThanOrEqualTo(1_700_000L);
    }

    @Test
    void previewRegenerateDayWarnsFromAiRequestFulfillmentReport() {
        Trip trip = sampleTrip();
        trip.setStartDate(LocalDate.now());
        trip.setEndDate(LocalDate.now());

        TripService service = new TripService(tripRepository, userRepository, destinationRepository, aiService, weatherService);
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));
        when(destinationRepository.findByNameIgnoreCaseOrSlugIgnoreCase(anyString(), anyString()))
                .thenReturn(Optional.empty());
        mockRainForecast();
        String userMessage = "Yêu cầu \"Nhảy dù ở Đà Nẵng\" chưa được áp dụng vì ngày này có mưa bão, hoạt động này không an toàn.";
        TripDto.RequestFulfillment requestFulfillment = requestFulfillment(
                "NOT_FULFILLED",
                "Nhảy dù ở Đà Nẵng",
                "NOT_APPLIED",
                "WEATHER_SAFETY",
                userMessage);
        when(aiService.regenerateDay(any(TripDto.GenerateRequest.class), any(), anyInt(), anyString(), anyString()))
                .thenReturn(new AiService.RegeneratedDayResult(proposedDayWithoutRequestedActivity(), requestFulfillment));

        TripDto.RegenerateDayRequest req = new TripDto.RegenerateDayRequest();
        req.setInstruction("Nhảy dù ở Đà Nẵng cũng hay mà");

        TripDto.RegenerateDayPreviewResponse response = service.previewRegenerateDay(1L, 7L, 1, req);

        assertThat(response.getRequestFulfillment()).isSameAs(requestFulfillment);
        assertThat(response.getWarnings()).contains(userMessage);
    }

    @Test
    void previewRegenerateDayWarnsWhenAiDoesNotReturnRequestFulfillmentReport() {
        Trip trip = sampleTrip();
        trip.setStartDate(LocalDate.now());
        trip.setEndDate(LocalDate.now());

        TripService service = new TripService(tripRepository, userRepository, destinationRepository, aiService, weatherService);
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));
        when(destinationRepository.findByNameIgnoreCaseOrSlugIgnoreCase(anyString(), anyString()))
                .thenReturn(Optional.empty());
        mockRainForecast();
        when(aiService.regenerateDay(any(TripDto.GenerateRequest.class), any(), anyInt(), anyString(), anyString()))
                .thenReturn(new AiService.RegeneratedDayResult(proposedDayWithoutRequestedActivity(), null));

        TripDto.RegenerateDayRequest req = new TripDto.RegenerateDayRequest();
        req.setInstruction("Nhảy dù ở Đà Nẵng cũng hay mà");

        TripDto.RegenerateDayPreviewResponse response = service.previewRegenerateDay(1L, 7L, 1, req);

        assertThat(response.getWarnings())
                .contains("Yêu cầu của bạn chưa được VivuPlan xác minh đầy đủ trong preview này. Hãy kiểm tra lại trước khi sử dụng.");
    }

    @Test
    void previewRegenerateDayNormalizesRequiredTransportCosts() {
        Trip trip = sampleTrip();
        TripDto.GenerateRequest baseRequest = generateRequest("", "");
        trip.setDestination(baseRequest.getDestination());
        trip.setDeparture(baseRequest.getDeparture());
        trip.setBudgetPerPerson(5_000_000L);
        trip.setOutboundTransport(Trip.TransportMode.PLANE);
        trip.setLocalTransport(Trip.TransportMode.MOTORBIKE);
        trip.setStartDate(LocalDate.now());
        trip.setEndDate(LocalDate.now());

        TripService service = new TripService(tripRepository, userRepository, destinationRepository, aiService, weatherService);
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));
        when(destinationRepository.findByNameIgnoreCaseOrSlugIgnoreCase(anyString(), anyString()))
                .thenReturn(Optional.empty());
        mockRainForecast();
        when(aiService.regenerateDay(any(TripDto.GenerateRequest.class), any(), anyInt(), anyString(), anyString()))
                .thenReturn(new AiService.RegeneratedDayResult(
                        proposedDayWithUnrealisticRequiredCosts(),
                        noRequestFulfillment()));

        TripDto.RegenerateDayRequest req = new TripDto.RegenerateDayRequest();
        req.setInstruction("Regenerate this day with realistic costs");

        TripDto.RegenerateDayPreviewResponse response = service.previewRegenerateDay(1L, 7L, 1, req);

        List<TripDto.ActivityResponse> activities = response.getDay().getActivities();
        assertThat(activities.get(0).getEstimatedCost()).isGreaterThanOrEqualTo(1_500_000L);
        assertThat(activities.get(1).getEstimatedCost()).isGreaterThanOrEqualTo(200_000L);
        assertThat(response.getNewBudget()).isGreaterThanOrEqualTo(1_700_000L);
    }

    private void mockRainForecast() {
        when(weatherService.getForecastForDestination(anyString(), nullable(Double.class), nullable(Double.class)))
                .thenReturn(List.of(WeatherService.DailyWeather.builder()
                        .date(LocalDate.now().toString())
                        .code(63)
                        .minTemp(22)
                        .maxTemp(28)
                        .precipitationProbability(80)
                        .build()));
    }

    private TripDto.GenerateRequest generateRequest(String mustVisit, String notes) {
        TripDto.GenerateRequest req = new TripDto.GenerateRequest();
        req.setDestination("Đà Nẵng");
        req.setDeparture("Hà Nội");
        req.setStartDate(LocalDate.now());
        req.setEndDate(LocalDate.now());
        req.setDays(1);
        req.setBudgetPerPerson(3_000_000L);
        req.setBudgetMode("PER_PERSON");
        req.setTravelerCount(1);
        req.setStyle("ADVENTURE");
        req.setGroupType("SOLO");
        req.setTransport("MIXED");
        req.setOutboundTransport("PLANE");
        req.setLocalTransport("MIXED");
        req.setDestinationSuggested(false);
        req.setMustVisit(mustVisit);
        req.setNotes(notes);
        return req;
    }

    private TripDto.RequestFulfillment requestFulfillment(
            String overallStatus,
            String requestedText,
            String status,
            String reasonCode,
            String userMessage) {
        TripDto.RequestFulfillment fulfillment = new TripDto.RequestFulfillment();
        fulfillment.setOverallStatus(overallStatus);
        TripDto.RequestFulfillmentItem item = new TripDto.RequestFulfillmentItem();
        item.setRequestedText(requestedText);
        item.setStatus(status);
        item.setReasonCode(reasonCode);
        item.setUserMessage(userMessage);
        fulfillment.setItems(List.of(item));
        return fulfillment;
    }

    private TripDto.RequestFulfillment noRequestFulfillment() {
        TripDto.RequestFulfillment fulfillment = new TripDto.RequestFulfillment();
        fulfillment.setOverallStatus("NO_REQUEST");
        fulfillment.setItems(List.of());
        return fulfillment;
    }

    private TripDto.DayResponse proposedDayWithoutRequestedActivity() {
        TripDto.DayResponse day = new TripDto.DayResponse();
        day.setDay(1);
        day.setTitle("Ngày 1 - Phương án trong nhà");
        day.setSummary("Lịch trình nhẹ nhàng, ít phụ thuộc thời tiết.");
        day.setActivities(List.of(
                activity("08:00", "Ăn sáng tại trung tâm", "FOOD"),
                activity("10:00", "Tham quan bảo tàng địa phương", "ATTRACTION"),
                activity("12:00", "Di chuyển bằng taxi nội thành", "TRANSPORT"),
                activity("14:00", "Cà phê acoustic", "CAFE")));
        return day;
    }

    private TripDto.DayResponse proposedDayWithUnrealisticRequiredCosts() {
        TripDto.DayResponse day = new TripDto.DayResponse();
        day.setDay(1);
        day.setTitle("Ngày 1 - Đà Nẵng");
        day.setSummary("Lịch trình có chi phí bắt buộc bị AI bỏ sót.");
        day.setActivities(List.of(
                activity(
                        "08:00",
                        "Di chuyển từ Hà Nội đến Đà Nẵng",
                        "TRANSPORT",
                        "Sân bay Quốc tế Nội Bài (HAN) -> Sân bay Quốc tế Đà Nẵng (DAD)",
                        "1 giờ 30 phút",
                        0,
                        "Bay thẳng Hà Nội - Đà Nẵng."),
                activity(
                        "10:30",
                        "Di chuyển về khách sạn & Nhận xe máy",
                        "TRANSPORT",
                        "Sân bay Đà Nẵng -> Khách sạn khu vực biển Mỹ Khê",
                        "1 giờ",
                        50_000,
                        "Chi phí di chuyển địa phương và nhiên liệu xe máy trong ngày. Chi phí thuê xe máy khoảng 150.000 - 200.000 VND/ngày, không bao gồm trong chi phí này."),
                activity("12:00", "Thưởng thức Mì Quảng", "FOOD"),
                activity("14:00", "Khám phá Bảo tàng Đà Nẵng", "ATTRACTION")));
        return day;
    }

    private TripDto.ActivityResponse activity(String time, String name, String type) {
        TripDto.ActivityResponse activity = new TripDto.ActivityResponse();
        activity.setTime(time);
        activity.setName(name);
        activity.setType(type);
        activity.setLocation("Trung tâm");
        activity.setDuration("1 giờ");
        activity.setEstimatedCost(0);
        activity.setRating(4.5);
        return activity;
    }

    private TripDto.ActivityResponse activity(
            String time,
            String name,
            String type,
            String location,
            String duration,
            long estimatedCost,
            String note) {
        TripDto.ActivityResponse activity = new TripDto.ActivityResponse();
        activity.setTime(time);
        activity.setName(name);
        activity.setType(type);
        activity.setLocation(location);
        activity.setDuration(duration);
        activity.setEstimatedCost(estimatedCost);
        activity.setNote(note);
        activity.setRating(4.5);
        return activity;
    }

    private User sampleUser() {
        return User.builder()
                .id(7L)
                .name("Test User")
                .email("test@example.com")
                .build();
    }

    private Trip sampleTrip() {
        User user = sampleUser();
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
