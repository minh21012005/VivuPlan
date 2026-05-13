package com.vivuplan.vivuplan_be.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivuplan.vivuplan_be.dto.TripDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiService {

    @Value("${app.ai.gemini.api-key:}")
    private String geminiApiKey;

    @Value("${app.ai.gemini.model:gemini-1.5-flash}")
    private String geminiModel;

    private final ObjectMapper objectMapper;

    private static final String GEMINI_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    public List<TripDto.DayResponse> generateItinerary(TripDto.GenerateRequest req) {
        String prompt = buildPrompt(req);
        log.info("Generating itinerary for: {} - {}N", req.getDestination(), req.getDays());

        try {
            String rawJson = callGemini(prompt);
            return parseItinerary(rawJson, req.getDays(), req.getDestination());
        } catch (Exception e) {
            log.error("AI generation failed, using fallback: {}", e.getMessage());
            return buildFallbackItinerary(req);
        }
    }

    private String buildPrompt(TripDto.GenerateRequest req) {
        long budgetK = req.getBudgetPerPerson() / 1000;
        return String.format("""
            Bạn là chuyên gia du lịch Việt Nam. Hãy tạo lịch trình du lịch CHI TIẾT và THỰC TẾ.

            THÔNG TIN CHUYẾN ĐI:
            - Điểm đến: %s
            - Thời gian: %d ngày
            - Ngân sách: %dk VND/người/ngày (tổng %dk VND)
            - Phong cách: %s
            - Nhóm: %s
            - Phương tiện: %s
            - Ghi chú: %s

            YÊU CẦU:
            1. Chỉ đề xuất địa điểm THỰC TẾ tồn tại tại %s
            2. Sắp xếp hoạt động theo địa lý để giảm di chuyển
            3. Ước tính chi phí THỰC TẾ theo thị trường Việt Nam hiện tại
            4. Thời gian hoạt động phù hợp (không nhồi nhét)
            5. Bao gồm giờ mở cửa thực tế của từng địa điểm
            6. Mỗi ngày 4-7 hoạt động

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
            req.getDestination(), req.getDays(), budgetK / req.getDays(), budgetK,
            req.getStyle(), req.getGroupType(), req.getTransport(),
            req.getNotes() != null ? req.getNotes() : "Không có",
            req.getDestination()
        );
    }

    private String callGemini(String prompt) {
        RestTemplate restTemplate = new RestTemplate();
        String url = String.format(GEMINI_URL, geminiModel, geminiApiKey);

        Map<String, Object> body = Map.of(
            "contents", List.of(Map.of(
                "parts", List.of(Map.of("text", prompt))
            )),
            "generationConfig", Map.of(
                "temperature", 0.7,
                "maxOutputTokens", 8192,
                "responseMimeType", "application/json"
            )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.postForEntity(
            url, new HttpEntity<>(body, headers), String.class
        );

        JsonNode root = null;
        try {
            root = objectMapper.readTree(response.getBody());
            return root.path("candidates").get(0)
                       .path("content").path("parts").get(0)
                       .path("text").asText();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Gemini response: " + e.getMessage());
        }
    }

    private List<TripDto.DayResponse> parseItinerary(String json, int days, String destination) {
        try {
            // Strip markdown code fences if present
            String cleaned = json.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            JsonNode arr = objectMapper.readTree(cleaned);

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
                    act.setSortOrder(order++);
                    activities.add(act);
                }
                day.setActivities(activities);
                result.add(day);
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to parse AI itinerary JSON: {}", e.getMessage());
            return buildFallbackItinerary(null);
        }
    }

    /** Fallback khi API key chưa cấu hình hoặc lỗi */
    private List<TripDto.DayResponse> buildFallbackItinerary(TripDto.GenerateRequest req) {
        int days = req != null ? req.getDays() : 3;
        String dest = req != null ? req.getDestination() : "Điểm đến";
        List<TripDto.DayResponse> result = new ArrayList<>();

        for (int d = 1; d <= days; d++) {
            TripDto.DayResponse day = new TripDto.DayResponse();
            day.setDay(d);
            day.setTitle("Ngày " + d + " – Khám phá " + dest);
            day.setSummary("Khám phá những điểm nổi bật của " + dest);

            List<TripDto.ActivityResponse> acts = new ArrayList<>();
            String[][] templates = {
                {"07:30", "Ăn sáng đặc sản địa phương", "FOOD", "Khu trung tâm", "45 phút", "35000", "Thử đặc sản địa phương buổi sáng"},
                {"09:00", "Tham quan điểm nổi bật", "ATTRACTION", dest, "2 giờ", "50000", "Check-in và khám phá"},
                {"12:00", "Ăn trưa nhà hàng địa phương", "FOOD", "Khu trung tâm", "1 giờ", "80000", "Thử ẩm thực địa phương"},
                {"14:00", "Khám phá khu vực lân cận", "ATTRACTION", dest, "2 giờ", "30000", "Đi bộ và chụp ảnh"},
                {"16:30", "Cà phê view đẹp", "CAFE", dest, "1 giờ", "50000", "Nghỉ ngơi và thưởng thức cà phê"},
                {"19:00", "Ăn tối và chợ đêm", "FOOD", "Khu đêm", "1.5 giờ", "100000", "Thử đồ ăn đêm địa phương"},
            };

            for (int i = 0; i < templates.length; i++) {
                TripDto.ActivityResponse a = new TripDto.ActivityResponse();
                a.setId((long)(d * 100 + i));
                a.setTime(templates[i][0]);
                a.setName(templates[i][1]);
                a.setType(templates[i][2]);
                a.setLocation(templates[i][3]);
                a.setDuration(templates[i][4]);
                a.setEstimatedCost(Long.parseLong(templates[i][5]));
                a.setNote(templates[i][6]);
                a.setRating(4.2 + (i * 0.1));
                a.setSortOrder(i);
                acts.add(a);
            }
            day.setActivities(acts);
            result.add(day);
        }
        return result;
    }
}
