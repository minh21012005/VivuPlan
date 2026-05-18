package com.vivuplan.vivuplan_be.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivuplan.vivuplan_be.dto.TripDto;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiServiceTest {

    @Test
    void itineraryQualityAllowsZeroReturnFlightWhenRoundTripCostIsBundled() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest req = generateRequest();

        QualityResult quality = assessItineraryQuality(service, List.of(roundTripBundledDay()), req);

        assertThat(quality.passed()).isTrue();
    }

    @Test
    void itineraryQualityStillFlagsZeroReturnFlightWithoutBundledRoundTripCost() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest req = generateRequest();

        QualityResult quality = assessItineraryQuality(service, List.of(separateReturnCostMissingDay()), req);

        assertThat(quality.passed()).isFalse();
        assertThat(quality.reason()).contains("intercity transport cost is missing");
    }

    @Test
    void generatedItineraryParserRejectsLegacyArrayResponse() {
        AiService service = new AiService(new ObjectMapper());

        assertThatThrownBy(() -> parseGeneratedItineraryResult(service, legacyArrayResponse()))
                .hasMessageContaining("expected one JSON object")
                .hasMessageContaining("requestFulfillment");
    }

    @Test
    void regeneratedDayParserRejectsLegacyArrayResponse() {
        AiService service = new AiService(new ObjectMapper());

        assertThatThrownBy(() -> parseRegeneratedDayResult(service, legacyArrayResponse(), 1))
                .hasMessageContaining("expected one JSON object")
                .hasMessageContaining("requestFulfillment");
    }

    @Test
    void generatedItineraryParserRequiresRequestFulfillment() {
        AiService service = new AiService(new ObjectMapper());

        assertThatThrownBy(() -> parseGeneratedItineraryResult(service, """
                {
                  "itinerary": [
                    {
                      "day": 1,
                      "title": "Day 1",
                      "summary": "Test",
                      "activities": []
                    }
                  ]
                }
                """))
                .hasMessageContaining("requestFulfillment");
    }

    @Test
    void qualityRetryPromptUsesSharedPacingAndSoftLocalTransportGuidance() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest req = generateRequest();

        String prompt = buildQualityRetryPrompt(service, req, "missing explicit local transport");

        assertThat(prompt)
                .contains("Never return more than 14 total items")
                .contains("For close walkable places, a clear walking note with cost 0 is enough")
                .doesNotContain("Add a clear local transportation plan with TRANSPORT activities for getting around");
    }

    @Test
    void regenerationPromptUsesSharedPacingAndSoftLocalTransportGuidance() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest req = generateRequest();

        String prompt = buildDayRegenerationPrompt(
                service,
                req,
                List.of(roundTripBundledDay()),
                1,
                "REGENERATE",
                "thêm hoạt động nhẹ nhàng",
                "missing explicit local transport");

        assertThat(prompt)
                .contains("Never return more than 14 total items")
                .contains("For close walkable places, a clear walking note with cost 0 is enough")
                .contains("The previous proposal was rejected because: missing explicit local transport");
    }

    @Test
    void itineraryQualityAllowsLightOneDayTripWithTwoActivities() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest req = generateRequest();
        req.setDays(1);

        TripDto.DayResponse day = baseDay();
        day.setActivities(List.of(
                activity("08:00", "Xe khách Hà Nội - Ninh Bình", "TRANSPORT",
                        "Hà Nội -> Ninh Bình", 300_000L, "Vé xe khách cho cả nhóm."),
                activity("11:00", "Ăn trưa dê núi tại Nhà hàng Đức Dê", "FOOD",
                        "Ninh Bình", 250_000L, null)));

        QualityResult quality = assessItineraryQuality(service, List.of(day), req);

        assertThat(quality.passed()).isTrue();
    }

    @Test
    void regeneratedDayQualityAllowsDenseButReasonableCityDay() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest req = generateRequest();

        TripDto.DayResponse day = baseDay();
        List<TripDto.ActivityResponse> activities = new java.util.ArrayList<>();
        for (int i = 0; i < 9; i++) {
            activities.add(activity(
                    String.format("%02d:00", 8 + i),
                    "Điểm trải nghiệm " + (i + 1),
                    i % 3 == 0 ? "FOOD" : "ATTRACTION",
                    "Khu trung tâm " + (i + 1),
                    100_000L,
                    null));
        }
        activities.add(activity("17:30", "Taxi ve khach san", "TRANSPORT",
                "Cho Con -> Khach san My Khe", 120_000L, "Taxi/Grab ve khach san."));
        activities.add(activity("18:30", "Nhan phong khach san", "ACCOMMODATION",
                "Khach san khu vuc My Khe", 0, "Chi phi luu tru da tinh trong muc khac."));
        day.setActivities(activities);

        QualityResult quality = assessRegeneratedDayQuality(service, day, List.of(), req);

        assertThat(quality.passed()).isTrue();
    }

    @Test
    void itineraryQualityRejectsDayWithTooManyNonLogisticsActivities() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest req = generateRequest();

        TripDto.DayResponse day = baseDay();
        List<TripDto.ActivityResponse> activities = new java.util.ArrayList<>();
        for (int i = 0; i < 13; i++) {
            activities.add(activity(
                    String.format("%02d:00", 7 + i),
                    "Lich trinh day dac " + (i + 1),
                    i % 3 == 0 ? "FOOD" : "ATTRACTION",
                    "Dia diem cu the " + (i + 1),
                    100_000L,
                    null));
        }
        day.setActivities(activities);

        QualityResult quality = assessItineraryQuality(service, List.of(day), req);

        assertThat(quality.passed()).isFalse();
        assertThat(quality.reason()).contains("too many non-logistics activities");
    }

    @Test
    void itineraryQualityAllowsThirteenItemsWhenLogisticsKeepPacingReasonable() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest req = generateRequest();

        TripDto.DayResponse day = baseDay();
        List<TripDto.ActivityResponse> activities = new java.util.ArrayList<>();
        for (int i = 0; i < 9; i++) {
            activities.add(activity(
                    String.format("%02d:00", 7 + i),
                    "Trai nghiem Da Nang " + (i + 1),
                    i % 3 == 0 ? "FOOD" : "ATTRACTION",
                    "Dia diem cu the Da Nang " + (i + 1),
                    100_000L,
                    null));
        }
        activities.add(activity("16:30", "Taxi My Khe ve Hai Chau", "TRANSPORT",
                "My Khe -> Hai Chau", 120_000L, "Chi phi taxi/Grab cho ca nhom."));
        activities.add(activity("17:30", "Nhan phong khach san My Khe", "ACCOMMODATION",
                "Khach san khu vuc My Khe", 0, "Chi phi luu tru da tinh trong muc khac."));
        activities.add(activity("18:30", "Di chuyen toi Cho dem Son Tra", "TRANSPORT",
                "My Khe -> Cho dem Son Tra", 80_000L, "Taxi/Grab toi cho dem."));
        activities.add(activity("21:30", "Taxi ve khach san", "TRANSPORT",
                "Cho dem Son Tra -> Khach san My Khe", 80_000L, "Taxi/Grab ve khach san."));
        day.setActivities(activities);

        QualityResult quality = assessItineraryQuality(service, List.of(day), req);

        assertThat(quality.passed()).isTrue();
    }

    @Test
    void itineraryQualityRejectsDayWithTooManyTotalItemsEvenWithLogistics() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest req = generateRequest();

        TripDto.DayResponse day = baseDay();
        List<TripDto.ActivityResponse> activities = new java.util.ArrayList<>();
        for (int i = 0; i < 9; i++) {
            activities.add(activity(
                    String.format("%02d:00", 6 + i),
                    "Trai nghiem Da Nang " + (i + 1),
                    i % 3 == 0 ? "FOOD" : "ATTRACTION",
                    "Dia diem cu the Da Nang " + (i + 1),
                    100_000L,
                    null));
        }
        for (int i = 0; i < 6; i++) {
            activities.add(activity(
                    String.format("%02d:30", 15 + i),
                    "Di chuyen chang ngan " + (i + 1),
                    "TRANSPORT",
                    "Tuyen noi do Da Nang " + (i + 1),
                    50_000L,
                    "Taxi/Grab noi do."));
        }
        day.setActivities(activities);

        QualityResult quality = assessItineraryQuality(service, List.of(day), req);

        assertThat(quality.passed()).isFalse();
        assertThat(quality.reason()).contains("too many activities");
    }

    private QualityResult assessItineraryQuality(
            AiService service,
            List<TripDto.DayResponse> days,
            TripDto.GenerateRequest req) throws Exception {
        Method method = AiService.class.getDeclaredMethod("assessItineraryQuality", List.class, TripDto.GenerateRequest.class);
        method.setAccessible(true);
        Object quality = method.invoke(service, days, req);

        Method passed = quality.getClass().getDeclaredMethod("passed");
        Method reason = quality.getClass().getDeclaredMethod("reason");
        passed.setAccessible(true);
        reason.setAccessible(true);
        return new QualityResult((Boolean) passed.invoke(quality), (String) reason.invoke(quality));
    }

    private QualityResult assessRegeneratedDayQuality(
            AiService service,
            TripDto.DayResponse day,
            List<TripDto.DayResponse> currentSchedule,
            TripDto.GenerateRequest req) throws Exception {
        Method method = AiService.class.getDeclaredMethod(
                "assessRegeneratedDayQuality",
                TripDto.DayResponse.class,
                List.class,
                TripDto.GenerateRequest.class);
        method.setAccessible(true);
        Object quality = method.invoke(service, day, currentSchedule, req);

        Method passed = quality.getClass().getDeclaredMethod("passed");
        Method reason = quality.getClass().getDeclaredMethod("reason");
        passed.setAccessible(true);
        reason.setAccessible(true);
        return new QualityResult((Boolean) passed.invoke(quality), (String) reason.invoke(quality));
    }

    private String buildQualityRetryPrompt(
            AiService service,
            TripDto.GenerateRequest req,
            String reason) throws Exception {
        Method method = AiService.class.getDeclaredMethod(
                "buildQualityRetryPrompt",
                TripDto.GenerateRequest.class,
                String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, req, reason);
    }

    private String buildDayRegenerationPrompt(
            AiService service,
            TripDto.GenerateRequest req,
            List<TripDto.DayResponse> currentSchedule,
            int dayNumber,
            String intent,
            String instruction,
            String retryReason) throws Exception {
        Method method = AiService.class.getDeclaredMethod(
                "buildDayRegenerationPrompt",
                TripDto.GenerateRequest.class,
                List.class,
                int.class,
                String.class,
                String.class,
                String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, req, currentSchedule, dayNumber, intent, instruction, retryReason);
    }

    private AiService.GeneratedItineraryResult parseGeneratedItineraryResult(
            AiService service,
            String json) throws Throwable {
        Method method = AiService.class.getDeclaredMethod("parseGeneratedItineraryResult", String.class);
        method.setAccessible(true);
        try {
            return (AiService.GeneratedItineraryResult) method.invoke(service, json);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private AiService.RegeneratedDayResult parseRegeneratedDayResult(
            AiService service,
            String json,
            int dayNumber) throws Throwable {
        Method method = AiService.class.getDeclaredMethod("parseRegeneratedDayResult", String.class, int.class);
        method.setAccessible(true);
        try {
            return (AiService.RegeneratedDayResult) method.invoke(service, json, dayNumber);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private String legacyArrayResponse() {
        return """
                [
                  {
                    "day": 1,
                    "title": "Day 1",
                    "summary": "Test",
                    "activities": []
                  }
                ]
                """;
    }

    private TripDto.GenerateRequest generateRequest() {
        TripDto.GenerateRequest req = new TripDto.GenerateRequest();
        req.setDestination("Đà Nẵng");
        req.setDeparture("Hà Nội");
        req.setStartDate(LocalDate.now());
        req.setEndDate(LocalDate.now());
        req.setDays(1);
        req.setBudgetPerPerson(5_000_000L);
        req.setBudgetMode("PER_PERSON");
        req.setTravelerCount(2);
        req.setStyle("RELAXING");
        req.setGroupType("COUPLE");
        req.setTransport("PLANE");
        req.setOutboundTransport("PLANE");
        req.setLocalTransport("MIXED");
        return req;
    }

    private TripDto.DayResponse roundTripBundledDay() {
        TripDto.DayResponse day = baseDay();
        day.setActivities(List.of(
                activity("08:00", "Vé máy bay khứ hồi Hà Nội - Đà Nẵng", "TRANSPORT",
                        "Sân bay Nội Bài (HAN) <-> Sân bay Đà Nẵng (DAD)", 3_600_000L,
                        "Chi phí vé máy bay khứ hồi cho cả nhóm, bao gồm cả chiều đi và chiều về."),
                activity("11:30", "Ăn trưa Mì Quảng Bà Mua", "FOOD",
                        "231 Trần Phú, Hải Châu, Đà Nẵng", 200_000L, null),
                activity("14:00", "Tham quan Bảo tàng Đà Nẵng", "ATTRACTION",
                        "24 Trần Phú, Hải Châu, Đà Nẵng", 80_000L, null),
                activity("20:00", "Chuyến bay Đà Nẵng (DAD) - Hà Nội (HAN)", "TRANSPORT",
                        "Sân bay Đà Nẵng (DAD) -> Sân bay Nội Bài (HAN)", 0,
                        "Chi phí đã được tính trong vé máy bay khứ hồi ở chặng đi.")));
        return day;
    }

    private TripDto.DayResponse separateReturnCostMissingDay() {
        TripDto.DayResponse day = baseDay();
        day.setActivities(List.of(
                activity("08:00", "Chuyến bay Hà Nội - Đà Nẵng", "TRANSPORT",
                        "Sân bay Nội Bài (HAN) -> Sân bay Đà Nẵng (DAD)", 1_800_000L,
                        "Vé máy bay một chiều cho cả nhóm."),
                activity("11:30", "Ăn trưa Mì Quảng Bà Mua", "FOOD",
                        "231 Trần Phú, Hải Châu, Đà Nẵng", 200_000L, null),
                activity("14:00", "Tham quan Bảo tàng Đà Nẵng", "ATTRACTION",
                        "24 Trần Phú, Hải Châu, Đà Nẵng", 80_000L, null),
                activity("20:00", "Chuyến bay Đà Nẵng (DAD) - Hà Nội (HAN)", "TRANSPORT",
                        "Sân bay Đà Nẵng (DAD) -> Sân bay Nội Bài (HAN)", 0,
                        "Vé máy bay về Hà Nội.")));
        return day;
    }

    private TripDto.DayResponse baseDay() {
        TripDto.DayResponse day = new TripDto.DayResponse();
        day.setDay(1);
        day.setTitle("Ngày 1 - Đà Nẵng");
        day.setSummary("Lịch trình kiểm thử chi phí máy bay.");
        return day;
    }

    private TripDto.ActivityResponse activity(
            String time,
            String name,
            String type,
            String location,
            long estimatedCost,
            String note) {
        TripDto.ActivityResponse activity = new TripDto.ActivityResponse();
        activity.setTime(time);
        activity.setName(name);
        activity.setType(type);
        activity.setLocation(location);
        activity.setDuration("1 giờ");
        activity.setEstimatedCost(estimatedCost);
        activity.setNote(note);
        activity.setRating(4.5);
        return activity;
    }

    private record QualityResult(boolean passed, String reason) {
    }
}
