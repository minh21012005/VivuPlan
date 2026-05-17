package com.vivuplan.vivuplan_be.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivuplan.vivuplan_be.dto.TripDto;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
