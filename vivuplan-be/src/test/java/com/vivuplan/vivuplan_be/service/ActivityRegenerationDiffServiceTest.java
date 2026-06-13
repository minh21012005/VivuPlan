package com.vivuplan.vivuplan_be.service;

import com.vivuplan.vivuplan_be.dto.TripDto;
import com.vivuplan.vivuplan_be.entity.Activity;
import com.vivuplan.vivuplan_be.entity.ItineraryDay;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityRegenerationDiffServiceTest {

    private final ActivityRegenerationDiffService service =
            new ActivityRegenerationDiffService(new ActivityMetadataReconciliationService());

    @Test
    void ignoresFormattingOnlyChangesAndReordering() {
        ItineraryDay oldDay = day(
                activity(1L, "08:00", "Cà phê Đà Lạt", "CAFE", "  Trung tâm Đà Lạt ", "1 giờ", 50_000L, "Ngắm cảnh.", 0),
                activity(2L, "10:00", "Tham quan Dinh Bảo Đại", "ATTRACTION", "Đà Lạt", "90 phút", 80_000L, null, 1));
        TripDto.DayResponse proposed = proposedDay(
                response("10:00", "THAM QUAN DINH BAO DAI", "ATTRACTION", "da lat", "1 giờ 30 phút", 80_000L, "", 0),
                response("08:00", "Ca phe Da Lat", "CAFE", "trung tam da lat", "60 phút", 50_000L, "ngam canh", 1));

        ActivityRegenerationDiffService.DiffResult result = service.diff(oldDay, proposed, "proposal-a");

        assertThat(result.changes()).isEmpty();
        assertThat(result.unchangedActivityCount()).isEqualTo(2);
    }

    @Test
    void matchesByPlaceAndGooglePlaceIdsBeforeText() {
        ItineraryDay oldDay = day(
                activity(1L, "08:00", "Tên cũ", "CAFE", "Địa chỉ cũ", "1 giờ", 50_000L, null, 0),
                activity(2L, "10:00", "Tên khác", "ATTRACTION", "Nơi khác", "1 giờ", 0, null, 1));
        oldDay.getActivities().get(0).setPlace(com.vivuplan.vivuplan_be.entity.Place.builder().id(44L).build());
        oldDay.getActivities().get(1).setGooglePlaceId("google-123");

        TripDto.ActivityResponse byPlace = response("08:30", "Tên mới hoàn toàn", "CAFE", "Địa chỉ mới", "1 giờ", 60_000L, null, 0);
        byPlace.setPlaceId(44L);
        TripDto.ActivityResponse byGoogle = response("10:30", "Một tên mới", "ATTRACTION", "Một nơi mới", "1 giờ", 10_000L, null, 1);
        byGoogle.setGooglePlaceId("google-123");

        ActivityRegenerationDiffService.DiffResult result =
                service.diff(oldDay, proposedDay(byGoogle, byPlace), "proposal-a");

        assertThat(result.changes()).hasSize(2);
        assertThat(result.changes()).allMatch(change -> "MODIFIED".equals(change.getType()));
        assertThat(result.changes()).extracting(TripDto.RegenerateActivityChange::getOldIndex)
                .containsExactlyInAnyOrder(0, 1);
    }

    @Test
    void reportsOnlyMeaningfulChangedFields() {
        ItineraryDay oldDay = day(
                activity(1L, "08:00", "Ăn sáng", "FOOD", "Chợ Đà Lạt", "1 giờ", 50_000L, "Món địa phương", 0));
        oldDay.getActivities().get(0).setPlace(com.vivuplan.vivuplan_be.entity.Place.builder().id(55L).build());
        TripDto.ActivityResponse replacement =
                response("08:30", "Ăn sáng mới", "CAFE", "Hòa Bình", "90 phút", 70_000L, "Thêm cà phê", 0);
        replacement.setPlaceId(55L);
        TripDto.DayResponse proposed = proposedDay(replacement);

        ActivityRegenerationDiffService.DiffResult result = service.diff(oldDay, proposed, "proposal-a");

        assertThat(result.changes()).singleElement().satisfies(change -> {
            assertThat(change.getType()).isEqualTo("MODIFIED");
            assertThat(change.getChangedFields()).containsExactly(
                    "TIME", "NAME", "TYPE", "LOCATION", "DURATION", "COST", "NOTE");
        });
    }

    @Test
    void treatsTiengDurationAsHoursWhenComparingActivities() {
        ItineraryDay oldDay = day(
                activity(1L, "08:00", "Breakfast", "FOOD", "Market", "1 tieng 30 phut", 50_000L, null, 0));
        TripDto.DayResponse proposed = proposedDay(
                response("08:00", "Breakfast", "FOOD", "Market", "90 phut", 50_000L, null, 0));

        ActivityRegenerationDiffService.DiffResult result = service.diff(oldDay, proposed, "proposal-duration");

        assertThat(result.changes()).isEmpty();
        assertThat(result.unchangedActivityCount()).isEqualTo(1);
    }

    @Test
    void everyUserFacingFieldIndividuallyCreatesModifiedChange() {
        assertSingleFieldChange("TIME", activity -> activity.setTime("08:30"));
        assertSingleFieldChange("NAME", activity -> activity.setName("Bữa sáng địa phương"));
        assertSingleFieldChange("TYPE", activity -> activity.setType("CAFE"));
        assertSingleFieldChange("LOCATION", activity -> activity.setLocation("Chợ trung tâm"));
        assertSingleFieldChange("DURATION", activity -> activity.setDuration("90 phút"));
        assertSingleFieldChange("COST", activity -> activity.setEstimatedCost(75_000L));
        assertSingleFieldChange("NOTE", activity -> activity.setNote("Ưu tiên món ít cay"));
    }

    @Test
    void createsAddedAndRemovedChangesWithoutDoubleMatching() {
        ItineraryDay oldDay = day(
                activity(1L, "08:00", "Ăn sáng", "FOOD", "Chợ", "1 giờ", 50_000L, null, 0),
                activity(2L, "10:00", "Bảo tàng", "ATTRACTION", "Trung tâm", "1 giờ", 0, null, 1));
        TripDto.DayResponse proposed = proposedDay(
                response("08:00", "Ăn sáng", "FOOD", "Chợ", "1 giờ", 50_000L, null, 0),
                response("14:00", "Vườn hoa", "ATTRACTION", "Phường 8", "2 giờ", 100_000L, null, 1));

        ActivityRegenerationDiffService.DiffResult result = service.diff(oldDay, proposed, "proposal-a");

        assertThat(result.unchangedActivityCount()).isEqualTo(1);
        assertThat(result.changes()).extracting(TripDto.RegenerateActivityChange::getType)
                .containsExactlyInAnyOrder("ADDED", "REMOVED");
    }

    @Test
    void treatsSameSlotDestinationExperienceAsModifiedInsteadOfRemoveAndAdd() {
        ItineraryDay oldDay = day(
                activity(
                        1L,
                        "14:00",
                        "Tham quan Ban Lac va Ban Pom Coong",
                        "ATTRACTION",
                        "Ban Lac va Ban Pom Coong, Mai Chau",
                        "2 gio 30 phut",
                        20_000L,
                        null,
                        0));
        TripDto.DayResponse proposed = proposedDay(
                response(
                        "14:00",
                        "Dap xe kham pha Ban Lac va Ban Pom Coong",
                        "ACTIVITY",
                        "Ban Lac va Ban Pom Coong, Mai Chau",
                        "2 gio 30 phut",
                        0,
                        null,
                        0));

        ActivityRegenerationDiffService.DiffResult result = service.diff(oldDay, proposed, "proposal-a");

        assertThat(result.changes()).singleElement().satisfies(change -> {
            assertThat(change.getType()).isEqualTo("MODIFIED");
            assertThat(change.getChangedFields()).containsExactly("NAME", "TYPE", "COST");
        });
    }

    @Test
    void authoritativeReferenceTreatsACompletelyDifferentActivityAsReplacement() {
        ItineraryDay oldDay = day(
                activity(
                        1L,
                        "09:00",
                        "Visit the city museum",
                        "ATTRACTION",
                        "Old quarter",
                        "2 hours",
                        80_000L,
                        "Learn about local history.",
                        0));
        TripDto.DayResponse proposed = proposedDay(
                response(
                        "09:30",
                        "Kayak on the river",
                        "ACTIVITY",
                        "Riverside pier",
                        "3 hours",
                        350_000L,
                        "Join a guided kayaking session.",
                        0));

        ActivityRegenerationDiffService.DiffResult result =
                service.diff(oldDay, proposed, "proposal-reference", Map.of(0, 0));

        assertThat(result.changes()).singleElement().satisfies(change -> {
            assertThat(change.getType()).isEqualTo("MODIFIED");
            assertThat(change.getOldActivity().getName()).isEqualTo("Visit the city museum");
            assertThat(change.getNewActivity().getName()).isEqualTo("Kayak on the river");
        });
        assertThat(result.diagnostics().referenceMatches()).isEqualTo(1);
        assertThat(result.diagnostics().semanticMatches()).isZero();
    }

    @Test
    void authoritativeReferenceSupportsPrimarySuccessorInSplit() {
        ItineraryDay oldDay = day(
                activity(
                        1L,
                        "14:00",
                        "Explore Ban Lac",
                        "ATTRACTION",
                        "Mai Chau",
                        "3 hours",
                        20_000L,
                        null,
                        0));
        TripDto.DayResponse proposed = proposedDay(
                response(
                        "14:00",
                        "Cycle through Ban Lac",
                        "ACTIVITY",
                        "Mai Chau",
                        "2 hours",
                        100_000L,
                        null,
                        0),
                response(
                        "16:15",
                        "Tea with a local family",
                        "ACTIVITY",
                        "Ban Lac, Mai Chau",
                        "45 minutes",
                        50_000L,
                        null,
                        1));

        ActivityRegenerationDiffService.DiffResult result =
                service.diff(oldDay, proposed, "proposal-split", Map.of(0, 0));

        assertThat(result.changes()).extracting(TripDto.RegenerateActivityChange::getType)
                .containsExactlyInAnyOrder("MODIFIED", "ADDED");
        assertThat(result.changes()).filteredOn(change -> "MODIFIED".equals(change.getType()))
                .singleElement()
                .satisfies(change -> assertThat(change.getNewIndex()).isZero());
        assertThat(result.changes()).filteredOn(change -> "ADDED".equals(change.getType()))
                .singleElement()
                .satisfies(change -> assertThat(change.getNewIndex()).isEqualTo(1));
    }

    @Test
    void authoritativeReferenceSupportsPrimaryPredecessorInMerge() {
        ItineraryDay oldDay = day(
                activity(
                        1L, "09:00", "Visit the craft village", "ATTRACTION",
                        "Village center", "90 minutes", 50_000L, null, 0),
                activity(
                        2L, "10:45", "Meet a local artisan", "ACTIVITY",
                        "Village workshop", "60 minutes", 100_000L, null, 1));
        TripDto.DayResponse proposed = proposedDay(
                response(
                        "09:00", "Craft village tour with an artisan", "ACTIVITY",
                        "Village center and workshop", "3 hours", 180_000L, null, 0));

        ActivityRegenerationDiffService.DiffResult result =
                service.diff(oldDay, proposed, "proposal-merge", Map.of(0, 0));

        assertThat(result.changes()).extracting(TripDto.RegenerateActivityChange::getType)
                .containsExactlyInAnyOrder("MODIFIED", "REMOVED");
        assertThat(result.changes()).filteredOn(change -> "MODIFIED".equals(change.getType()))
                .singleElement()
                .satisfies(change -> {
                    assertThat(change.getOldIndex()).isZero();
                    assertThat(change.getNewIndex()).isZero();
                });
        assertThat(result.changes()).filteredOn(change -> "REMOVED".equals(change.getType()))
                .singleElement()
                .satisfies(change -> assertThat(change.getOldIndex()).isEqualTo(1));
    }

    @Test
    void matchesBanLacReplacementWhenTheNewLocationIsDescribedMoreBroadly() {
        ItineraryDay oldDay = day(
                activity(
                        1L,
                        "14:00",
                        "Tham quan Ban Lac va Ban Pom Coong",
                        "ATTRACTION",
                        "Ban Lac va Ban Pom Coong, Mai Chau",
                        "2 gio 30 phut",
                        20_000L,
                        "Di bo hoac di xe may kham pha hai ban lang.",
                        0));
        TripDto.DayResponse proposed = proposedDay(
                response(
                        "14:00",
                        "Dap xe kham pha Ban Lac, Ban Pom Coong, Ban Van",
                        "ACTIVITY",
                        "Cac ban lang tai thung lung Mai Chau",
                        "2 gio",
                        100_000L,
                        "Thue xe dap de dao quanh cac ban.",
                        0));

        ActivityRegenerationDiffService.DiffResult result =
                service.diff(oldDay, proposed, "proposal-ban-lac");

        assertThat(result.changes()).singleElement().satisfies(change -> {
            assertThat(change.getType()).isEqualTo("MODIFIED");
            assertThat(change.getOldActivity().getName()).contains("Ban Lac");
            assertThat(change.getNewActivity().getName()).contains("Ban Lac");
        });
    }

    @Test
    void matchesHangChieuReplacementAcrossActivityAndAttractionTypes() {
        ItineraryDay oldDay = day(
                activity(
                        1L,
                        "16:30",
                        "Chinh phuc Hang Chieu",
                        "ACTIVITY",
                        "Hang Chieu, Mai Chau",
                        "1 gio 30 phut",
                        60_000L,
                        "Leo bo hon 1000 bac thang.",
                        0));
        TripDto.DayResponse proposed = proposedDay(
                response(
                        "16:00",
                        "Kham pha Hang Chieu",
                        "ATTRACTION",
                        "Hang Chieu, Mai Chau, Hoa Binh",
                        "1 gio 30 phut",
                        60_000L,
                        "Can leo khoang 1200 bac thang.",
                        0));

        ActivityRegenerationDiffService.DiffResult result =
                service.diff(oldDay, proposed, "proposal-hang-chieu");

        assertThat(result.changes()).singleElement().satisfies(change -> {
            assertThat(change.getType()).isEqualTo("MODIFIED");
            assertThat(change.getChangedFields()).contains("TIME", "NAME", "TYPE", "LOCATION", "NOTE");
        });
    }

    @Test
    void doesNotMatchUnrelatedActivitiesMerelyBecauseTheyShareTimeAndArea() {
        ItineraryDay oldDay = day(
                activity(
                        1L,
                        "14:00",
                        "Tham quan bao tang dan toc",
                        "ATTRACTION",
                        "Trung tam Mai Chau",
                        "2 gio",
                        50_000L,
                        null,
                        0));
        TripDto.DayResponse proposed = proposedDay(
                response(
                        "14:00",
                        "Thuong thuc ca phe san may",
                        "CAFE",
                        "Trung tam Mai Chau",
                        "2 gio",
                        80_000L,
                        null,
                        0));

        ActivityRegenerationDiffService.DiffResult result =
                service.diff(oldDay, proposed, "proposal-unrelated");

        assertThat(result.changes()).extracting(TripDto.RegenerateActivityChange::getType)
                .containsExactlyInAnyOrder("ADDED", "REMOVED");
    }

    @Test
    void treatsSameMealSlotWithExpandedLocationAsModified() {
        ItineraryDay oldDay = day(
                activity(
                        1L,
                        "12:30",
                        "Bua trua tai nha hang gan Dong Thien Duong",
                        "FOOD",
                        "Khu vuc gan Dong Thien Duong",
                        "1 gio 30 phut",
                        800_000L,
                        "Nghi ngoi va thuong thuc bua trua sau khi kham pha hang dong.",
                        0));
        TripDto.DayResponse proposed = proposedDay(
                response(
                        "12:30",
                        "Bua trua voi cac mon dac san ga nuong, com nieu",
                        "FOOD",
                        "Khu vuc nha hang gan Dong Thien Duong, Phong Nha - Ke Bang",
                        "1 gio 30 phut",
                        700_000L,
                        "Thuong thuc am thuc dan da, dac trung cua vung Quang Binh sau khi kham pha hang dong.",
                        0));

        ActivityRegenerationDiffService.DiffResult result = service.diff(oldDay, proposed, "proposal-a");

        assertThat(result.changes()).singleElement().satisfies(change -> {
            assertThat(change.getType()).isEqualTo("MODIFIED");
            assertThat(change.getChangedFields()).contains("NAME", "LOCATION", "COST", "NOTE");
        });
    }

    @Test
    void exposesTransportDetailChangesAsUserFacingModification() {
        ItineraryDay oldDay = day(
                activity(
                        1L,
                        "08:30",
                        "Thue xe o to rieng di Dong Thien Duong va Song Chay - Hang Toi",
                        "TRANSPORT",
                        "Thi tran Phong Nha",
                        "8 gio",
                        2_000_000L,
                        "Thue xe o to rieng co tai xe de di chuyen thuan tien cho ca gia dinh trong ngay.",
                        0));
        TripDto.DayResponse proposed = proposedDay(
                response(
                        "08:30",
                        "Thue xe o to rieng di Dong Thien Duong va Song Chay - Hang Toi",
                        "TRANSPORT",
                        "Thi tran Phong Nha, Bo Trach, Quang Binh",
                        "30 phut",
                        1_200_000L,
                        "Xe o to rieng co tai xe phuc vu ca ngay tham quan cac diem den chinh.",
                        0));

        ActivityRegenerationDiffService.DiffResult result = service.diff(oldDay, proposed, "proposal-a");

        assertThat(result.changes()).singleElement().satisfies(change -> {
            assertThat(change.getType()).isEqualTo("MODIFIED");
            assertThat(change.getChangedFields()).containsExactly("LOCATION", "DURATION", "COST", "NOTE");
        });
        assertThat(result.unchangedActivityCount()).isZero();
    }

    @Test
    void exposesBreakfastCostAndNoteChangesAsUserFacingModification() {
        ItineraryDay oldDay = day(
                activity(
                        1L,
                        "07:30",
                        "Bua sang tai homestay/khach san",
                        "FOOD",
                        "Thi tran Phong Nha, Bo Trach, Quang Binh",
                        "1 gio",
                        200_000L,
                        "Thuong thuc bua sang nhe nhang de chuan bi cho mot ngay dai.",
                        0));
        TripDto.DayResponse proposed = proposedDay(
                response(
                        "07:30",
                        "Bua sang tai homestay/khach san",
                        "FOOD",
                        "Thi tran Phong Nha, Bo Trach, Quang Binh",
                        "1 gio",
                        0,
                        "Thuong thuc bua sang tai noi luu tru de nap nang luong cho ngay moi.",
                        0));

        ActivityRegenerationDiffService.DiffResult result = service.diff(oldDay, proposed, "proposal-a");

        assertThat(result.changes()).singleElement().satisfies(change -> {
            assertThat(change.getType()).isEqualTo("MODIFIED");
            assertThat(change.getChangedFields()).containsExactly("COST", "NOTE");
        });
        assertThat(result.unchangedActivityCount()).isZero();
    }

    @Test
    void exposesCostDurationAndNoteChangesAsUserFacingModification() {
        ItineraryDay oldDay = day(
                activity(
                        1L,
                        "14:00",
                        "Trai nghiem Song Chay - Hang Toi",
                        "ACTIVITY",
                        "Bo Trach, Quang Binh",
                        "3 gio",
                        2_000_000L,
                        "Tham gia cac hoat dong zipline, cheo thuyen kayak va tam bun khoang.",
                        0));
        TripDto.DayResponse proposed = proposedDay(
                response(
                        "14:00",
                        "Trai nghiem Song Chay - Hang Toi",
                        "ACTIVITY",
                        "Bo Trach, Quang Binh",
                        "4 gio",
                        2_200_000L,
                        "Tham gia cac hoat dong mao hiem va vui choi tai Song Chay - Hang Toi nhu zipline, cheo thuyen kayak va tam bun khoang tu nhien.",
                        0));

        ActivityRegenerationDiffService.DiffResult result = service.diff(oldDay, proposed, "proposal-a");

        assertThat(result.changes()).singleElement().satisfies(change -> {
            assertThat(change.getType()).isEqualTo("MODIFIED");
            assertThat(change.getChangedFields()).containsExactly("DURATION", "COST", "NOTE");
        });
        assertThat(result.unchangedActivityCount()).isZero();
    }

    @Test
    void partialSelectionKeepsEveryUnselectedUserFacingChange() {
        ItineraryDay oldDay = day(
                activity(
                        1L,
                        "07:30",
                        "Breakfast at hotel",
                        "FOOD",
                        "Phong Nha town",
                        "1 hour",
                        200_000L,
                        "Light breakfast before a long day.",
                        0),
                activity(
                        2L,
                        "20:00",
                        "Walk around central Phong Nha",
                        "NIGHTLIFE",
                        "Phong Nha town",
                        "1 hour",
                        0,
                        "Explore souvenir shops or snack stalls.",
                        1));
        TripDto.DayResponse proposed = proposedDay(
                response(
                        "07:30",
                        "Breakfast at hotel",
                        "FOOD",
                        "Phong Nha town",
                        "1 hour",
                        0,
                        "Have breakfast at the accommodation.",
                        0),
                response(
                        "20:00",
                        "Relax at riverside cafe",
                        "NIGHTLIFE",
                        "Son river bank, Phong Nha town",
                        "1 hour 30 minutes",
                        200_000L,
                        "Enjoy a peaceful local cafe by the river.",
                        1));

        ActivityRegenerationDiffService.DiffResult diff =
                service.diff(oldDay, proposed, "proposal-a", Map.of(0, 0, 1, 1));

        assertThat(diff.changes()).hasSize(2);
        String cafeChangeId = diff.changes().stream()
                .filter(change -> change.getNewActivity() != null
                        && "Relax at riverside cafe".equals(change.getNewActivity().getName()))
                .findFirst()
                .orElseThrow()
                .getChangeId();

        TripDto.DayResponse merged = service.merge(
                oldDay,
                proposed,
                diff.changes(),
                Set.of(cafeChangeId),
                diff.metadataPatches(),
                false);

        assertThat(merged.getActivities()).extracting(TripDto.ActivityResponse::getName)
                .containsExactly("Breakfast at hotel", "Relax at riverside cafe");
        assertThat(merged.getActivities().get(0).getEstimatedCost()).isEqualTo(200_000L);
        assertThat(merged.getActivities().get(0).getNote()).isEqualTo("Light breakfast before a long day.");
    }

    @Test
    void duplicatePlaceIdsUseTieBreakerInsteadOfCreatingFalseDiffsOnReorder() {
        ItineraryDay oldDay = day(
                activity(1L, "08:00", "Breakfast at resort", "FOOD", "Resort", "1 hour", 50_000L, null, 0),
                activity(2L, "19:00", "Dinner at resort", "FOOD", "Resort", "1 hour", 100_000L, null, 1));
        oldDay.getActivities().forEach(activity ->
                activity.setPlace(com.vivuplan.vivuplan_be.entity.Place.builder().id(44L).build()));

        TripDto.ActivityResponse dinner = response("19:00", "Dinner at resort", "FOOD", "Resort", "1 hour", 100_000L, null, 0);
        dinner.setPlaceId(44L);
        TripDto.ActivityResponse breakfast = response("08:00", "Breakfast at resort", "FOOD", "Resort", "1 hour", 50_000L, null, 1);
        breakfast.setPlaceId(44L);

        ActivityRegenerationDiffService.DiffResult result =
                service.diff(oldDay, proposedDay(dinner, breakfast), "proposal-a");

        assertThat(result.changes()).isEmpty();
        assertThat(result.unchangedActivityCount()).isEqualTo(2);
    }

    @Test
    void treatsGooglePlaceIdAsOpaqueExactIdentifier() {
        ItineraryDay oldDay = day(
                activity(1L, "08:00", "Old breakfast", "FOOD", "Old venue", "1 hour", 50_000L, null, 0));
        oldDay.getActivities().get(0).setGooglePlaceId("ChIJAbC_123");

        TripDto.ActivityResponse proposed = response(
                "14:00",
                "New garden walk",
                "ATTRACTION",
                "New park",
                "2 hours",
                70_000L,
                null,
                0);
        proposed.setGooglePlaceId("chijabc_123");

        ActivityRegenerationDiffService.DiffResult result =
                service.diff(oldDay, proposedDay(proposed), "proposal-a");

        assertThat(result.changes()).extracting(TripDto.RegenerateActivityChange::getType)
                .containsExactlyInAnyOrder("ADDED", "REMOVED");
    }

    @Test
    void ignoresMetadataOnlyChanges() {
        ItineraryDay oldDay = day(
                activity(1L, "08:00", "Breakfast", "FOOD", "Market", "1 hour", 50_000L, "Try local food", 0));
        oldDay.getActivities().get(0).setRating(4.2);
        oldDay.getActivities().get(0).setLatitude(10.1);
        oldDay.getActivities().get(0).setLongitude(106.1);
        oldDay.getActivities().get(0).setCoordinateSource(Activity.CoordinateSource.GEOCODED_LOCATION);
        oldDay.getActivities().get(0).setCoordinateConfidence(Activity.CoordinateConfidence.LOW);

        TripDto.ActivityResponse proposed = response(
                "08:00",
                "Breakfast",
                "FOOD",
                "Market",
                "1 hour",
                50_000L,
                "Try local food",
                0);
        proposed.setRating(4.9);
        proposed.setLatitude(10.2);
        proposed.setLongitude(106.2);
        proposed.setPlaceId(99L);
        proposed.setGooglePlaceId("different-google-id");
        proposed.setCoordinateSource("VERIFIED_PLACE");
        proposed.setCoordinateConfidence("HIGH");

        ActivityRegenerationDiffService.DiffResult result =
                service.diff(oldDay, proposedDay(proposed), "proposal-a");

        assertThat(result.changes()).isEmpty();
        assertThat(result.unchangedActivityCount()).isEqualTo(1);
        assertThat(result.metadataUpgradeCount()).isEqualTo(1);
        assertThat(result.unchangedActivities()).singleElement()
                .satisfies(activity -> assertThat(activity.isMetadataUpgradeAvailable()).isTrue());
    }

    @Test
    void fuzzyMatchingIsOneToOne() {
        ItineraryDay oldDay = day(
                activity(1L, "08:00", "Ăn sáng bánh căn", "FOOD", "Chợ Đà Lạt", "1 giờ", 50_000L, null, 0),
                activity(2L, "09:00", "Ăn sáng bánh mì", "FOOD", "Chợ Đà Lạt", "1 giờ", 40_000L, null, 1));
        TripDto.DayResponse proposed = proposedDay(
                response("08:15", "Thưởng thức bánh căn", "FOOD", "Chợ Đà Lạt", "1 giờ", 60_000L, null, 0));

        ActivityRegenerationDiffService.DiffResult result = service.diff(oldDay, proposed, "proposal-a");

        assertThat(result.changes()).hasSize(2);
        assertThat(result.changes()).extracting(TripDto.RegenerateActivityChange::getType)
                .containsExactlyInAnyOrder("MODIFIED", "REMOVED");
    }

    @Test
    void ambiguousSemanticCandidatesAreNotForcedIntoACompleteMatching() {
        ItineraryDay oldDay = day(
                activity(
                        1L, "08:00", "Coffee river garden", "CAFE",
                        "River center", "1 hour", 50_000L, null, 0),
                activity(
                        2L, "08:00", "Coffee", "CAFE",
                        "River", "1 hour", 40_000L, null, 1));
        TripDto.DayResponse proposed = proposedDay(
                response(
                        "08:00", "Coffee river garden experience", "CAFE",
                        "River center", "1 hour", 60_000L, null, 0),
                response(
                        "08:00", "Garden", "CAFE",
                        "Center", "1 hour", 30_000L, null, 1));

        ActivityRegenerationDiffService.DiffResult result =
                service.diff(oldDay, proposed, "proposal-a");

        assertThat(result.changes()).extracting(TripDto.RegenerateActivityChange::getType)
                .containsExactlyInAnyOrder("MODIFIED", "REMOVED", "ADDED");
        assertThat(result.diagnostics().ambiguousPairs()).isGreaterThan(0);
    }

    @Test
    void equalFoodCandidatesRemainAmbiguousInsteadOfBeingMatchedByTimeAndType() {
        ItineraryDay oldDay = day(
                activity(
                        1L, "07:30", "Breakfast at hotel", "FOOD",
                        "Hotel restaurant", "1 hour", 100_000L, null, 0),
                activity(
                        2L, "07:30", "Breakfast at hotel", "FOOD",
                        "Hotel restaurant", "1 hour", 120_000L, null, 1));
        TripDto.DayResponse proposed = proposedDay(
                response(
                        "07:30", "Local breakfast at hotel", "FOOD",
                        "Hotel restaurant", "1 hour", 150_000L, null, 0));

        ActivityRegenerationDiffService.DiffResult result =
                service.diff(oldDay, proposed, "proposal-ambiguous-food");

        assertThat(result.changes()).extracting(TripDto.RegenerateActivityChange::getType)
                .containsExactlyInAnyOrder("REMOVED", "REMOVED", "ADDED");
        assertThat(result.diagnostics().semanticMatches()).isZero();
        assertThat(result.diagnostics().ambiguousPairs()).isEqualTo(2);
    }

    @Test
    void semanticFallbackUsesLanguageIndependentNgrams() {
        ItineraryDay vietnameseDay = day(
                activity(
                        1L, "16:30", "Chinh phuc Hang Chieu", "ACTIVITY",
                        "Hang Chieu, Mai Chau", "90 phut", 60_000L, null, 0));
        TripDto.DayResponse vietnameseProposal = proposedDay(
                response(
                        "16:00", "Kham pha Hang Chieu", "ATTRACTION",
                        "Hang Chieu, Mai Chau, Hoa Binh", "1 gio 30 phut", 60_000L, null, 0));

        ItineraryDay englishDay = day(
                activity(
                        2L, "10:00", "Explore riverside market", "ATTRACTION",
                        "Old riverside district", "2 hours", 0, null, 0));
        TripDto.DayResponse englishProposal = proposedDay(
                response(
                        "10:15", "Riverside market walking tour", "ACTIVITY",
                        "Old riverside district", "2 hours", 50_000L, null, 0));

        assertThat(service.diff(
                        vietnameseDay,
                        vietnameseProposal,
                        "proposal-ngram-vi").changes())
                .singleElement()
                .satisfies(change -> assertThat(change.getType()).isEqualTo("MODIFIED"));
        assertThat(service.diff(
                        englishDay,
                        englishProposal,
                        "proposal-ngram-en").changes())
                .singleElement()
                .satisfies(change -> assertThat(change.getType()).isEqualTo("MODIFIED"));
    }

    @Test
    void selectingAllChangesProducesExactlyTheProposedActivitiesIncludingRemoval() {
        ItineraryDay oldDay = day(
                activity(1L, "08:00", "Ăn sáng", "FOOD", "Chợ", "1 giờ", 50_000L, null, 0),
                activity(2L, "10:00", "Bảo tàng", "ATTRACTION", "Trung tâm", "1 giờ", 0, null, 1),
                activity(3L, "16:00", "Cà phê", "CAFE", "Hồ Xuân Hương", "1 giờ", 60_000L, null, 2));
        TripDto.DayResponse proposed = proposedDay(
                response("08:30", "Ăn sáng", "FOOD", "Chợ", "1 giờ", 50_000L, null, 0),
                response("14:00", "Vườn hoa", "ATTRACTION", "Phường 8", "2 giờ", 100_000L, null, 1));
        ActivityRegenerationDiffService.DiffResult diff = service.diff(oldDay, proposed, "proposal-a");
        Set<String> allIds = diff.changes().stream()
                .map(TripDto.RegenerateActivityChange::getChangeId)
                .collect(java.util.stream.Collectors.toSet());

        TripDto.DayResponse merged = service.merge(
                oldDay,
                proposed,
                diff.changes(),
                allIds,
                diff.metadataPatches(),
                false);

        assertThat(merged.getTitle()).isEqualTo(proposed.getTitle());
        assertThat(merged.getActivities()).extracting(TripDto.ActivityResponse::getName)
                .containsExactly("Ăn sáng", "Vườn hoa");
    }

    @Test
    void partialMergeKeepsUnselectedRemovalAndOldTitle() {
        ItineraryDay oldDay = day(
                activity(1L, "08:00", "Ăn sáng", "FOOD", "Chợ", "1 giờ", 50_000L, null, 0),
                activity(2L, "10:00", "Bảo tàng", "ATTRACTION", "Trung tâm", "1 giờ", 0, null, 1));
        TripDto.DayResponse proposed = proposedDay(
                response("08:30", "Ăn sáng mới", "FOOD", "Chợ", "1 giờ", 60_000L, null, 0));
        ActivityRegenerationDiffService.DiffResult diff = service.diff(oldDay, proposed, "proposal-a");
        String modifiedId = diff.changes().stream()
                .filter(change -> "MODIFIED".equals(change.getType()))
                .findFirst()
                .orElseThrow()
                .getChangeId();

        TripDto.DayResponse merged = service.merge(
                oldDay,
                proposed,
                diff.changes(),
                Set.of(modifiedId),
                diff.metadataPatches(),
                false);

        assertThat(merged.getTitle()).isEqualTo(oldDay.getTitle());
        assertThat(merged.getActivities()).extracting(TripDto.ActivityResponse::getName)
                .containsExactly("Ăn sáng mới", "Bảo tàng");
    }

    @Test
    void appliesTrustedMetadataPatchOnlyWhenRequested() {
        ItineraryDay oldDay = day(
                activity(1L, "08:00", "Breakfast", "FOOD", "Market", "1 hour", 50_000L, null, 0));
        oldDay.getActivities().get(0).setLatitude(10.1);
        oldDay.getActivities().get(0).setLongitude(106.1);
        oldDay.getActivities().get(0).setCoordinateSource(Activity.CoordinateSource.AI_PROVIDED);
        oldDay.getActivities().get(0).setCoordinateConfidence(Activity.CoordinateConfidence.LOW);

        TripDto.ActivityResponse proposed = response(
                "08:00", "Breakfast", "FOOD", "Market", "1 hour", 50_000L, null, 0);
        proposed.setPlaceId(99L);
        proposed.setLatitude(10.2);
        proposed.setLongitude(106.2);
        proposed.setCoordinateSource("VERIFIED_PLACE");
        proposed.setCoordinateConfidence("HIGH");
        proposed.setRating(4.8);
        TripDto.DayResponse proposedDay = proposedDay(proposed);
        ActivityRegenerationDiffService.DiffResult diff = service.diff(oldDay, proposedDay, "proposal-a");

        TripDto.DayResponse withoutUpgrade = service.merge(
                oldDay, proposedDay, diff.changes(), Set.of(), diff.metadataPatches(), false);
        TripDto.DayResponse withUpgrade = service.merge(
                oldDay, proposedDay, diff.changes(), Set.of(), diff.metadataPatches(), true);

        assertThat(withoutUpgrade.getActivities().get(0).getLatitude()).isEqualTo(10.1);
        assertThat(withUpgrade.getActivities().get(0)).satisfies(activity -> {
            assertThat(activity.getLatitude()).isEqualTo(10.2);
            assertThat(activity.getLongitude()).isEqualTo(106.2);
            assertThat(activity.getCoordinateSource()).isEqualTo("VERIFIED_PLACE");
            assertThat(activity.getPlaceId()).isEqualTo(99L);
            assertThat(activity.getRating()).isEqualTo(4.8);
        });
        assertThat(withUpgrade.getTitle()).isEqualTo(oldDay.getTitle());
    }

    @Test
    void metadataOnlySelectionDoesNotApplyActionableChanges() {
        ItineraryDay oldDay = day(
                activity(1L, "08:00", "Breakfast", "FOOD", "Market", "1 hour", 50_000L, null, 0),
                activity(2L, "10:00", "Museum", "ATTRACTION", "Center", "1 hour", 0, null, 1));
        oldDay.getActivities().get(0).setLatitude(10.1);
        oldDay.getActivities().get(0).setLongitude(106.1);
        oldDay.getActivities().get(0).setCoordinateSource(Activity.CoordinateSource.AI_PROVIDED);
        oldDay.getActivities().get(0).setCoordinateConfidence(Activity.CoordinateConfidence.LOW);

        TripDto.ActivityResponse breakfast = response(
                "08:00", "Breakfast", "FOOD", "Market", "1 hour", 50_000L, null, 0);
        breakfast.setPlaceId(99L);
        breakfast.setLatitude(10.2);
        breakfast.setLongitude(106.2);
        breakfast.setCoordinateSource("VERIFIED_PLACE");
        breakfast.setCoordinateConfidence("HIGH");
        TripDto.ActivityResponse museum = response(
                "10:30", "New museum tour", "ATTRACTION", "Center", "2 hours", 80_000L, null, 1);
        TripDto.DayResponse proposed = proposedDay(breakfast, museum);
        ActivityRegenerationDiffService.DiffResult diff = service.diff(oldDay, proposed, "proposal-a");

        TripDto.DayResponse merged = service.merge(
                oldDay, proposed, diff.changes(), Set.of(), diff.metadataPatches(), true);

        assertThat(merged.getActivities()).extracting(TripDto.ActivityResponse::getName)
                .containsExactly("Breakfast", "Museum");
        assertThat(merged.getActivities().get(0).getLatitude()).isEqualTo(10.2);
        assertThat(merged.getTitle()).isEqualTo(oldDay.getTitle());
    }

    @Test
    void neverOverwritesManualCoordinatesWithMetadataPatch() {
        ItineraryDay oldDay = day(
                activity(1L, "08:00", "Breakfast", "FOOD", "Market", "1 hour", 50_000L, null, 0));
        oldDay.getActivities().get(0).setLatitude(10.1);
        oldDay.getActivities().get(0).setLongitude(106.1);
        oldDay.getActivities().get(0).setCoordinateSource(Activity.CoordinateSource.MANUAL);
        oldDay.getActivities().get(0).setCoordinateConfidence(Activity.CoordinateConfidence.HIGH);

        TripDto.ActivityResponse proposed = response(
                "08:00", "Breakfast", "FOOD", "Market", "1 hour", 50_000L, null, 0);
        proposed.setPlaceId(99L);
        proposed.setLatitude(10.2);
        proposed.setLongitude(106.2);
        proposed.setCoordinateSource("VERIFIED_PLACE");
        proposed.setCoordinateConfidence("HIGH");

        ActivityRegenerationDiffService.DiffResult diff =
                service.diff(oldDay, proposedDay(proposed), "proposal-a");

        assertThat(diff.metadataPatches()).singleElement().satisfies(patch -> {
            assertThat(patch.upgradedActivity().getLatitude()).isEqualTo(10.1);
            assertThat(patch.upgradedActivity().getLongitude()).isEqualTo(106.1);
            assertThat(patch.upgradedActivity().getCoordinateSource()).isEqualTo("MANUAL");
        });
    }

    @Test
    void rejectsMetadataPatchWhenVerifiedPlaceIdentifiersConflict() {
        ItineraryDay oldDay = day(
                activity(1L, "08:00", "Breakfast", "FOOD", "Market", "1 hour", 50_000L, null, 0));
        oldDay.getActivities().get(0).setPlace(
                com.vivuplan.vivuplan_be.entity.Place.builder().id(10L).build());
        oldDay.getActivities().get(0).setGooglePlaceId("verified-old");
        oldDay.getActivities().get(0).setLatitude(10.1);
        oldDay.getActivities().get(0).setLongitude(106.1);
        oldDay.getActivities().get(0).setCoordinateSource(Activity.CoordinateSource.VERIFIED_PLACE);
        oldDay.getActivities().get(0).setCoordinateConfidence(Activity.CoordinateConfidence.HIGH);

        TripDto.ActivityResponse proposed = response(
                "08:00", "Breakfast", "FOOD", "Market", "1 hour", 50_000L, null, 0);
        proposed.setPlaceId(11L);
        proposed.setGooglePlaceId("verified-new");
        proposed.setLatitude(10.2);
        proposed.setLongitude(106.2);
        proposed.setCoordinateSource("VERIFIED_PLACE");
        proposed.setCoordinateConfidence("HIGH");

        ActivityRegenerationDiffService.DiffResult diff =
                service.diff(oldDay, proposedDay(proposed), "proposal-a");

        assertThat(diff.metadataPatches()).isEmpty();
    }

    @Test
    void fingerprintChangesWhenTheCurrentDayIsEdited() {
        ItineraryDay day = day(
                activity(1L, "08:00", "Ăn sáng", "FOOD", "Chợ", "1 giờ", 50_000L, null, 0));
        String before = service.fingerprint(day);

        day.getActivities().get(0).setEstimatedCost(70_000L);

        assertThat(service.fingerprint(day)).isNotEqualTo(before);
    }

    @Test
    void fingerprintChangesForFormattingOnlyManualEdits() {
        ItineraryDay day = day(
                activity(1L, "08:00", "Breakfast", "FOOD", "Market", "1 hour", 50_000L, null, 0));
        String before = service.fingerprint(day);

        day.getActivities().get(0).setName("BREAKFAST!");

        assertThat(service.fingerprint(day)).isNotEqualTo(before);
    }

    private void assertSingleFieldChange(
            String expectedField,
            Consumer<TripDto.ActivityResponse> mutation) {
        ItineraryDay oldDay = day(
                activity(1L, "08:00", "Bữa sáng", "FOOD", "Chợ", "1 giờ", 50_000L, null, 0));
        oldDay.getActivities().get(0).setPlace(
                com.vivuplan.vivuplan_be.entity.Place.builder().id(55L).build());
        TripDto.ActivityResponse proposed =
                response("08:00", "Bữa sáng", "FOOD", "Chợ", "60 phút", 50_000L, null, 0);
        proposed.setPlaceId(55L);
        mutation.accept(proposed);

        ActivityRegenerationDiffService.DiffResult result =
                service.diff(oldDay, proposedDay(proposed), "proposal-" + expectedField);

        assertThat(result.changes()).singleElement().satisfies(change -> {
            assertThat(change.getType()).isEqualTo("MODIFIED");
            assertThat(change.getChangedFields()).containsExactly(expectedField);
        });
    }

    private ItineraryDay day(Activity... activities) {
        ItineraryDay day = ItineraryDay.builder()
                .title("Ngày hiện tại")
                .summary("Tóm tắt cũ")
                .activities(new ArrayList<>())
                .build();
        for (Activity activity : activities) {
            activity.setItineraryDay(day);
            day.getActivities().add(activity);
        }
        return day;
    }

    private Activity activity(
            Long id,
            String time,
            String name,
            String type,
            String location,
            String duration,
            long cost,
            String note,
            int sortOrder) {
        return Activity.builder()
                .id(id)
                .time(time)
                .name(name)
                .type(Activity.ActivityType.valueOf(type))
                .location(location)
                .duration(duration)
                .estimatedCost(cost)
                .note(note)
                .sortOrder(sortOrder)
                .build();
    }

    private TripDto.DayResponse proposedDay(TripDto.ActivityResponse... activities) {
        TripDto.DayResponse day = new TripDto.DayResponse();
        day.setDay(1);
        day.setTitle("Ngày AI đề xuất");
        day.setSummary("Tóm tắt mới");
        day.setActivities(List.of(activities));
        return day;
    }

    private TripDto.ActivityResponse response(
            String time,
            String name,
            String type,
            String location,
            String duration,
            long cost,
            String note,
            int sortOrder) {
        TripDto.ActivityResponse activity = new TripDto.ActivityResponse();
        activity.setTime(time);
        activity.setName(name);
        activity.setType(type);
        activity.setLocation(location);
        activity.setDuration(duration);
        activity.setEstimatedCost(cost);
        activity.setNote(note);
        activity.setSortOrder(sortOrder);
        return activity;
    }
}
