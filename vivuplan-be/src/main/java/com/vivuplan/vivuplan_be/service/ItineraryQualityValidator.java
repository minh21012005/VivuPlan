package com.vivuplan.vivuplan_be.service;

import com.vivuplan.vivuplan_be.dto.TripDto;

import java.text.Normalizer;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic cross-day checks that complement the model prompt. These checks
 * trigger one retry for renderable quality problems and never mutate activities.
 */
final class ItineraryQualityValidator {

    enum FailureType {
        STRUCTURAL,
        QUALITY
    }

    record Result(boolean passed, String reason, FailureType failureType) {
        static Result pass() {
            return new Result(true, "ok", null);
        }

        static Result structural(String reason) {
            return new Result(false, reason, FailureType.STRUCTURAL);
        }

        static Result quality(String reason) {
            return new Result(false, reason, FailureType.QUALITY);
        }
    }

    Result validateFull(List<TripDto.DayResponse> days, TripDto.GenerateRequest req) {
        if (days == null) {
            return Result.structural("response has no days");
        }
        if (days.size() != Math.max(1, req.getDays())) {
            return Result.structural("expected " + req.getDays() + " days but got " + days.size());
        }
        Result numberingResult = validateDayNumbers(days, Math.max(1, req.getDays()));
        if (!numberingResult.passed()) {
            return numberingResult;
        }

        for (TripDto.DayResponse day : days) {
            Result dayResult = validateDay(day, req);
            if (!dayResult.passed()) {
                return dayResult;
            }
        }

        Result transportResult = validateTripTransportTimeline(days, req);
        return transportResult.passed() ? Result.pass() : transportResult;
    }

    Result validateRegenerated(
            TripDto.DayResponse regeneratedDay,
            List<TripDto.DayResponse> currentSchedule,
            TripDto.GenerateRequest req) {
        if (regeneratedDay == null) {
            return Result.structural("response has no day");
        }
        if (regeneratedDay.getDay() < 1 || regeneratedDay.getDay() > Math.max(1, req.getDays())) {
            return Result.structural("regenerated day number is outside the trip range: " + regeneratedDay.getDay());
        }
        List<TripDto.DayResponse> merged = replaceDay(currentSchedule, regeneratedDay);
        Result dayResult = validateDay(regeneratedDay, req);
        if (!dayResult.passed()) {
            return dayResult;
        }
        Result transportResult = validateTripTransportTimeline(merged, req);
        return transportResult.passed() ? Result.pass() : transportResult;
    }

    private Result validateDay(TripDto.DayResponse day, TripDto.GenerateRequest req) {
        if (day == null || day.getActivities() == null || day.getActivities().isEmpty()) {
            return Result.structural("day has no activities");
        }

        int minimum = minimumMeaningfulActivities(day, req);
        long meaningful = day.getActivities().stream().filter(this::isMeaningfulActivity).count();
        if (meaningful < minimum) {
            return Result.quality(
                    "day " + day.getDay() + " has fewer than " + minimum + " meaningful activities");
        }
        if (ItineraryQualityPolicy.exceedsTotalItems(day.getActivities().size())) {
            return Result.quality("day " + day.getDay() + " has too many activities");
        }

        List<ScheduledRange> ranges = new ArrayList<>();
        for (TripDto.ActivityResponse activity : day.getActivities()) {
            if (activity == null || normalize(activity.getName()).isBlank()) {
                return Result.structural("activity has no name");
            }
            if (!isValidTime(activity.getTime())) {
                return Result.structural("activity has invalid time: " + activity.getTime());
            }
            String type = normalize(activity.getType());
            if (!isValidType(type)) {
                return Result.structural("activity has invalid type: " + activity.getType());
            }

            Result costOwnership = validateCostOwnership(activity, type);
            if (!costOwnership.passed()) {
                return costOwnership;
            }

            if (!isNonBlockingBooking(activity, type) && !isOvernightArrival(activity, type)) {
                int durationMinutes = parseDurationMinutes(activity.getDuration());
                if (durationMinutes > 0) {
                    ranges.add(new ScheduledRange(
                            LocalTime.parse(activity.getTime()),
                            durationMinutes,
                            activity.getName()));
                }
            }
        }

        ranges.sort(Comparator.comparing(ScheduledRange::start));
        List<ScheduledRange> activeRanges = new ArrayList<>();
        for (ScheduledRange current : ranges) {
            activeRanges.removeIf(previous -> previous.endMinutes() <= current.startMinutes());
            for (ScheduledRange previous : activeRanges) {
                if (current.overlapMinutes(previous) > 30) {
                    return Result.quality("activity times overlap: " + previous.name() + " / " + current.name());
                }
            }
            activeRanges.add(current);
        }
        return Result.pass();
    }

    private Result validateDayNumbers(List<TripDto.DayResponse> days, int expectedDays) {
        Set<Integer> seen = new HashSet<>();
        for (int index = 0; index < days.size(); index++) {
            TripDto.DayResponse day = days.get(index);
            if (day == null) {
                return Result.structural("response contains a null day");
            }
            int dayNumber = day.getDay();
            if (dayNumber < 1 || dayNumber > expectedDays) {
                return Result.structural("day number is outside the trip range: " + dayNumber);
            }
            if (!seen.add(dayNumber)) {
                return Result.structural("duplicate day number: " + dayNumber);
            }
            if (dayNumber != index + 1) {
                return Result.structural(
                        "days are out of order: expected day " + (index + 1) + " but got " + dayNumber);
            }
        }
        for (int dayNumber = 1; dayNumber <= expectedDays; dayNumber++) {
            if (!seen.contains(dayNumber)) {
                return Result.structural("missing day number: " + dayNumber);
            }
        }
        return Result.pass();
    }

    private Result validateTripTransportTimeline(
            List<TripDto.DayResponse> days,
            TripDto.GenerateRequest req) {
        if (days == null || days.isEmpty() || isSamePlace(req.getDeparture(), req.getDestination())) {
            return Result.pass();
        }

        List<TransportEvidence> evidence = new ArrayList<>();
        for (TripDto.DayResponse day : days) {
            if (day == null || day.getActivities() == null) {
                continue;
            }
            for (TripDto.ActivityResponse activity : day.getActivities()) {
                if (!"transport".equals(normalize(activity.getType()))) {
                    continue;
                }
                String text = combinedText(activity);
                if (isIntercityTransport(text, req.getOutboundTransport())) {
                    evidence.add(new TransportEvidence(day.getDay(), activity, text));
                }
            }
        }

        if (evidence.isEmpty()) {
            if (days.size() == 1) {
                return Result.pass();
            }
            return Result.quality("intercity outbound and return timeline is missing");
        }

        boolean outbound = evidence.stream().anyMatch(item -> isOutboundEvidence(item, req));
        boolean returning = evidence.stream().anyMatch(item -> isReturnEvidence(item, req));
        if (!outbound) {
            return Result.quality("intercity outbound timeline is missing or unclear");
        }
        if (!returning) {
            return Result.quality("intercity return timeline is missing or unclear");
        }

        for (TransportEvidence item : evidence) {
            if (isOvernightArrival(item.activity(), "transport")
                    && !activityExplainsOvernightRoute(item.activity(), req)) {
                return Result.quality("overnight arrival does not identify its intercity route: "
                        + item.activity().getName());
            }
        }

        return validateZeroCostReferences(days);
    }

    private Result validateZeroCostReferences(List<TripDto.DayResponse> days) {
        Set<String> paidRoundTripModes = new HashSet<>();
        Set<String> paidVehiclePackageKinds = new HashSet<>();
        for (TripDto.DayResponse day : days) {
            if (day == null || day.getActivities() == null) {
                continue;
            }
            for (TripDto.ActivityResponse activity : day.getActivities()) {
                if (!"transport".equals(normalize(activity.getType())) || activity.getEstimatedCost() <= 0) {
                    continue;
                }
                String text = combinedText(activity);
                if (mentionsRoundTrip(text) && mentionsIntercityFare(text)) {
                    paidRoundTripModes.add(ItineraryQualityPolicy.intercityModeKey(text, null));
                }
                if (mentionsVehiclePackage(text) && nameReflectsVehiclePackage(normalize(activity.getName()))) {
                    paidVehiclePackageKinds.add(ItineraryQualityPolicy.vehicleKind(text));
                }
            }
        }

        for (TripDto.DayResponse day : days) {
            if (day == null || day.getActivities() == null) {
                continue;
            }
            for (TripDto.ActivityResponse activity : day.getActivities()) {
                if (!"transport".equals(normalize(activity.getType())) || activity.getEstimatedCost() != 0) {
                    continue;
                }
                String text = combinedText(activity);
                String intercityMode = ItineraryQualityPolicy.intercityModeKey(text, null);
                if (mentionsAlreadyIncluded(text)
                        && mentionsIntercityTicket(text)
                        && !ItineraryQualityPolicy.ownerCovers(
                                paidRoundTripModes,
                                intercityMode,
                                "intercity")) {
                    return Result.quality("zero-cost intercity leg references a missing paid round-trip owner: "
                            + activity.getName());
                }
                String vehicleKind = ItineraryQualityPolicy.vehicleKind(text);
                if (mentionsAlreadyIncluded(text)
                        && mentionsVehicleReference(text)
                        && !ItineraryQualityPolicy.ownerCovers(
                                paidVehiclePackageKinds,
                                vehicleKind,
                                "vehicle")) {
                    return Result.quality("zero-cost local transfer references a missing paid vehicle package: "
                            + activity.getName());
                }
            }
        }
        return Result.pass();
    }

    private Result validateCostOwnership(TripDto.ActivityResponse activity, String type) {
        if (!"transport".equals(type) || activity.getEstimatedCost() <= 0) {
            return Result.pass();
        }
        String name = normalize(activity.getName());
        String text = combinedText(activity);

        if (mentionsIntercityFare(text) && mentionsLocalTransfer(text)) {
            return Result.quality("intercity ticket and local transfer costs are bundled in one activity: "
                    + activity.getName());
        }
        if (mentionsVehiclePackage(text) && !nameReflectsVehiclePackage(name)) {
            return Result.quality("multi-day vehicle package cost is attached to a single movement activity: "
                    + activity.getName());
        }
        return Result.pass();
    }

    private int minimumMeaningfulActivities(TripDto.DayResponse day, TripDto.GenerateRequest req) {
        boolean hasIntercity = day.getActivities().stream()
                .filter(activity -> "transport".equals(normalize(activity.getType())))
                .map(this::combinedText)
                .anyMatch(text -> isIntercityTransport(text, req.getOutboundTransport()));
        return hasIntercity || ItineraryQualityPolicy.isRelaxedPacing(req)
                ? ItineraryQualityPolicy.MIN_ACTIVITIES_LIGHT_DAY
                : ItineraryQualityPolicy.MIN_ACTIVITIES_DEFAULT;
    }

    private boolean isMeaningfulActivity(TripDto.ActivityResponse activity) {
        if (activity == null) {
            return false;
        }
        String name = normalize(activity.getName());
        boolean fillerName = containsAny(name,
                "nghi ngoi",
                "thu gian tai phong",
                "chuan bi",
                "thoi gian tu do",
                "ve homestay nghi",
                "ve khach san nghi",
                "ve noi luu tru nghi");
        return !fillerName;
    }

    private boolean isIntercityTransport(String text, String outboundMode) {
        return ItineraryQualityPolicy.isIntercityTransport(text, outboundMode);
    }

    private boolean isOutboundEvidence(TransportEvidence evidence, TripDto.GenerateRequest req) {
        String departure = normalize(req.getDeparture());
        String text = evidence.text();
        boolean firstHalf = evidence.day() <= Math.max(1, (req.getDays() + 1) / 2);
        return firstHalf && (text.contains(departure)
                || containsAny(text, "khoi hanh", "chieu di", "bay den", "den ga", "den ben xe")
                || (evidence.day() == 1 && ItineraryQualityPolicy.looksLikeRoute(text)));
    }

    private boolean isReturnEvidence(TransportEvidence evidence, TripDto.GenerateRequest req) {
        String departure = normalize(req.getDeparture());
        String text = evidence.text();
        boolean secondHalf = evidence.day() >= Math.max(1, (req.getDays() + 1) / 2);
        return secondHalf
                && (containsAny(text, "tro ve", "ve " + departure, "ve lai " + departure, "tro lai " + departure, "chieu ve", "bay ve", "tau ve")
                        || (evidence.day() == Math.max(1, req.getDays())
                                && ItineraryQualityPolicy.looksLikeRoute(text)));
    }

    private boolean activityExplainsOvernightRoute(
            TripDto.ActivityResponse activity,
            TripDto.GenerateRequest req) {
        String text = combinedText(activity);
        String departure = normalize(req.getDeparture());
        return text.contains(departure)
                && containsAny(text, "tau", "chuyen bay", "xe khach", "limousine", "lai xe");
    }

    private boolean isOvernightArrival(TripDto.ActivityResponse activity, String type) {
        if (!"transport".equals(normalize(type))) {
            return false;
        }
        String text = combinedText(activity);
        return containsAny(text,
                "toi hom truoc",
                "dem hom truoc",
                "khoi hanh tu toi",
                "khoi hanh dem",
                "tau dem",
                "xe dem",
                "chuyen bay dem");
    }

    private boolean isNonBlockingBooking(TripDto.ActivityResponse activity, String type) {
        if (!"transport".equals(type)) {
            return false;
        }
        String name = normalize(activity.getName());
        String text = combinedText(activity);
        boolean packageName = containsAny(name,
                "ve may bay khu hoi",
                "ve tau khu hoi",
                "ve xe khu hoi",
                "dat ve",
                "mua ve",
                "thue xe",
                "goi xe",
                "dat xe");
        boolean movementName = containsAny(name,
                "chuyen bay",
                "bay den",
                "bay ve",
                "len tau",
                "tau tu",
                "tau ve",
                "di chuyen",
                "don san bay",
                "don khach",
                "dua don",
                "dua khach",
                "lai xe");
        return packageName && !movementName && (mentionsRoundTrip(text) || mentionsVehiclePackage(text));
    }

    private boolean mentionsIntercityTicket(String text) {
        return containsAny(text,
                "ve may bay",
                "ve tau",
                "ve xe khach",
                "ve limousine",
                "chuyen bay",
                "tau hoa");
    }

    private boolean mentionsIntercityFare(String text) {
        return containsAny(text,
                "ve may bay",
                "gia ve may bay",
                "chi phi ve may bay",
                "ve tau",
                "gia ve tau",
                "chi phi ve tau",
                "ve xe khach",
                "gia ve xe khach",
                "chi phi xe khach",
                "ve limousine",
                "gia ve limousine");
    }

    private boolean mentionsLocalTransfer(String text) {
        return mentionsLocalVehicle(text) && containsAny(text,
                "san bay",
                "ga ",
                "ga dong hoi",
                "ben xe",
                "phong nha",
                "khach san",
                "homestay",
                "terminal",
                "dua don",
                "transfer");
    }

    private boolean mentionsLocalVehicle(String text) {
        return containsAny(text,
                "taxi",
                "grab",
                "xe rieng",
                "xe co tai xe",
                "shuttle",
                "xe trung chuyen",
                "dua don",
                "transfer");
    }

    private boolean mentionsVehiclePackage(String text) {
        return mentionsVehicleReference(text)
                && (containsAny(text,
                        "ca ngay",
                        "nhieu ngay",
                        "ngay 1 va ngay 2",
                        "ngay 1-2",
                        "tron goi",
                        "goi xe",
                        "da thue",
                        "ca chuyen",
                        "suot chuyen",
                        "tu ngay")
                        || text.matches(".*\\b\\d+\\s*ngay\\b.*"));
    }

    private boolean mentionsVehicleReference(String text) {
        return mentionsLocalVehicle(text)
                || containsAny(text,
                        "thue xe",
                        "xe thue",
                        "thue xe may",
                        "xe may thue",
                        "thue xe dap",
                        "xe dap thue",
                        "thue o to",
                        "thue oto",
                        "o to thue",
                        "oto thue",
                        "tu lai",
                        "rental");
    }

    private boolean nameReflectsVehiclePackage(String name) {
        return containsAny(name,
                "thue xe",
                "thue xe may",
                "thue xe dap",
                "thue o to",
                "thue oto",
                "xe may thue",
                "xe dap thue",
                "o to thue",
                "oto thue",
                "tu lai",
                "goi xe",
                "xe rieng",
                "xe co tai xe",
                "shuttle",
                "dua don tron goi");
    }

    private boolean mentionsRoundTrip(String text) {
        return containsAny(text,
                "khu hoi",
                "hai chieu",
                "2 chieu",
                "ca di va ve",
                "di va ve",
                "bao gom chieu ve",
                "round trip",
                "round-trip");
    }

    private boolean mentionsAlreadyIncluded(String text) {
        return containsAny(text,
                "da bao gom",
                "da tinh",
                "da duoc tinh",
                "bao gom trong",
                "chi phi da tra",
                "da mua san",
                "da dat truoc",
                "da mua truoc",
                "da thanh toan");
    }

    private boolean isSamePlace(String departure, String destination) {
        String from = normalize(departure);
        String to = normalize(destination);
        return from.isBlank() || to.isBlank()
                || from.equals(to)
                || from.contains(to)
                || to.contains(from);
    }

    private List<TripDto.DayResponse> replaceDay(
            List<TripDto.DayResponse> currentSchedule,
            TripDto.DayResponse regeneratedDay) {
        if (currentSchedule == null || currentSchedule.isEmpty()) {
            return List.of(regeneratedDay);
        }
        List<TripDto.DayResponse> merged = new ArrayList<>();
        boolean replaced = false;
        for (TripDto.DayResponse day : currentSchedule) {
            if (day != null && day.getDay() == regeneratedDay.getDay()) {
                if (!replaced) {
                    merged.add(regeneratedDay);
                    replaced = true;
                }
            } else {
                merged.add(day);
            }
        }
        if (!replaced) {
            merged.add(regeneratedDay);
        }
        merged.sort(Comparator.comparingInt(TripDto.DayResponse::getDay));
        return merged;
    }

    private int parseDurationMinutes(String duration) {
        String normalized = normalize(duration);
        if (normalized.isBlank()) {
            return 0;
        }
        int minutes = 0;
        java.util.regex.Matcher hourMatcher = Pattern
                .compile("(\\d+(?:[\\.,]\\d+)?)\\s*(?:gio|tieng|hour|hours|h)(?![a-zA-Z])")
                .matcher(normalized);
        if (hourMatcher.find()) {
            minutes += Math.round(Float.parseFloat(hourMatcher.group(1).replace(",", ".")) * 60);
        }
        java.util.regex.Matcher minuteMatcher = Pattern.compile("(\\d+)\\s*(?:phut|minute|minutes|min|p)\\b")
                .matcher(normalized);
        if (minuteMatcher.find()) {
            minutes += Integer.parseInt(minuteMatcher.group(1));
        }
        return minutes;
    }

    private boolean isValidTime(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            LocalTime.parse(value);
            return value.matches("\\d{2}:\\d{2}");
        } catch (DateTimeParseException ignored) {
            return false;
        }
    }

    private boolean isValidType(String type) {
        return Set.of(
                "food",
                "cafe",
                "attraction",
                "transport",
                "accommodation",
                "activity",
                "nightlife").contains(type);
    }

    private String combinedText(TripDto.ActivityResponse activity) {
        return normalize(String.join(" ",
                nullToBlank(activity.getName()),
                nullToBlank(activity.getLocation()),
                nullToBlank(activity.getNote())));
    }

    private boolean containsAny(String text, String... terms) {
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

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace("đ", "d")
                .replace("Đ", "D")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private record ScheduledRange(LocalTime start, int durationMinutes, String name) {
        int startMinutes() {
            return start.getHour() * 60 + start.getMinute();
        }

        int endMinutes() {
            return startMinutes() + Math.max(15, durationMinutes);
        }

        long overlapMinutes(ScheduledRange other) {
            return Math.max(0,
                    Math.min(endMinutes(), other.endMinutes())
                            - Math.max(startMinutes(), other.startMinutes()));
        }
    }

    private record TransportEvidence(int day, TripDto.ActivityResponse activity, String text) {
    }
}
