package com.vivuplan.vivuplan_be.service;

import com.vivuplan.vivuplan_be.dto.TripDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ItineraryQualityValidatorTest {

    private final ItineraryQualityValidator validator = new ItineraryQualityValidator();

    @Test
    void genericThresholdIsSharedAndKeepsTwoIsolatedGenericItemsAllowed() {
        assertThat(ItineraryQualityPolicy.maxGenericActivitiesAllowed(4)).isEqualTo(2);
        assertThat(ItineraryQualityPolicy.maxGenericActivitiesAllowed(8)).isEqualTo(2);
        assertThat(ItineraryQualityPolicy.maxGenericActivitiesAllowed(12)).isEqualTo(3);
    }

    @Test
    void planeClassifierAcceptsAirportAliasesWithoutTreatingLocalRoutesAsFlights() {
        assertThat(ItineraryQualityPolicy.isIntercityTransport(
                "Noi Bai -> Lien Khuong",
                "PLANE")).isTrue();
        assertThat(ItineraryQualityPolicy.isIntercityTransport(
                "Bao tang Quang Binh - khach san",
                "PLANE")).isFalse();
    }

    @Test
    void pacingGuidanceDoesNotTreatFirstAndLastDaysAsAutomaticallyLight() {
        assertThat(ItineraryQualityPolicy.vietnamPacingGuidance())
                .contains("5-10 display items")
                .contains("3-7 items")
                .contains("do not treat the first or last day as light automatically")
                .contains("target 6-10 FOOD/CAFE/ATTRACTION/ACTIVITY/NIGHTLIFE items")
                .contains("10 non-logistics items is a pacing target, not a separate hard limit")
                .contains("more than 15 total items");
    }

    @Test
    void normalFirstDayRequiresThreeMeaningfulActivities() {
        TripDto.GenerateRequest req = request(3);
        TripDto.DayResponse firstDay = day(1,
                activity("08:00", "Bao tang Quang Binh", "ATTRACTION", "Dong Hoi", "1 gio", 50_000, null),
                activity("12:00", "An trua chao canh ca loc", "FOOD", "Cho Dong Hoi", "1 gio", 200_000, null));

        ItineraryQualityValidator.Result result = validator.validateFull(
                List.of(firstDay, normalDay(2), normalDay(3)),
                req);

        assertThat(result.passed()).isFalse();
        assertThat(result.failureType()).isEqualTo(ItineraryQualityValidator.FailureType.QUALITY);
        assertThat(result.reason()).contains("fewer than 3 meaningful activities");
    }

    @Test
    void emptyDayIsStructuralInsteadOfBestEffortQuality() {
        TripDto.GenerateRequest req = request(1);
        TripDto.DayResponse emptyDay = day(1);

        ItineraryQualityValidator.Result result = validator.validateFull(List.of(emptyDay), req);

        assertThat(result.failureType()).isEqualTo(ItineraryQualityValidator.FailureType.STRUCTURAL);
        assertThat(result.reason()).contains("no activities");
    }

    @Test
    void duplicateOrMissingDayNumbersAreStructural() {
        TripDto.GenerateRequest req = request(3);

        ItineraryQualityValidator.Result result = validator.validateFull(
                List.of(normalDay(1), normalDay(1), normalDay(3)),
                req);

        assertThat(result.failureType()).isEqualTo(ItineraryQualityValidator.FailureType.STRUCTURAL);
        assertThat(result.reason()).contains("duplicate day number");
    }

    @Test
    void outOfOrderDayNumbersAreStructural() {
        TripDto.GenerateRequest req = request(3);

        ItineraryQualityValidator.Result result = validator.validateFull(
                List.of(normalDay(2), normalDay(1), normalDay(3)),
                req);

        assertThat(result.failureType()).isEqualTo(ItineraryQualityValidator.FailureType.STRUCTURAL);
        assertThat(result.reason()).contains("out of order");
    }

    @Test
    void invalidActivityTypeIsStructuralInSharedValidator() {
        TripDto.GenerateRequest req = request(1);
        TripDto.DayResponse invalidDay = day(1,
                activity("08:00", "Bao tang Quang Binh", "UNKNOWN",
                        "Dong Hoi", "1 gio", 50_000, null),
                activity("10:00", "Cho Dong Hoi", "ATTRACTION",
                        "Dong Hoi", "1 gio", 0, null),
                activity("12:00", "An chao canh ca loc", "FOOD",
                        "Cho Dong Hoi", "1 gio", 200_000, null));

        ItineraryQualityValidator.Result result = validator.validateFull(List.of(invalidDay), req);

        assertThat(result.failureType()).isEqualTo(ItineraryQualityValidator.FailureType.STRUCTURAL);
        assertThat(result.reason()).contains("invalid type");
    }

    @Test
    void vietnameseFamilyNoteUsesSharedRelaxedMinimum() {
        TripDto.GenerateRequest req = request(1);
        req.setNotes("Chuyen di gia dinh, uu tien nhe nhang.");
        TripDto.DayResponse familyDay = day(1,
                activity("09:00", "Bao tang Quang Binh", "ATTRACTION",
                        "Dong Hoi", "1 gio", 50_000, null),
                activity("12:00", "An chao canh ca loc", "FOOD",
                        "Cho Dong Hoi", "1 gio", 200_000, null));

        ItineraryQualityValidator.Result result = validator.validateFull(List.of(familyDay), req);

        assertThat(result.passed()).as(result.reason()).isTrue();
    }

    @Test
    void airportAliasRoutesCountAsIntercityWithoutCityNames() {
        TripDto.GenerateRequest req = request(3);
        req.setOutboundTransport("PLANE");

        TripDto.DayResponse day1 = day(1,
                activity("06:00", "Noi Bai -> Lien Khuong", "TRANSPORT",
                        "Noi Bai -> Lien Khuong", "3 gio", 4_000_000,
                        "Ve may bay khu hoi cho ca nhom, bao gom chieu ve."),
                activity("10:00", "An banh can Da Lat", "FOOD",
                        "Cho Da Lat", "1 gio", 300_000, null));
        TripDto.DayResponse day3 = day(3,
                activity("09:00", "Uong ca phe tai Cho Da Lat", "CAFE",
                        "Cho Da Lat", "1 gio", 200_000, null),
                activity("16:00", "Lien Khuong -> Noi Bai", "TRANSPORT",
                        "Lien Khuong -> Noi Bai", "3 gio", 0,
                        "Chieu ve da tinh trong ve may bay khu hoi ngay 1."));

        ItineraryQualityValidator.Result result = validator.validateFull(
                List.of(day1, normalDay(2), day3),
                req);

        assertThat(result.passed()).as(result.reason()).isTrue();
    }

    @Test
    void fillerDoesNotSatisfyMeaningfulMinimum() {
        TripDto.GenerateRequest req = request(1);
        TripDto.DayResponse day = day(1,
                activity("08:00", "Bao tang Quang Binh", "ATTRACTION", "Dong Hoi", "1 gio", 50_000, null),
                activity("10:00", "Nghi ngoi va chuan bi", "ACTIVITY", "Khach san", "2 gio", 0, null),
                activity("12:00", "An trua chao canh ca loc", "FOOD", "Cho Dong Hoi", "1 gio", 200_000, null));

        ItineraryQualityValidator.Result result = validator.validateFull(List.of(day), req);

        assertThat(result.passed()).isFalse();
        assertThat(result.reason()).contains("meaningful activities");
    }

    @Test
    void movementTransportParticipatesInOverlapValidation() {
        TripDto.GenerateRequest req = request(1);
        TripDto.DayResponse day = day(1,
                activity("08:00", "Tham quan Bao tang Quang Binh", "ATTRACTION",
                        "Bao tang Quang Binh", "2 gio", 50_000, null),
                activity("09:00", "Taxi den Cho Dong Hoi", "TRANSPORT",
                        "Bao tang Quang Binh -> Cho Dong Hoi", "1 gio", 100_000, "Taxi cho ca nhom."),
                activity("11:00", "An trua chao canh ca loc", "FOOD",
                        "Cho Dong Hoi", "1 gio", 200_000, null));

        ItineraryQualityValidator.Result result = validator.validateFull(List.of(day), req);

        assertThat(result.passed()).isFalse();
        assertThat(result.reason()).contains("activity times overlap");
    }

    @Test
    void bookingOnlyTransportDoesNotCreateFalseOverlap() {
        TripDto.GenerateRequest req = request(1);
        TripDto.DayResponse day = day(1,
                activity("08:00", "Ve tau khu hoi Ha Noi - Dong Hoi", "TRANSPORT",
                        "Ga Ha Noi <-> Ga Dong Hoi", "10 gio", 3_200_000,
                        "Ve tau khu hoi cho ca nhom, bao gom chieu ve."),
                activity("08:30", "Tham quan Bao tang Quang Binh", "ATTRACTION",
                        "Bao tang Quang Binh", "1 gio", 50_000, null),
                activity("10:00", "An chao canh ca loc", "FOOD",
                        "Cho Dong Hoi", "1 gio", 200_000, null),
                activity("20:00", "Len tau Dong Hoi ve Ha Noi", "TRANSPORT",
                        "Ga Dong Hoi -> Ga Ha Noi", "10 gio", 0,
                        "Chi phi da tinh trong ve tau khu hoi luc 08:00."));

        ItineraryQualityValidator.Result result = validator.validateFull(List.of(day), req);

        assertThat(result.passed()).as(result.reason()).isTrue();
    }

    @Test
    void detectsPhongNhaTicketAndTransferCostBundledTogether() {
        TripDto.GenerateRequest req = request(1);
        TripDto.DayResponse day = day(1,
                activity("06:00", "Den Ga Dong Hoi va di chuyen ve Phong Nha", "TRANSPORT",
                        "Ga Dong Hoi", "1 gio 30 phut", 6_800_000,
                        "Bao gom ve tau khu hoi Ha Noi - Dong Hoi va taxi khu hoi Ga Dong Hoi - Phong Nha."),
                activity("08:00", "An chao canh ca loc", "FOOD",
                        "Cho Phong Nha", "1 gio", 200_000, null),
                activity("09:30", "Tham quan Dong Phong Nha", "ATTRACTION",
                        "Dong Phong Nha", "3 gio", 1_000_000, null));

        ItineraryQualityValidator.Result result = validator.validateFull(List.of(day), req);

        assertThat(result.passed()).isFalse();
        assertThat(result.reason()).contains("ticket and local transfer costs are bundled");
    }

    @Test
    void detectsMultiDayVehiclePackageAttachedToShortTransfer() {
        TripDto.GenerateRequest req = request(1);
        TripDto.DayResponse day = day(1,
                activity("08:00", "Di chuyen den Ben thuyen Phong Nha", "TRANSPORT",
                        "Ben thuyen Phong Nha", "15 phut", 2_400_000,
                        "Chi phi xe rieng co tai xe cho ngay 1 va ngay 2."),
                activity("09:00", "Tham quan Dong Phong Nha", "ATTRACTION",
                        "Dong Phong Nha", "3 gio", 1_000_000, null),
                activity("12:30", "An trua ca suoi nuong", "FOOD",
                        "Cho Phong Nha", "1 gio", 400_000, null));

        ItineraryQualityValidator.Result result = validator.validateFull(List.of(day), req);

        assertThat(result.passed()).isFalse();
        assertThat(result.reason()).contains("multi-day vehicle package cost");
    }

    @Test
    void longActivityOverlapIsDetectedBeyondAdjacentSortedRange() {
        TripDto.GenerateRequest req = request(1);
        TripDto.DayResponse day = day(1,
                activity("08:00", "Tour vuon quoc gia", "ACTIVITY",
                        "Phong Nha", "4 gio", 500_000, null),
                activity("09:30", "Dung chan chup anh", "ATTRACTION",
                        "Phong Nha", "30 phut", 0, null),
                activity("10:30", "Tham quan hang dong", "ATTRACTION",
                        "Phong Nha", "1 gio", 200_000, null));

        ItineraryQualityValidator.Result result = validator.validateFull(List.of(day), req);

        assertThat(result.failureType()).isEqualTo(ItineraryQualityValidator.FailureType.QUALITY);
        assertThat(result.reason()).contains("Tour vuon quoc gia / Tham quan hang dong");
    }

    @Test
    void selfDriveRentalPackageOwnsLaterZeroCostMovement() {
        TripDto.GenerateRequest req = request(1);
        TripDto.DayResponse day = day(1,
                activity("08:00", "Thue o to tu lai 3 ngay", "TRANSPORT",
                        "Trung tam Dong Hoi", "30 phut", 2_400_000,
                        "Tong chi phi thue xe tu lai 3 ngay cho ca nhom."),
                activity("09:00", "Dong Phong Nha", "ATTRACTION",
                        "Phong Nha", "2 gio", 500_000, null),
                activity("12:00", "Di chuyen bang o to tu lai", "TRANSPORT",
                        "Phong Nha -> Dong Hoi", "1 gio", 0,
                        "Chi phi thue xe da tinh trong goi 3 ngay luc 08:00."));

        ItineraryQualityValidator.Result result = validator.validateFull(List.of(day), req);

        assertThat(result.passed()).as(result.reason()).isTrue();
    }

    @Test
    void zeroCostLegRequiresRoundTripOwnerForTheSameTransportMode() {
        TripDto.GenerateRequest req = request(3);
        req.setOutboundTransport("MIXED");
        TripDto.DayResponse day1 = day(1,
                activity("06:00", "Tau Ha Noi den Dong Hoi", "TRANSPORT",
                        "Ga Ha Noi -> Ga Dong Hoi", "10 gio", 3_200_000,
                        "Ve tau khu hoi cho ca nhom, bao gom chieu ve."),
                activity("17:00", "An chao canh ca loc", "FOOD",
                        "Cho Dong Hoi", "1 gio", 200_000, null));
        TripDto.DayResponse day3 = day(3,
                activity("08:00", "Bao tang Quang Binh", "ATTRACTION",
                        "Dong Hoi", "1 gio", 50_000, null),
                activity("10:00", "An sang tai Cho Dong Hoi", "FOOD",
                        "Cho Dong Hoi", "1 gio", 200_000, null),
                activity("16:00", "Chuyen bay Dong Hoi ve Noi Bai", "TRANSPORT",
                        "San bay Dong Hoi -> San bay Noi Bai", "2 gio", 0,
                        "Chi phi da tinh trong ve khu hoi ngay 1."));

        ItineraryQualityValidator.Result result = validator.validateFull(
                List.of(day1, normalDay(2), day3),
                req);

        assertThat(result.failureType()).isEqualTo(ItineraryQualityValidator.FailureType.QUALITY);
        assertThat(result.reason()).contains("missing paid round-trip owner");
    }

    @Test
    void allowsGatewayDifferentFromDestinationWhenOutboundAndReturnAreExplicit() {
        TripDto.GenerateRequest req = request(3);
        req.setOutboundTransport("TRAIN");

        TripDto.DayResponse day1 = day(1,
                activity("06:00", "Den Dong Hoi bang tau tu Ha Noi", "TRANSPORT",
                        "Ga Dong Hoi", "10 gio", 6_400_000,
                        "Ve tau khu hoi Ha Noi - Dong Hoi; chuyen di khoi hanh toi hom truoc."),
                activity("07:30", "An chao canh ca loc", "FOOD", "Cho Dong Hoi", "1 gio", 200_000, null),
                activity("09:00", "Tham quan Dong Phong Nha", "ATTRACTION",
                        "Dong Phong Nha", "3 gio", 1_000_000, null));
        TripDto.DayResponse day2 = normalDay(2);
        TripDto.DayResponse day3 = day(3,
                activity("08:00", "Tham quan Bao tang Quang Binh", "ATTRACTION",
                        "Bao tang Quang Binh", "1 gio", 50_000, null),
                activity("10:00", "An chao canh ca loc", "FOOD", "Cho Dong Hoi", "1 gio", 200_000, null),
                activity("16:00", "Len tau Dong Hoi ve Ha Noi", "TRANSPORT",
                        "Ga Dong Hoi -> Ga Ha Noi", "10 gio", 0,
                        "Chi phi da duoc tinh trong ve tau khu hoi o ngay 1."));

        ItineraryQualityValidator.Result result = validator.validateFull(List.of(day1, day2, day3), req);

        assertThat(result.passed()).isTrue();
    }

    @Test
    void overnightArrivalMayExplainModeAndRouteAcrossActivityFields() {
        TripDto.GenerateRequest req = request(3);
        req.setOutboundTransport("TRAIN");
        TripDto.DayResponse day1 = day(1,
                activity("06:00", "Den Ga Dong Hoi va ve Phong Nha", "TRANSPORT",
                        "Ga Dong Hoi", "10 gio", 6_400_000,
                        "Ve tau khu hoi Ha Noi - Dong Hoi; chuyen di khoi hanh toi hom truoc."),
                activity("08:00", "An chao canh ca loc", "FOOD", "Cho Dong Hoi", "1 gio", 200_000, null),
                activity("09:30", "Tham quan Dong Phong Nha", "ATTRACTION",
                        "Dong Phong Nha", "3 gio", 1_000_000, null));
        TripDto.DayResponse day3 = day(3,
                activity("08:00", "Bao tang Quang Binh", "ATTRACTION", "Dong Hoi", "1 gio", 50_000, null),
                activity("10:00", "An chao canh ca loc", "FOOD", "Cho Dong Hoi", "1 gio", 200_000, null),
                activity("16:00", "Len tau Dong Hoi ve Ha Noi", "TRANSPORT",
                        "Ga Dong Hoi -> Ga Ha Noi", "10 gio", 0,
                        "Da tinh trong ve tau khu hoi ngay 1."));

        ItineraryQualityValidator.Result result = validator.validateFull(
                List.of(day1, normalDay(2), day3),
                req);

        assertThat(result.passed()).as(result.reason()).isTrue();
    }

    @Test
    void leavesSpecificityCheckInAndWeatherLanguageToPromptGuidance() {
        TripDto.GenerateRequest req = request(1);

        ItineraryQualityValidator.Result genericMeal = validator.validateFull(List.of(day(1,
                activity("08:00", "Bua sang dia phuong", "FOOD",
                        "Quan an tai Phong Nha", "1 gio", 200_000, "Thu cac mon pho hoac bun."),
                activity("10:00", "Dong Phong Nha", "ATTRACTION", "Dong Phong Nha", "2 gio", 500_000, null),
                activity("13:00", "Bao tang Quang Binh", "ATTRACTION", "Dong Hoi", "1 gio", 50_000, null))),
                req);
        assertThat(genericMeal.passed()).isTrue();

        ItineraryQualityValidator.Result earlyCheckIn = validator.validateFull(List.of(day(1,
                activity("11:30", "Nhan phong Phong Nha Farmstay", "ACCOMMODATION",
                        "Son Trach, Bo Trach", "30 phut", 2_000_000, "Chi phi 2 dem."),
                activity("12:30", "An ca suoi nuong", "FOOD", "Cho Phong Nha", "1 gio", 300_000, null),
                activity("14:00", "Dong Phong Nha", "ATTRACTION", "Dong Phong Nha", "2 gio", 500_000, null))),
                req);
        assertThat(earlyCheckIn.passed()).as(earlyCheckIn.reason()).isTrue();

        ItineraryQualityValidator.Result genericAccommodation = validator.validateFull(List.of(day(1,
                activity("13:00", "Nhan phong Homestay/Eco-lodge tai Phong Nha", "ACCOMMODATION",
                        "Phong Nha, Bo Trach, Quang Binh", "30 phut", 2_000_000, "Chi phi 2 dem."),
                activity("14:00", "Dong Phong Nha", "ATTRACTION", "Dong Phong Nha", "2 gio", 500_000, null),
                activity("17:00", "An ca suoi nuong", "FOOD", "Cho Phong Nha", "1 gio", 300_000, null))),
                req);
        assertThat(genericAccommodation.passed()).isTrue();

        TripDto.DayResponse weatherDay = day(1,
                activity("08:00", "Trai nghiem Song Chay - Hang Toi neu thoi tiet cho phep", "ATTRACTION",
                        "Song Chay - Hang Toi", "2 gio", 500_000,
                        "Kiem tra muc nuoc, song lon va dieu kien van hanh truoc khi tham gia."),
                activity("11:00", "An ca suoi nuong", "FOOD", "Cho Phong Nha", "1 gio", 300_000, null),
                activity("13:00", "Bao tang Quang Binh", "ATTRACTION", "Dong Hoi", "1 gio", 50_000, null));
        weatherDay.setTitle("Ngay ngoai troi tuy theo thoi tiet");
        weatherDay.setSummary("Lich trinh linh hoat neu co mua lon.");

        ItineraryQualityValidator.Result weatherText = validator.validateFull(List.of(weatherDay), req);

        assertThat(weatherText.passed()).as(weatherText.reason()).isTrue();
    }

    @Test
    void unparseableDurationDoesNotCreateAssumedOverlap() {
        TripDto.GenerateRequest req = request(1);
        TripDto.DayResponse day = day(1,
                activity("08:00", "Bao tang Quang Binh", "ATTRACTION",
                        "Dong Hoi", "buoi sang", 50_000, null),
                activity("08:30", "Cho Dong Hoi", "ATTRACTION",
                        "Dong Hoi", "45 phut", 0, null),
                activity("10:00", "An chao canh ca loc", "FOOD",
                        "Cho Dong Hoi", "1 gio", 200_000, null));

        ItineraryQualityValidator.Result result = validator.validateFull(List.of(day), req);

        assertThat(result.passed()).as(result.reason()).isTrue();
    }

    @Test
    void blankDurationDoesNotCreateAssumedOverlap() {
        TripDto.GenerateRequest req = request(1);
        TripDto.DayResponse day = day(1,
                activity("08:00", "Bao tang Quang Binh", "ATTRACTION",
                        "Dong Hoi", null, 50_000, null),
                activity("08:30", "Cho Dong Hoi", "ATTRACTION",
                        "Dong Hoi", "45 phut", 0, null),
                activity("10:00", "An chao canh ca loc", "FOOD",
                        "Cho Dong Hoi", "1 gio", 200_000, null));

        ItineraryQualityValidator.Result result = validator.validateFull(List.of(day), req);

        assertThat(result.passed()).as(result.reason()).isTrue();
    }

    @Test
    void nonBlockingBookingMayShareStartTimeWithPhysicalActivity() {
        TripDto.GenerateRequest req = request(1);
        req.setOutboundTransport("TRAIN");
        TripDto.DayResponse day = day(1,
                activity("08:00", "Ve tau khu hoi Ha Noi - Dong Hoi", "TRANSPORT",
                        "Ga Ha Noi <-> Ga Dong Hoi", "10 gio", 3_200_000,
                        "Ve tau khu hoi cho ca nhom, bao gom chieu ve."),
                activity("08:00", "Bao tang Quang Binh", "ATTRACTION",
                        "Dong Hoi", "1 gio", 50_000, null),
                activity("10:00", "An chao canh ca loc", "FOOD",
                        "Cho Dong Hoi", "1 gio", 200_000, null));

        ItineraryQualityValidator.Result result = validator.validateFull(List.of(day), req);

        assertThat(result.passed()).as(result.reason()).isTrue();
    }

    @Test
    void blockingActivitiesWithSameStartTimeRemainAnOverlapQualityIssue() {
        TripDto.GenerateRequest req = request(1);
        TripDto.DayResponse day = day(1,
                activity("08:00", "Bao tang Quang Binh", "ATTRACTION",
                        "Dong Hoi", "2 gio", 50_000, null),
                activity("08:00", "Cho Dong Hoi", "ATTRACTION",
                        "Dong Hoi", "1 gio", 0, null),
                activity("11:00", "An chao canh ca loc", "FOOD",
                        "Cho Dong Hoi", "1 gio", 200_000, null));

        ItineraryQualityValidator.Result result = validator.validateFull(List.of(day), req);

        assertThat(result.failureType()).isEqualTo(ItineraryQualityValidator.FailureType.QUALITY);
        assertThat(result.reason()).contains("activity times overlap");
    }

    @Test
    void statusOnlyTransportMilestoneDoesNotTriggerBackendRetry() {
        TripDto.GenerateRequest req = request(1);
        TripDto.DayResponse day = day(1,
                activity("06:00", "Ve may bay khu hoi Ha Noi - Dong Hoi", "TRANSPORT",
                        "San bay Noi Bai -> San bay Dong Hoi", "2 gio", 4_000_000,
                        "Ve may bay khu hoi cho ca nhom, bao gom chieu ve."),
                activity("08:00", "Ha canh San bay Dong Hoi", "TRANSPORT",
                        "San bay Dong Hoi", "0 gio", 0, "Moc den san bay."),
                activity("10:00", "Tham quan Bao tang Quang Binh", "ATTRACTION",
                        "Dong Hoi", "1 gio", 50_000, null),
                activity("12:00", "An chao canh ca loc", "FOOD",
                        "Cho Dong Hoi", "1 gio", 300_000, null),
                activity("20:00", "Chuyen bay Dong Hoi ve Ha Noi", "TRANSPORT",
                        "San bay Dong Hoi -> San bay Noi Bai", "2 gio", 0,
                        "Chi phi da tinh trong ve may bay khu hoi luc 06:00."));

        ItineraryQualityValidator.Result result = validator.validateFull(List.of(day), req);

        assertThat(result.passed()).as(result.reason()).isTrue();
    }

    @Test
    void looksLikeRouteRecognizesVietnameseDestinationMarkers() {
        assertThat(ItineraryQualityPolicy.looksLikeRoute("tu ha noi di sa pa")).isTrue();
        assertThat(ItineraryQualityPolicy.looksLikeRoute("tu cat bi sang noi bai")).isTrue();
        assertThat(ItineraryQualityPolicy.looksLikeRoute("tu phong nha ve dong hoi")).isTrue();
        assertThat(ItineraryQualityPolicy.looksLikeRoute("tu ha noi den dong hoi")).isTrue();
    }

    @Test
    void trainClassifierRecognizesDiTauAndTauLua() {
        assertThat(ItineraryQualityPolicy.isIntercityTransport(
                "di tau den hue",
                "TRAIN")).isTrue();
        assertThat(ItineraryQualityPolicy.isIntercityTransport(
                "chuyen tau lua di vinh",
                "TRAIN")).isTrue();
    }

    @Test
    void busClassifierRecognizesSleeperBuses() {
        assertThat(ItineraryQualityPolicy.isIntercityTransport(
                "di xe giuong nam di sa pa",
                "BUS")).isTrue();
        assertThat(ItineraryQualityPolicy.isIntercityTransport(
                "xe nam chat luong cao",
                "BUS")).isTrue();
    }

    @Test
    void returnEvidenceRecognizesVeLaiAndTroLai() {
        TripDto.GenerateRequest req = request(3);
        req.setOutboundTransport("TRAIN");

        TripDto.DayResponse day1 = day(1,
                activity("06:00", "Ga Ha Noi di ga Dong Hoi", "TRANSPORT",
                        "Ga Ha Noi", "10 gio", 6_400_000,
                        "Ve tau khu hoi Ha Noi - Dong Hoi."),
                activity("17:00", "An toi", "FOOD", "Dong Hoi", "1 gio", 200_000, null));
        TripDto.DayResponse day3 = day(3,
                activity("08:00", "Cafe", "CAFE", "Dong Hoi", "1 gio", 50_000, null),
                activity("10:00", "An trua", "FOOD", "Dong Hoi", "1 gio", 200_000, null),
                activity("16:00", "Len xe khach ve lai Ha Noi", "TRANSPORT",
                        "Dong Hoi -> Ha Noi", "10 gio", 0,
                        "Da tinh trong ve tau ngay 1."));

        ItineraryQualityValidator.Result result = validator.validateFull(
                List.of(day1, normalDay(2), day3),
                req);
        assertThat(result.passed()).as(result.reason()).isTrue();
    }

    @Test
    void parseDurationMinutesSupportsTieng() {
        TripDto.GenerateRequest req = request(1);
        TripDto.DayResponse day = day(1,
                activity("08:00", "Tham quan dong", "ATTRACTION", "Phong Nha", "2 tieng", 100_000, null),
                activity("09:00", "An sang", "FOOD", "Phong Nha", "1.5 tieng", 50_000, null),
                activity("12:00", "An trua", "FOOD", "Phong Nha", "1 gio", 200_000, null));

        ItineraryQualityValidator.Result result = validator.validateFull(List.of(day), req);

        assertThat(result.passed()).isFalse();
        assertThat(result.reason()).contains("activity times overlap");
    }

    @Test
    void parseDurationMinutesHandlesCompoundHourMinuteFormat() {
        // "1h30p" should parse as 90 minutes, not just 30.
        // Before fix: h\b failed when h followed by digit, so only 30 min counted.
        TripDto.GenerateRequest req = request(1);
        TripDto.DayResponse day = day(1,
                activity("08:00", "Tour hang dong", "ACTIVITY", "Phong Nha", "1h30p", 200_000, null),
                activity("08:30", "An sang", "FOOD", "Phong Nha", "1 gio", 50_000, null),
                activity("12:00", "An trua", "FOOD", "Phong Nha", "1 gio", 200_000, null));

        // 08:00 + 1h30p = 09:30. An sang at 08:30 overlaps by 60 min (> 30 min threshold fails).
        ItineraryQualityValidator.Result result = validator.validateFull(List.of(day), req);

        assertThat(result.passed()).isFalse();
        assertThat(result.reason()).contains("activity times overlap");
    }

    @Test
    void tuDoInPlaceNameIsNotFiller() {
        // "Bai bien Tu Do" contains "tu do" but should NOT be treated as filler.
        // Before fix: bare "tu do" substring matched any name containing "tu do".
        TripDto.GenerateRequest req = request(1);
        TripDto.DayResponse day = day(1,
                activity("08:00", "Bai bien Tu Do", "ATTRACTION", "Dong Hoi", "2 gio", 0, null),
                activity("11:00", "An trua hai san", "FOOD", "Dong Hoi", "1 gio", 200_000, null),
                activity("13:00", "Bao tang Quang Binh", "ATTRACTION", "Dong Hoi", "1 gio", 50_000, null));

        ItineraryQualityValidator.Result result = validator.validateFull(List.of(day), req);

        assertThat(result.passed()).as(result.reason()).isTrue();
    }

    @Test
    void prePurchasedPhraseIsRecognizedAsAlreadyIncluded() {
        // "da mua san" should count as already-included evidence, preventing false
        // zero-cost reference failure. Before fix, this phrase was not in the list.
        TripDto.GenerateRequest req = request(3);
        req.setOutboundTransport("TRAIN");

        TripDto.DayResponse day1 = day(1,
                activity("06:00", "Tau Ha Noi den Dong Hoi", "TRANSPORT",
                        "Ga Ha Noi -> Ga Dong Hoi", "10 gio", 6_400_000,
                        "Ve tau khu hoi cho ca nhom, bao gom chieu ve."),
                activity("17:00", "An toi", "FOOD", "Dong Hoi", "1 gio", 200_000, null));
        TripDto.DayResponse day3 = day(3,
                activity("08:00", "Bao tang Quang Binh", "ATTRACTION", "Dong Hoi", "1 gio", 50_000, null),
                activity("10:00", "An sang", "FOOD", "Dong Hoi", "1 gio", 200_000, null),
                activity("16:00", "Len tau Dong Hoi ve Ha Noi", "TRANSPORT",
                        "Ga Dong Hoi -> Ga Ha Noi", "10 gio", 0,
                        "Ve da mua san trong goi khu hoi ngay 1."));

        ItineraryQualityValidator.Result result = validator.validateFull(
                List.of(day1, normalDay(2), day3),
                req);

        assertThat(result.passed()).as(result.reason()).isTrue();
    }

    private TripDto.GenerateRequest request(int days) {
        TripDto.GenerateRequest req = new TripDto.GenerateRequest();
        req.setDeparture(days == 1 ? "Phong Nha - Ke Bang" : "Ha Noi");
        req.setDestination("Phong Nha - Ke Bang");
        req.setDays(days);
        req.setStyle("ADVENTURE");
        req.setGroupType("FRIENDS");
        req.setOutboundTransport("MIXED");
        req.setLocalTransport("MIXED");
        return req;
    }

    private TripDto.DayResponse normalDay(int dayNumber) {
        return day(dayNumber,
                activity("08:00", "Bao tang Quang Binh", "ATTRACTION", "Dong Hoi", "1 gio", 50_000, null),
                activity("10:00", "Cho Dong Hoi", "ATTRACTION", "Dong Hoi", "1 gio", 0, null),
                activity("12:00", "An chao canh ca loc", "FOOD", "Cho Dong Hoi", "1 gio", 200_000, null));
    }

    private TripDto.DayResponse day(int dayNumber, TripDto.ActivityResponse... activities) {
        TripDto.DayResponse day = new TripDto.DayResponse();
        day.setDay(dayNumber);
        day.setTitle("Ngay " + dayNumber);
        day.setSummary("Lich trinh cu the");
        day.setActivities(List.of(activities));
        return day;
    }

    private TripDto.ActivityResponse activity(
            String time,
            String name,
            String type,
            String location,
            String duration,
            long cost,
            String note) {
        TripDto.ActivityResponse activity = new TripDto.ActivityResponse();
        activity.setTime(time);
        activity.setName(name);
        activity.setType(type);
        activity.setLocation(location);
        activity.setDuration(duration);
        activity.setEstimatedCost(cost);
        activity.setNote(note);
        return activity;
    }
}
