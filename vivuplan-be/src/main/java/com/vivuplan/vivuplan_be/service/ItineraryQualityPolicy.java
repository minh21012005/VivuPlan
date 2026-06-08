package com.vivuplan.vivuplan_be.service;

import com.vivuplan.vivuplan_be.dto.TripDto;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

final class ItineraryQualityPolicy {

    static final int MIN_ACTIVITIES_DEFAULT = 3;
    static final int MIN_ACTIVITIES_LIGHT_DAY = 2;

    static final int NORMAL_DAY_MIN_DISPLAY_ITEMS = 5;
    static final int NORMAL_DAY_MAX_DISPLAY_ITEMS = 10;
    static final int LIGHT_DAY_MIN_DISPLAY_ITEMS = 3;
    static final int LIGHT_DAY_MAX_DISPLAY_ITEMS = 7;
    static final int DENSE_DAY_MIN_NON_LOGISTICS_ITEMS = 6;
    static final int DENSE_DAY_TARGET_MAX_NON_LOGISTICS_ITEMS = 10;
    static final int MAX_TOTAL_ITEMS_PER_DAY = 15;

    private ItineraryQualityPolicy() {
    }

    static String vietnamPacingGuidance() {
        return String.format(
                "Keep each day realistic for Vietnam: normal sightseeing days should have %d-%d display items; "
                        + "travel-constrained, partially usable, relaxing, or family days may have %d-%d items; "
                        + "do not treat the first or last day as light automatically when arrival is early or departure is late; "
                        + "dense but realistic city/food/adventure days should target %d-%d FOOD/CAFE/ATTRACTION/ACTIVITY/NIGHTLIFE items when distances are close and pacing is believable. "
                        + "%d non-logistics items is a pacing target, not a separate hard limit; a coherent day may exceed it when the timeline remains realistic. "
                        + "Never return more than %d total items in one day, and do not pad the itinerary just to hit a count.",
                NORMAL_DAY_MIN_DISPLAY_ITEMS,
                NORMAL_DAY_MAX_DISPLAY_ITEMS,
                LIGHT_DAY_MIN_DISPLAY_ITEMS,
                LIGHT_DAY_MAX_DISPLAY_ITEMS,
                DENSE_DAY_MIN_NON_LOGISTICS_ITEMS,
                DENSE_DAY_TARGET_MAX_NON_LOGISTICS_ITEMS,
                DENSE_DAY_TARGET_MAX_NON_LOGISTICS_ITEMS,
                MAX_TOTAL_ITEMS_PER_DAY);
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

    static int maxGenericActivitiesAllowed(int activityCount) {
        return Math.max(2, activityCount / 4);
    }

    static boolean isRelaxedPacing(TripDto.GenerateRequest req) {
        if (req == null) {
            return false;
        }
        String context = normalize(String.join(" ",
                nullToBlank(req.getStyle()),
                nullToBlank(req.getGroupType()),
                nullToBlank(req.getNotes())));
        return containsAny(context,
                "relaxing",
                "nghi duong",
                "family",
                "gia dinh",
                "tre em",
                "nguoi lon tuoi",
                "nhe nhang",
                "thu gian");
    }

    static boolean isIntercityTransport(String text, String outboundMode) {
        String normalizedText = normalize(text);
        if (mentionsLocalTerminalTransfer(normalizedText)) {
            return false;
        }

        String mode = normalize(outboundMode);
        boolean routeLike = looksLikeRoute(normalizedText);
        if ("plane".equals(mode)) {
            return containsAny(normalizedText, "chuyen bay", "bay den", "bay ve", "ve may bay")
                    || (routeLike && mentionsAtLeastTwoAirportAliases(normalizedText));
        }
        if ("train".equals(mode)) {
            return containsAny(normalizedText, "tau hoa", "ve tau", "len tau", "den ga bang tau", "di tau", "tau lua", "chuyen tau")
                    || (routeLike && containsAny(normalizedText, "ga ", "nha ga"));
        }
        if ("bus".equals(mode)) {
            return containsAny(normalizedText, "xe khach", "limousine", "ve xe", "ben xe", "xe giuong nam", "xe nam", "xe chat luong cao", "xe du lich");
        }
        if ("personal car".equals(mode) || "personal_car".equals(mode)) {
            return containsAny(normalizedText,
                    "lai xe",
                    "o to ca nhan",
                    "oto ca nhan",
                    "xe ca nhan",
                    "tu lai",
                    "di o to",
                    "di oto",
                    "lai o to",
                    "lai oto",
                    "di xe o to",
                    "di xe oto",
                    "lai xe o to",
                    "lai xe oto")
                    || (routeLike && containsAny(normalizedText, "o to", "oto", "xe hoi"));
        }
        if ("personal motorbike".equals(mode) || "personal_motorbike".equals(mode)) {
            return containsAny(normalizedText, "xe may ca nhan", "chay xe may", "di xe may", "di chuyen bang xe may", "phuot xe may", "chay xe")
                    || (routeLike && normalizedText.contains("xe may"));
        }
        return containsAny(normalizedText,
                "chuyen bay",
                "ve may bay",
                "tau hoa",
                "ve tau",
                "len tau",
                "di tau",
                "tau lua",
                "chuyen tau",
                "xe khach",
                "limousine",
                "xe giuong nam",
                "xe nam",
                "lai xe lien tinh",
                "o to ca nhan",
                "di o to",
                "di oto",
                "xe may ca nhan",
                "di xe may");
    }

    static String intercityModeKey(String text, String outboundMode) {
        String normalizedText = normalize(text);
        if (containsAny(normalizedText, "chuyen bay", "ve may bay", "bay den", "bay ve")
                || mentionsAtLeastTwoAirportAliases(normalizedText)) {
            return "plane";
        }
        if (containsAny(normalizedText, "tau hoa", "ve tau", "len tau", "tau ve", "den ga bang tau", "di tau", "tau lua", "chuyen tau")) {
            return "train";
        }
        if (containsAny(normalizedText, "xe khach", "limousine", "ve xe khach", "ben xe", "xe giuong nam", "xe nam")) {
            return "bus";
        }
        if (containsAny(normalizedText, "xe may ca nhan", "chay xe may", "di xe may", "di chuyen bang xe may")) {
            return "personal_motorbike";
        }
        if (containsAny(normalizedText, "lai xe", "o to ca nhan", "oto ca nhan", "xe ca nhan", "tu lai", "di o to", "di oto")) {
            return "personal_car";
        }

        String normalizedMode = normalize(outboundMode).replace(' ', '_');
        return switch (normalizedMode) {
            case "plane", "train", "bus", "personal_car", "personal_motorbike" -> normalizedMode;
            default -> "intercity";
        };
    }

    static String vehicleKind(String text) {
        String normalizedText = normalize(text);
        if (containsAny(normalizedText, "xe may", "motorbike", "scooter")) {
            return "motorbike";
        }
        if (containsAny(normalizedText, "xe dap", "bike", "bicycle")) {
            return "bicycle";
        }
        if (containsAny(normalizedText, "o to", "oto", "car")) {
            return "car";
        }
        if (containsAny(normalizedText,
                "xe rieng",
                "xe co tai xe",
                "shuttle",
                "xe trung chuyen",
                "dua don",
                "transfer")) {
            return "chauffeured_vehicle";
        }
        return "vehicle";
    }

    static boolean ownerCovers(Set<String> ownerKinds, String requiredKind, String genericKind) {
        if (ownerKinds == null || ownerKinds.isEmpty()) {
            return false;
        }
        if (requiredKind == null || requiredKind.isBlank() || genericKind.equals(requiredKind)) {
            return true;
        }
        return ownerKinds.contains(requiredKind) || ownerKinds.contains(genericKind);
    }

    static boolean looksLikeRoute(String text) {
        String normalized = normalize(text);
        return containsAny(normalized, "->", "<->", "\u2192", "\u2194", " - ")
                || normalized.matches(".*\\b(?:tu|from)\\b.+\\b(?:den|toi|to|di|sang|ve)\\b.+");
    }

    private static boolean mentionsLocalTerminalTransfer(String text) {
        boolean mentionsTerminal = containsAny(text,
                "san bay",
                "cang hang khong",
                "airport",
                "terminal",
                "ga tau",
                "nha ga",
                "ben xe",
                "bus station",
                "train station");
        return mentionsTerminal
                && mentionsLocalVehicle(text)
                && !containsAny(text,
                        "chuyen bay",
                        "ve may bay",
                        "tau hoa",
                        "ve tau",
                        "xe khach",
                        "ve xe khach",
                        "limousine");
    }

    private static boolean mentionsLocalVehicle(String text) {
        return containsAny(text,
                "taxi",
                "grab",
                "shuttle",
                "xe trung chuyen",
                "xe dua don",
                "dua don",
                "transfer");
    }

    private static boolean mentionsAtLeastTwoAirportAliases(String text) {
        String[] aliases = {
                "noi bai",
                "tan son nhat",
                "lien khuong",
                "cam ranh",
                "phu bai",
                "cat bi",
                "da nang",
                "phu quoc",
                "van don",
                "dong hoi",
                "tho xuan",
                "pleiku",
                "phu cat",
                "can tho",
                "chu lai",
                "buon ma thuot",
                "rach gia",
                "ca mau",
                "con dao",
                "dien bien"
        };
        int matches = 0;
        for (String alias : aliases) {
            if (text.contains(alias) && ++matches >= 2) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAny(String text, String... terms) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace("\u0111", "d")
                .replace("\u0110", "D")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }
}
