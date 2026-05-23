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
    void itineraryQualityFlagsGenericFoodPlaceholders() throws Exception {
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

        assertThat(quality.passed()).isFalse();
        assertThat(quality.reason()).contains("food/cafe is generic");
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
                .contains("Keep destination-defining outdoor/scenic places in the main plan when generally safe");
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
