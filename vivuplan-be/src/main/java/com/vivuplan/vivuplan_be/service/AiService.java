package com.vivuplan.vivuplan_be.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivuplan.vivuplan_be.dto.TripDto;
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

    public List<TripDto.DayResponse> generateItinerary(TripDto.GenerateRequest req) {
        String prompt = buildPrompt(req);
        log.info("Generating itinerary for: {} - {}N using Gemini model {}", req.getDestination(), req.getDays(), geminiModel);

        try {
            String rawJson = callGemini(prompt);
            return parseItinerary(rawJson, req);
        } catch (Exception e) {
            log.error("AI generation failed with Gemini model {}, using fallback: {}", geminiModel, e.getMessage());
            return buildFallbackItinerary(req);
        }
    }

    private String buildPrompt(TripDto.GenerateRequest req) {
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

    private List<TripDto.DayResponse> parseItinerary(String json, TripDto.GenerateRequest req) {
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
            if (isLowQualityItinerary(result, req.getDays())) {
                log.warn("AI itinerary for {} was too generic or repetitive, using curated fallback", req.getDestination());
                return buildFallbackItinerary(req);
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to parse AI itinerary JSON: {}. Raw length={}", e.getMessage(), json != null ? json.length() : 0);
            return buildFallbackItinerary(req);
        }
    }

    /** Fallback khi API key chưa cấu hình hoặc lỗi */
    private List<TripDto.DayResponse> buildFallbackItinerary(TripDto.GenerateRequest req) {
        int days = req != null ? req.getDays() : 3;
        String dest = req != null ? req.getDestination() : "Điểm đến";
        String departure = req != null && req.getDeparture() != null ? req.getDeparture() : "điểm xuất phát";
        if (normalize(dest).contains("hoi an")) {
            return buildHoiAnFallback(days, departure);
        }
        return buildGeneralVietnamFallback(days, dest, departure);
    }

    private List<TripDto.DayResponse> buildHoiAnFallback(int days, String departure) {
        List<TripDto.DayResponse> result = new ArrayList<>();
        List<DayTemplate> templates = List.of(
                new DayTemplate(
                        "Phố cổ Hội An và ẩm thực đặc trưng",
                        "Di chuyển từ " + departure + " đến Hội An, nhận phòng rồi đi bộ cụm phố cổ, chùa Cầu, nhà cổ và chợ đêm.",
                        List.of(
                                new ActivityTemplate("07:00", "Di chuyển " + departure + " → Đà Nẵng", "TRANSPORT", "Điểm xuất phát " + departure + " đến Đà Nẵng", "1-2 giờ nếu bay, lâu hơn nếu đi tàu/xe", 1200000, "Nên chọn chuyến sáng để còn nửa ngày khám phá Hội An; chi phí thay đổi theo phương tiện và thời điểm đặt vé.", 4.3, null, null),
                                new ActivityTemplate("09:15", "Xe đưa đón Đà Nẵng → Hội An", "TRANSPORT", "Sân bay Đà Nẵng đến khu phố cổ Hội An", "45-60 phút", 120000, "Đi shuttle hoặc xe ghép; nếu đi 2 người có thể cân nhắc taxi/Grab khoảng 350k-450k/xe.", 4.2, 16.0544, 108.2022),
                                new ActivityTemplate("11:00", "Gửi hành lý và ăn bánh mì Phượng", "FOOD", "Bánh mì Phượng, 2B Phan Chu Trinh, Hội An", "45 phút", 35000, "Gọi bánh mì thập cẩm hoặc bánh mì gà; nên đi trước giờ trưa để đỡ xếp hàng.", 4.5, 15.8797, 108.3298),
                                new ActivityTemplate("14:00", "Chùa Cầu Nhật Bản", "ATTRACTION", "Nguyễn Thị Minh Khai, phường Minh An, Hội An", "45 phút", 0, "Đi bộ từ trung tâm phố cổ, chụp ảnh và nghe câu chuyện thương cảng Hội An.", 4.4, 15.8777, 108.3268),
                                new ActivityTemplate("15:00", "Nhà cổ Tấn Ký và Hội quán Phúc Kiến", "ATTRACTION", "101 Nguyễn Thái Học và 46 Trần Phú, Hội An", "1 giờ 30 phút", 120000, "Mua vé tham quan phố cổ, chọn vài điểm tiêu biểu thay vì đi dàn trải.", 4.4, 15.8765, 108.3277),
                                new ActivityTemplate("16:45", "Faifo Coffee ngắm mái ngói phố cổ", "CAFE", "Faifo Coffee, 130 Trần Phú, Hội An", "1 giờ", 65000, "Gọi cà phê dừa hoặc cold brew, lên rooftop lúc chiều muộn để chụp phố cổ từ trên cao.", 4.3, 15.8775, 108.3287),
                                new ActivityTemplate("18:30", "Ăn cao lầu Thanh", "FOOD", "Cao lầu Thanh, 26 Thái Phiên, Hội An", "1 giờ", 60000, "Nên gọi cao lầu và thêm nước mót; đây là món phải thử ở Hội An.", 4.3, 15.8795, 108.3305),
                                new ActivityTemplate("20:00", "Chợ đêm Nguyễn Hoàng và thả hoa đăng", "ATTRACTION", "Đường Nguyễn Hoàng, ven sông Hoài, Hội An", "1 giờ 30 phút", 50000, "Đi dọc chợ đêm, xem đèn lồng và có thể thả hoa đăng trên sông Hoài.", 4.4, 15.8763, 108.3252)
                        )
                ),
                new DayTemplate(
                        "Làng rau Trà Quế, rừng dừa Cẩm Thanh và biển An Bàng",
                        "Một ngày nhẹ hơn ở vùng ven Hội An: đạp/đi xe máy qua làng rau, trải nghiệm thuyền thúng và kết thúc ở biển.",
                        List.of(
                                new ActivityTemplate("07:30", "Ăn sáng mì Quảng Ông Hai", "FOOD", "Mì Quảng Ông Hai, 6A Trương Minh Lượng, Hội An", "45 phút", 45000, "Gọi mì Quảng gà hoặc tôm thịt, ăn sáng no trước khi đi vùng ven.", 4.2, 15.8808, 108.3338),
                                new ActivityTemplate("08:45", "Làng rau Trà Quế", "ATTRACTION", "Thôn Trà Quế, Cẩm Hà, Hội An", "1 giờ 30 phút", 35000, "Thuê xe máy/xe đạp đi từ phố cổ; trải nghiệm vườn rau, chụp ảnh đường làng.", 4.3, 15.9001, 108.3346),
                                new ActivityTemplate("11:30", "Ăn cơm gà Bà Buội", "FOOD", "Cơm gà Bà Buội, 22 Phan Chu Trinh, Hội An", "1 giờ", 70000, "Gọi cơm gà xé hoặc cơm gà đùi; quán đông nên tránh đúng 12:00 nếu có thể.", 4.2, 15.8795, 108.3296),
                                new ActivityTemplate("14:00", "Rừng dừa Bảy Mẫu Cẩm Thanh", "ACTIVITY", "Cẩm Thanh, Hội An", "1 giờ 30 phút", 150000, "Đi thuyền thúng, xem biểu diễn quay thúng; nên hỏi giá trọn gói trước khi lên thuyền.", 4.3, 15.8617, 108.3761),
                                new ActivityTemplate("16:15", "Biển An Bàng", "ATTRACTION", "Đường Hai Bà Trưng, Cẩm An, Hội An", "1 giờ 30 phút", 30000, "Đi chiều mát, thuê ghế/nghỉ biển; phù hợp cặp đôi hơn Cửa Đại nếu muốn không khí nhẹ.", 4.4, 15.9137, 108.3398),
                                new ActivityTemplate("18:30", "Ăn tối tại Soul Kitchen An Bàng", "FOOD", "Soul Kitchen, biển An Bàng, Hội An", "1 giờ 30 phút", 220000, "Gọi hải sản, pizza hoặc món Việt nhẹ; chọn bàn gần biển nếu còn chỗ.", 4.4, 15.9144, 108.3394),
                                new ActivityTemplate("20:30", "Uống nước mót và dạo phố cổ", "CAFE", "Mót Hội An, 150 Trần Phú, Hội An", "45 phút", 25000, "Gọi nước thảo mộc mót rồi đi bộ đoạn Trần Phú - Bạch Đằng buổi tối.", 4.5, 15.8776, 108.3291)
                        )
                ),
                new DayTemplate(
                        "Thánh địa Mỹ Sơn và mua đặc sản trước khi về",
                        "Đi Mỹ Sơn buổi sáng để hiểu văn hóa Chăm Pa, quay lại Hội An ăn trưa, mua quà rồi di chuyển ra Đà Nẵng.",
                        List.of(
                                new ActivityTemplate("07:00", "Ăn sáng bánh bao bánh vạc Hoa Hồng Trắng", "FOOD", "White Rose Restaurant, 533 Hai Bà Trưng, Hội An", "45 phút", 70000, "Gọi bánh bao bánh vạc và hoành thánh chiên, hợp để thử món đặc trưng Hội An.", 4.1, 15.8844, 108.3296),
                                new ActivityTemplate("08:00", "Thánh địa Mỹ Sơn", "ATTRACTION", "Duy Phú, Duy Xuyên, Quảng Nam", "3 giờ", 180000, "Đi sớm để tránh nắng; xem cụm đền Chăm và show múa Chăm nếu khớp giờ.", 4.4, 15.7652, 108.1220),
                                new ActivityTemplate("12:45", "Ăn trưa bánh xèo Giếng Bá Lễ", "FOOD", "Bánh xèo Giếng Bá Lễ, 45/51 Trần Hưng Đạo, Hội An", "1 giờ", 90000, "Gọi bánh xèo, nem lụi và cuốn rau sống; phù hợp nếu muốn bữa trưa đậm vị miền Trung.", 4.2, 15.8813, 108.3292),
                                new ActivityTemplate("14:15", "Chợ Hội An mua đặc sản", "ATTRACTION", "Chợ Hội An, Trần Phú - Bạch Đằng", "1 giờ", 100000, "Mua bánh đậu xanh, tương ớt Hội An hoặc cà phê làm quà; nhớ hỏi giá trước.", 4.1, 15.8763, 108.3300),
                                new ActivityTemplate("15:30", "Cà phê The Espresso Station", "CAFE", "The Espresso Station, 28/2 Trần Hưng Đạo, Hội An", "45 phút", 60000, "Gọi cà phê muối hoặc ice cube coffee trước khi rời phố cổ.", 4.5, 15.8809, 108.3289),
                                new ActivityTemplate("16:30", "Di chuyển Hội An → Đà Nẵng", "TRANSPORT", "Hội An đến sân bay/ga trung tâm Đà Nẵng", "45-60 phút", 120000, "Canh giờ ra sân bay/ga trước giờ khởi hành ít nhất 2 tiếng nếu quay về " + departure + ".", 4.2, 16.0544, 108.2022),
                                new ActivityTemplate("19:00", "Di chuyển Đà Nẵng → " + departure, "TRANSPORT", "Đà Nẵng về " + departure, "1-2 giờ nếu bay, lâu hơn nếu đi tàu/xe", 1200000, "Nếu ngân sách 2 triệu/người, nên đặt vé sớm hoặc coi ngân sách này là chưa gồm vé di chuyển đường dài.", 4.3, 16.0544, 108.2022)
                        )
                ),
                new DayTemplate(
                        "Làng gốm Thanh Hà và lớp nấu ăn Hội An",
                        "Dành thêm một ngày cho trải nghiệm thủ công, chợ địa phương và học nấu món Quảng.",
                        List.of(
                                new ActivityTemplate("07:30", "Ăn sáng phở Tùng Hội An", "FOOD", "Phở Tùng, 51/7 Phan Châu Trinh, Hội An", "45 phút", 50000, "Gọi phở bò hoặc mì bò, phù hợp bữa sáng nhanh trước khi đi làng nghề.", 4.1, 15.8798, 108.3297),
                                new ActivityTemplate("09:00", "Làng gốm Thanh Hà", "ACTIVITY", "Phạm Phán, Thanh Hà, Hội An", "2 giờ", 50000, "Thử nặn gốm, xem lò gốm và ghé công viên đất nung nếu thích chụp ảnh.", 4.2, 15.8819, 108.3077),
                                new ActivityTemplate("12:00", "Ăn trưa quán Bale Well", "FOOD", "Bale Well, 45/51 Trần Hưng Đạo, Hội An", "1 giờ", 100000, "Gọi set cuốn bánh xèo, thịt nướng, nem lụi; no và hợp nhóm/cặp đôi.", 4.3, 15.8811, 108.3291),
                                new ActivityTemplate("14:30", "Lớp nấu ăn món Quảng", "ACTIVITY", "Khu Cẩm Thanh hoặc Trà Quế, Hội An", "2 giờ 30 phút", 450000, "Chọn lớp có đi chợ và nấu cao lầu/mì Quảng; nên đặt trước một ngày.", 4.6, 15.8620, 108.3760),
                                new ActivityTemplate("18:30", "Ăn tối Morning Glory Original", "FOOD", "Morning Glory Original, 106 Nguyễn Thái Học, Hội An", "1 giờ 30 phút", 220000, "Gọi cao lầu, hoành thánh chiên và thịt kho kiểu Hội An nếu muốn bữa tối chỉn chu.", 4.3, 15.8769, 108.3278),
                                new ActivityTemplate("20:15", "Dạo bờ sông Bạch Đằng", "ATTRACTION", "Đường Bạch Đằng, ven sông Hoài, Hội An", "1 giờ", 0, "Đi bộ nhẹ sau bữa tối, ngắm thuyền và đèn lồng ven sông.", 4.4, 15.8759, 108.3264)
                        )
                )
        );

        for (int i = 0; i < days; i++) {
            result.add(toDayResponse(i + 1, templates.get(Math.min(i, templates.size() - 1))));
        }
        return result;
    }

    private List<TripDto.DayResponse> buildGeneralVietnamFallback(int days, String dest, String departure) {
        List<TripDto.DayResponse> result = new ArrayList<>();

        for (int d = 1; d <= days; d++) {
            TripDto.DayResponse day = new TripDto.DayResponse();
            day.setDay(d);
            String theme = switch ((d - 1) % 3) {
                case 0 -> "trung tâm và món đặc trưng";
                case 1 -> "vùng ven và trải nghiệm địa phương";
                default -> "điểm biểu tượng và mua quà";
            };
            day.setTitle("Ngày " + d + " – " + dest + ": " + theme);
            day.setSummary((d == 1 ? "Xuất phát từ " + departure + ", " : "") + "Ưu tiên hoạt động cụ thể, món ăn địa phương và nhịp di chuyển vừa phải tại " + dest + ".");
            day.setActivities(List.of(
                    activity(d, 0, "07:30", "Ăn sáng món đặc trưng tại khu trung tâm " + dest, "FOOD", "Khu trung tâm " + dest, "45 phút", 50000, "Chọn quán đông khách địa phương, ưu tiên món nổi tiếng của " + dest + " thay vì buffet khách sạn.", 4.2, null, null),
                    activity(d, 1, "09:00", "Tham quan cụm điểm biểu tượng của " + dest, "ATTRACTION", "Khu tham quan chính tại " + dest, "2 giờ", 80000, "Chọn 1-2 điểm gần nhau, hỏi giờ mở cửa trước khi đi.", 4.3, null, null),
                    activity(d, 2, "12:00", "Ăn trưa tại quán địa phương được đánh giá tốt", "FOOD", "Khu trung tâm " + dest, "1 giờ", 90000, "Gọi món đặc sản vùng, tránh quán chỉ phục vụ tour nếu muốn trải nghiệm thật hơn.", 4.2, null, null),
                    activity(d, 3, "14:30", "Trải nghiệm văn hóa hoặc thiên nhiên vùng ven " + dest, "ACTIVITY", "Khu vực vùng ven " + dest, "2 giờ", 120000, "Dành buổi chiều cho hoạt động ít trùng lặp với buổi sáng.", 4.3, null, null),
                    activity(d, 4, "17:00", "Cà phê/ngắm hoàng hôn tại điểm view đẹp", "CAFE", "Khu view đẹp tại " + dest, "1 giờ", 70000, "Chọn quán có không gian nghỉ chân, tránh lịch quá dày.", 4.2, null, null),
                    activity(d, 5, "19:00", "Ăn tối và dạo khu đêm địa phương", "FOOD", "Khu ăn tối/chợ đêm tại " + dest, "1 giờ 30 phút", 130000, "Kết thúc ngày bằng món địa phương và đi bộ nhẹ.", 4.3, null, null)
            ));
            result.add(day);
        }
        return result;
    }

    private TripDto.DayResponse toDayResponse(int dayNumber, DayTemplate template) {
        TripDto.DayResponse day = new TripDto.DayResponse();
        day.setDay(dayNumber);
        day.setTitle("Ngày " + dayNumber + " – " + template.title());
        day.setSummary(template.summary());
        List<TripDto.ActivityResponse> activities = new ArrayList<>();
        for (int i = 0; i < template.activities().size(); i++) {
            activities.add(activity(dayNumber, i, template.activities().get(i)));
        }
        day.setActivities(activities);
        return day;
    }

    private TripDto.ActivityResponse activity(int dayNumber, int order, ActivityTemplate template) {
        return activity(dayNumber, order, template.time(), template.name(), template.type(), template.location(),
                template.duration(), template.estimatedCost(), template.note(), template.rating(),
                template.latitude(), template.longitude());
    }

    private TripDto.ActivityResponse activity(int dayNumber, int order, String time, String name, String type,
                                              String location, String duration, long estimatedCost, String note,
                                              double rating, Double latitude, Double longitude) {
        TripDto.ActivityResponse a = new TripDto.ActivityResponse();
        a.setId((long) (dayNumber * 100 + order));
        a.setTime(time);
        a.setName(name);
        a.setType(type);
        a.setLocation(location);
        a.setDuration(duration);
        a.setEstimatedCost(estimatedCost);
        a.setNote(note);
        a.setRating(rating);
        a.setLatitude(latitude);
        a.setLongitude(longitude);
        a.setSortOrder(order);
        return a;
    }

    private boolean isLowQualityItinerary(List<TripDto.DayResponse> days, int expectedDays) {
        if (days == null || days.size() != expectedDays) return true;
        Set<String> dayFingerprints = new HashSet<>();
        int genericActivities = 0;
        int totalActivities = 0;
        for (TripDto.DayResponse day : days) {
            if (day.getActivities() == null || day.getActivities().size() < 4) return true;
            StringBuilder fingerprint = new StringBuilder();
            for (TripDto.ActivityResponse act : day.getActivities()) {
                totalActivities++;
                String name = normalize(act.getName());
                String location = normalize(act.getLocation());
                fingerprint.append(name).append("|");
                if (isGenericActivity(name, location)) {
                    genericActivities++;
                }
            }
            dayFingerprints.add(fingerprint.toString());
        }
        if (days.size() > 1 && dayFingerprints.size() == 1) return true;
        return totalActivities > 0 && genericActivities >= Math.max(2, totalActivities / 3);
    }

    private boolean isGenericActivity(String normalizedName, String normalizedLocation) {
        List<String> genericTerms = List.of(
                "dac san dia phuong",
                "diem noi bat",
                "khu vuc lan can",
                "nha hang dia phuong",
                "ca phe view dep",
                "cho dem",
                "tham quan",
                "kham pha"
        );
        boolean genericName = genericTerms.stream().anyMatch(normalizedName::contains);
        boolean weakLocation = normalizedLocation.isBlank()
                || normalizedLocation.equals("khu trung tam")
                || normalizedLocation.equals("khu dem")
                || normalizedLocation.length() < 6;
        return genericName || weakLocation;
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

    private record DayTemplate(String title, String summary, List<ActivityTemplate> activities) {}

    private record ActivityTemplate(String time, String name, String type, String location, String duration,
                                    long estimatedCost, String note, double rating, Double latitude, Double longitude) {}
}
