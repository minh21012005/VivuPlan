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
import java.util.concurrent.atomic.AtomicReference;

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
    void updateActivityClearsCostReviewWarningWhenUserProvidesCost() {
        Trip trip = sampleTrip();
        Activity activity = trip.getItineraryDays().get(0).getActivities().get(0);
        activity.setEstimatedCost(0L);
        activity.setNote("Chi phí cần kiểm tra: hoạt động này có thể phát sinh phí, nhưng AI chưa đưa ra mức ước tính đáng tin cậy.");
        TripService service = new TripService(tripRepository, userRepository, destinationRepository, aiService, weatherService);
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));
        when(tripRepository.saveAndFlush(any(Trip.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TripDto.UpdateActivityRequest req = new TripDto.UpdateActivityRequest();
        req.setTime("09:00");
        req.setName("Hoạt động buổi sáng");
        req.setType("ACTIVITY");
        req.setLocation("Trung tâm");
        req.setDuration("2 giờ");
        req.setEstimatedCost(150_000L);
        req.setNote(activity.getNote());

        TripDto.TripResponse response = service.updateActivity(1L, 7L, 100L, req);

        TripDto.ActivityResponse updatedActivity = response.getSchedule().get(0).getActivities().get(0);
        assertThat(updatedActivity.getCostEstimateStatus()).isNull();
        assertThat(updatedActivity.getNote()).isNull();
        assertThat(response.getWarnings()).noneMatch(warning -> warning.contains("Cần kiểm tra"));
    }

    @Test
    void getTripReturnsPersistedAiWarnings() {
        Trip trip = sampleTrip();
        trip.setAiWarnings("Yêu cầu chèo sup chưa được áp dụng vì trời mưa.\nTổng chi phí có thể vượt ngân sách.");
        TripService service = new TripService(tripRepository, userRepository, destinationRepository, aiService, weatherService);
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));

        TripDto.TripResponse response = service.getTrip(1L, 7L);

        assertThat(response.getWarnings())
                .containsExactly(
                        "Yêu cầu chèo sup chưa được áp dụng vì trời mưa.");
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
    void generateAndSaveDoesNotWarnWhenAiReportsNoMeaningfulRequest() {
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
                        List.of(proposedDayWithoutRequestedActivity()),
                        noRequestFulfillment()));

        TripDto.GenerateRequest req = generateRequest("", "Tạo lịch trình nhẹ nhàng, không cần thêm yêu cầu đặc biệt.");

        TripDto.TripResponse response = service.generateAndSave(7L, req);

        assertThat(response.getRequestFulfillment().getOverallStatus()).isEqualTo("NO_REQUEST");
        assertThat(response.getWarnings()).isEmpty();
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
        AtomicReference<Trip> savedTrip = new AtomicReference<>();
        when(tripRepository.saveAndFlush(any(Trip.class))).thenAnswer(invocation -> {
            Trip saved = invocation.getArgument(0);
            saved.setId(1L);
            savedTrip.set(saved);
            return saved;
        });
        when(aiService.generateItinerary(any(TripDto.GenerateRequest.class)))
                .thenReturn(new AiService.GeneratedItineraryResult(
                        List.of(proposedDayWithUnrealisticRequiredCosts()),
                        noRequestFulfillment()));

        TripDto.GenerateRequest req = generateRequest("", "");

        TripDto.TripResponse response = service.generateAndSave(7L, req);

        List<TripDto.ActivityResponse> activities = response.getSchedule().get(0).getActivities();
        assertThat(activities.get(0).getEstimatedCost()).isZero();
        assertThat(activities.get(0).getCostEstimateStatus()).isEqualTo("NEEDS_REVIEW");
        assertThat(activities.get(0).getCostEstimateMessage()).isNotBlank();
        assertThat(activities.get(1).getEstimatedCost()).isGreaterThanOrEqualTo(200_000L);
        assertThat(response.getBudget().getTransport()).isGreaterThanOrEqualTo(200_000L);
        assertThat(response.getWarnings()).anyMatch(warning -> warning.contains("Cần kiểm tra"));
        assertThat(savedTrip.get().getAiWarnings()).isNull();
    }

    @Test
    void generateAndSaveDoesNotOverridePlausibleShortRouteTransportCost() {
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
                        List.of(proposedShortRouteBusDay()),
                        noRequestFulfillment()));

        TripDto.GenerateRequest req = generateRequest("", "");
        req.setDestination("Ninh BÃ¬nh");
        req.setOutboundTransport("BUS");

        TripDto.TripResponse response = service.generateAndSave(7L, req);

        TripDto.ActivityResponse busActivity = response.getSchedule().get(0).getActivities().get(0);
        assertThat(busActivity.getEstimatedCost()).isEqualTo(120_000L);
        assertThat(busActivity.getCostEstimateStatus()).isNull();
    }

    @Test
    void generateAndSaveDoesNotFlagReturnFlightWhenRoundTripCostIsBundled() {
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
                        List.of(proposedRoundTripIncludedFlightDay()),
                        noRequestFulfillment()));

        TripDto.GenerateRequest req = generateRequest("", "");
        req.setBudgetPerPerson(5_000_000L);

        TripDto.TripResponse response = service.generateAndSave(7L, req);

        TripDto.ActivityResponse returnFlight = response.getSchedule().get(0).getActivities().get(3);
        assertThat(returnFlight.getEstimatedCost()).isZero();
        assertThat(returnFlight.getCostEstimateStatus()).isNull();
        assertThat(response.getWarnings()).noneMatch(warning -> warning.contains("Cần kiểm tra"));
        assertThat(response.getBudget().getTransport()).isEqualTo(3_600_000L);
    }

    @Test
    void generateAndSaveKeepsBudgetOverageInBudgetCardInsteadOfAiWarnings() {
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
                        List.of(proposedOverBudgetDay()),
                        noRequestFulfillment()));

        TripDto.GenerateRequest req = generateRequest("", "");
        req.setBudgetPerPerson(3_000_000L);

        TripDto.TripResponse response = service.generateAndSave(7L, req);

        assertThat(response.getBudget().getTotal()).isGreaterThan(3_000_000L);
        assertThat(response.getWarnings()).noneMatch(warning -> warning.contains("vượt ngân sách"));
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
        assertThat(activities.get(0).getEstimatedCost()).isZero();
        assertThat(activities.get(0).getCostEstimateStatus()).isEqualTo("NEEDS_REVIEW");
        assertThat(activities.get(0).getCostEstimateMessage()).isNotBlank();
        assertThat(activities.get(1).getEstimatedCost()).isGreaterThanOrEqualTo(200_000L);
        assertThat(response.getNewBudget()).isGreaterThanOrEqualTo(200_000L);
        assertThat(response.getWarnings()).anyMatch(warning -> warning.contains("Cần kiểm tra"));
    }

    @Test
    void previewRegenerateDayWarnsWhenOverBudgetAndStillAllowsApply() {
        Trip trip = sampleTrip();
        trip.setStartDate(LocalDate.now());
        trip.setEndDate(LocalDate.now());
        trip.setBudgetPerPerson(230_000L);

        TripService service = new TripService(tripRepository, userRepository, destinationRepository, aiService, weatherService);
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));
        when(destinationRepository.findByNameIgnoreCaseOrSlugIgnoreCase(anyString(), anyString()))
                .thenReturn(Optional.empty());
        mockRainForecast();
        when(aiService.regenerateDay(any(TripDto.GenerateRequest.class), any(), anyInt(), anyString(), anyString()))
                .thenReturn(new AiService.RegeneratedDayResult(
                        proposedOverBudgetDay(),
                        noRequestFulfillment()));
        when(tripRepository.saveAndFlush(any(Trip.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TripDto.RegenerateDayRequest req = new TripDto.RegenerateDayRequest();
        req.setInstruction("Regenerate with realistic costs");

        TripDto.RegenerateDayPreviewResponse preview = service.previewRegenerateDay(1L, 7L, 1, req);

        assertThat(preview.getWarnings()).anyMatch(warning -> warning.contains("ngân sách"));

        TripDto.ApplyRegenerateDayRequest applyRequest = new TripDto.ApplyRegenerateDayRequest();
        applyRequest.setProposalId(preview.getProposalId());
        TripDto.TripResponse updated = service.applyRegeneratedDay(1L, 7L, 1, applyRequest);

        assertThat(updated.getSchedule().get(0).getActivities()).hasSize(4);
        assertThat(updated.getBudget().getTotal()).isGreaterThan(trip.getBudgetPerPerson());
        assertThat(updated.getWarnings()).noneMatch(warning -> warning.contains("ngân sách"));
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

    private TripDto.DayResponse proposedShortRouteBusDay() {
        TripDto.DayResponse day = new TripDto.DayResponse();
        day.setDay(1);
        day.setTitle("Ngay 1 - Ninh Binh");
        day.setSummary("Lich trinh ngan ngay bang xe khach.");
        day.setActivities(List.of(
                activity(
                        "08:00",
                        "Di chuyen tu Ha Noi den Ninh Binh",
                        "TRANSPORT",
                        "Ben xe Giap Bat -> Trung tam Ninh Binh",
                        "2 gio",
                        120_000,
                        "Xe khach mot chieu, uoc tinh 120k/nguoi."),
                activity("10:30", "Tham quan Trang An", "ATTRACTION"),
                activity("12:30", "An trua com chay de nui", "FOOD"),
                activity("14:00", "Tham quan chua Bai Dinh", "ATTRACTION")));
        return day;
    }

    private TripDto.DayResponse proposedRoundTripIncludedFlightDay() {
        TripDto.DayResponse day = new TripDto.DayResponse();
        day.setDay(1);
        day.setTitle("Ngay 1 - Da Nang");
        day.setSummary("Lich trinh co ve may bay khu hoi tinh mot lan.");
        day.setActivities(List.of(
                activity(
                        "08:00",
                        "Ve may bay khu hoi Ha Noi - Da Nang",
                        "TRANSPORT",
                        "San bay Noi Bai (HAN) <-> San bay Da Nang (DAD)",
                        "2 gio",
                        3_600_000L,
                        "Chi phi ve may bay khu hoi cho ca nhom, bao gom ca chieu di va chieu ve."),
                activity("11:30", "An trua Mi Quang Ba Mua", "FOOD", "231 Tran Phu, Da Nang", "1 gio", 200_000L, null),
                activity("14:00", "Tham quan Bao tang Da Nang", "ATTRACTION", "24 Tran Phu, Da Nang", "2 gio", 80_000L, null),
                activity(
                        "20:00",
                        "Chuyen bay Da Nang - Ha Noi",
                        "TRANSPORT",
                        "San bay Da Nang (DAD) -> San bay Noi Bai (HAN)",
                        "2 gio",
                        0,
                        "Ve chieu ve khoang 1.800.000 VND da duoc tinh trong ve may bay khu hoi o chang di.")));
        return day;
    }

    private TripDto.DayResponse proposedOverBudgetDay() {
        TripDto.DayResponse day = new TripDto.DayResponse();
        day.setDay(1);
        day.setTitle("Ngay 1 - Chi phi cao");
        day.setSummary("Lich trinh co tong chi phi vuot ngan sach.");
        day.setActivities(List.of(
                activity("08:00", "Bay den Da Nang", "TRANSPORT", "San bay", "2 gio", 2_000_000L, "Uoc tinh ve may bay."),
                activity("11:00", "Nhan phong khach san", "ACCOMMODATION", "Khach san", "30 phut", 1_500_000L, "Uoc tinh luu tru."),
                activity("13:00", "An trua hai san", "FOOD", "Nha hang", "1 gio", 800_000L, null),
                activity("15:00", "Tour trong ngay", "ACTIVITY", "Da Nang", "2 gio", 900_000L, null)));
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
