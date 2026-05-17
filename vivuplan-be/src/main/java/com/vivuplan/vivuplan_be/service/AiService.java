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

    public record GeneratedItineraryResult(
            List<TripDto.DayResponse> days,
            TripDto.RequestFulfillment requestFulfillment) {
    }

    public record RegeneratedDayResult(
            TripDto.DayResponse day,
            TripDto.RequestFulfillment requestFulfillment) {
    }

    @Value("${app.ai.gemini.api-key:}")
    private String geminiApiKey;

    @Value("${app.ai.gemini.model:gemini-2.5-flash}")
    private String geminiModel;

    private final ObjectMapper objectMapper;

    private static final String GEMINI_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";
    private static final String AI_GENERATION_USER_MESSAGE =
            "AI chưa tạo được lịch trình đủ cụ thể cho chuyến đi này. Vui lòng thử lại hoặc bổ sung thêm điểm muốn ghé, điều cần tránh hay ghi chú để VivuPlan lập lại lịch trình.";

    public GeneratedItineraryResult generateItinerary(TripDto.GenerateRequest req) {
        String prompt = buildPrompt(req);
        log.info("Generating itinerary for: {} - {}N using Gemini model {}", req.getDestination(), req.getDays(), geminiModel);

        try {
            String rawJson = callGemini(prompt);
            GeneratedItineraryResult result = parseGeneratedItineraryResult(rawJson);
            QualityCheck quality = assessItineraryQuality(result.days(), req);
            if (quality.passed()) {
                return result;
            }

            log.warn("AI itinerary for {} failed quality check: {}. Retrying once with stricter prompt.",
                    req.getDestination(), quality.reason());

            String retryJson = callGemini(buildQualityRetryPrompt(req, quality.reason()));
            GeneratedItineraryResult retryResult = parseGeneratedItineraryResult(retryJson);
            QualityCheck retryQuality = assessItineraryQuality(retryResult.days(), req);
            if (retryQuality.passed()) {
                return retryResult;
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

    public RegeneratedDayResult regenerateDay(
            TripDto.GenerateRequest req,
            List<TripDto.DayResponse> currentSchedule,
            int dayNumber,
            String intent,
            String instruction
    ) {
        log.info("Regenerating day {} for trip to {} using intent {}", dayNumber, req.getDestination(), intent);

        try {
            TripDto.GenerateRequest qualityReq = withRegenerationInstruction(req, instruction);
            String rawJson = callGeminiForSingleDay(buildDayRegenerationPrompt(req, currentSchedule, dayNumber, intent, instruction, null));
            RegeneratedDayResult result = parseRegeneratedDayResult(rawJson, dayNumber);
            QualityCheck quality = assessRegeneratedDayQuality(result.day(), currentSchedule, qualityReq);
            if (quality.passed()) {
                return result;
            }

            log.warn("AI regenerated day {} for {} failed quality check: {}. Retrying once.",
                    dayNumber, req.getDestination(), quality.reason());

            String retryJson = callGeminiForSingleDay(buildDayRegenerationPrompt(req, currentSchedule, dayNumber, intent, instruction, quality.reason()));
            RegeneratedDayResult retryResult = parseRegeneratedDayResult(retryJson, dayNumber);
            QualityCheck retryQuality = assessRegeneratedDayQuality(retryResult.day(), currentSchedule, qualityReq);
            if (retryQuality.passed()) {
                return retryResult;
            }

            log.warn("AI retry regenerated day {} for {} still failed quality check: {}.",
                    dayNumber, req.getDestination(), retryQuality.reason());
            throw new AiGenerationException("AI chưa tạo được phương án chỉnh ngày này đủ tốt. Vui lòng thử lại với yêu cầu cụ thể hơn.");
        } catch (AiGenerationException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI day regeneration failed with Gemini model {}: {}", geminiModel, e.getMessage(), e);
            throw new AiGenerationException("AI chưa tạo được phương án chỉnh ngày này đủ tốt. Vui lòng thử lại với yêu cầu cụ thể hơn.", e);
        }
    }

    private TripDto.GenerateRequest withRegenerationInstruction(TripDto.GenerateRequest source, String instruction) {
        TripDto.GenerateRequest copy = new TripDto.GenerateRequest();
        copy.setDestination(source.getDestination());
        copy.setDeparture(source.getDeparture());
        copy.setStartDate(source.getStartDate());
        copy.setEndDate(source.getEndDate());
        copy.setDays(source.getDays());
        copy.setBudgetPerPerson(source.getBudgetPerPerson());
        copy.setBudgetTotal(source.getBudgetTotal());
        copy.setBudgetMode(source.getBudgetMode());
        copy.setTravelerCount(source.getTravelerCount());
        copy.setStyle(source.getStyle());
        copy.setGroupType(source.getGroupType());
        copy.setTransport(source.getTransport());
        copy.setOutboundTransport(source.getOutboundTransport());
        copy.setLocalTransport(source.getLocalTransport());
        copy.setDestinationSuggested(source.getDestinationSuggested());
        copy.setMustVisit(source.getMustVisit());
        copy.setAvoid(source.getAvoid());
        String mergedNotes = String.join("\n",
                source.getNotes() != null ? source.getNotes() : "",
                instruction != null && !instruction.isBlank() ? "Yêu cầu chỉnh ngày: " + instruction : ""
        ).trim();
        copy.setNotes(mergedNotes);
        return copy;
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
            Add a clear local transportation plan with TRANSPORT activities for getting around %s. Do not hide rental, taxi, Grab, walking, bicycle, or local transfer costs inside FOOD/CAFE/ATTRACTION notes.
            Include all required paid transport, rental, lodging, ticket, and tour costs in estimatedCost. Do not mark required costs as not included.
            Before returning, sum every activity.estimatedCost and keep the total at or below the total group budget unless the required outbound/return transport alone makes that impossible.
            If the budget is tight, keep required transport realistic but reduce optional paid attractions, tours, premium meals, shopping, and accommodation comfort instead of exceeding budget.
            If realistic required costs make the budget impossible, return realistic costs anyway. Never understate costs to fit the budget.
            Do not repeat the same day structure across days.
            """, reason, req.getDestination(), req.getDestination());
    }

    private String buildDayRegenerationPrompt(
            TripDto.GenerateRequest req,
            List<TripDto.DayResponse> currentSchedule,
            int dayNumber,
            String intent,
            String instruction,
            String retryReason
    ) {
        int travelers = req.getTravelerCount() != null ? Math.max(1, req.getTravelerCount()) : 1;
        long totalBudget = resolvePromptTotalBudget(req, travelers);
        String scheduleJson = slimScheduleJson(currentSchedule);

        String retryBlock = retryReason == null || retryReason.isBlank()
                ? ""
                : String.format("""

                    IMPORTANT RETRY INSTRUCTION:
                    The previous proposal was rejected because: %s
                    Fix that issue. Return a safer, more specific version of day %d only.
                    The regenerated day should contain 4-6 activities. Never return more than 8 activities.
                    If the day has 4 or more FOOD/CAFE/ATTRACTION/ACTIVITY items, one of the activities MUST be a local TRANSPORT item.
                    Create a separate TRANSPORT activity with route/mode/cost instead of putting transport cost in an ATTRACTION, FOOD, CAFE, or ACTIVITY note.
                    Include all required paid transport, rental, lodging, ticket, and tour costs in estimatedCost. Do not mark required costs as not included.
                    """, retryReason, dayNumber);

        return String.format("""
            You are a senior Vietnam travel planner. Regenerate ONE DAY of an existing itinerary.
            Return JSON only. All user-facing text must be Vietnamese with correct accents.
            The response MUST be one JSON object with keys "day" and "requestFulfillment".

            Trip constraints that MUST NOT change:
            - Departure: %s
            - Destination: %s
            - Start date: %s
            - End date: %s
            - Trip duration: %d days
            - Travelers: %d
            - Budget mode: %s
            - Total group budget ceiling: %,d VND
            - Style: %s
            - Group: %s
            - Outbound transport: %s
            - Local transport: %s
            - Must visit: %s
            - Avoid: %s
            - Notes: %s
            - Weather Forecast (per trip day): %s

            Weather-aware planning rules:
            1. Each forecast line is "Day N (date): condition, temp, rain chance → risk level".
            2. For the day being regenerated, honor its risk level: "HIGH RAIN RISK" → indoor activities only; "LIGHT RAIN" → mostly indoor, short outdoor if rain < 60%%; "Good weather" → outdoor preferred.
            3. Never mention the weather in the regenerated day's title, summary, activities, or notes. Just naturally plan appropriate activities.
               If weather or another constraint blocks the user's request, explain that in requestFulfillment.items[].userMessage.
            4. If forecast is "none", plan normally without weather constraints.

            Regeneration task:
            - Regenerate day number: %d
            - User free-form request: %s
            - Fallback intent if request is empty: %s

            Current full itinerary JSON:
            %s

            Rules:
            1. Return exactly ONE JSON object. Its "day" key MUST contain exactly ONE day object whose day value is %d.
            2. Do not change other days. Use them only as context to avoid duplicate places and impossible pacing.
            3. Keep 4-6 activities for the regenerated day. Never return more than 8 activities.
            4. Keep times in HH:mm 24h format and avoid overlaps.
            5. estimatedCost MUST be total VND for the whole group of %d travelers.
            5a. Never set estimatedCost to 0 for paid intercity transport such as flights, trains, buses, private cars, airport transfers, vehicle rental pickup, lodging, tickets, tours, shows, or paid experiences.
            5b. If a note mentions a required price, that price MUST be included in estimatedCost. Do not write "not included", "khong bao gom", or "chua bao gom" for required trip costs.
            5c. Check-in/check-out or returning a rented vehicle may be 0 only when the actual lodging or rental fee is already counted in another activity.
            6. Preserve user constraints: avoid banned items, respect must-visit where relevant, respect style/group.
            7. If Local transport is not MIXED, local transport activities must follow that selected mode unless clearly impractical and explained.
            8. Add explicit TRANSPORT activities for moving between clusters or inside %s. If the regenerated day has 4 or more FOOD/CAFE/ATTRACTION/ACTIVITY items, one activity MUST be local TRANSPORT. Do not hide rental/taxi/Grab/walking costs inside non-TRANSPORT notes.
            9. Use named, real places/restaurants/cafes. Avoid generic wording such as "địa phương", "điểm nổi bật", "khu trung tâm" unless paired with a specific real name.
            10. Treat the user's free-form request as the primary goal. Infer the requested change from natural language, for example seafood, cheaper, lighter pacing, fewer walks, more local food, more culture, better transport, or replacing a disliked place.
            11. If the user asks for food such as seafood, vegetarian food, coffee, local dishes, or a specific cuisine, adjust FOOD/CAFE activities while keeping the day practical.
            12. If the user asks to save money, reduce cost without making the plan unrealistic.
            13. If the user asks for a lighter day, reduce density, walking, and rushed transitions.
            14. If the user asks for clearer transport, make local movement especially clear.
            15. If the user asks to keep an existing place, preserve it when it does not break constraints.
            16. If the user request is empty, create a generally better version of the day: more specific, realistic, well-paced, and within constraints.
            17. Always evaluate the user's free-form request in requestFulfillment.
            18. Split meaningful user requests into concrete requested items. Treat implicit phrasing as a request when it proposes an activity, place, food, experience, or constraint, for example "nhảy dù ở Đà Nẵng cũng hay mà".
            19. If a requested item is fully reflected in the regenerated day, mark it FULFILLED with reasonCode APPLIED.
            20. If a requested item is omitted, substituted, weakened, unsafe, too expensive, duplicated, or impossible under constraints, mark it PARTIAL or NOT_APPLIED and write a short Vietnamese userMessage explaining why.
            21. Use reasonCode WEATHER_SAFETY when rain/storm/weather risk is the main reason. Other allowed reasonCode values: APPLIED, BUDGET, TIME_CONFLICT, DUPLICATE, CONSTRAINT, UNCLEAR, OTHER.
            22. If there is no meaningful request, set overallStatus to NO_REQUEST and items to [].
            23. If you are unsure whether the request was satisfied, mark the item UNCLEAR and explain what the user should check.

            JSON schema:
            {
              "day": {
                "day": %d,
                "title": "Ngày %d - Chủ đề ngắn",
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
              },
              "requestFulfillment": {
                "overallStatus": "FULFILLED|PARTIAL|NOT_FULFILLED|UNCLEAR|NO_REQUEST",
                "items": [
                  {
                    "requestedText": "Hoạt động, địa điểm, món ăn, trải nghiệm, hoặc ràng buộc user đã yêu cầu",
                    "status": "FULFILLED|PARTIAL|NOT_APPLIED|UNCLEAR",
                    "reasonCode": "APPLIED|WEATHER_SAFETY|BUDGET|TIME_CONFLICT|DUPLICATE|CONSTRAINT|UNCLEAR|OTHER",
                    "userMessage": "Thông báo ngắn bằng tiếng Việt nếu chưa đáp ứng đầy đủ; để trống nếu đã đáp ứng đầy đủ"
                  }
                ]
              }
            }
            %s
            """,
            req.getDeparture(), req.getDestination(),
            req.getStartDate() != null ? req.getStartDate().toString() : "not provided",
            req.getEndDate() != null ? req.getEndDate().toString() : "not provided",
            req.getDays(),
            travelers,
            req.getBudgetMode() != null ? req.getBudgetMode() : "PER_PERSON",
            totalBudget,
            req.getStyle(),
            req.getGroupType(),
            req.getOutboundTransport(),
            req.getLocalTransport(),
            req.getMustVisit() != null && !req.getMustVisit().isBlank() ? req.getMustVisit() : "none",
            req.getAvoid() != null && !req.getAvoid().isBlank() ? req.getAvoid() : "none",
            req.getNotes() != null && !req.getNotes().isBlank() ? req.getNotes() : "none",
            req.getWeatherForecast() != null && !req.getWeatherForecast().isBlank() ? req.getWeatherForecast() : "none",
            dayNumber,
            instruction != null && !instruction.isBlank() ? instruction : "none",
            intent != null && !intent.isBlank() ? intent : "REGENERATE",
            scheduleJson,
            dayNumber,
            travelers,
            req.getDestination(),
            dayNumber,
            dayNumber,
            retryBlock
        );
    }

    private RegeneratedDayResult parseRegeneratedDayResult(String json, int dayNumber) {
        try {
            JsonNode root = objectMapper.readTree(cleanJson(json));
            TripDto.DayResponse day;
            TripDto.RequestFulfillment requestFulfillment = null;

            if (root.isArray()) {
                List<TripDto.DayResponse> days = parseItinerary(json);
                day = requireSingleRegeneratedDay(days, dayNumber);
            } else if (root.path("day").isObject()) {
                day = parseDayNode(root.path("day"));
                requestFulfillment = parseRequestFulfillment(root.path("requestFulfillment"));
            } else if (root.path("activities").isArray()) {
                day = parseDayNode(root);
            } else {
                throw new IllegalArgumentException("AI response is not a regenerated day JSON object");
            }

            if (day.getDay() != dayNumber) {
                throw new RuntimeException("AI returned wrong day number: " + day.getDay());
            }
            return new RegeneratedDayResult(day, requestFulfillment);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse AI regenerated day JSON: " + e.getMessage()
                    + ". Raw length=" + (json != null ? json.length() : 0), e);
        }
    }

    private TripDto.DayResponse requireSingleRegeneratedDay(List<TripDto.DayResponse> days, int dayNumber) {
        if (days.size() != 1) {
            throw new RuntimeException("AI must return exactly one regenerated day");
        }
        TripDto.DayResponse day = days.get(0);
        if (day.getDay() != dayNumber) {
            throw new RuntimeException("AI returned wrong day number: " + day.getDay());
        }
        return day;
    }

    private TripDto.RequestFulfillment parseRequestFulfillment(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        TripDto.RequestFulfillment fulfillment = new TripDto.RequestFulfillment();
        String overallStatus = node.path("overallStatus").asText("");
        fulfillment.setOverallStatus(overallStatus.isBlank() ? "UNCLEAR" : overallStatus);

        List<TripDto.RequestFulfillmentItem> items = new ArrayList<>();
        JsonNode itemsNode = node.path("items");
        if (itemsNode.isArray()) {
            for (JsonNode itemNode : itemsNode) {
                TripDto.RequestFulfillmentItem item = new TripDto.RequestFulfillmentItem();
                item.setRequestedText(blankToNull(itemNode.path("requestedText").asText("")));
                item.setStatus(blankToNull(itemNode.path("status").asText("")));
                item.setReasonCode(blankToNull(itemNode.path("reasonCode").asText("")));
                item.setUserMessage(blankToNull(itemNode.path("userMessage").asText("")));
                items.add(item);
            }
        }
        fulfillment.setItems(items);
        return fulfillment;
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
            The response MUST be one JSON object with keys "itinerary" and "requestFulfillment".

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
            - Weather Forecast (per trip day): %s

            Weather-aware planning rules:
            1. Read the Weather Forecast carefully. Each line is labeled "Day N (date): condition, temp, rain chance → risk level".
            2. For days labeled "HIGH RAIN RISK": schedule museums, indoor markets, cooking classes, spa, or covered shopping areas. Move boat tours, trekking, beach, or open-air sightseeing to a lower-risk day.
            3. For days labeled "LIGHT RAIN": mix indoor-heavy morning with short outdoor activities in the afternoon if the rain chance is below 60%%.
            4. For days labeled "Good weather": maximize outdoor, scenic, or active experiences.
            5. Never mention the weather forecast to the user in the output text. Just naturally plan the right activities.
            6. If forecast is "none" or unavailable, plan normally without weather constraints.

            Cost rules:
            1. estimatedCost MUST be the estimated total VND for the whole group of %d travelers.
            2. Treat the total group budget as an upper spending limit, not a target that must be fully spent.
            3. The full trip cost should stay at or below the total group budget. It is acceptable and often desirable to be under budget when realistic costs are lower.
            4. If the budget is generous, prefer more comfortable or higher-quality choices such as better transport times, cleaner accommodation areas, memorable paid experiences, or reputable restaurants, but do not invent unnecessary costs just to use the budget.
            5. Include realistic major costs: round-trip outbound transport, local transport, accommodation, food, entrance tickets, paid tours, shows, and shopping only if useful.
            6. For fixed-price items such as cable car, theme park, show, museum, paid tour, boat tour, or entrance ticket, use a realistic recent public-market estimate and mention the unit basis in note, for example "khoảng 850k/người".
            7. For accommodation, include a clear ACCOMMODATION activity with total lodging cost for all nights and all travelers. Do not use the accommodation type for a taxi/check-in only.
            8. Never set estimatedCost to 0 for paid intercity transport such as flights, trains, buses, private cars, airport transfers, vehicle rental pickup, lodging, tickets, tours, shows, or paid experiences.
            9. If a note mentions a required price, that price MUST be included in estimatedCost. Do not write "not included", "khong bao gom", or "chua bao gom" for required trip costs.
            10. Check-in/check-out or returning a rented vehicle may be 0 only when the actual lodging or rental fee is already counted in another activity.
            11. If the budget cannot support all expensive attractions, choose fewer paid activities instead of exceeding budget.
            12. Before returning, sum every activity.estimatedCost and keep the total at or below the total group budget unless the required outbound/return transport alone makes that impossible.
            13. If the budget is tight, keep required transport realistic but reduce optional paid attractions, tours, premium meals, shopping, and accommodation comfort instead of exceeding budget.
            14. If realistic required costs make the budget impossible, return realistic costs anyway. Never understate costs to fit the budget.
            15. Prefer specific real places, restaurants, dishes, addresses/areas, and realistic travel pacing.
            16. Keep notes concise. Do not invent exact official prices when unsure; use "ước tính" or "khoảng".

            Local transportation rules:
            1. Always make the local transportation plan explicit. Users must know how to move between places inside %s.
            2. If Local transport is MIXED or unclear, choose the most practical option for Vietnamese travelers and say it clearly: thuê xe máy, taxi/Grab, thuê ô tô, xe đạp, đi bộ, shuttle, or a combination.
            3. If Local transport is MOTORBIKE, CAR, BUS, TRAIN, WALKING, or PLANE, follow that selected mode for local movement unless it is impractical; if you must deviate, explain why in the TRANSPORT note.
            4. Add TRANSPORT activities for local movement, not only for the outbound/return trip. Examples: "Thuê xe máy tại thị trấn Mộc Châu", "Di chuyển khách sạn -> Thác Dải Yếm bằng xe máy", "Taxi/Grab từ nhà hàng về homestay".
            5. Each local TRANSPORT activity must include mode, route or area, estimated duration, and group cost. Rental or taxi costs must be estimatedCost on TRANSPORT, never hidden inside FOOD/CAFE/ATTRACTION notes.
            6. If places are close enough to walk, add a TRANSPORT activity or clear route note that says "Đi bộ khoảng X phút" with cost 0.
            7. Do not put all local transport detail into one unrelated dinner or attraction note.

            Itinerary quality rules:
            1. Return exactly %d days in the itinerary array.
            2. Each day must have 4-6 activities.
            3. FOOD/CAFE activities must name a specific dish or restaurant/cafe.
            4. ATTRACTION/ACTIVITY activities must name a specific real place in or near %s.
            5. TRANSPORT activities must include both outbound/return travel and local travel between clusters of places.
            6. Do not use generic names like "ăn sáng đặc sản địa phương", "tham quan điểm nổi bật", "khám phá khu vực lân cận", "nhà hàng địa phương", or "cà phê view đẹp".
            7. Days must be clearly different and should not repeat the same activity sequence.

            User request fulfillment rules:
            1. Always evaluate user-specific requests from Must visit, Avoid, and Notes in requestFulfillment.
            2. Split meaningful requests into concrete requested items. Treat implicit phrasing as a request when it proposes an activity, place, food, experience, or constraint, for example "nhảy dù ở Đà Nẵng cũng hay mà".
            3. If a requested item is fully reflected in the itinerary, mark it FULFILLED with reasonCode APPLIED.
            4. If a requested item is omitted, substituted, weakened, unsafe, too expensive, duplicated, or impossible under constraints, mark it PARTIAL or NOT_APPLIED and write a short Vietnamese userMessage explaining why.
            5. Use reasonCode WEATHER_SAFETY when rain/storm/weather risk is the main reason. Other allowed reasonCode values: APPLIED, BUDGET, TIME_CONFLICT, DUPLICATE, CONSTRAINT, UNCLEAR, OTHER.
            6. If there is no meaningful user-specific request, set overallStatus to NO_REQUEST and items to [].
            7. If you are unsure whether a request was satisfied, mark the item UNCLEAR and explain what the user should check.
            8. Never mention the weather in itinerary day titles, summaries, activities, or notes. If weather blocks a user request, explain it only in requestFulfillment.items[].userMessage.

            JSON schema:
            {
              "itinerary": [
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
              ],
              "requestFulfillment": {
                "overallStatus": "FULFILLED|PARTIAL|NOT_FULFILLED|UNCLEAR|NO_REQUEST",
                "items": [
                  {
                    "requestedText": "Hoạt động, địa điểm, món ăn, trải nghiệm, hoặc ràng buộc user đã yêu cầu",
                    "status": "FULFILLED|PARTIAL|NOT_APPLIED|UNCLEAR",
                    "reasonCode": "APPLIED|WEATHER_SAFETY|BUDGET|TIME_CONFLICT|DUPLICATE|CONSTRAINT|UNCLEAR|OTHER",
                    "userMessage": "Thông báo ngắn bằng tiếng Việt nếu chưa đáp ứng đầy đủ; để trống nếu đã đáp ứng đầy đủ"
                  }
                ]
              }
            }
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
            req.getWeatherForecast() != null && !req.getWeatherForecast().isBlank() ? req.getWeatherForecast() : "none",
            travelers,
            req.getDestination(),
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
        return callGeminiWithRetry(prompt, 20000);
    }

    /**
     * Dedicated Gemini call for single-day regeneration.
     * Uses a higher maxOutputTokens budget because gemini-2.5-flash consumes thinking tokens
     * that count against the same limit, easily exhausting the 20 000-token default when
     * the full schedule JSON is included in the prompt.
     */
    private String callGeminiForSingleDay(String prompt) {
        return callGeminiWithRetry(prompt, 65536);
    }

    /**
     * Core Gemini HTTP call with exponential-backoff retry for transient 503/429 errors.
     * Retries up to 2 times (delays: 2 s, 4 s) before giving up.
     */
    private String callGeminiWithRetry(String prompt, int maxOutputTokens) {
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
                "maxOutputTokens", maxOutputTokens,
                "responseMimeType", "application/json"
            )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        int[] retryDelaysMs = {2000, 4000};
        HttpStatusCodeException lastTransientError = null;

        for (int attempt = 0; attempt <= retryDelaysMs.length; attempt++) {
            try {
                ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
                return parseGeminiResponse(response.getBody(), maxOutputTokens);
            } catch (HttpStatusCodeException e) {
                int code = e.getStatusCode().value();
                if ((code == 503 || code == 429) && attempt < retryDelaysMs.length) {
                    lastTransientError = e;
                    log.warn("Gemini returned {} (attempt {}/{}), retrying in {} ms...",
                            code, attempt + 1, retryDelaysMs.length + 1, retryDelaysMs[attempt]);
                    try {
                        Thread.sleep(retryDelaysMs[attempt]);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted while waiting to retry Gemini call", ie);
                    }
                } else {
                    // Non-transient error or out of retries — throw immediately.
                    throw new RuntimeException("Gemini request failed for model " + geminiModel
                            + " with status " + e.getStatusCode() + ": " + e.getResponseBodyAsString());
                }
            }
        }

        // All retries exhausted for a transient error.
        throw new AiGenerationException(
                "Dịch vụ AI đang quá tải, vui lòng thử lại sau vài giây.",
                lastTransientError);
    }

    private String parseGeminiResponse(String responseBody, int maxOutputTokens) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode candidate = root.path("candidates").get(0);
            String finishReason = candidate.path("finishReason").asText("");
            String text = candidate.path("content").path("parts").get(0).path("text").asText();
            log.debug("Gemini response finishReason={}, textLength={}, maxTokens={}",
                    finishReason, text.length(), maxOutputTokens);
            if ("MAX_TOKENS".equals(finishReason)) {
                throw new RuntimeException("Gemini response was truncated by maxOutputTokens (" + maxOutputTokens + ")");
            }
            if (text.isBlank()) {
                throw new RuntimeException("Gemini response text is empty");
            }
            return text;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Gemini response: " + e.getMessage());
        }
    }

    /**
     * Returns a compact JSON representation of the schedule to use as prompt context.
     * Strips heavy / redundant fields (note, latitude, longitude, googlePlaceId, sortOrder,
     * estimatedCost, rating, duration) that the model does not need to avoid duplicate places
     * or understand pacing. This significantly reduces input token count.
     */
    private String slimScheduleJson(List<TripDto.DayResponse> schedule) {
        if (schedule == null || schedule.isEmpty()) return "[]";
        try {
            List<Map<String, Object>> slim = new ArrayList<>();
            for (TripDto.DayResponse day : schedule) {
                List<Map<String, Object>> acts = new ArrayList<>();
                if (day.getActivities() != null) {
                    for (TripDto.ActivityResponse act : day.getActivities()) {
                        Map<String, Object> a = new LinkedHashMap<>();
                        a.put("time", act.getTime());
                        a.put("name", act.getName());
                        a.put("type", act.getType());
                        a.put("location", act.getLocation());
                        acts.add(a);
                    }
                }
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("day", day.getDay());
                d.put("title", day.getTitle());
                d.put("activities", acts);
                slim.add(d);
            }
            return objectMapper.writeValueAsString(slim);
        } catch (Exception e) {
            return "[]";
        }
    }

    private GeneratedItineraryResult parseGeneratedItineraryResult(String json) {
        try {
            JsonNode root = objectMapper.readTree(cleanJson(json));
            if (root.isArray()) {
                return new GeneratedItineraryResult(parseItinerary(json), null);
            }

            JsonNode itineraryNode = root.path("itinerary");
            if (itineraryNode.isMissingNode() || itineraryNode.isNull()) {
                itineraryNode = root.path("schedule");
            }
            if (itineraryNode.isMissingNode() || itineraryNode.isNull()) {
                itineraryNode = root.path("days");
            }
            if (!itineraryNode.isArray()) {
                throw new IllegalArgumentException("AI response is not an itinerary JSON object");
            }

            List<TripDto.DayResponse> days = new ArrayList<>();
            for (JsonNode dayNode : itineraryNode) {
                days.add(parseDayNode(dayNode));
            }
            return new GeneratedItineraryResult(days, parseRequestFulfillment(root.path("requestFulfillment")));
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse AI itinerary JSON: " + e.getMessage()
                    + ". Raw length=" + (json != null ? json.length() : 0), e);
        }
    }

    private List<TripDto.DayResponse> parseItinerary(String json) {
        try {
            JsonNode arr = objectMapper.readTree(cleanJson(json));
            if (!arr.isArray()) {
                throw new IllegalArgumentException("AI response is not a JSON array");
            }

            List<TripDto.DayResponse> result = new ArrayList<>();
            for (JsonNode dayNode : arr) {
                result.add(parseDayNode(dayNode));
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse AI itinerary JSON: " + e.getMessage()
                    + ". Raw length=" + (json != null ? json.length() : 0), e);
        }
    }

    private TripDto.DayResponse parseDayNode(JsonNode dayNode) {
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
            if (!actNode.path("latitude").isMissingNode()) {
                act.setLatitude(actNode.path("latitude").asDouble());
            }
            if (!actNode.path("longitude").isMissingNode()) {
                act.setLongitude(actNode.path("longitude").asDouble());
            }
            if (!actNode.path("googlePlaceId").isMissingNode()) {
                act.setGooglePlaceId(actNode.path("googlePlaceId").asText());
            }
            act.setSortOrder(order++);
            activities.add(act);
        }
        day.setActivities(activities);
        return day;
    }

    private String cleanJson(String json) {
        return json == null
                ? ""
                : json.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private QualityCheck assessItineraryQuality(List<TripDto.DayResponse> days, TripDto.GenerateRequest req) {
        int expectedDays = req.getDays();
        if (days == null) {
            return QualityCheck.fail("response has no days");
        }
        if (days.size() != expectedDays) {
            return QualityCheck.fail("expected " + expectedDays + " days but got " + days.size());
        }

        Set<String> dayFingerprints = new HashSet<>();
        int genericActivities = 0;
        int totalActivities = 0;
        int localTransportActivities = 0;
        int localTransportActivitiesMatchingSelection = 0;
        int nonLogisticsActivities = 0;
        for (TripDto.DayResponse day : days) {
            if (day.getActivities() == null || day.getActivities().size() < 4) {
                return QualityCheck.fail("day " + day.getDay() + " has fewer than 4 activities");
            }
            StringBuilder fingerprint = new StringBuilder();
            for (TripDto.ActivityResponse act : day.getActivities()) {
                totalActivities++;
                String name = normalize(act.getName());
                String location = normalize(act.getLocation());
                String type = normalize(act.getType());
                String note = normalize(act.getNote());
                fingerprint.append(name).append("|");
                if (act.getEstimatedCost() < 0) {
                    return QualityCheck.fail("activity has negative cost: " + act.getName());
                }
                String costIssue = requiredActivityCostIssue(act, req, type, name, location, note);
                if (costIssue != null) {
                    return QualityCheck.fail(costIssue);
                }
                if (isGenericActivity(name, location, type)) {
                    genericActivities++;
                }
                if (isLocalTransportCostHiddenInNonTransport(type, name, note)) {
                    return QualityCheck.fail("local transport cost is hidden in non-transport activity: " + act.getName());
                }
                if (isLocalTransportActivity(type, name, location, note, req)) {
                    localTransportActivities++;
                    if (matchesSelectedLocalTransport(name + " " + location + " " + note, req)) {
                        localTransportActivitiesMatchingSelection++;
                    }
                }
                if (!type.equals("transport") && !type.equals("accommodation")) {
                    nonLogisticsActivities++;
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

        if (requiresLocalTransportPlan(expectedDays, nonLogisticsActivities) && localTransportActivities == 0) {
            return QualityCheck.fail("missing explicit local transport plan inside " + req.getDestination());
        }

        if (requiresSelectedLocalTransport(req)
                && localTransportActivities > 0
                && localTransportActivitiesMatchingSelection == 0) {
            return QualityCheck.fail("local transport plan does not follow selected mode " + req.getLocalTransport());
        }

        return QualityCheck.pass();
    }

    private QualityCheck assessRegeneratedDayQuality(
            TripDto.DayResponse day,
            List<TripDto.DayResponse> currentSchedule,
            TripDto.GenerateRequest req
    ) {
        if (day == null) {
            return QualityCheck.fail("response has no day");
        }
        if (day.getActivities() == null || day.getActivities().size() < 4) {
            return QualityCheck.fail("regenerated day has fewer than 4 activities");
        }
        if (day.getActivities().size() > 15) {
            return QualityCheck.fail("regenerated day has too many activities");
        }

        int genericActivities = 0;
        int localTransportActivities = 0;
        int selectedLocalTransportMatches = 0;
        int nonLogisticsActivities = 0;
        Set<String> seenTimes = new HashSet<>();
        List<TimeRange> ranges = new ArrayList<>();
        String avoid = normalize(String.join("\n",
                req.getAvoid() != null ? req.getAvoid() : "",
                extractNegativeInstruction(req.getNotes())
        ));

        for (TripDto.ActivityResponse act : day.getActivities()) {
            String name = normalize(act.getName());
            String location = normalize(act.getLocation());
            String type = normalize(act.getType());
            String note = normalize(act.getNote());
            String combined = String.join(" ", name, location, note);

            if (name.isBlank()) {
                return QualityCheck.fail("activity has no name");
            }
            if (!isValidTime(act.getTime())) {
                return QualityCheck.fail("activity has invalid time: " + act.getTime());
            }
            if (!seenTimes.add(act.getTime())) {
                return QualityCheck.fail("multiple activities start at the same time: " + act.getTime());
            }
            if (act.getEstimatedCost() < 0) {
                return QualityCheck.fail("activity has negative cost: " + act.getName());
            }
            String costIssue = requiredActivityCostIssue(act, req, type, name, location, note);
            if (costIssue != null) {
                return QualityCheck.fail(costIssue);
            }
            if (!avoid.isBlank() && containsAvoidedContent(combined, avoid)) {
                return QualityCheck.fail("activity appears to violate avoid instruction: " + act.getName());
            }
            if (isGenericActivity(name, location, type)) {
                genericActivities++;
            }
            if (isLocalTransportCostHiddenInNonTransport(type, name, note)) {
                return QualityCheck.fail("local transport cost is hidden in non-transport activity: " + act.getName());
            }
            if (isLocalTransportActivity(type, name, location, note, req)) {
                localTransportActivities++;
                if (matchesSelectedLocalTransport(combined, req)) {
                    selectedLocalTransportMatches++;
                }
            }
            if (!type.equals("transport") && !type.equals("accommodation")) {
                nonLogisticsActivities++;
            }

            ranges.add(new TimeRange(act.getTime(), parseActivityDurationMinutes(act.getDuration())));
        }

        ranges.sort(Comparator.comparing(TimeRange::start));
        for (int i = 1; i < ranges.size(); i++) {
            // TRANSPORT activities are bookings/rentals that do not block a fixed time slot;
            // exclude them from strict overlap checking to avoid false positives.
            String prevType = normalize(day.getActivities().get(i - 1).getType());
            String currType = normalize(day.getActivities().get(i).getType());
            if (prevType.equals("transport") || currType.equals("transport")) continue;
            if (ranges.get(i).startsBefore(ranges.get(i - 1).end())) {
                return QualityCheck.fail("activity times overlap");
            }
        }

        if (genericActivities > Math.max(2, day.getActivities().size() / 2)) {
            return QualityCheck.fail("too many generic activities in regenerated day");
        }
        if (nonLogisticsActivities >= 4 && localTransportActivities == 0) {
            return QualityCheck.fail("missing explicit local transport in regenerated day");
        }
        if (requiresSelectedLocalTransport(req)
                && localTransportActivities > 0
                && selectedLocalTransportMatches == 0) {
            return QualityCheck.fail("local transport plan does not follow selected mode " + req.getLocalTransport());
        }
        if (hasTooManyDuplicatePlaces(day, currentSchedule)) {
            return QualityCheck.fail("regenerated day repeats too many places from other days");
        }

        return QualityCheck.pass();
    }

    private String requiredActivityCostIssue(
            TripDto.ActivityResponse act,
            TripDto.GenerateRequest req,
            String normalizedType,
            String normalizedName,
            String normalizedLocation,
            String normalizedNote
    ) {
        String combined = String.join(" ", normalizedName, normalizedLocation, normalizedNote);
        long cost = Math.max(0, act.getEstimatedCost());

        if (mentionsExcludedRequiredCost(combined)) {
            return "activity excludes a required cost from estimatedCost: " + act.getName();
        }
        if (isOutboundOrReturnTransport(combined, req)) {
            if (cost == 0) {
                return "intercity transport cost is missing: " + act.getName();
            }
        }
        if (isVehicleRentalStartActivity(normalizedType, combined)) {
            if (cost == 0) {
                return "vehicle rental cost is missing: " + act.getName();
            }
        }
        return null;
    }

    private boolean isVehicleRentalStartActivity(String normalizedType, String normalizedText) {
        if (!normalizedType.equals("transport")) {
            return false;
        }
        return containsAny(normalizedText,
                "thue xe may",
                "nhan xe may",
                "lay xe may",
                "thue xe dap",
                "nhan xe dap",
                "thue o to",
                "thue oto",
                "nhan o to",
                "nhan oto",
                "lay o to",
                "lay oto");
    }

    private boolean mentionsExcludedRequiredCost(String normalizedText) {
        return containsAny(normalizedText,
                "khong bao gom trong chi phi nay",
                "chua bao gom trong chi phi nay",
                "khong bao gom vao chi phi",
                "chua bao gom vao chi phi",
                "not included");
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

    private boolean containsAvoidedContent(String combinedActivityText, String avoidText) {
        List<String> avoidTerms = extractAvoidTerms(avoidText);
        if (avoidTerms.isEmpty()) {
            return false;
        }
        return avoidTerms.stream().anyMatch(combinedActivityText::contains);
    }

    private String extractNegativeInstruction(String notes) {
        return String.join("\n", extractAvoidTerms(notes));
    }

    private List<String> extractAvoidTerms(String text) {
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return List.of();
        }

        // "bo" removed: too short, collides with common Vietnamese words/place names (bờ biển, bổ sung, etc.)
        List<String> negativeMarkers = List.of("khong muon", "khong thich", "tranh", "dung", "khong can", "loai bo", "khong lay");
        Set<String> terms = new LinkedHashSet<>();
        for (String clause : normalized.split("[,;\\.\\n]+")) {
            String trimmedClause = clause.trim();
            if (trimmedClause.isBlank()) continue;

            String avoidPhrase = trimmedClause;
            for (String marker : negativeMarkers) {
                int index = trimmedClause.indexOf(marker);
                if (index >= 0) {
                    avoidPhrase = trimmedClause.substring(index + marker.length()).trim();
                    break;
                }
            }

            for (String part : avoidPhrase.split("\\s+(?:va|hoac|hay)\\s+")) {
                String cleaned = cleanAvoidTerm(part);
                if (cleaned.length() >= 3) {
                    terms.add(cleaned);
                }
            }
        }

        return new ArrayList<>(terms);
    }

    private String cleanAvoidTerm(String term) {
        String cleaned = term == null ? "" : term.trim();
        cleaned = cleaned.replaceAll("\\s+(?:tai|o)\\s+.*$", "").trim();
        String previous;
        do {
            previous = cleaned;
            cleaned = cleaned.replaceAll("^(?:an|uong|thuong thuc|dung|di|ghe|toi|tham quan|mua|check in|trai nghiem)\\s+", "").trim();
        } while (!cleaned.equals(previous));
        return cleaned
                .replaceAll("\\s+(?:nua|trong ngay nay|trong lich trinh|cho ngay nay)$", "")
                .trim();
    }

    private boolean hasTooManyDuplicatePlaces(TripDto.DayResponse regeneratedDay, List<TripDto.DayResponse> currentSchedule) {
        Set<String> otherPlaces = new HashSet<>();
        for (TripDto.DayResponse existingDay : currentSchedule == null ? List.<TripDto.DayResponse>of() : currentSchedule) {
            if (existingDay.getDay() == regeneratedDay.getDay() || existingDay.getActivities() == null) continue;
            for (TripDto.ActivityResponse activity : existingDay.getActivities()) {
                String key = normalize(activity.getName() + " " + activity.getLocation());
                if (!key.isBlank()) otherPlaces.add(key);
            }
        }

        int duplicateCount = 0;
        int comparableCount = 0;
        for (TripDto.ActivityResponse activity : regeneratedDay.getActivities()) {
            String type = normalize(activity.getType());
            if (type.equals("transport") || type.equals("accommodation")) continue;
            String key = normalize(activity.getName() + " " + activity.getLocation());
            if (key.isBlank()) continue;
            comparableCount++;
            // Only count as duplicate when both the candidate key and the matched other
            // are long enough (>= 6 chars) to avoid false positives on short words like
            // "bien" or "dao" that appear in many place names.
            if (key.length() >= 6 && otherPlaces.stream()
                    .anyMatch(other -> other.length() >= 6 && (other.contains(key) || key.contains(other)))) {
                duplicateCount++;
            }
        }

        return comparableCount >= 3 && duplicateCount > comparableCount / 2;
    }

    private boolean isValidTime(String time) {
        return time != null && time.matches("([01]\\d|2[0-3]):[0-5]\\d");
    }

    private int parseActivityDurationMinutes(String duration) {
        if (duration == null || duration.isBlank()) return 60;
        String normalized = normalize(duration);
        int minutes = 0;

        java.util.regex.Matcher hourMatcher = java.util.regex.Pattern
                .compile("(\\d+(?:[\\.,]\\d+)?)\\s*(gio|h)")
                .matcher(normalized);
        if (hourMatcher.find()) {
            minutes += Math.round(Float.parseFloat(hourMatcher.group(1).replace(",", ".")) * 60);
        }

        java.util.regex.Matcher minuteMatcher = java.util.regex.Pattern
                .compile("(\\d+)\\s*(phut|p|min)")
                .matcher(normalized);
        if (minuteMatcher.find()) {
            minutes += Integer.parseInt(minuteMatcher.group(1));
        }

        return minutes > 0 ? minutes : 60;
    }

    private boolean requiresLocalTransportPlan(int expectedDays, int nonLogisticsActivities) {
        return expectedDays >= 2 && nonLogisticsActivities >= 4;
    }

    private boolean requiresSelectedLocalTransport(TripDto.GenerateRequest req) {
        String selected = normalize(req.getLocalTransport());
        return !selected.isBlank() && !selected.equals("mixed");
    }

    private boolean matchesSelectedLocalTransport(String normalizedText, TripDto.GenerateRequest req) {
        String selected = normalize(req.getLocalTransport());
        if (selected.isBlank() || selected.equals("mixed")) {
            return true;
        }

        List<String> terms = switch (selected) {
            case "motorbike" -> List.of("xe may", "thue xe may");
            case "car" -> List.of("o to", "oto", "taxi", "grab", "xe rieng", "xe dua don", "thue o to", "thue oto");
            case "bus" -> List.of("xe bus", "xe buyt", "xe khach", "bus");
            case "train" -> List.of("tau hoa", "tau");
            case "walking" -> List.of("di bo", "walking");
            case "plane" -> List.of("may bay", "bay", "san bay");
            default -> List.of(selected);
        };

        return terms.stream().anyMatch(normalizedText::contains);
    }

    private boolean isLocalTransportActivity(
            String normalizedType,
            String normalizedName,
            String normalizedLocation,
            String normalizedNote,
            TripDto.GenerateRequest req
    ) {
        if (!normalizedType.equals("transport")) {
            return false;
        }

        String combined = String.join(" ", normalizedName, normalizedLocation, normalizedNote);
        if (isOutboundOrReturnTransport(combined, req)) {
            return false;
        }

        return containsLocalTransportMode(combined)
                || combined.contains("noi vung")
                || combined.contains("trong " + normalize(req.getDestination()))
                || combined.contains("giua cac diem")
                || combined.contains("ve homestay")
                || combined.contains("ve khach san");
    }

    private boolean isOutboundOrReturnTransport(String normalizedText, TripDto.GenerateRequest req) {
        String departure = normalize(req.getDeparture());
        String destination = normalize(req.getDestination());
        if (departure.isBlank() || destination.isBlank()) {
            return false;
        }

        return normalizedText.contains(departure) && normalizedText.contains(destination);
    }

    private boolean isLocalTransportCostHiddenInNonTransport(String normalizedType, String normalizedName, String normalizedNote) {
        if (normalizedType.equals("transport")) {
            return false;
        }

        String combined = normalizedName + " " + normalizedNote;
        // Use more specific cost signals to avoid false-positives from common Vietnamese words:
        // - "gia" collides with "gia dinh", "gia lai", place names containing "Gia".
        // - "dong" collides with directional/geographical words ("dong bac", "song dong").
        // - "ngay" collides with "ngay mai", "ngay dau", etc.
        // Only flag when a clearly monetary signal is present alongside a transport mode.
        return containsLocalTransportMode(combined)
                && (combined.contains("chi phi") || combined.contains("vnd")
                || combined.contains("k/nguoi") || combined.contains("k/xe")
                || combined.contains("/ngay") || combined.contains("thue phi")
                || java.util.regex.Pattern.compile("\\d+[\\s]*k").matcher(combined).find());
    }

    private boolean containsLocalTransportMode(String normalizedText) {
        List<String> localTransportTerms = List.of(
                "thue xe may",
                "xe may",
                "taxi",
                "grab",
                "thue xe o to",
                "thue o to",
                "thue oto",
                "o to rieng",
                "oto rieng",
                "xe rieng",
                "co tai xe",
                "xe hop dong",
                "xe dua don",
                "xe dien",
                "xe dap",
                "di bo",
                "shuttle"
        );
        return localTransportTerms.stream().anyMatch(normalizedText::contains);
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

    private record TimeRange(String start, int durationMinutes) {
        java.time.LocalTime end() {
            return java.time.LocalTime.parse(start).plusMinutes(Math.max(15, durationMinutes));
        }

        boolean startsBefore(java.time.LocalTime otherEnd) {
            return java.time.LocalTime.parse(start).isBefore(otherEnd);
        }
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
