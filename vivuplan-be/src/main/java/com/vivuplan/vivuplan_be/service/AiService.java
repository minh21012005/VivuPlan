package com.vivuplan.vivuplan_be.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivuplan.vivuplan_be.dto.TripDto;
import com.vivuplan.vivuplan_be.exception.AiGenerationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiService {

    @Value("${app.ai.gemini.api-key:}")
    private String geminiApiKey;

    @Value("${app.ai.gemini.model:gemini-2.5-flash}")
    private String geminiModel;

    private final ObjectMapper objectMapper;

    private static final String GEMINI_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";
    private static final String AI_GENERATION_USER_MESSAGE =
            "AI chưa tạo được lịch trình đủ cụ thể cho chuyến đi này. Vui lòng thử lại hoặc bổ sung thêm điểm muốn ghé, điều cần tránh hay ghi chú để VivuPlan lập lại lịch trình.";

    public List<TripDto.DayResponse> generateItinerary(TripDto.GenerateRequest req) {
        String prompt = buildPrompt(req);
        log.info("Generating itinerary for: {} - {}N using Gemini model {}", req.getDestination(), req.getDays(), geminiModel);

        try {
            String rawJson = callGemini(prompt);
            List<TripDto.DayResponse> itinerary = parseItinerary(rawJson);
            QualityCheck quality = assessItineraryQuality(itinerary, req.getDays());
            if (quality.passed()) {
                return itinerary;
            }

            log.warn("AI itinerary for {} failed quality check: {}. Retrying once with stricter prompt.",
                    req.getDestination(), quality.reason());

            String retryJson = callGemini(buildQualityRetryPrompt(req, quality.reason()));
            List<TripDto.DayResponse> retryItinerary = parseItinerary(retryJson);
            QualityCheck retryQuality = assessItineraryQuality(retryItinerary, req.getDays());
            if (retryQuality.passed()) {
                return retryItinerary;
            }

            log.warn("AI retry itinerary for {} still failed quality check: {}. Returning error to user.",
                    req.getDestination(), retryQuality.reason());
            throw new AiGenerationException(AI_GENERATION_USER_MESSAGE);
        } catch (AiGenerationException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI generation failed with Gemini model {}: {}", geminiModel, e.getMessage(), e);
            throw new AiGenerationException(AI_GENERATION_USER_MESSAGE, e);
        }
    }

    private String buildPrompt(TripDto.GenerateRequest req) {
        return buildCostAwarePrompt(req);
    }

    private String buildQualityRetryPrompt(TripDto.GenerateRequest req, String reason) {
        return buildCostAwarePrompt(req) + String.format("""

            IMPORTANT RETRY INSTRUCTION:
            The previous itinerary was rejected because: %s
            Regenerate the itinerary from scratch.
            Use named, real places and restaurants in or near %s.
            Avoid placeholder wording such as "địa điểm nổi bật", "đặc sản địa phương", "khu trung tâm", "vùng ven", "nhà hàng địa phương", or "cà phê view đẹp" unless paired with a specific real name and location.
            Do not repeat the same day structure across days.
            """, reason, req.getDestination());
    }

    private String buildLegacyPrompt(TripDto.GenerateRequest req) {
        long budgetK = req.getBudgetPerPerson() / 1000;
        return String.format("""
            Bạn là chuyên gia du lịch Việt Nam. Hãy tạo lịch trình du lịch CHI TIẾT và THỰC TẾ.

            THÔNG TIN CHUYẾN ĐI:
            - Điểm xuất phát: %s
            - Điểm đến: %s
            - Ngày đi: %s
            - Ngày về: %s
            - Thời gian: %d ngày
            - Số người: %d
            - Ngân sách: %dk VND/người/ngày (người dùng nhập %dk VND, kiểu ngân sách %s)
            - Phong cách: %s
            - Nhóm: %s
            - Phương tiện chính: %s
            - Di chuyển đến điểm đến: %s
            - Di chuyển trong chuyến đi: %s
            - Điểm đến do AI chọn: %s
            - Địa điểm muốn ghé: %s
            - Điều cần tránh: %s
            - Ghi chú: %s

            YÊU CẦU:
            1. Chỉ đề xuất địa điểm THỰC TẾ tồn tại tại %s
            2. Ngày đầu và ngày cuối phải tính đến di chuyển từ/đến %s
            3. Sắp xếp hoạt động theo địa lý để giảm di chuyển
            4. Ước tính chi phí THỰC TẾ theo thị trường Việt Nam hiện tại
            5. Thời gian hoạt động phù hợp (không nhồi nhét)
            6. Bao gồm giờ mở cửa thực tế của từng địa điểm
            7. Mỗi ngày 4-7 hoạt động
            8. Không dùng câu chung chung như "ăn sáng đặc sản địa phương", "tham quan điểm nổi bật", "khám phá khu vực lân cận"
            9. Mỗi hoạt động phải có tên địa điểm/quán cụ thể, món nên ăn hoặc việc nên làm cụ thể
            10. Các ngày phải khác nhau rõ rệt, không lặp cùng chuỗi hoạt động
            11. Với FOOD/CAFE, name phải gồm món hoặc quán cụ thể, note phải nói nên gọi món gì
            12. Với ATTRACTION/ACTIVITY, name phải là địa điểm cụ thể, note phải nói trải nghiệm cụ thể tại đó
            13. Trả về đúng %d ngày, không thiếu ngày, không thêm ngày

            TRẢ LỜI DẠNG JSON THUẦN TÚY (không có markdown), schema:
            [
              {
                "day": 1,
                "title": "Ngày 1 – Tên chủ đề",
                "summary": "Mô tả tóm tắt ngày",
                "activities": [
                  {
                    "time": "08:00",
                    "name": "Tên địa điểm",
                    "type": "FOOD|CAFE|ATTRACTION|TRANSPORT|ACCOMMODATION",
                    "location": "Địa chỉ cụ thể",
                    "duration": "1 giờ",
                    "estimatedCost": 50000,
                    "note": "Gợi ý hữu ích",
                    "rating": 4.5,
                    "latitude": 11.9403,
                    "longitude": 108.4583
                  }
                ]
              }
            ]
            """,
            req.getDeparture(), req.getDestination(),
            req.getStartDate() != null ? req.getStartDate().toString() : "Chưa cung cấp",
            req.getEndDate() != null ? req.getEndDate().toString() : "Chưa cung cấp",
            req.getDays(), req.getTravelerCount() != null ? req.getTravelerCount() : 1,
            budgetK / Math.max(1, req.getDays()), budgetK, req.getBudgetMode() != null ? req.getBudgetMode() : "PER_PERSON",
            req.getStyle(), req.getGroupType(), req.getTransport(),
            req.getOutboundTransport(), req.getLocalTransport(),
            Boolean.TRUE.equals(req.getDestinationSuggested()) ? "Có" : "Không",
            req.getMustVisit() != null && !req.getMustVisit().isBlank() ? req.getMustVisit() : "Không có",
            req.getAvoid() != null && !req.getAvoid().isBlank() ? req.getAvoid() : "Không có",
            req.getNotes() != null ? req.getNotes() : "Không có",
            req.getDestination(), req.getDeparture(), req.getDays()
        );
    }

    private String buildCostAwarePrompt(TripDto.GenerateRequest req) {
        int days = Math.max(1, req.getDays());
        int travelers = req.getTravelerCount() != null ? Math.max(1, req.getTravelerCount()) : 1;
        long totalBudget = resolvePromptTotalBudget(req, travelers);
        long perPersonBudget = totalBudget / travelers;
        long perPersonPerDay = perPersonBudget / days;

        return String.format("""
            You are a senior Vietnam travel planner. Create a practical itinerary for Vietnamese travelers.
            Return JSON only. All text values shown to users must be written in Vietnamese with correct accents.

            Trip:
            - Departure: %s
            - Destination: %s
            - Start date: %s
            - End date: %s
            - Duration: %d days
            - Travelers: %d
            - Budget mode: %s
            - Total group budget: %,d VND
            - Budget per person: %,d VND
            - Budget per person per day: %,d VND
            - Style: %s
            - Group: %s
            - Outbound transport: %s
            - Local transport: %s
            - Must visit: %s
            - Avoid: %s
            - Notes: %s

            Cost rules:
            1. estimatedCost MUST be the estimated total VND for the whole group of %d travelers.
            2. Treat the total group budget as an upper spending limit, not a target that must be fully spent.
            3. The full trip cost should stay at or below the total group budget. It is acceptable and often desirable to be under budget when realistic costs are lower.
            4. If the budget is generous, prefer more comfortable or higher-quality choices such as better transport times, cleaner accommodation areas, memorable paid experiences, or reputable restaurants, but do not invent unnecessary costs just to use the budget.
            5. Include realistic major costs: round-trip outbound transport, local transport, accommodation, food, entrance tickets, paid tours, shows, and shopping only if useful.
            6. For fixed-price items such as cable car, theme park, show, museum, paid tour, boat tour, or entrance ticket, use a realistic recent public-market estimate and mention the unit basis in note, for example "khoảng 850k/người".
            7. For accommodation, include a clear ACCOMMODATION activity with total lodging cost for all nights and all travelers. Do not use the accommodation type for a taxi/check-in only.
            8. If the budget cannot support all expensive attractions, choose fewer paid activities instead of exceeding budget.
            9. Prefer specific real places, restaurants, dishes, addresses/areas, and realistic travel pacing.
            10. Keep notes concise. Do not invent exact official prices when unsure; use "ước tính" or "khoảng".

            Itinerary quality rules:
            1. Return exactly %d days.
            2. Each day must have 4-6 activities.
            3. FOOD/CAFE activities must name a specific dish or restaurant/cafe.
            4. ATTRACTION/ACTIVITY activities must name a specific real place in or near %s.
            5. Do not use generic names like "ăn sáng đặc sản địa phương", "tham quan điểm nổi bật", "khám phá khu vực lân cận", "nhà hàng địa phương", or "cà phê view đẹp".
            6. Days must be clearly different and should not repeat the same activity sequence.

            JSON schema:
            [
              {
                "day": 1,
                "title": "Ngày 1 - Chủ đề ngắn",
                "summary": "Tóm tắt ngắn",
                "activities": [
                  {
                    "time": "08:00",
                    "name": "Tên địa điểm hoặc món/quán cụ thể",
                    "type": "FOOD|CAFE|ATTRACTION|TRANSPORT|ACCOMMODATION|ACTIVITY",
                    "location": "Địa chỉ hoặc khu vực cụ thể",
                    "duration": "1 giờ",
                    "estimatedCost": 50000,
                    "note": "Gợi ý ngắn, có đơn giá nếu là chi phí cố định",
                    "rating": 4.5,
                    "latitude": 11.9403,
                    "longitude": 108.4583
                  }
                ]
              }
            ]
            """,
            req.getDeparture(), req.getDestination(),
            req.getStartDate() != null ? req.getStartDate().toString() : "not provided",
            req.getEndDate() != null ? req.getEndDate().toString() : "not provided",
            days,
            travelers,
            req.getBudgetMode() != null ? req.getBudgetMode() : "PER_PERSON",
            totalBudget,
            perPersonBudget,
            perPersonPerDay,
            req.getStyle(),
            req.getGroupType(),
            req.getOutboundTransport(),
            req.getLocalTransport(),
            req.getMustVisit() != null && !req.getMustVisit().isBlank() ? req.getMustVisit() : "none",
            req.getAvoid() != null && !req.getAvoid().isBlank() ? req.getAvoid() : "none",
            req.getNotes() != null && !req.getNotes().isBlank() ? req.getNotes() : "none",
            travelers,
            days,
            req.getDestination()
        );
    }

    private long resolvePromptTotalBudget(TripDto.GenerateRequest req, int travelers) {
        if ("TOTAL".equalsIgnoreCase(req.getBudgetMode()) && req.getBudgetTotal() != null && req.getBudgetTotal() > 0) {
            return req.getBudgetTotal();
        }
        return Math.max(0, req.getBudgetPerPerson()) * travelers;
    }

    private String callGemini(String prompt) {
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            throw new IllegalStateException("Gemini API key is not configured");
        }
        RestTemplate restTemplate = new RestTemplate();
        String url = String.format(GEMINI_URL, geminiModel, geminiApiKey);

        Map<String, Object> body = Map.of(
            "contents", List.of(Map.of(
                "parts", List.of(Map.of("text", prompt))
            )),
            "generationConfig", Map.of(
                "temperature", 0.35,
                "maxOutputTokens", 20000,
                "responseMimeType", "application/json"
            )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response;
        try {
            response = restTemplate.postForEntity(
                url, new HttpEntity<>(body, headers), String.class
            );
        } catch (HttpStatusCodeException e) {
            throw new RuntimeException("Gemini request failed for model " + geminiModel
                    + " with status " + e.getStatusCode() + ": " + e.getResponseBodyAsString());
        }

        JsonNode root = null;
        try {
            root = objectMapper.readTree(response.getBody());
            JsonNode candidate = root.path("candidates").get(0);
            String finishReason = candidate.path("finishReason").asText("");
            String text = candidate.path("content").path("parts").get(0).path("text").asText();
            log.debug("Gemini response finishReason={}, textLength={}", finishReason, text.length());
            if ("MAX_TOKENS".equals(finishReason)) {
                throw new RuntimeException("Gemini response was truncated by maxOutputTokens");
            }
            if (text.isBlank()) {
                throw new RuntimeException("Gemini response text is empty");
            }
            return text;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Gemini response: " + e.getMessage());
        }
    }

    private List<TripDto.DayResponse> parseItinerary(String json) {
        try {
            // Strip markdown code fences if present
            String cleaned = json.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            JsonNode arr = objectMapper.readTree(cleaned);
            if (!arr.isArray()) {
                throw new IllegalArgumentException("AI response is not a JSON array");
            }

            List<TripDto.DayResponse> result = new ArrayList<>();
            for (JsonNode dayNode : arr) {
                TripDto.DayResponse day = new TripDto.DayResponse();
                day.setDay(dayNode.path("day").asInt());
                day.setTitle(dayNode.path("title").asText());
                day.setSummary(dayNode.path("summary").asText());

                List<TripDto.ActivityResponse> activities = new ArrayList<>();
                int order = 0;
                for (JsonNode actNode : dayNode.path("activities")) {
                    TripDto.ActivityResponse act = new TripDto.ActivityResponse();
                    act.setTime(actNode.path("time").asText("09:00"));
                    act.setName(actNode.path("name").asText());
                    act.setType(actNode.path("type").asText("ATTRACTION"));
                    act.setLocation(actNode.path("location").asText());
                    act.setDuration(actNode.path("duration").asText());
                    act.setEstimatedCost(actNode.path("estimatedCost").asLong(0));
                    act.setNote(actNode.path("note").asText());
                    act.setRating(actNode.path("rating").asDouble(0));
                    if (!actNode.path("latitude").isMissingNode())
                        act.setLatitude(actNode.path("latitude").asDouble());
                    if (!actNode.path("longitude").isMissingNode())
                        act.setLongitude(actNode.path("longitude").asDouble());
                    if (!actNode.path("googlePlaceId").isMissingNode())
                        act.setGooglePlaceId(actNode.path("googlePlaceId").asText());
                    act.setSortOrder(order++);
                    activities.add(act);
                }
                day.setActivities(activities);
                result.add(day);
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse AI itinerary JSON: " + e.getMessage()
                    + ". Raw length=" + (json != null ? json.length() : 0), e);
        }
    }

    private QualityCheck assessItineraryQuality(List<TripDto.DayResponse> days, int expectedDays) {
        if (days == null) {
            return QualityCheck.fail("response has no days");
        }
        if (days.size() != expectedDays) {
            return QualityCheck.fail("expected " + expectedDays + " days but got " + days.size());
        }

        Set<String> dayFingerprints = new HashSet<>();
        int genericActivities = 0;
        int totalActivities = 0;
        for (TripDto.DayResponse day : days) {
            if (day.getActivities() == null || day.getActivities().size() < 4) {
                return QualityCheck.fail("day " + day.getDay() + " has fewer than 4 activities");
            }
            StringBuilder fingerprint = new StringBuilder();
            for (TripDto.ActivityResponse act : day.getActivities()) {
                totalActivities++;
                String name = normalize(act.getName());
                String location = normalize(act.getLocation());
                fingerprint.append(name).append("|");
                if (isGenericActivity(name, location, normalize(act.getType()))) {
                    genericActivities++;
                }
            }
            dayFingerprints.add(fingerprint.toString());
        }

        if (days.size() > 1 && dayFingerprints.size() == 1) {
            return QualityCheck.fail("all days have identical activity sequences");
        }

        int maxGenericAllowed = Math.max(3, totalActivities / 2);
        if (genericActivities > maxGenericAllowed) {
            return QualityCheck.fail("too many generic activities: " + genericActivities + "/" + totalActivities);
        }

        return QualityCheck.pass();
    }

    private boolean isGenericActivity(String normalizedName, String normalizedLocation, String normalizedType) {
        List<String> genericTerms = List.of(
                "an sang dac san dia phuong",
                "an trua dac san dia phuong",
                "an toi dac san dia phuong",
                "dac san dia phuong",
                "diem noi bat",
                "khu vuc lan can",
                "nha hang dia phuong",
                "quan dia phuong",
                "ca phe view dep",
                "cum diem bieu tuong",
                "khu tham quan chinh",
                "trai nghiem van hoa hoac thien nhien"
        );
        boolean genericName = genericTerms.stream().anyMatch(normalizedName::contains);
        boolean weakLocation = normalizedLocation.isBlank()
                || normalizedLocation.equals("khu trung tam")
                || normalizedLocation.equals("khu dem")
                || normalizedLocation.contains("khu trung tam")
                || normalizedLocation.contains("khu tham quan chinh")
                || normalizedLocation.contains("khu vuc vung ven")
                || normalizedLocation.contains("khu view dep")
                || normalizedLocation.contains("khu an toi")
                || normalizedLocation.length() < 6;
        boolean transportOrAccommodation = normalizedType.equals("transport") || normalizedType.equals("accommodation");
        return !transportOrAccommodation && genericName && weakLocation;
    }

    private String normalize(String value) {
        if (value == null) return "";
        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace("đ", "d")
                .replace("Đ", "D")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private record QualityCheck(boolean passed, String reason) {
        static QualityCheck pass() {
            return new QualityCheck(true, "ok");
        }

        static QualityCheck fail(String reason) {
            return new QualityCheck(false, reason);
        }
    }
}
