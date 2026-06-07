package com.vivuplan.vivuplan_be.service;

final class ItineraryQualityPolicy {

    static final int MIN_ACTIVITIES_DEFAULT = 3;
    static final int MIN_ACTIVITIES_LIGHT_DAY = 2;

    static final int NORMAL_DAY_MIN_DISPLAY_ITEMS = 5;
    static final int NORMAL_DAY_MAX_DISPLAY_ITEMS = 8;
    static final int LIGHT_DAY_MIN_DISPLAY_ITEMS = 3;
    static final int LIGHT_DAY_MAX_DISPLAY_ITEMS = 6;
    static final int DENSE_DAY_MIN_NON_LOGISTICS_ITEMS = 6;
    static final int MAX_TOTAL_ITEMS_PER_DAY = 14;
    static final int MAX_NON_LOGISTICS_ITEMS_PER_DAY = 9;

    private ItineraryQualityPolicy() {
    }

    static String vietnamPacingGuidance() {
        return String.format(
                "Keep each day realistic for Vietnam: normal sightseeing days should have %d-%d display items; "
                        + "first/last travel days and relaxing/family days may have %d-%d items; "
                        + "dense but realistic city/food/adventure days may have %d-%d FOOD/CAFE/ATTRACTION/ACTIVITY/NIGHTLIFE items when distances are close and pacing is believable. "
                        + "Never return more than %d total items in one day or more than %d FOOD/CAFE/ATTRACTION/ACTIVITY/NIGHTLIFE items in one day, and do not pad the itinerary just to hit a count.",
                NORMAL_DAY_MIN_DISPLAY_ITEMS,
                NORMAL_DAY_MAX_DISPLAY_ITEMS,
                LIGHT_DAY_MIN_DISPLAY_ITEMS,
                LIGHT_DAY_MAX_DISPLAY_ITEMS,
                DENSE_DAY_MIN_NON_LOGISTICS_ITEMS,
                MAX_NON_LOGISTICS_ITEMS_PER_DAY,
                MAX_TOTAL_ITEMS_PER_DAY,
                MAX_NON_LOGISTICS_ITEMS_PER_DAY);
    }

    static String localTransportGuidance(String destination) {
        String place = destination == null || destination.isBlank() ? "the destination" : destination;
        return String.format(
                "Add separate TRANSPORT activities only for outbound/return travel, moving between distant clusters, or movement with meaningful cost inside %s. "
                        + "For close walkable places, a clear walking note with cost 0 is enough. "
                        + "Never hide rental, taxi, Grab, paid bicycle, or local transfer costs inside FOOD/CAFE/ATTRACTION notes. "
                        + "If a rented vehicle is used across multiple activities or days, put the total rental fee in one TRANSPORT activity and make later pickup/return notes reference that counted fee.",
                place);
    }

    static boolean exceedsTotalItems(int itemCount) {
        return itemCount > MAX_TOTAL_ITEMS_PER_DAY;
    }

    static boolean exceedsNonLogisticsItems(long itemCount) {
        return itemCount > MAX_NON_LOGISTICS_ITEMS_PER_DAY;
    }
}
