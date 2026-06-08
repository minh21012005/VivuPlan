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
    void itineraryQualityFlagsBundledRoundTripDescribedAsSingleLegCost() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest req = generateRequest();

        QualityResult quality = assessItineraryQuality(service, List.of(inconsistentBundledRoundTripDay()), req);

        assertThat(quality.passed()).isFalse();
        assertThat(quality.reason()).contains("intercity transport cost is inconsistent");
    }

    @Test
    void itineraryQualityFlagsPaidReturnLegWhenRoundTripCostIsBundled() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest req = generateRequest();

        QualityResult quality = assessItineraryQuality(service, List.of(doubleCountedRoundTripDay()), req);

        assertThat(quality.passed()).isFalse();
        assertThat(quality.reason()).contains("intercity transport cost is double-counted");
    }

    @Test
    void itineraryQualityAllowsLocalAirportTransferWhenRoundTripFlightCostIsBundled() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest req = generateRequest();
        req.setDestination("Quy Nhon");

        TripDto.DayResponse day = baseDay();
        day.setActivities(List.of(
                activity("08:00", "Ve may bay khu hoi Ha Noi - Quy Nhon", "TRANSPORT",
                        "San bay Noi Bai (HAN) <-> San bay Phu Cat (UIH)", 3_800_000L,
                        "Chi phi khu hoi cho ca nhom, bao gom ca chieu di va chieu ve."),
                activity("11:30", "An trua bun ca Quy Nhon", "FOOD",
                        "Trung tam Quy Nhon", 200_000L, null),
                activity("14:00", "Tham quan Eo Gio", "ATTRACTION",
                        "Eo Gio, Quy Nhon", 120_000L, null),
                activity("18:30", "An toi hai san", "FOOD",
                        "Nha hang hai san Quy Nhon", 350_000L, null),
                activity("20:00", "Di chuyen ra san bay Phu Cat (UIH)", "TRANSPORT",
                        "Quy Nhon -> San bay Phu Cat", 250_000L,
                        "Taxi ra san bay de lam thu tuc chuyen bay ve Ha Noi.")));

        QualityResult quality = assessItineraryQuality(service, List.of(day), req);

        assertThat(quality.passed()).isTrue();
    }

    @Test
    void itineraryQualityStillFlagsPaidFlightLegHiddenBehindAirportTransferText() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest req = generateRequest();
        req.setDestination("Quy Nhon");

        TripDto.DayResponse day = baseDay();
        day.setActivities(List.of(
                activity("08:00", "Ve may bay khu hoi Ha Noi - Quy Nhon", "TRANSPORT",
                        "San bay Noi Bai (HAN) <-> San bay Phu Cat (UIH)", 3_800_000L,
                        "Chi phi khu hoi cho ca nhom, bao gom ca chieu di va chieu ve."),
                activity("11:30", "An trua bun ca Quy Nhon", "FOOD",
                        "Trung tam Quy Nhon", 200_000L, null),
                activity("14:00", "Tham quan Eo Gio", "ATTRACTION",
                        "Eo Gio, Quy Nhon", 120_000L, null),
                activity("18:30", "An toi hai san", "FOOD",
                        "Nha hang hai san Quy Nhon", 350_000L, null),
                activity("20:00", "Di chuyen ra san bay Phu Cat va bay ve Ha Noi", "TRANSPORT",
                        "Quy Nhon -> San bay Phu Cat -> Ha Noi", 1_800_000L,
                        "Chi phi cho chuyen bay ve.")));

        QualityResult quality = assessItineraryQuality(service, List.of(day), req);

        assertThat(quality.passed()).isFalse();
        assertThat(quality.reason()).contains("intercity transport cost is double-counted");
    }

    @Test
    void itineraryQualityDoesNotTreatAdvisoryRoundTripTextAsBundledCost() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest req = generateRequest();

        QualityResult quality = assessItineraryQuality(service, List.of(separateLegsWithRoundTripAdvisoryDay()), req);

        assertThat(quality.passed()).isTrue();
    }

    @Test
    void itineraryQualityAcceptsNightlifeActivityType() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest req = generateRequest();
        req.setDestination("Phu Quoc");

        TripDto.DayResponse day = baseDay();
        day.setActivities(List.of(
                activity("08:00", "Bay den Phu Quoc", "TRANSPORT",
                        "San bay Noi Bai -> San bay Phu Quoc", 2_400_000L,
                        "Ve may bay khu hoi duoc tinh trong chi phi di chuyen."),
                activity("10:30", "Tham quan Dinh Cau", "ATTRACTION",
                        "Dinh Cau, Phu Quoc", 0L,
                        "Diem ngam bien gan trung tam Duong Dong."),
                activity("12:00", "An trua bun quay Kien Xay", "FOOD",
                        "Bun quay Kien Xay, Phu Quoc", 180_000L,
                        "Thu mon dac san dia phuong."),
                activity("15:00", "Tam bien Bai Sao", "ATTRACTION",
                        "Bai Sao, Phu Quoc", 0L,
                        "Bien dep, nen di khi thoi tiet on."),
                activity("19:30", "Kham pha Grand World Phu Quoc", "NIGHTLIFE",
                        "Grand World Phu Quoc", 300_000L,
                        "Trai nghiem khong gian dem va show ngoai troi neu phu hop.")));

        QualityResult quality = assessItineraryQuality(service, List.of(day), req);

        assertThat(quality.passed()).isTrue();
    }

    @Test
    void regeneratedDayQualityReplacesOldDayBeforeCheckingIntercityCosts() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest req = generateRequest();

        QualityResult quality = assessRegeneratedDayQuality(
                service,
                paidSeparateRoundTripDay(),
                List.of(roundTripBundledDay()),
                req);

        assertThat(quality.passed()).isTrue();
    }

    @Test
    void regeneratedDayQualityDoesNotTreatPositiveNotesAsAvoidTerms() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest req = generateRequest();
        req.setDestination("Sa Pa");
        req.setNotes("Thich chup anh va an dac san");

        TripDto.DayResponse day = baseDay();
        day.setActivities(List.of(
                activity("08:00", "An sang tai Pho Cuong Sa Pa", "FOOD",
                        "Pho Cuong, Sa Pa", 100_000L,
                        "Bua sang am bung."),
                activity("09:30", "Tham quan Nui Ham Rong", "ATTRACTION",
                        "Nui Ham Rong, Sa Pa", 140_000L,
                        "Diem chup anh dep gan trung tam."),
                activity("18:30", "An toi dac san tai Cho Sa Pa", "FOOD",
                        "Cho Sa Pa, thi xa Sa Pa", 250_000L,
                        "Thuong thuc dac san dia phuong tai khu cho dem.")));

        QualityResult quality = assessRegeneratedDayQuality(service, day, List.of(), req);

        assertThat(quality.passed()).isTrue();
    }

    @Test
    void regeneratedDayQualityStillHonorsNegativeNotesAsAvoidTerms() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest req = generateRequest();
        req.setNotes("Khong thich hai san");

        TripDto.DayResponse day = baseDay();
        day.setActivities(List.of(
                activity("08:00", "An sang tai Mi Quang Ba Mua", "FOOD",
                        "231 Tran Phu, Da Nang", 100_000L, null),
                activity("09:30", "Tham quan Bao tang Da Nang", "ATTRACTION",
                        "24 Tran Phu, Hai Chau, Da Nang", 80_000L, null),
                activity("18:30", "An toi tai Hai san Be Man", "FOOD",
                        "Lo 14 Hoang Sa, Da Nang", 400_000L,
                        "Thuong thuc hai san tuoi.")));

        QualityResult quality = assessRegeneratedDayQuality(service, day, List.of(), req);

        assertThat(quality.passed()).isFalse();
        assertThat(quality.reason()).contains("avoid instruction");
    }

    @Test
    void itineraryQualityHonorsAvoidTermsOnInitialGeneration() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest req = generateRequest();
        req.setAvoid("Hai san");

        TripDto.DayResponse day = baseDay();
        day.setActivities(List.of(
                activity("08:00", "An sang tai Mi Quang Ba Mua", "FOOD",
                        "231 Tran Phu, Da Nang", 100_000L, null),
                activity("09:30", "Tham quan Bao tang Da Nang", "ATTRACTION",
                        "24 Tran Phu, Hai Chau, Da Nang", 80_000L, null),
                activity("18:30", "An toi tai Hai san Be Man", "FOOD",
                        "Lo 14 Hoang Sa, Da Nang", 400_000L,
                        "Thuong thuc hai san tuoi.")));

        QualityResult quality = assessItineraryQuality(service, List.of(day), req);

        assertThat(quality.passed()).isFalse();
        assertThat(quality.reason()).contains("avoid instruction");
    }

    @Test
    void itineraryQualityDoesNotTreatNegatedAvoidMentionAsViolation() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest req = generateRequest();
        req.setAvoid("Hai san");

        TripDto.DayResponse day = baseDay();
        day.setActivities(List.of(
                activity("08:00", "An sang tai Mi Quang Ba Mua", "FOOD",
                        "231 Tran Phu, Da Nang", 100_000L, null),
                activity("09:30", "Tham quan Bao tang Da Nang", "ATTRACTION",
                        "24 Tran Phu, Hai Chau, Da Nang", 80_000L, null),
                activity("18:30", "An toi tai Nha hang La Do", "FOOD",
                        "Nha hang La Do, Da Nang", 250_000L,
                        "Goi y mon ga nuong, khong co hai san.")));

        QualityResult quality = assessItineraryQuality(service, List.of(day), req);

        assertThat(quality.passed()).isTrue();
    }

    @Test
    void regeneratedDayQualityDoesNotTreatNonVegetarianMealAsVegetarianAvoidViolation() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest req = generateRequest();
        req.setDestination("Yen Tu");
        req.setAvoid("Khong muon an chay");

        TripDto.DayResponse day = baseDay();
        day.setActivities(List.of(
                activity("08:00", "Di chuyen len khu danh thang Yen Tu", "TRANSPORT",
                        "Ben xe Uong Bi", 200_000L, "Di chuyen som de kip lich tham quan."),
                activity("11:30", "An trua voi mon an dia phuong", "FOOD",
                        "Nha hang Tung Lam Yen Tu", 250_000L,
                        "Chon mon dia phuong man, khong phai mon chay."),
                activity("14:00", "Tham quan Thien vien Truc Lam Yen Tu", "ATTRACTION",
                        "Yen Tu, Uong Bi", 80_000L, "Giu lich nhe nhaang sau bua trua.")));

        QualityResult quality = assessRegeneratedDayQuality(service, day, List.of(), req);

        assertThat(quality.passed()).isTrue();
    }

    @Test
    void regeneratedDayQualityHandlesVietnameseVegetarianNegationWithAccents() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest req = generateRequest();
        req.setDestination("Yen Tu");
        req.setAvoid("Kh\u00f4ng mu\u1ed1n \u0103n chay");

        TripDto.DayResponse day = baseDay();
        day.setActivities(List.of(
                activity("08:00", "Di chuyen len khu danh thang Yen Tu", "TRANSPORT",
                        "Ben xe Uong Bi", 200_000L, "Di chuyen som de kip lich tham quan."),
                activity("11:30", "An trua voi mon an dia phuong", "FOOD",
                        "Nha hang Tung Lam Yen Tu", 250_000L,
                        "Chon mon dia phuong man, kh\u00f4ng ph\u1ea3i m\u00f3n chay."),
                activity("14:00", "Tham quan Thien vien Truc Lam Yen Tu", "ATTRACTION",
                        "Yen Tu, Uong Bi", 80_000L, "Giu lich nhe nhaang sau bua trua.")));

        QualityResult quality = assessRegeneratedDayQuality(service, day, List.of(), req);

        assertThat(quality.passed()).isTrue();
    }

    @Test
    void regeneratedDayQualityStillFlagsVegetarianMealWhenUserAvoidsVegetarianFood() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest req = generateRequest();
        req.setDestination("Yen Tu");
        req.setAvoid("Khong muon an chay");

        TripDto.DayResponse day = baseDay();
        day.setActivities(List.of(
                activity("08:00", "Di chuyen len khu danh thang Yen Tu", "TRANSPORT",
                        "Ben xe Uong Bi", 200_000L, "Di chuyen som de kip lich tham quan."),
                activity("11:30", "An trua com chay de nui", "FOOD",
                        "Nha hang gan chua Hoa Yen", 180_000L,
                        "Bua trua chay thanh dam trong khu Yen Tu."),
                activity("14:00", "Tham quan Thien vien Truc Lam Yen Tu", "ATTRACTION",
                        "Yen Tu, Uong Bi", 80_000L, "Giu lich nhe nhaang sau bua trua.")));

        QualityResult quality = assessRegeneratedDayQuality(service, day, List.of(), req);

        assertThat(quality.passed()).isFalse();
        assertThat(quality.reason()).contains("avoid instruction");
    }

    @Test
    void itineraryQualityAvoidUsesWordBoundariesAndSkipsAmbiguousCrabTerm() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest req = generateRequest();
        req.setDestination("Ha Long");
        req.setAvoid("Cua");

        TripDto.DayResponse day = baseDay();
        day.setActivities(List.of(
                activity("08:00", "An sang tai Banh cuon Goc Bang", "FOOD",
                        "Goc Bang, Ha Long", 100_000L, null),
                activity("09:30", "Tham quan Lang chai Cua Van", "ATTRACTION",
                        "Lang chai Cua Van, Vinh Ha Long", 300_000L, null),
                activity("18:30", "An toi tai Nha hang Co Ngu", "FOOD",
                        "Nha hang Co Ngu, Ha Long", 300_000L,
                        "Chon cac mon ga hoac rau.")));

        QualityResult quality = assessItineraryQuality(service, List.of(day), req);

        assertThat(quality.passed()).isTrue();
    }

    @Test
    void itineraryQualityStillHonorsSpecificCrabAvoidPhrase() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest req = generateRequest();
        req.setAvoid("Tom cua");

        TripDto.DayResponse day = baseDay();
        day.setActivities(List.of(
                activity("08:00", "An sang tai Mi Quang Ba Mua", "FOOD",
                        "231 Tran Phu, Da Nang", 100_000L, null),
                activity("09:30", "Tham quan Bao tang Da Nang", "ATTRACTION",
                        "24 Tran Phu, Hai Chau, Da Nang", 80_000L, null),
                activity("18:30", "An toi lau tom cua", "FOOD",
                        "Nha hang La Do, Da Nang", 300_000L,
                        "Lau tom cua cho ca nhom.")));

        QualityResult quality = assessItineraryQuality(service, List.of(day), req);

        assertThat(quality.passed()).isFalse();
        assertThat(quality.reason()).contains("avoid instruction");
    }

    @Test
    void itineraryQualityDoesNotTreatPositiveNotesAsAvoidTerms() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest req = generateRequest();
        req.setDestination("Sa Pa");
        req.setNotes("Thich chup anh va an dac san");

        TripDto.DayResponse day = baseDay();
        day.setActivities(List.of(
                activity("08:00", "An sang tai Pho Cuong Sa Pa", "FOOD",
                        "Pho Cuong, Sa Pa", 100_000L,
                        "Bua sang am bung."),
                activity("09:30", "Tham quan Nui Ham Rong", "ATTRACTION",
                        "Nui Ham Rong, Sa Pa", 140_000L,
                        "Diem chup anh dep gan trung tam."),
                activity("18:30", "An toi dac san tai Cho Sa Pa", "FOOD",
                        "Cho Sa Pa, thi xa Sa Pa", 250_000L,
                        "Thuong thuc dac san dia phuong tai khu cho dem.")));

        QualityResult quality = assessItineraryQuality(service, List.of(day), req);

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
    void itineraryQualityAllowsZeroCostVehicleReturnWhenRentalWasAlreadyCounted() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest req = generateRequest();

        TripDto.DayResponse day = baseDay();
        day.setActivities(List.of(
                activity("08:00", "Nhan xe may thue tai Tam Coc", "TRANSPORT",
                        "Tam Coc, Ninh Binh", 300_000L,
                        "Chi phi thue xe may 2 ngay cho ca nhom."),
                activity("09:30", "Tham quan Hang Mua", "ATTRACTION",
                        "Khe Dau Ha, Ninh Binh", 200_000L, null),
                activity("12:00", "An trua tai Nha hang Duc De", "FOOD",
                        "Ninh Binh", 250_000L, null),
                activity("17:00", "Tra phong va tra xe may thue", "TRANSPORT",
                        "Tam Coc, Ninh Binh", 0,
                        "Chi phi thue xe da duoc tinh o luc nhan xe.")));

        QualityResult quality = assessItineraryQuality(service, List.of(day), req);

        assertThat(quality.passed()).isTrue();
    }

    @Test
    void itineraryQualityAllowsZeroCostVehiclePickupWhenRentalWasAlreadyCountedElsewhere() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest req = generateRequest();

        TripDto.DayResponse day = baseDay();
        day.setActivities(List.of(
                activity("08:00", "Thue xe may 3 ngay tai Tam Coc", "TRANSPORT",
                        "Tam Coc, Ninh Binh", 450_000L,
                        "Tong phi thue xe may 3 ngay cho ca nhom."),
                activity("08:30", "Nhan xe may thue va di chuyen ve homestay", "TRANSPORT",
                        "Tam Coc, Ninh Binh", 0,
                        "Phi thue xe may da duoc tinh o hoat dong thue xe luc 08:00."),
                activity("09:30", "Tham quan Hang Mua", "ATTRACTION",
                        "Khe Dau Ha, Ninh Binh", 200_000L, null),
                activity("12:00", "An trua tai Nha hang Duc De", "FOOD",
                        "Ninh Binh", 250_000L, null)));

        QualityResult quality = assessItineraryQuality(service, List.of(day), req);

        assertThat(quality.passed()).isTrue();
    }

    @Test
    void itineraryQualityStillFlagsZeroCostVehiclePickup() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest req = generateRequest();

        TripDto.DayResponse day = baseDay();
        day.setActivities(List.of(
                activity("08:00", "Nhan xe may thue tai Tam Coc", "TRANSPORT",
                        "Tam Coc, Ninh Binh", 0,
                        "Nhan xe may de di chuyen trong ngay."),
                activity("09:30", "Tham quan Hang Mua", "ATTRACTION",
                        "Khe Dau Ha, Ninh Binh", 200_000L, null),
                activity("12:00", "An trua tai Nha hang Duc De", "FOOD",
                        "Ninh Binh", 250_000L, null)));

        QualityResult quality = assessItineraryQuality(service, List.of(day), req);

        assertThat(quality.passed()).isFalse();
        assertThat(quality.reason()).contains("vehicle rental cost is missing");
    }

    @Test
    void itineraryQualityDoesNotTreatPersonalVehiclePickupAsRentalCostIssue() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest req = generateRequest();

        TripDto.DayResponse day = baseDay();
        day.setActivities(List.of(
                activity("08:00", "Nhan xe may ca nhan tai khach san", "TRANSPORT",
                        "Khach san trung tam Da Nang", 0,
                        "Dung xe may ca nhan cua ban de di chuyen trong ngay."),
                activity("09:30", "Tham quan Bao tang Da Nang", "ATTRACTION",
                        "24 Tran Phu, Hai Chau, Da Nang", 80_000L, null),
                activity("12:00", "An trua tai Cho Con", "FOOD",
                        "Cho Con, Da Nang", 200_000L, null)));

        QualityResult quality = assessItineraryQuality(service, List.of(day), req);

        assertThat(quality.passed()).isTrue();
    }

    @Test
    void itineraryQualityAllowsSomeGenericFoodPlaceholders() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest req = generateRequest();

        TripDto.DayResponse day = baseDay();
        day.setActivities(List.of(
                activity("08:00", "An sang Pho hoac Bun tai Da Nang", "FOOD",
                        "Quan Pho Cuong hoac quan an dia phuong", 100_000L,
                        "Bua sang am bung."),
                activity("09:30", "Tham quan Bao tang Da Nang", "ATTRACTION",
                        "24 Tran Phu, Hai Chau, Da Nang", 80_000L, null),
                activity("12:00", "An trua tai nha hang dia phuong", "FOOD",
                        "Khu vuc trung tam Da Nang", 200_000L,
                        "Thuong thuc cac mon an dac trung.")));

        QualityResult quality = assessItineraryQuality(service, List.of(day), req);

        assertThat(quality.passed()).isTrue();
    }

    @Test
    void itineraryQualityAllowsFoodAtSpecificMarketEvenWhenNoteMentionsLocalSpecialties() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest req = generateRequest();
        req.setDestination("Sa Pa");

        TripDto.DayResponse day = baseDay();
        day.setActivities(List.of(
                activity("08:00", "An sang Pho Cuong Sa Pa", "FOOD",
                        "Pho Cuong, trung tam Sa Pa", 100_000L,
                        "Bua sang am bung."),
                activity("09:30", "Tham quan Nui Ham Rong", "ATTRACTION",
                        "Nui Ham Rong, Sa Pa", 140_000L, null),
                activity("18:30", "An toi dac san tai Cho Sa Pa", "FOOD",
                        "Cho Sa Pa, thi xa Sa Pa", 250_000L,
                        "Thuong thuc dac san dia phuong tai khu cho dem.")));

        QualityResult quality = assessItineraryQuality(service, List.of(day), req);

        assertThat(quality.passed()).isTrue();
    }

    @Test
    void itineraryQualityAllowsSpecificRestaurantWithBroadLocation() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest req = generateRequest();
        req.setDestination("Sa Pa");

        TripDto.DayResponse day = baseDay();
        day.setActivities(List.of(
                activity("08:00", "An sang tai Pho Cuong Sa Pa", "FOOD",
                        "Khu vuc trung tam Sa Pa", 100_000L,
                        "Bua sang am bung."),
                activity("09:30", "Tham quan Nui Ham Rong", "ATTRACTION",
                        "Nui Ham Rong, Sa Pa", 140_000L, null),
                activity("18:30", "An toi tai Nha hang La Do", "FOOD",
                        "Khu vuc trung tam Sa Pa", 250_000L,
                        "Thuong thuc dac san vung cao.")));

        QualityResult quality = assessItineraryQuality(service, List.of(day), req);

        assertThat(quality.passed()).isTrue();
    }

    @Test
    void itineraryQualityAllowsGenericFoodTitleWhenNoteNamesSpecificRestaurant() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest req = generateRequest();
        req.setDestination("Sa Pa");

        TripDto.DayResponse day = baseDay();
        day.setActivities(List.of(
                activity("08:00", "An sang tai nha hang dia phuong", "FOOD",
                        "Khu vuc trung tam Sa Pa", 100_000L,
                        "Goi y Nha hang A Phu Sa Pa neu muon an mon vung cao."),
                activity("09:30", "Tham quan Nui Ham Rong", "ATTRACTION",
                        "Nui Ham Rong, Sa Pa", 140_000L, null),
                activity("18:30", "An toi tai Nha hang La Do", "FOOD",
                        "Khu vuc trung tam Sa Pa", 250_000L,
                        "Thuong thuc dac san vung cao.")));

        QualityResult quality = assessItineraryQuality(service, List.of(day), req);

        assertThat(quality.passed()).isTrue();
    }

    @Test
    void itineraryQualityAllowsGenericFoodTitleWhenLocationIsSpecificMarket() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest req = generateRequest();
        req.setDestination("Sa Pa");

        TripDto.DayResponse day = baseDay();
        day.setActivities(List.of(
                activity("08:00", "An sang tai Pho Cuong Sa Pa", "FOOD",
                        "Pho Cuong, trung tam Sa Pa", 100_000L,
                        "Bua sang am bung."),
                activity("09:30", "Tham quan Nui Ham Rong", "ATTRACTION",
                        "Nui Ham Rong, Sa Pa", 140_000L, null),
                activity("18:30", "An toi dac san dia phuong", "FOOD",
                        "Cho Sa Pa, trung tam Sa Pa", 250_000L,
                        "Thuong thuc cac mon dac san vung cao tai khu cho dem.")));

        QualityResult quality = assessItineraryQuality(service, List.of(day), req);

        assertThat(quality.passed()).isTrue();
    }

    @Test
    void itineraryQualityAllowsGenericAccommodationTitleWhenNoteNamesSpecificHotel() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest req = generateRequest();
        req.setDestination("Sa Pa");

        TripDto.DayResponse day = baseDay();
        day.setActivities(List.of(
                activity("08:00", "An sang tai Pho Cuong Sa Pa", "FOOD",
                        "Pho Cuong, trung tam Sa Pa", 100_000L,
                        "Bua sang am bung."),
                activity("09:30", "Tham quan Nui Ham Rong", "ATTRACTION",
                        "Nui Ham Rong, Sa Pa", 140_000L, null),
                activity("14:00", "Nhan phong tai khach san/homestay", "ACCOMMODATION",
                        "Khu vuc trung tam Sa Pa", 1_200_000L,
                        "Luu tru tai Sa Pa Centre Hotel, chi phi uoc tinh 2 dem cho ca nhom.")));

        QualityResult quality = assessItineraryQuality(service, List.of(day), req);

        assertThat(quality.passed()).isTrue();
    }

    @Test
    void itineraryQualityFlagsGenericAccommodationWithoutSpecificReference() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest req = generateRequest();
        req.setDestination("Sa Pa");

        TripDto.DayResponse day = baseDay();
        day.setActivities(List.of(
                activity("08:00", "An sang tai Pho Cuong Sa Pa", "FOOD",
                        "Pho Cuong, trung tam Sa Pa", 100_000L,
                        "Bua sang am bung."),
                activity("09:30", "Tham quan Nui Ham Rong", "ATTRACTION",
                        "Nui Ham Rong, Sa Pa", 140_000L, null),
                activity("14:00", "Nhan phong tai khach san/homestay", "ACCOMMODATION",
                        "Khu vuc trung tam Sa Pa", 1_200_000L,
                        "Chi phi luu tru 2 dem cho ca nhom.")));

        QualityResult quality = assessItineraryQuality(service, List.of(day), req);

        assertThat(quality.passed()).isFalse();
        assertThat(quality.reason()).contains("accommodation is generic");
    }

    @Test
    void itineraryQualityDoesNotCountGenericActivityTitleWhenLocationIsSpecific() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest req = generateRequest();
        req.setDestination("Sa Pa");

        TripDto.DayResponse day = baseDay();
        day.setActivities(List.of(
                activity("08:00", "Tham quan diem noi bat", "ATTRACTION",
                        "Nui Ham Rong, Sa Pa", 140_000L, null),
                activity("10:00", "Kham pha van hoa dia phuong", "ACTIVITY",
                        "Ban Cat Cat, Sa Pa", 150_000L, null),
                activity("13:30", "Check in view dep", "ATTRACTION",
                        "Deo O Quy Ho, Sa Pa", 0L, null),
                activity("16:00", "Trai nghiem cho dia phuong", "ACTIVITY",
                        "Cho tinh Sa Pa, trung tam Sa Pa", 0L, null),
                activity("18:30", "An toi tai Nha hang La Do", "FOOD",
                        "Nha hang La Do, Sa Pa", 250_000L,
                        "Thuong thuc dac san vung cao.")));

        QualityResult quality = assessItineraryQuality(service, List.of(day), req);

        assertThat(quality.passed()).isTrue();
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
                .contains("Never return more than " + ItineraryQualityPolicy.MAX_TOTAL_ITEMS_PER_DAY + " total items")
                .contains("For close walkable places, a clear walking note with cost 0 is enough")
                .contains("If a rented vehicle is used across multiple activities or days")
                .contains("Do not create a 0-cost pickup/receive-rental activity unless another TRANSPORT activity clearly includes that rental fee")
                .contains("signature/must-try experiences using your own Vietnam travel knowledge")
                .contains("Final self-check before returning JSON")
                .contains("The itinerary array has exactly " + req.getDays() + " days")
                .contains("Every estimatedCost is a non-negative group-level VND amount")
                .contains("Apply this checklist silently")
                .contains("Return exactly one JSON object matching the required schema")
                .contains("with no markdown, comments, checklist, or surrounding text")
                .doesNotContain("The plan respects Must visit, Avoid, weather safety")
                .doesNotContain("requestFulfillment honestly explains")
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
                .contains("Never return more than " + ItineraryQualityPolicy.MAX_TOTAL_ITEMS_PER_DAY + " total items")
                .contains("For close walkable places, a clear walking note with cost 0 is enough")
                .contains("If a rented vehicle is used across multiple activities or days")
                .contains("Do not create a 0-cost pickup/receive-rental activity unless another TRANSPORT activity clearly includes that rental fee")
                .contains("Preserve or restore relevant destination-signature/must-try experiences")
                .contains("Final self-check before returning JSON")
                .contains("The \"day\" object has day value 1 and does not change other days")
                .contains("Every estimatedCost is a non-negative group-level VND amount")
                .doesNotContain("The day respects the user request, Must visit, Avoid, weather safety")
                .doesNotContain("requestFulfillment honestly explains")
                .contains("The previous proposal was rejected because: missing explicit local transport");
    }

    @Test
    void generatedPromptTreatsVerifiedPlacesAsTrustedSuggestionsNotAllowedOnlyList() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest req = generateRequest();
        req.setVerifiedPlacesContext("- Bảo tàng Đà Nẵng | type=ATTRACTION | address=24 Trần Phú");

        String prompt = buildPrompt(service, req);

        assertThat(prompt)
                .contains("Treat Style as the user's primary planning bias, not a hard restriction")
                .contains("trusted suggestions, not an allowed-only list")
                .contains("Candidates are ordered by backend relevance")
                .contains("do not blindly pick the top items")
                .contains("infer the destination's signature experiences and must-try categories")
                .contains("even when they are NOT listed in the verified candidates")
                .contains("return PARTIAL or NOT_FULFILLED with a concise grouped requestFulfillment item even without a user-specific request")
                .contains("add at most 1-3 items, grouped by core experience category")
                .contains("mention 1-3 representative missed places/activities when helpful")
                .contains("Tràng An, Tam Cốc, Hang Múa")
                .contains("do not list every famous place that cannot fit the itinerary")
                .doesNotContain("You may use other real places only when the verified candidates do not cover");
    }

    @Test
    void generatedPromptAddsBalancedFallbackGuidanceWhenVerifiedPlacesAreUnavailable() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest req = generateRequest();
        req.setDestination("Vuon quoc gia Pu Mat");
        req.setVerifiedPlacesContext("none");

        String prompt = buildPrompt(service, req);

        assertThat(prompt)
                .contains("Verified place candidates are unavailable")
                .contains("still create a confident, destination-specific itinerary")
                .contains("include real signature experiences for the destination")
                .contains("Avoid generic filler")
                .contains("Do not invent obscure business names")
                .contains("use specific real named options only when you are confident they exist")
                .contains("use a concrete neighborhood, market, food street, public venue, pickup area, or lodging area")
                .doesNotContain("concrete proper name and address")
                .doesNotContain("still create a confident, practical itinerary");
    }

    @Test
    void generatedPromptDistinguishesPersonalMotorbikeFromRentalMotorbike() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest req = generateRequest();
        req.setOutboundTransport("PERSONAL_MOTORBIKE");
        req.setLocalTransport("PERSONAL_MOTORBIKE");

        String prompt = buildPrompt(service, req);

        assertThat(prompt)
                .contains("Outbound transport: PERSONAL_MOTORBIKE")
                .contains("Local transport: PERSONAL_MOTORBIKE")
                .contains("the traveler will use their own motorbike at the destination")
                .contains("Do not create motorbike rental, pickup, return, or rental-fee activities")
                .contains("fuel, parking, ferry, toll");
    }

    @Test
    void generatedPromptDistinguishesTaxiGrabAndRentalCarLocalTransport() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest taxiReq = generateRequest();
        taxiReq.setLocalTransport("TAXI_GRAB");

        String taxiPrompt = buildPrompt(service, taxiReq);

        assertThat(taxiPrompt)
                .contains("Local transport: TAXI_GRAB")
                .contains("Local transport choice: taxi/Grab")
                .contains("Do not create vehicle rental, pickup, return, or rental-fee activities")
                .contains("per-route taxi/Grab TRANSPORT activities");

        TripDto.GenerateRequest rentalCarReq = generateRequest();
        rentalCarReq.setLocalTransport("RENTAL_CAR");

        String rentalCarPrompt = buildPrompt(service, rentalCarReq);

        assertThat(rentalCarPrompt)
                .contains("Local transport: RENTAL_CAR")
                .contains("Local transport choice: rented car")
                .contains("create one clear car rental TRANSPORT activity")
                .contains("fuel/parking/toll");
    }

    @Test
    void generatedPromptHandlesAiSelectedAndWalkingFirstTransportClearly() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest mixedReq = generateRequest();
        mixedReq.setOutboundTransport("MIXED");
        mixedReq.setLocalTransport("MIXED");

        String mixedPrompt = buildPrompt(service, mixedReq);

        assertThat(mixedPrompt)
                .contains("Outbound transport choice: choose the simplest practical way")
                .contains("based on departure, distance, trip length, budget, group type, weather, and safety")
                .contains("Local transport choice: choose a practical mix inside the destination")
                .contains("Do not treat MIXED as a request to use every mode")
                .contains("prefer the fewest realistic modes");

        TripDto.GenerateRequest walkingReq = generateRequest();
        walkingReq.setLocalTransport("WALKING");

        String walkingPrompt = buildPrompt(service, walkingReq);

        assertThat(walkingPrompt)
                .contains("Local transport: WALKING")
                .contains("Local transport choice: walking-first")
                .contains("Do not force far-apart places into a walking route")
                .contains("add taxi/Grab, shuttle, public transport, or another safe paid transfer")
                .contains("when distance, weather, terrain, children, seniors, luggage, or safety makes walking unrealistic");
    }

    @Test
    void generatedPromptExplainsOutboundTransportAndPersonalVehicleLocalFallback() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest planeReq = generateRequest();
        planeReq.setOutboundTransport("PLANE");
        planeReq.setLocalTransport("TAXI_GRAB");

        String planePrompt = buildPrompt(service, planeReq);

        assertThat(planePrompt)
                .contains("Outbound transport choice: plane")
                .contains("round-trip flight cost")
                .contains("airport transfers")
                .contains("do not replace it with train, bus, or private car");

        TripDto.GenerateRequest personalCarReq = generateRequest();
        personalCarReq.setOutboundTransport("PERSONAL_CAR");
        personalCarReq.setLocalTransport("MIXED");

        String personalCarPrompt = buildPrompt(service, personalCarReq);

        assertThat(personalCarPrompt)
                .contains("Outbound transport ownership: the traveler reaches the destination with their own car")
                .contains("Do not create intercity bus/train/flight tickets or car rental for the outbound leg")
                .contains("the traveler already has a personal car")
                .contains("Prefer continuing to use it locally when parking, road access, and route make sense");
    }

    @Test
    void destinationSuggestionPromptIncludesTransportChoiceGuidance() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.DestinationSuggestionRequest req = destinationSuggestionRequest();
        req.setOutboundTransport("MIXED");
        req.setLocalTransport("WALKING");

        String prompt = buildDestinationSuggestionPrompt(service, req, "[]");

        assertThat(prompt)
                .contains("Outbound transport: MIXED")
                .contains("Local transport: WALKING")
                .contains("Outbound transport choice: choose the simplest practical way")
                .contains("Local transport choice: walking-first")
                .contains("Final self-check before returning JSON")
                .contains("The suggestions array contains exactly 3 items with all required fields")
                .contains("Every enum-like fit field uses only a value allowed by the schema")
                .doesNotContain("No suggestion conflicts with Avoid or ignores departure")
                .contains("Return exactly one JSON object matching the required schema")
                .containsSubsequence("Constraints:", "Final self-check before returning JSON");
    }

    @Test
    void destinationSuggestionParserRequiresFitNotes() throws Throwable {
        AiService service = new AiService(new ObjectMapper());

        List<TripDto.DestinationSuggestion> suggestions = parseDestinationSuggestions(service, """
                {
                  "suggestions": [
                    {
                      "name": "Tam Đảo",
                      "region": "Miền Bắc",
                      "reason": "Gần Hà Nội, hợp chuyến ngắn và nhịp đi nhẹ.",
                      "overallFit": "Phù hợp nhất",
                      "overallNote": "Cân bằng tốt giữa đường đi gần, chi phí và nhu cầu nghỉ nhẹ.",
                      "budgetFit": "Phù hợp",
                      "budgetNote": "Ngân sách đủ cho ăn uống và điểm tham quan chính.",
                      "durationFit": "Phù hợp",
                      "durationNote": "Một ngày vẫn đủ ghé các điểm nổi bật.",
                      "travelFit": "Phù hợp",
                      "travelNote": "Đường đi tương đối gần, hợp đi về trong ngày.",
                      "styleFit": "Rất hợp",
                      "styleNote": "Hợp nhu cầu nghỉ nhẹ, chụp ảnh và đổi không khí.",
                      "fromCatalog": true
                    },
                    {
                      "name": "Ba Vì",
                      "region": "Miền Bắc",
                      "reason": "Không quá xa, có thiên nhiên và lịch trình dễ đi.",
                      "overallFit": "Rất phù hợp",
                      "overallNote": "Lựa chọn gần, dễ đi và hợp lịch ngắn.",
                      "budgetFit": "Phù hợp",
                      "budgetNote": "Chi phí dễ kiểm soát cho chuyến ngắn.",
                      "durationFit": "Phù hợp",
                      "durationNote": "Một ngày đủ cho các điểm chính nếu đi sớm.",
                      "travelFit": "Phù hợp",
                      "travelNote": "Tuyến đi gần Hà Nội, không chiếm quá nhiều thời gian.",
                      "styleFit": "Phù hợp",
                      "styleNote": "Hợp nhóm thích thiên nhiên và hoạt động nhẹ.",
                      "fromCatalog": false
                    },
                    {
                      "name": "Ninh Bình",
                      "region": "Miền Bắc",
                      "reason": "Có cảnh đẹp đặc trưng và vẫn khả thi cho lịch ngắn.",
                      "overallFit": "Đáng cân nhắc",
                      "overallNote": "Trải nghiệm nổi bật nhưng cần đi sớm vì xa hơn.",
                      "budgetFit": "Khá phù hợp",
                      "budgetNote": "Cần chọn lọc điểm tham quan để giữ ngân sách.",
                      "durationFit": "Khá phù hợp",
                      "durationNote": "Một ngày hơi gọn nhưng vẫn có thể đi điểm chính.",
                      "travelFit": "Khá phù hợp",
                      "travelNote": "Đường đi xa hơn, nên xuất phát sớm để đỡ vội.",
                      "styleFit": "Rất hợp",
                      "styleNote": "Rất hợp nếu muốn cảnh đẹp, chèo thuyền và chụp ảnh.",
                      "fromCatalog": true
                    }
                  ]
                }
                """);

        assertThat(suggestions).hasSize(3);
        assertThat(suggestions.get(0).getTravelNote()).contains("gần");
    }

    @Test
    void destinationSuggestionParserRejectsMissingTravelNote() {
        AiService service = new AiService(new ObjectMapper());

        assertThatThrownBy(() -> parseDestinationSuggestions(service, """
                {
                  "suggestions": [
                    {
                      "name": "Tam Đảo",
                      "region": "Miền Bắc",
                      "reason": "Gần Hà Nội, hợp chuyến ngắn và nhịp đi nhẹ.",
                      "overallFit": "Phù hợp nhất",
                      "overallNote": "Cân bằng tốt giữa đường đi gần, chi phí và nhu cầu nghỉ nhẹ.",
                      "budgetFit": "Phù hợp",
                      "budgetNote": "Ngân sách đủ cho ăn uống và điểm tham quan chính.",
                      "durationFit": "Phù hợp",
                      "durationNote": "Một ngày vẫn đủ ghé các điểm nổi bật.",
                      "travelFit": "Phù hợp",
                      "styleFit": "Rất hợp",
                      "styleNote": "Hợp nhu cầu nghỉ nhẹ, chụp ảnh và đổi không khí.",
                      "fromCatalog": true
                    },
                    {
                      "name": "Ba Vì",
                      "region": "Miền Bắc",
                      "reason": "Không quá xa, có thiên nhiên và lịch trình dễ đi.",
                      "overallFit": "Rất phù hợp",
                      "overallNote": "Lựa chọn gần, dễ đi và hợp lịch ngắn.",
                      "budgetFit": "Phù hợp",
                      "budgetNote": "Chi phí dễ kiểm soát cho chuyến ngắn.",
                      "durationFit": "Phù hợp",
                      "durationNote": "Một ngày đủ cho các điểm chính nếu đi sớm.",
                      "travelFit": "Phù hợp",
                      "travelNote": "Tuyến đi gần Hà Nội, không chiếm quá nhiều thời gian.",
                      "styleFit": "Phù hợp",
                      "styleNote": "Hợp nhóm thích thiên nhiên và hoạt động nhẹ.",
                      "fromCatalog": false
                    },
                    {
                      "name": "Ninh Bình",
                      "region": "Miền Bắc",
                      "reason": "Có cảnh đẹp đặc trưng và vẫn khả thi cho lịch ngắn.",
                      "overallFit": "Đáng cân nhắc",
                      "overallNote": "Trải nghiệm nổi bật nhưng cần đi sớm vì xa hơn.",
                      "budgetFit": "Khá phù hợp",
                      "budgetNote": "Cần chọn lọc điểm tham quan để giữ ngân sách.",
                      "durationFit": "Khá phù hợp",
                      "durationNote": "Một ngày hơi gọn nhưng vẫn có thể đi điểm chính.",
                      "travelFit": "Khá phù hợp",
                      "travelNote": "Đường đi xa hơn, nên xuất phát sớm để đỡ vội.",
                      "styleFit": "Rất hợp",
                      "styleNote": "Rất hợp nếu muốn cảnh đẹp, chèo thuyền và chụp ảnh.",
                      "fromCatalog": true
                    }
                  ]
                }
                """))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("travelNote");
    }

    @Test
    void destinationSuggestionParserRejectsMultipleTopOverallFits() {
        AiService service = new AiService(new ObjectMapper());

        assertThatThrownBy(() -> parseDestinationSuggestions(service, """
                {
                  "suggestions": [
                    {
                      "name": "Tam Đảo",
                      "region": "Miền Bắc",
                      "reason": "Gần Hà Nội, hợp chuyến ngắn và nhịp đi nhẹ.",
                      "overallFit": "Phù hợp nhất",
                      "overallNote": "Cân bằng tốt giữa đường đi gần, chi phí và nhu cầu nghỉ nhẹ.",
                      "budgetFit": "Phù hợp",
                      "budgetNote": "Ngân sách đủ cho ăn uống và điểm tham quan chính.",
                      "durationFit": "Phù hợp",
                      "durationNote": "Một ngày vẫn đủ ghé các điểm nổi bật.",
                      "travelFit": "Phù hợp",
                      "travelNote": "Đường đi tương đối gần, hợp đi về trong ngày.",
                      "styleFit": "Rất hợp",
                      "styleNote": "Hợp nhu cầu nghỉ nhẹ, chụp ảnh và đổi không khí.",
                      "fromCatalog": true
                    },
                    {
                      "name": "Ba Vì",
                      "region": "Miền Bắc",
                      "reason": "Không quá xa, có thiên nhiên và lịch trình dễ đi.",
                      "overallFit": "Phù hợp nhất",
                      "overallNote": "Lựa chọn gần, dễ đi và hợp lịch ngắn.",
                      "budgetFit": "Phù hợp",
                      "budgetNote": "Chi phí dễ kiểm soát cho chuyến ngắn.",
                      "durationFit": "Phù hợp",
                      "durationNote": "Một ngày đủ cho các điểm chính nếu đi sớm.",
                      "travelFit": "Phù hợp",
                      "travelNote": "Tuyến đi gần Hà Nội, không chiếm quá nhiều thời gian.",
                      "styleFit": "Phù hợp",
                      "styleNote": "Hợp nhóm thích thiên nhiên và hoạt động nhẹ.",
                      "fromCatalog": false
                    },
                    {
                      "name": "Ninh Bình",
                      "region": "Miền Bắc",
                      "reason": "Có cảnh đẹp đặc trưng và vẫn khả thi cho lịch ngắn.",
                      "overallFit": "Đáng cân nhắc",
                      "overallNote": "Trải nghiệm nổi bật nhưng cần đi sớm vì xa hơn.",
                      "budgetFit": "Khá phù hợp",
                      "budgetNote": "Cần chọn lọc điểm tham quan để giữ ngân sách.",
                      "durationFit": "Khá phù hợp",
                      "durationNote": "Một ngày hơi gọn nhưng vẫn có thể đi điểm chính.",
                      "travelFit": "Khá phù hợp",
                      "travelNote": "Đường đi xa hơn, nên xuất phát sớm để đỡ vội.",
                      "styleFit": "Rất hợp",
                      "styleNote": "Rất hợp nếu muốn cảnh đẹp, chèo thuyền và chụp ảnh.",
                      "fromCatalog": true
                    }
                  ]
                }
                """))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("more than one top fit");
    }

    @Test
    void regenerationPromptTreatsVerifiedPlacesAsTrustedSuggestionsNotAllowedOnlyList() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest req = generateRequest();
        req.setVerifiedPlacesContext("- Bảo tàng Đà Nẵng | type=ATTRACTION | address=24 Trần Phú");

        String prompt = buildDayRegenerationPrompt(
                service,
                req,
                List.of(roundTripBundledDay()),
                1,
                "REGENERATE",
                "thêm trải nghiệm địa phương hay",
                null);

        assertThat(prompt)
                .contains("Treat Style as the user's primary planning bias, not a hard restriction")
                .contains("trusted suggestions, not an allowed-only list")
                .contains("Candidates are ordered by backend relevance")
                .contains("do not blindly pick the top items")
                .contains("infer the destination's signature experiences and must-try categories")
                .contains("even when they are NOT listed in the verified candidates")
                .contains("return PARTIAL or NOT_FULFILLED with a concise grouped requestFulfillment item even without a user-specific request")
                .contains("add at most 1-3 items, grouped by core experience category")
                .contains("mention 1-3 representative missed places/activities when helpful")
                .contains("Tràng An, Tam Cốc, Hang Múa")
                .contains("do not list every famous place that cannot fit the itinerary")
                .doesNotContain("You may use other real places only when the verified candidates do not cover");
    }

    @Test
    void generatedPromptAddsSafetyOverrideAndSignatureExplanationWhenEveryDayIsHighRisk() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest req = generateRequest();
        req.setDays(2);
        req.setWeatherForecast("""
                Day 1 (2026-05-19): Thunderstorm, 25-30°C, rain chance 95% → HIGH RAIN RISK – prefer indoor activities
                Day 2 (2026-05-20): Rain, 24-29°C, rain chance 90% → HIGH RAIN RISK – prefer indoor activities
                """);

        String prompt = buildPrompt(service, req);

        assertThat(prompt)
                .contains("every trip day is SEVERE WEATHER RISK or legacy HIGH RAIN RISK")
                .contains("unsafe weather-dependent outdoor")
                .contains("priority=destination-signature")
                .contains("reasonCode WEATHER_SAFETY");
    }

    @Test
    void generatedPromptTreatsRainFlexAsFlexibleNotIndoorOnly() throws Exception {
        AiService service = new AiService(new ObjectMapper());
        TripDto.GenerateRequest req = generateRequest();
        req.setWeatherForecast("""
                Day 1 (2026-05-19): Rain, 25-30C, rain chance 75%, rain 4.0mm, wind 12km/h -> RAIN FLEX - keep signature outdoor activities when practical; add indoor backup
                """);

        String prompt = buildPrompt(service, req);

        assertThat(prompt)
                .contains("RAIN FLEX")
                .contains("Outdoor timing windows")
                .contains("Best daytime outdoor slot")
                .contains("Best light outdoor evening slot")
                .contains("treat rain as potentially intermittent")
                .contains("low-impact weather context")
                .contains("schedule outdoor/scenic highlights into the least rainy practical daytime part of the day")
                .contains("not a reason to reduce outdoor diversity")
                .contains("real destination-signature evening cultural areas")
                .contains("Phố cổ Hoa Lư")
                .contains("Keep destination-defining outdoor/scenic places in the main plan when generally safe")
                .contains("safety-sensitive outdoor activities that require sustained suitable conditions")
                .contains("trekking, hiking, climbing, caving, canyoning")
                .contains("RAIN FLEX is not an automatic ban")
                .contains("including access and return time")
                .contains("thunderstorms, heavy rain, strong wind, flooding, rough seas, slippery trails")
                .contains("reconfirm conditions and operating status");
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
        String[] names = {
                "Mì Quảng Bà Mua",
                "Bảo tàng Đà Nẵng",
                "Cộng Cà Phê Bạch Đằng",
                "Chợ Hàn",
                "Nhà hàng Bếp Cuốn Đà Nẵng",
                "Bảo tàng Điêu khắc Chăm Đà Nẵng",
                "Cầu Rồng",
                "Hải sản Bé Mặn",
                "Chợ đêm Sơn Trà"
        };
        String[] locations = {
                "231 Trần Phú, Hải Châu, Đà Nẵng",
                "24 Trần Phú, Hải Châu, Đà Nẵng",
                "96-98 Bạch Đằng, Hải Châu, Đà Nẵng",
                "119 Trần Phú, Hải Châu, Đà Nẵng",
                "54 Nguyễn Văn Thoại, Ngũ Hành Sơn, Đà Nẵng",
                "02 đường 2/9, Hải Châu, Đà Nẵng",
                "Đường Nguyễn Văn Linh, Hải Châu, Đà Nẵng",
                "Lô 14 Hoàng Sa, Sơn Trà, Đà Nẵng",
                "Mai Hắc Đế, Sơn Trà, Đà Nẵng"
        };
        for (int i = 0; i < ItineraryQualityPolicy.MAX_NON_LOGISTICS_ITEMS_PER_DAY; i++) {
            activities.add(activity(
                    String.format("%02d:00", 8 + i),
                    names[i],
                    i % 3 == 0 ? "FOOD" : "ATTRACTION",
                    locations[i],
                    100_000L,
                    null));
        }
        activities.add(activity("17:30", "Taxi ve khach san", "TRANSPORT",
                "Cho Con -> Khach san My Khe", 120_000L, "Taxi/Grab ve khach san."));
        activities.add(activity("18:30", "Nhan phong Khach san Brilliant Da Nang", "ACCOMMODATION",
                "162 Bach Dang, Hai Chau, Da Nang", 0, "Chi phi luu tru da tinh trong muc khac."));
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
        for (int i = 0; i <= ItineraryQualityPolicy.MAX_NON_LOGISTICS_ITEMS_PER_DAY; i++) {
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
        for (int i = 0; i < ItineraryQualityPolicy.MAX_NON_LOGISTICS_ITEMS_PER_DAY; i++) {
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
        activities.add(activity("17:30", "Nhan phong Khach san Brilliant Da Nang", "ACCOMMODATION",
                "162 Bach Dang, Hai Chau, Da Nang", 0, "Chi phi luu tru da tinh trong muc khac."));
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
        for (int i = 0; i < ItineraryQualityPolicy.MAX_NON_LOGISTICS_ITEMS_PER_DAY; i++) {
            activities.add(activity(
                    String.format("%02d:00", 6 + i),
                    "Trai nghiem Da Nang " + (i + 1),
                    i % 3 == 0 ? "FOOD" : "ATTRACTION",
                    "Dia diem cu the Da Nang " + (i + 1),
                    100_000L,
                    null));
        }
        int logisticsItemsToExceedTotal = ItineraryQualityPolicy.MAX_TOTAL_ITEMS_PER_DAY
                - ItineraryQualityPolicy.MAX_NON_LOGISTICS_ITEMS_PER_DAY
                + 1;
        for (int i = 0; i < logisticsItemsToExceedTotal; i++) {
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

    private String buildPrompt(
            AiService service,
            TripDto.GenerateRequest req) throws Exception {
        Method method = AiService.class.getDeclaredMethod("buildPrompt", TripDto.GenerateRequest.class);
        method.setAccessible(true);
        return (String) method.invoke(service, req);
    }

    private String buildDestinationSuggestionPrompt(
            AiService service,
            TripDto.DestinationSuggestionRequest req,
            String catalogContext) throws Exception {
        Method method = AiService.class.getDeclaredMethod(
                "buildDestinationSuggestionPrompt",
                TripDto.DestinationSuggestionRequest.class,
                String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, req, catalogContext);
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

    @SuppressWarnings("unchecked")
    private List<TripDto.DestinationSuggestion> parseDestinationSuggestions(
            AiService service,
            String json) throws Throwable {
        Method method = AiService.class.getDeclaredMethod("parseDestinationSuggestions", String.class);
        method.setAccessible(true);
        try {
            return (List<TripDto.DestinationSuggestion>) method.invoke(service, json);
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
        req.setOutboundTransport("PLANE");
        req.setLocalTransport("MIXED");
        return req;
    }

    private TripDto.DestinationSuggestionRequest destinationSuggestionRequest() {
        TripDto.DestinationSuggestionRequest req = new TripDto.DestinationSuggestionRequest();
        req.setDeparture("Ha Noi");
        req.setStartDate(LocalDate.now().plusDays(7));
        req.setEndDate(LocalDate.now().plusDays(9));
        req.setDays(3);
        req.setBudgetPerPerson(3_000_000L);
        req.setBudgetMode("PER_PERSON");
        req.setTravelerCount(2);
        req.setStyle("RELAXING");
        req.setGroupType("COUPLE");
        req.setOutboundTransport("MIXED");
        req.setLocalTransport("MIXED");
        req.setMustVisit("bien hoac nui nhe nhang");
        req.setAvoid("di bo qua nhieu");
        req.setNotes("uu tien lich trinh nhe");
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

    private TripDto.DayResponse inconsistentBundledRoundTripDay() {
        TripDto.DayResponse day = baseDay();
        day.setActivities(List.of(
                activity("08:00", "Ve may bay khu hoi Ha Noi - Da Nang", "TRANSPORT",
                        "San bay Noi Bai (HAN) -> San bay Da Nang (DAD)", 3_600_000L,
                        "Gia ve khu hoi cho ca nhom, day la chi phi cho chieu di."),
                activity("11:30", "An trua Mi Quang Ba Mua", "FOOD",
                        "231 Tran Phu, Hai Chau, Da Nang", 200_000L, null),
                activity("14:00", "Tham quan Bao tang Da Nang", "ATTRACTION",
                        "24 Tran Phu, Hai Chau, Da Nang", 80_000L, null)));
        return day;
    }

    private TripDto.DayResponse doubleCountedRoundTripDay() {
        TripDto.DayResponse day = baseDay();
        day.setActivities(List.of(
                activity("08:00", "Ve may bay khu hoi Ha Noi - Da Nang", "TRANSPORT",
                        "San bay Noi Bai (HAN) <-> San bay Da Nang (DAD)", 3_600_000L,
                        "Chi phi ve may bay khu hoi cho ca nhom, bao gom ca chieu di va chieu ve."),
                activity("11:30", "An trua Mi Quang Ba Mua", "FOOD",
                        "231 Tran Phu, Hai Chau, Da Nang", 200_000L, null),
                activity("14:00", "Tham quan Bao tang Da Nang", "ATTRACTION",
                        "24 Tran Phu, Hai Chau, Da Nang", 80_000L, null),
                activity("20:00", "Chuyen bay Da Nang - Ha Noi", "TRANSPORT",
                        "San bay Da Nang (DAD) -> San bay Noi Bai (HAN)", 1_800_000L,
                        "Chi phi cho chieu ve.")));
        return day;
    }

    private TripDto.DayResponse separateLegsWithRoundTripAdvisoryDay() {
        TripDto.DayResponse day = baseDay();
        day.setActivities(List.of(
                activity("08:00", "Chuyen bay Ha Noi - Da Nang", "TRANSPORT",
                        "San bay Noi Bai (HAN) -> San bay Da Nang (DAD)", 1_800_000L,
                        "Ve mot chieu cho ca nhom; nen dat ve khu hoi rieng neu muon gia tot hon."),
                activity("11:30", "An trua Mi Quang Ba Mua", "FOOD",
                        "231 Tran Phu, Hai Chau, Da Nang", 200_000L, null),
                activity("14:00", "Tham quan Bao tang Da Nang", "ATTRACTION",
                        "24 Tran Phu, Hai Chau, Da Nang", 80_000L, null),
                activity("20:00", "Chuyen bay Da Nang - Ha Noi", "TRANSPORT",
                        "San bay Da Nang (DAD) -> San bay Noi Bai (HAN)", 1_800_000L,
                        "Ve may bay ve Ha Noi.")));
        return day;
    }

    private TripDto.DayResponse paidSeparateRoundTripDay() {
        TripDto.DayResponse day = baseDay();
        day.setActivities(List.of(
                activity("08:00", "Chuyen bay Ha Noi - Da Nang", "TRANSPORT",
                        "San bay Noi Bai (HAN) -> San bay Da Nang (DAD)", 1_800_000L,
                        "Ve may bay mot chieu cho ca nhom."),
                activity("11:30", "An trua Mi Quang Ba Mua", "FOOD",
                        "231 Tran Phu, Hai Chau, Da Nang", 200_000L, null),
                activity("14:00", "Tham quan Bao tang Da Nang", "ATTRACTION",
                        "24 Tran Phu, Hai Chau, Da Nang", 80_000L, null),
                activity("20:00", "Chuyen bay Da Nang - Ha Noi", "TRANSPORT",
                        "San bay Da Nang (DAD) -> San bay Noi Bai (HAN)", 1_800_000L,
                        "Ve may bay ve Ha Noi.")));
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
