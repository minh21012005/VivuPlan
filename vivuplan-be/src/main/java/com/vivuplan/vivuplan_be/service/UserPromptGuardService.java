package com.vivuplan.vivuplan_be.service;

import com.vivuplan.vivuplan_be.dto.TripDto;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@Service
public class UserPromptGuardService {

    public static final int MAX_DESTINATION_LENGTH = 100;
    public static final int MAX_DEPARTURE_LENGTH = 100;
    public static final int MAX_MUST_VISIT_LENGTH = 300;
    public static final int MAX_AVOID_LENGTH = 300;
    public static final int MAX_NOTES_LENGTH = 800;
    public static final int MAX_REGENERATE_INSTRUCTION_LENGTH = 500;

    private static final String OUT_OF_SCOPE_MESSAGE =
            "Nội dung yêu cầu nên liên quan đến chuyến đi, địa điểm, ăn uống, di chuyển hoặc sở thích du lịch.";
    private static final String UNSAFE_PROMPT_MESSAGE =
            "Nội dung yêu cầu có dấu hiệu điều khiển hệ thống. Vui lòng chỉ nhập mong muốn cho chuyến đi.";

    private static final List<String> PROMPT_INJECTION_MARKERS = List.of(
            "ignore previous", "ignore all previous", "disregard previous",
            "system prompt", "developer message", "reveal prompt", "show prompt",
            "jailbreak", "act as", "you are now", "forget previous",
            "bo qua huong dan", "bo qua chi dan", "bo qua yeu cau truoc",
            "quen cac huong dan", "tiet lo prompt", "hien thi prompt",
            "doi vai tro", "khong can tra ve json", "khong theo schema"
    );

    private static final List<String> TRAVEL_MARKERS = List.of(
            "du lich", "lich trinh", "chuyen di", "di choi", "tham quan",
            "diem den", "noi muon ghe", "ghe", "tranh", "an", "uong",
            "nha hang", "quan", "cafe", "khach san", "homestay", "resort",
            "di chuyen", "xe", "may bay", "tau", "bus", "grab", "taxi",
            "bien", "nui", "dao", "chua", "den", "bao tang", "cho dem",
            "tre em", "nguoi lon tuoi", "di bo", "di ung", "an chay",
            "ngan sach", "tiet kiem", "nghi duong", "hai san"
    );

    private static final List<String> OFF_TOPIC_MARKERS = List.of(
            "giai phuong trinh", "chung minh", "dao ham", "tich phan",
            "lap trinh", "viet code", "source code", "javascript", "python",
            "java", "sql", "bai tap", "chan doan", "ke don", "toa thuoc",
            "benh gi", "dau tu", "chung khoan", "crypto", "bitcoin",
            "phap ly", "hop dong", "khoi kien"
    );

    public void validateAndSanitizeGenerateRequest(TripDto.GenerateRequest req) {
        req.setDestination(validateOptionalTravelText("Điểm đến", req.getDestination(), MAX_DESTINATION_LENGTH));
        req.setDeparture(validateRequiredTravelText("Điểm xuất phát", req.getDeparture(), MAX_DEPARTURE_LENGTH));
        req.setMustVisit(validateOptionalTravelText("Nơi muốn ghé", req.getMustVisit(), MAX_MUST_VISIT_LENGTH));
        req.setAvoid(validateOptionalTravelText("Điều muốn tránh", req.getAvoid(), MAX_AVOID_LENGTH));
        req.setNotes(validateOptionalTravelText("Ghi chú", req.getNotes(), MAX_NOTES_LENGTH));
    }

    public void validateAndSanitizeDestinationSuggestionRequest(TripDto.DestinationSuggestionRequest req) {
        req.setDeparture(validateRequiredTravelText("Điểm xuất phát", req.getDeparture(), MAX_DEPARTURE_LENGTH));
        req.setMustVisit(validateOptionalTravelText("Nơi muốn ghé", req.getMustVisit(), MAX_MUST_VISIT_LENGTH));
        req.setAvoid(validateOptionalTravelText("Điều muốn tránh", req.getAvoid(), MAX_AVOID_LENGTH));
        req.setNotes(validateOptionalTravelText("Ghi chú", req.getNotes(), MAX_NOTES_LENGTH));
    }

    public String validateAndSanitizeRegenerateInstruction(String instruction) {
        String sanitized = sanitize(instruction);
        if (sanitized == null || sanitized.isBlank()) {
            throw new IllegalArgumentException("Vui lòng nhập điều bạn muốn chỉnh trong lịch trình.");
        }
        validateLength("Yêu cầu chỉnh ngày", sanitized, MAX_REGENERATE_INSTRUCTION_LENGTH);
        validateTravelScope(sanitized);
        return sanitized;
    }

    private String validateRequiredTravelText(String label, String value, int maxLength) {
        String sanitized = sanitize(value);
        if (sanitized == null || sanitized.isBlank()) {
            throw new IllegalArgumentException(label + " không được để trống.");
        }
        validateLength(label, sanitized, maxLength);
        validateTravelScope(sanitized);
        return sanitized;
    }

    private String validateOptionalTravelText(String label, String value, int maxLength) {
        String sanitized = sanitize(value);
        if (sanitized == null || sanitized.isBlank() || isMeaninglessEmptyWord(sanitized)) {
            return null;
        }
        validateLength(label, sanitized, maxLength);
        validateTravelScope(sanitized);
        return sanitized;
    }

    private boolean isMeaninglessEmptyWord(String value) {
        String normalized = normalize(value).trim();
        return java.util.Set.of("khong", "ko", "khong co", "ko co", "none", "nothing", "null", "na", "khong co gi", "ko co gi").contains(normalized);
    }

    private void validateLength(String label, String value, int maxLength) {
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(label + " tối đa " + maxLength + " ký tự.");
        }
    }

    private void validateTravelScope(String value) {
        String normalized = normalize(value);
        if (PROMPT_INJECTION_MARKERS.stream().anyMatch(normalized::contains)) {
            throw new IllegalArgumentException(UNSAFE_PROMPT_MESSAGE);
        }
        boolean looksOffTopic = OFF_TOPIC_MARKERS.stream().anyMatch(normalized::contains);
        boolean hasTravelContext = TRAVEL_MARKERS.stream().anyMatch(marker -> containsPhrase(normalized, marker));
        if (looksOffTopic && !hasTravelContext) {
            throw new IllegalArgumentException(OUT_OF_SCOPE_MESSAGE);
        }
    }

    private boolean containsPhrase(String normalized, String marker) {
        String pattern = "(?<![a-z0-9])" + java.util.regex.Pattern.quote(marker) + "(?![a-z0-9])";
        return java.util.regex.Pattern.compile(pattern).matcher(normalized).find();
    }

    private String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String withoutControlChars = value.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "");
        String collapsedLines = withoutControlChars.lines()
                .map(line -> line.replaceAll("[ \t]{2,}", " ").trim())
                .filter(line -> !line.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        return collapsedLines.trim();
    }

    private String normalize(String value) {
        String lower = value == null ? "" : value.toLowerCase(Locale.ROOT);
        String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "").replace('đ', 'd');
    }
}
