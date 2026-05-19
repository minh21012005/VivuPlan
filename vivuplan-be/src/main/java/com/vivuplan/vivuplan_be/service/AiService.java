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

    private static final int PROMPT_TOKEN_WARN_THRESHOLD = 8_000;

    public record GeneratedItineraryResult(
            List<TripDto.DayResponse> days,
            TripDto.RequestFulfillment requestFulfillment) {
    }

    public record RegeneratedDayResult(
            TripDto.DayResponse day,
            TripDto.RequestFulfillment requestFulfillment) {
    }

    private static class AiResponseFormatException extends RuntimeException {
        private AiResponseFormatException(String message) {
            super(message);
        }
    }

    @Value("${app.ai.gemini.api-key:}")
    private String geminiApiKey;

    @Value("${app.ai.gemini.model:gemini-2.5-flash}")
    private String geminiModel;

    private final ObjectMapper objectMapper;

    private static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";
    private static final String AI_GENERATION_USER_MESSAGE = "AI chưa tạo được lịch trình đủ cụ thể cho chuyến đi này. Vui lòng thử lại hoặc bổ sung thêm điểm muốn ghé, điều cần tránh hay ghi chú để VivuPlan lập lại lịch trình.";

    public GeneratedItineraryResult generateItinerary(TripDto.GenerateRequest req) {
        String prompt = buildPrompt(req);
        log.info("Generating itinerary for: {} - {}N using Gemini model {}", req.getDestination(), req.getDays(),
                geminiModel);

        try {
            String rawJson = callGemini(prompt);
            boolean usedRetry = false;
            GeneratedItineraryResult result;
            try {
                result = parseGeneratedItineraryResult(rawJson);
            } catch (AiResponseFormatException e) {
                usedRetry = true;
                log.warn(
                        "AI itinerary for {} returned invalid response contract: {}. Retrying once with strict JSON contract.",
                        req.getDestination(), e.getMessage());
                String retryJson = callGemini(buildQualityRetryPrompt(req, formatContractRetryReason(e.getMessage())));
                result = parseGeneratedItineraryResult(retryJson);
            }
            QualityCheck quality = assessItineraryQuality(result.days(), req);
            if (quality.passed()) {
                return result;
            }

            if (usedRetry) {
                if (isRecoverableCostQualityIssue(quality.reason())) {
                    log.warn(
                            "AI retry itinerary for {} still has a cost completeness issue: {}. Returning itinerary.",
                            req.getDestination(), quality.reason());
                    return result;
                }

                log.warn(
                        "AI retry itinerary for {} still failed quality check after contract retry: {}. Returning error to user.",
                        req.getDestination(), quality.reason());
                throw new AiGenerationException(AI_GENERATION_USER_MESSAGE);
            }

            log.warn("AI itinerary for {} failed quality check: {}. Retrying once with stricter prompt.",
                    req.getDestination(), quality.reason());

            String retryJson = callGemini(buildQualityRetryPrompt(req, quality.reason()));
            GeneratedItineraryResult retryResult = parseGeneratedItineraryResult(retryJson);
            QualityCheck retryQuality = assessItineraryQuality(retryResult.days(), req);
            if (retryQuality.passed()) {
                return retryResult;
            }

            if (isRecoverableCostQualityIssue(retryQuality.reason())) {
                log.warn(
                        "AI retry itinerary for {} still has a cost completeness issue: {}. Returning itinerary.",
                        req.getDestination(), retryQuality.reason());
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
            String instruction) {
        log.info("Regenerating day {} for trip to {} using intent {}", dayNumber, req.getDestination(), intent);

        try {
            TripDto.GenerateRequest qualityReq = withRegenerationInstruction(req, instruction);
            String rawJson = callGeminiForSingleDay(
                    buildDayRegenerationPrompt(req, currentSchedule, dayNumber, intent, instruction, null));
            boolean usedRetry = false;
            RegeneratedDayResult result;
            try {
                result = parseRegeneratedDayResult(rawJson, dayNumber);
            } catch (AiResponseFormatException e) {
                usedRetry = true;
                log.warn(
                        "AI regenerated day {} for {} returned invalid response contract: {}. Retrying once with strict JSON contract.",
                        dayNumber, req.getDestination(), e.getMessage());
                String retryJson = callGeminiForSingleDay(buildDayRegenerationPrompt(
                        req,
                        currentSchedule,
                        dayNumber,
                        intent,
                        instruction,
                        formatContractRetryReason(e.getMessage())));
                result = parseRegeneratedDayResult(retryJson, dayNumber);
            }
            QualityCheck quality = assessRegeneratedDayQuality(result.day(), currentSchedule, qualityReq);
            if (quality.passed()) {
                return result;
            }

            if (usedRetry) {
                if (isRecoverableCostQualityIssue(quality.reason())) {
                    log.warn(
                            "AI retry regenerated day {} for {} still has a cost completeness issue: {}. Returning itinerary.",
                            dayNumber, req.getDestination(), quality.reason());
                    return result;
                }

                log.warn("AI retry regenerated day {} for {} still failed quality check after contract retry: {}.",
                        dayNumber, req.getDestination(), quality.reason());
                throw new AiGenerationException(
                        "AI chưa tạo được phương án chỉnh ngày này đủ tốt. Vui lòng thử lại với yêu cầu cụ thể hơn.");
            }

            log.warn("AI regenerated day {} for {} failed quality check: {}. Retrying once.",
                    dayNumber, req.getDestination(), quality.reason());

            String retryJson = callGeminiForSingleDay(
                    buildDayRegenerationPrompt(req, currentSchedule, dayNumber, intent, instruction, quality.reason()));
            RegeneratedDayResult retryResult = parseRegeneratedDayResult(retryJson, dayNumber);
            QualityCheck retryQuality = assessRegeneratedDayQuality(retryResult.day(), currentSchedule, qualityReq);
            if (retryQuality.passed()) {
                return retryResult;
            }

            if (isRecoverableCostQualityIssue(retryQuality.reason())) {
                log.warn(
                        "AI retry regenerated day {} for {} still has a cost completeness issue: {}. Returning itinerary.",
                        dayNumber, req.getDestination(), retryQuality.reason());
                return retryResult;
            }

            log.warn("AI retry regenerated day {} for {} still failed quality check: {}.",
                    dayNumber, req.getDestination(), retryQuality.reason());
            throw new AiGenerationException(
                    "AI chưa tạo được phương án chỉnh ngày này đủ tốt. Vui lòng thử lại với yêu cầu cụ thể hơn.");
        } catch (AiGenerationException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI day regeneration failed with Gemini model {}: {}", geminiModel, e.getMessage(), e);
            throw new AiGenerationException(
                    "AI chưa tạo được phương án chỉnh ngày này đủ tốt. Vui lòng thử lại với yêu cầu cụ thể hơn.", e);
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
                instruction != null && !instruction.isBlank() ? "Yêu cầu chỉnh ngày: " + instruction : "").trim();
        copy.setNotes(mergedNotes);
        copy.setWeatherForecast(source.getWeatherForecast());
        copy.setVerifiedPlacesContext(source.getVerifiedPlacesContext());
        return copy;
    }

    private String buildPrompt(TripDto.GenerateRequest req) {
        return buildCostAwarePrompt(req);
    }

    private String weatherSafetyOverrideGuidance(String weatherForecast) {
        if (weatherForecast == null || weatherForecast.isBlank() || "none".equalsIgnoreCase(weatherForecast.trim())) {
            return "";
        }

        List<String> dayLines = weatherForecast.lines()
                .map(String::trim)
                .filter(line -> line.toLowerCase(Locale.ROOT).startsWith("day "))
                .toList();
        if (dayLines.isEmpty()) {
            return "";
        }

        long severeRiskDays = dayLines.stream()
                .filter(this::isSevereWeatherForecastLine)
                .count();
        if (severeRiskDays == dayLines.size()) {
            return """
                    7. Weather safety override: every trip day is SEVERE WEATHER RISK or legacy HIGH RAIN RISK. Do not schedule unsafe weather-dependent outdoor, open-air adventure, water, beach, boat, paddling, trekking, paragliding, or similar safety-sensitive activities. If destination-signature scenic places would normally be expected but cannot be included safely, add a PARTIAL or NOT_APPLIED requestFulfillment item with reasonCode WEATHER_SAFETY and a brief Vietnamese userMessage.
                    """
                    .stripTrailing();
        }
        if (severeRiskDays > 0) {
            return """
                    7. Weather safety override: SEVERE WEATHER RISK or legacy HIGH RAIN RISK is a hard safety constraint only for unsafe outdoor/water/adventure activities on those days. Move destination-signature outdoor/scenic places to safer days when possible. If no safer day exists, include only a safe short/covered version when realistic, or explain the omission/substitution in requestFulfillment with reasonCode WEATHER_SAFETY.
                    """
                    .stripTrailing();
        }
        return "";
    }

    private boolean isSevereWeatherForecastLine(String line) {
        String normalized = line == null ? "" : line.toLowerCase(Locale.ROOT);
        return normalized.contains("severe weather risk") || normalized.contains("high rain risk");
    }

    private String formatContractRetryReason(String reason) {
        return "response JSON contract was invalid: " + reason;
    }

    private String buildQualityRetryPrompt(TripDto.GenerateRequest req, String reason) {
        return buildCostAwarePrompt(req) + String.format(
                """

                        IMPORTANT RETRY INSTRUCTION:
                        The previous itinerary was rejected because: %s
                        Regenerate the itinerary from scratch.
                        Return exactly ONE JSON object with keys "itinerary" and "requestFulfillment". Never return a bare JSON array, and never use "days" or "schedule" instead of "itinerary".
                        Use named, real places and restaurants in or near %s.
                        Avoid ANY generic placeholder wording (e.g., "địa điểm nổi bật", "đặc sản địa phương", "nhà hàng hải sản", "ăn tối ở khách sạn", "chợ địa phương", "địa điểm thuê xe"). Every place, restaurant, cafe, accommodation, or rental shop MUST be a specific real-world business with a concrete proper name.
                        Preserve the destination's signature/must-try experiences using your own Vietnam travel knowledge, including specific real places not present in verified candidates. If severe weather, time, budget, safety, or route constraints make a normally expected signature experience unsuitable, explain the omission/substitution in requestFulfillment.
                        Anti-Bias Rule: Do not default to the same well-known corporate chains. Suggest diverse, logically located, and budget-appropriate places.
                        %s
                        %s
                        Include all required paid transport, rental, lodging, ticket, and tour costs in estimatedCost. Do not mark required costs as not included.
                        Before returning, sum every activity.estimatedCost and keep the total at or below the total group budget unless the required outbound/return transport alone makes that impossible.
                        If the budget is tight, keep required transport realistic but reduce optional paid attractions, tours, premium meals, shopping, and accommodation comfort instead of exceeding budget.
                        If realistic required costs make the budget impossible, return realistic costs anyway. Never understate costs to fit the budget.
                        Do not repeat the same day structure across days.
                        """,
                reason,
                req.getDestination(),
                ItineraryQualityPolicy.vietnamPacingGuidance(),
                ItineraryQualityPolicy.localTransportGuidance(req.getDestination()));
    }

    private String buildDayRegenerationPrompt(
            TripDto.GenerateRequest req,
            List<TripDto.DayResponse> currentSchedule,
            int dayNumber,
            String intent,
            String instruction,
            String retryReason) {
        int travelers = req.getTravelerCount() != null ? Math.max(1, req.getTravelerCount()) : 1;
        long totalBudget = resolvePromptTotalBudget(req, travelers);
        String scheduleJson = slimScheduleJson(currentSchedule);

        String retryBlock = retryReason == null || retryReason.isBlank()
                ? ""
                : String.format(
                        """

                                IMPORTANT RETRY INSTRUCTION:
                                The previous proposal was rejected because: %s
                                Fix that issue. Return a safer, more specific version of day %d only.
                                Return exactly ONE JSON object with keys "day" and "requestFulfillment". Never return a bare JSON array.
                                Preserve or restore relevant destination-signature/must-try experiences using your own Vietnam travel knowledge, including specific real places not present in verified candidates. If a normally expected signature experience is omitted or substituted for a real constraint, explain it in requestFulfillment.
                                %s
                                %s
                                Create a separate TRANSPORT activity with route/mode/cost instead of putting transport cost in an ATTRACTION, FOOD, CAFE, or ACTIVITY note.
                                Include all required paid transport, rental, lodging, ticket, and tour costs in estimatedCost. Do not mark required costs as not included.
                                """,
                        retryReason,
                        dayNumber,
                        ItineraryQualityPolicy.vietnamPacingGuidance(),
                        ItineraryQualityPolicy.localTransportGuidance(req.getDestination()));

        return String.format(
                """
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
                        - Verified place candidates for this destination: %s

                        Weather-aware planning rules:
                        1. Each forecast line is "Day N (date): condition, temp, rain chance → risk level".
                        2. For the day being regenerated, honor its risk level: "HIGH RAIN RISK" → indoor activities only; "LIGHT RAIN" → mostly indoor, short outdoor if rain < 60%%; "Good weather" → outdoor preferred.
                        3. Never mention the weather in the regenerated day's title, summary, activities, or notes. Just naturally plan appropriate activities.
                           If weather or another constraint blocks the user's request, explain that in requestFulfillment.items[].userMessage.
                        4. If forecast is "none", plan normally without weather constraints.
                        %s

                        Important weather interpretation update:
                        - Treat RAIN FLEX or legacy LIGHT RAIN as a flexible-planning signal, not an indoor-only rule.
                        - Keep destination-defining outdoor/scenic places when practical; use shorter time windows and indoor backup notes.
                        - Treat SEVERE WEATHER RISK or legacy HIGH RAIN RISK as a hard safety constraint only for unsafe outdoor/water/adventure activities on the affected day.
                        - If weather blocks or weakens a user request or a destination-signature experience, explain it in requestFulfillment.items[].userMessage with reasonCode WEATHER_SAFETY.

                        Style rules:
                        1. Treat Style as the user's primary planning bias, not a hard restriction.
                        2. Keep the day practical and balanced; include other activity types when they improve route, meals, rest, weather safety, or the user's explicit request.
                        3. If Must visit, Avoid, Notes, weather, budget, or group needs conflict with Style, prioritize those more specific constraints.

                        Destination essence rules:
                        1. Before choosing places, infer the destination's signature experiences and must-try categories from your own Vietnam travel knowledge, even when they are NOT listed in the verified candidates. Examples: iconic scenic areas, old towns, caves, boat routes, viewpoints, beaches/islands, cultural sites, night markets, food streets, local dishes, craft villages, or seasonal highlights.
                        2. The verified candidates are helpful evidence, not the full universe. If a signature experience is missing from the candidate list, you may still include a specific real place/activity with a concrete name and realistic location.
                        3. For this regenerated day, preserve or restore at least one relevant destination-signature experience when it fits the full trip, route, weather, budget, and pacing. Do not replace the destination's core appeal with only generic indoor cafes, malls, meals, or rest stops unless safety or constraints truly require it.
                        4. If a normally expected signature experience is omitted, weakened, or moved away because of severe weather, time, budget, duplication with other days, group safety, or route constraints, add a PARTIAL or NOT_APPLIED requestFulfillment item explaining the reason in Vietnamese. Do this even when the user did not explicitly request that place.

                        Verified place rules:
                        1. Treat verified place candidates as trusted suggestions, not an allowed-only list.
                        2. Candidates are ordered by backend relevance. Consider higher-ranked candidates first, but do not blindly pick the top items when route, weather, pacing, budget, or the user's request makes another choice better.
                        3. Prefer verified candidates when they fit this regenerated day, the user's request, route, weather, and budget.
                        3a. Candidates marked priority=destination-signature are core destination experiences. Preserve them when safe and practical; if weather makes them unsafe and there is no safer slot, explain the omission or substitution in requestFulfillment with reasonCode WEATHER_SAFETY.
                        4. CRITICAL: The candidate list is NOT exhaustive. It may lack specific accommodations, restaurants, cafes, rental shops, or niche local spots. For ANY category lacking suitable candidates, you MUST actively use your extensive internal knowledge to suggest specific, real, and named businesses/places that realistically match the user's budget, style, and daily route.
                        5. When using a verified candidate, copy its exact name and use its address/coords in location, latitude, and longitude.
                        6. When using a non-candidate place or activity, it MUST be a specific, existing real-world place with a concrete proper name and address. Absolutely DO NOT use ANY generic or unnamed placeholders for ANY activity (e.g., "ăn trưa tại địa phương", "nhà hàng hải sản", "ăn tối ở khách sạn", "thuê homestay", "chợ địa phương", "quán cà phê").
                        7. Anti-Bias Rule: Do NOT lazily reuse the same default chains or luxury brands. Actively suggest diverse, budget-appropriate, logically located, and context-relevant local businesses.
                        8. Do not force every candidate into the day; choose only what makes the day practical.

                        Regeneration task:
                        - Regenerate day number: %d
                        - User free-form request: %s
                        - Fallback intent if request is empty: %s

                        Current full itinerary JSON:
                        %s

                        Rules:
                        1. Return exactly ONE JSON object. Its "day" key MUST contain exactly ONE day object whose day value is %d.
                        2. Do not change other days. Use them only as context to avoid duplicate places and impossible pacing.
                        3. %s
                        4. Keep times in HH:mm 24h format and avoid meaningful overlaps.
                        5. estimatedCost MUST be total VND for the whole group of %d travelers.
                        5a. Never set estimatedCost to 0 for paid intercity transport such as flights, trains, buses, private cars, airport transfers, vehicle rental pickup, lodging, tickets, tours, shows, or paid experiences.
                        5b. If a note mentions a required price, that price MUST be included in estimatedCost. Do not write "not included", "khong bao gom", or "chua bao gom" for required trip costs.
                        5c. Check-in/check-out or returning a rented vehicle may be 0 only when the actual lodging or rental fee is already counted in another activity.
                        6. Preserve user constraints: avoid banned items, respect must-visit where relevant, respect style/group.
                        7. Treat Local transport as the user's preference, not an absolute law. Follow it when practical; if a different mode is safer or more realistic in Vietnam, explain briefly in a TRANSPORT note.
                        8. %s
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
                        22. If there is no meaningful user-specific request and no destination-signature omission/substitution needs explanation, set overallStatus to NO_REQUEST and items to []. If a signature experience is omitted or substituted for a real constraint, return PARTIAL or NOT_FULFILLED with a requestFulfillment item even without a user-specific request.
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
                req.getWeatherForecast() != null && !req.getWeatherForecast().isBlank() ? req.getWeatherForecast()
                        : "none",
                req.getVerifiedPlacesContext() != null && !req.getVerifiedPlacesContext().isBlank()
                        ? req.getVerifiedPlacesContext()
                        : "none",
                weatherSafetyOverrideGuidance(req.getWeatherForecast()),
                dayNumber,
                instruction != null && !instruction.isBlank() ? instruction : "none",
                intent != null && !intent.isBlank() ? intent : "REGENERATE",
                scheduleJson,
                dayNumber,
                ItineraryQualityPolicy.vietnamPacingGuidance(),
                travelers,
                ItineraryQualityPolicy.localTransportGuidance(req.getDestination()),
                dayNumber,
                dayNumber,
                retryBlock);
    }

    private RegeneratedDayResult parseRegeneratedDayResult(String json, int dayNumber) {
        try {
            JsonNode root = objectMapper.readTree(cleanJson(json));
            if (root == null || !root.isObject()) {
                throw new AiResponseFormatException(
                        "expected one JSON object with keys \"day\" and \"requestFulfillment\"");
            }

            JsonNode dayNode = root.path("day");
            if (!dayNode.isObject()) {
                throw new AiResponseFormatException("missing required object key \"day\"");
            }

            TripDto.DayResponse day = parseDayNode(dayNode);
            TripDto.RequestFulfillment requestFulfillment = parseRequiredRequestFulfillment(
                    root.path("requestFulfillment"));
            if (day.getDay() != dayNumber) {
                throw new RuntimeException("AI returned wrong day number: " + day.getDay());
            }
            return new RegeneratedDayResult(day, requestFulfillment);
        } catch (AiResponseFormatException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse AI regenerated day JSON: " + e.getMessage()
                    + ". Raw length=" + (json != null ? json.length() : 0), e);
        }
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

    private TripDto.RequestFulfillment parseRequiredRequestFulfillment(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isObject()) {
            throw new AiResponseFormatException("missing required object key \"requestFulfillment\"");
        }
        return parseRequestFulfillment(node);
    }

    private String buildCostAwarePrompt(TripDto.GenerateRequest req) {
        int days = Math.max(1, req.getDays());
        int travelers = req.getTravelerCount() != null ? Math.max(1, req.getTravelerCount()) : 1;
        long totalBudget = resolvePromptTotalBudget(req, travelers);
        long perPersonBudget = totalBudget / travelers;
        long perPersonPerDay = perPersonBudget / days;

        return String.format(
                """
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
                        - Verified place candidates for this destination: %s

                        Weather-aware planning rules:
                        1. Read the Weather Forecast carefully. Each line is labeled "Day N (date): condition, temp, rain chance → risk level".
                        2. For days labeled "HIGH RAIN RISK": schedule museums, indoor markets, cooking classes, spa, or covered shopping areas. Move boat tours, trekking, beach, or open-air sightseeing to a lower-risk day.
                        3. For days labeled "LIGHT RAIN": mix indoor-heavy morning with short outdoor activities in the afternoon if the rain chance is below 60%%.
                        4. For days labeled "Good weather": maximize outdoor, scenic, or active experiences.
                        5. Never mention the weather forecast to the user in the output text. Just naturally plan the right activities.
                        6. If forecast is "none" or unavailable, plan normally without weather constraints.
                        %s

                        Important weather interpretation update:
                        - Treat RAIN FLEX or legacy LIGHT RAIN as a flexible-planning signal, not an indoor-only rule.
                        - Keep destination-defining outdoor/scenic places when practical; use shorter time windows and indoor backup notes.
                        - Treat SEVERE WEATHER RISK or legacy HIGH RAIN RISK as a hard safety constraint only for unsafe outdoor/water/adventure activities on the affected day.
                        - If weather blocks all or most destination-signature scenic experiences, explain the omission/substitution in requestFulfillment.items[].userMessage with reasonCode WEATHER_SAFETY so the user knows the plan changed for safety, not because the system missed them.

                        Style rules:
                        1. Treat Style as the user's primary planning bias, not a hard restriction.
                        2. Keep the itinerary practical and balanced; include other activity types when they improve route, meals, rest, weather safety, or the user's explicit request.
                        3. If Must visit, Avoid, Notes, weather, budget, or group needs conflict with Style, prioritize those more specific constraints.

                        Destination essence rules:
                        1. Before building the itinerary, infer the destination's signature experiences and must-try categories from your own Vietnam travel knowledge, even when they are NOT listed in the verified candidates. Examples: iconic scenic areas, old towns, caves, boat routes, viewpoints, beaches/islands, cultural sites, night markets, food streets, local dishes, craft villages, or seasonal highlights.
                        2. The verified candidates are helpful evidence, not the full universe. If a signature experience is missing from the candidate list, you may still include a specific real place/activity with a concrete name and realistic location.
                        3. Across the full trip, include a representative set of destination-signature experiences when they fit duration, route, weather, budget, and group needs. For short trips, prioritize the most iconic 1-3 experiences instead of padding with generic indoor stops.
                        4. Do not remove all famous outdoor/scenic/must-try experiences just because there is RAIN FLEX or normal rain chance. Prefer safer timing, shorter windows, backup notes, or moving them to a better day.
                        5. If normally expected signature experiences are omitted, weakened, or substituted because of severe weather, time, budget, group safety, duplication, or route constraints, add PARTIAL or NOT_APPLIED requestFulfillment items explaining the reason in Vietnamese. Do this even when the user did not explicitly request those places, so the user knows the plan changed for a real reason.

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

                        Verified place rules:
                        1. Treat verified place candidates as trusted suggestions, not an allowed-only list.
                        2. Candidates are ordered by backend relevance. Consider higher-ranked candidates first, but do not blindly pick the top items when route, weather, pacing, budget, or the user's request makes another choice better.
                        3. Prefer verified candidates when they fit the user's constraints, route, weather, and budget.
                        3a. Candidates marked priority=destination-signature are core destination experiences. Preserve them when safe and practical; if weather makes them unsafe and there is no safer slot, explain the omission or substitution in requestFulfillment with reasonCode WEATHER_SAFETY.
                        4. CRITICAL: The candidate list is NOT exhaustive. It may lack specific accommodations, restaurants, cafes, rental shops, or niche local spots. For ANY category lacking suitable candidates, you MUST actively use your extensive internal knowledge to suggest specific, real, and named businesses/places that realistically match the user's budget, style, and daily route.
                        5. When using a verified candidate, copy its exact name and use its address/coords in location, latitude, and longitude.
                        6. When using a non-candidate place or activity, it MUST be a specific, existing real-world place with a concrete proper name and address. Absolutely DO NOT use ANY generic or unnamed placeholders for ANY activity (e.g., "ăn trưa tại địa phương", "nhà hàng hải sản", "ăn tối ở khách sạn", "thuê homestay", "chợ địa phương", "quán cà phê").
                        7. Anti-Bias Rule: Do NOT lazily reuse the same default chains or luxury brands. Actively suggest diverse, budget-appropriate, logically located, and context-relevant local businesses.
                        8. Do not force every candidate into the trip; choose only what makes the itinerary practical.

                        Local transportation rules:
                        1. Make the local transportation plan explicit when places are far apart or movement has meaningful cost. Users must know how to move between places inside %s.
                        2. If Local transport is MIXED or unclear, choose the most practical option for Vietnamese travelers and say it clearly: thuê xe máy, taxi/Grab, thuê ô tô, xe đạp, đi bộ, shuttle, or a combination.
                        3. Treat Local transport as the user's preference, not an absolute law. Follow that selected mode when practical; if a different mode is safer or more realistic in Vietnam, explain why in the TRANSPORT note.
                        4. %s
                        5. Each local TRANSPORT activity must include mode, route or area, estimated duration, and group cost. Rental or taxi costs must be estimatedCost on TRANSPORT, never hidden inside FOOD/CAFE/ATTRACTION notes.
                        6. If places are close enough to walk, a clear route note that says "Đi bộ khoảng X phút" with cost 0 is enough; a separate TRANSPORT activity is optional.
                        7. Do not put all local transport detail into one unrelated dinner or attraction note.

                        Itinerary quality rules:
                        1. Return exactly %d days in the itinerary array.
                        2. %s
                        3. FOOD/CAFE activities must name a specific dish or restaurant/cafe.
                        4. ATTRACTION/ACTIVITY activities must name a specific real place in or near %s.
                        5. TRANSPORT activities must include outbound/return travel and local travel between distant clusters of places. Walking between nearby places can be documented in notes.
                        6. Do not use generic names like "ăn sáng đặc sản địa phương", "tham quan điểm nổi bật", "khám phá khu vực lân cận", "nhà hàng địa phương", or "cà phê view đẹp".
                        7. Days must be clearly different and should not repeat the same activity sequence.

                        User request fulfillment rules:
                        1. Always evaluate user-specific requests from Must visit, Avoid, and Notes in requestFulfillment.
                        2. Split meaningful requests into concrete requested items. Treat implicit phrasing as a request when it proposes an activity, place, food, experience, or constraint, for example "nhảy dù ở Đà Nẵng cũng hay mà".
                        3. If a requested item is fully reflected in the itinerary, mark it FULFILLED with reasonCode APPLIED.
                        4. If a requested item is omitted, substituted, weakened, unsafe, too expensive, duplicated, or impossible under constraints, mark it PARTIAL or NOT_APPLIED and write a short Vietnamese userMessage explaining why.
                        5. Use reasonCode WEATHER_SAFETY when rain/storm/weather risk is the main reason. Other allowed reasonCode values: APPLIED, BUDGET, TIME_CONFLICT, DUPLICATE, CONSTRAINT, UNCLEAR, OTHER.
                        6. If there is no meaningful user-specific request and no destination-signature omission/substitution needs explanation, set overallStatus to NO_REQUEST and items to []. If a signature experience is omitted or substituted for a real constraint, return PARTIAL or NOT_FULFILLED with a requestFulfillment item even without a user-specific request.
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
                req.getWeatherForecast() != null && !req.getWeatherForecast().isBlank() ? req.getWeatherForecast()
                        : "none",
                req.getVerifiedPlacesContext() != null && !req.getVerifiedPlacesContext().isBlank()
                        ? req.getVerifiedPlacesContext()
                        : "none",
                weatherSafetyOverrideGuidance(req.getWeatherForecast()),
                travelers,
                req.getDestination(),
                ItineraryQualityPolicy.localTransportGuidance(req.getDestination()),
                days,
                ItineraryQualityPolicy.vietnamPacingGuidance(),
                req.getDestination());
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
     * Uses a higher maxOutputTokens budget because gemini-2.5-flash consumes
     * thinking tokens
     * that count against the same limit, easily exhausting the 20 000-token default
     * when
     * the full schedule JSON is included in the prompt.
     */
    private String callGeminiForSingleDay(String prompt) {
        return callGeminiWithRetry(prompt, 65536);
    }

    /**
     * Core Gemini HTTP call with exponential-backoff retry for transient 503/429
     * errors.
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
                        "parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "temperature", 0.3,
                        "maxOutputTokens", maxOutputTokens,
                        "responseMimeType", "application/json"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        int[] retryDelaysMs = { 2000, 4000 };
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
            JsonNode usage = root.path("usageMetadata");
            int promptTokens = usage.path("promptTokenCount").asInt(-1);
            int outputTokens = usage.path("candidatesTokenCount").asInt(-1);
            int totalTokens = usage.path("totalTokenCount").asInt(-1);
            log.debug(
                    "Gemini response finishReason={}, textLength={}, promptTokens={}, outputTokens={}, totalTokens={}, maxOutputTokens={}",
                    finishReason, text.length(), promptTokens, outputTokens, totalTokens, maxOutputTokens);
            if (promptTokens > PROMPT_TOKEN_WARN_THRESHOLD) {
                log.warn(
                        "Gemini prompt is getting large: promptTokens={}, warnThreshold={}, totalTokens={}, maxOutputTokens={}",
                        promptTokens, PROMPT_TOKEN_WARN_THRESHOLD, totalTokens, maxOutputTokens);
            }
            if ("MAX_TOKENS".equals(finishReason)) {
                throw new RuntimeException(
                        "Gemini response was truncated by maxOutputTokens (" + maxOutputTokens + ")");
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
     * Returns a compact JSON representation of the schedule to use as prompt
     * context.
     * Strips heavy / redundant fields (note, latitude, longitude, googlePlaceId,
     * sortOrder,
     * estimatedCost, rating, duration) that the model does not need to avoid
     * duplicate places
     * or understand pacing. This significantly reduces input token count.
     */
    private String slimScheduleJson(List<TripDto.DayResponse> schedule) {
        if (schedule == null || schedule.isEmpty())
            return "[]";
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
            if (root == null || !root.isObject()) {
                throw new AiResponseFormatException(
                        "expected one JSON object with keys \"itinerary\" and \"requestFulfillment\"");
            }

            JsonNode itineraryNode = root.path("itinerary");
            if (!itineraryNode.isArray()) {
                throw new AiResponseFormatException("missing required array key \"itinerary\"");
            }

            List<TripDto.DayResponse> days = new ArrayList<>();
            for (JsonNode dayNode : itineraryNode) {
                days.add(parseDayNode(dayNode));
            }
            return new GeneratedItineraryResult(days, parseRequiredRequestFulfillment(root.path("requestFulfillment")));
        } catch (AiResponseFormatException e) {
            throw e;
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

    private String nullToBlank(String value) {
        return value == null ? "" : value;
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
        int nonLogisticsActivities = 0;
        String recoverableCostIssue = null;
        boolean bundledIntercityTransportCost = hasBundledIntercityTransportCost(days, req);
        for (TripDto.DayResponse day : days) {
            int minActivities = minimumActivitiesForDay(day, req);
            if (day.getActivities() == null || day.getActivities().size() < minActivities) {
                return QualityCheck.fail("day " + day.getDay() + " has fewer than " + minActivities + " activities");
            }
            if (ItineraryQualityPolicy.exceedsTotalItems(day.getActivities().size())) {
                return QualityCheck.fail("day " + day.getDay() + " has too many activities");
            }
            int dayNonLogisticsActivities = 0;
            StringBuilder fingerprint = new StringBuilder();
            for (TripDto.ActivityResponse act : day.getActivities()) {
                totalActivities++;
                String name = normalize(act.getName());
                String location = normalize(act.getLocation());
                String type = normalize(act.getType());
                String note = normalize(act.getNote());
                fingerprint.append(name).append("|");
                
                if (!isValidType(type)) {
                    return QualityCheck.fail("activity has invalid type: " + type + " for " + act.getName());
                }
                
                if (act.getEstimatedCost() < 0) {
                    return QualityCheck.fail("activity has negative cost: " + act.getName());
                }
                String costIssue = requiredActivityCostIssue(act, req, type, name, location, note,
                        bundledIntercityTransportCost);
                if (costIssue != null) {
                    if (isRecoverableCostQualityIssue(costIssue)) {
                        recoverableCostIssue = recoverableCostIssue == null ? costIssue : recoverableCostIssue;
                    } else {
                        return QualityCheck.fail(costIssue);
                    }
                }
                if (isGenericActivity(name, location, type)) {
                    genericActivities++;
                }
                if (isLocalTransportActivity(type, name, location, note, req)) {
                    localTransportActivities++;
                }
                if (!isLogisticsType(type)) {
                    dayNonLogisticsActivities++;
                    nonLogisticsActivities++;
                }
            }
            if (ItineraryQualityPolicy.exceedsNonLogisticsItems(dayNonLogisticsActivities)) {
                return QualityCheck.fail("day " + day.getDay() + " has too many non-logistics activities");
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

        if (recoverableCostIssue != null) {
            return QualityCheck.fail(recoverableCostIssue);
        }

        return QualityCheck.pass();
    }

    private QualityCheck assessRegeneratedDayQuality(
            TripDto.DayResponse day,
            List<TripDto.DayResponse> currentSchedule,
            TripDto.GenerateRequest req) {
        if (day == null) {
            return QualityCheck.fail("response has no day");
        }
        int minActivities = minimumActivitiesForDay(day, req);
        if (day.getActivities() == null || day.getActivities().size() < minActivities) {
            return QualityCheck.fail("regenerated day has fewer than " + minActivities + " activities");
        }
        if (ItineraryQualityPolicy.exceedsTotalItems(day.getActivities().size())) {
            return QualityCheck.fail("regenerated day has too many activities");
        }

        int genericActivities = 0;
        int localTransportActivities = 0;
        int nonLogisticsActivities = 0;
        String recoverableCostIssue = null;
        Set<String> seenTimes = new HashSet<>();
        List<TimeRange> ranges = new ArrayList<>();
        List<TripDto.DayResponse> scheduleForCostContext = new ArrayList<>();
        scheduleForCostContext.add(day);
        if (currentSchedule != null) {
            scheduleForCostContext.addAll(currentSchedule);
        }
        boolean bundledIntercityTransportCost = hasBundledIntercityTransportCost(scheduleForCostContext, req);
        String avoid = normalize(String.join("\n",
                req.getAvoid() != null ? req.getAvoid() : "",
                extractNegativeInstruction(req.getNotes())));

        for (TripDto.ActivityResponse act : day.getActivities()) {
            String name = normalize(act.getName());
            String location = normalize(act.getLocation());
            String type = normalize(act.getType());
            String note = normalize(act.getNote());
            String combined = String.join(" ", name, location, note);

            if (name.isBlank()) {
                return QualityCheck.fail("activity has no name");
            }
            if (!isValidType(type)) {
                return QualityCheck.fail("activity has invalid type: " + type + " for " + act.getName());
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
            String costIssue = requiredActivityCostIssue(act, req, type, name, location, note,
                    bundledIntercityTransportCost);
            if (costIssue != null) {
                if (isRecoverableCostQualityIssue(costIssue)) {
                    recoverableCostIssue = recoverableCostIssue == null ? costIssue : recoverableCostIssue;
                } else {
                    return QualityCheck.fail(costIssue);
                }
            }
            if (!avoid.isBlank() && containsAvoidedContent(combined, avoid)) {
                return QualityCheck.fail("activity appears to violate avoid instruction: " + act.getName());
            }
            if (isGenericActivity(name, location, type)) {
                genericActivities++;
            }
            if (isLocalTransportActivity(type, name, location, note, req)) {
                localTransportActivities++;
            }
            if (!isLogisticsType(type)) {
                nonLogisticsActivities++;
            }

            ranges.add(new TimeRange(act.getTime(), parseActivityDurationMinutes(act.getDuration()), type));
        }

        ranges.sort(Comparator.comparing(TimeRange::start));
        for (int i = 1; i < ranges.size(); i++) {
            // TRANSPORT activities are bookings/rentals that do not block a fixed time
            // slot;
            // exclude them from strict overlap checking to avoid false positives.
            TimeRange previous = ranges.get(i - 1);
            TimeRange current = ranges.get(i);
            if (previous.isLogistics() || current.isLogistics())
                continue;
            if (current.overlapMinutes(previous) > 30) {
                return QualityCheck.fail("activity times overlap");
            }
        }

        if (genericActivities > Math.max(2, day.getActivities().size() / 2)) {
            return QualityCheck.fail("too many generic activities in regenerated day");
        }
        if (ItineraryQualityPolicy.exceedsNonLogisticsItems(nonLogisticsActivities)) {
            return QualityCheck.fail("regenerated day has too many non-logistics activities");
        }
        if (hasTooManyDuplicatePlaces(day, currentSchedule)) {
            return QualityCheck.fail("regenerated day repeats too many places from other days");
        }

        if (recoverableCostIssue != null) {
            return QualityCheck.fail(recoverableCostIssue);
        }

        return QualityCheck.pass();
    }

    private String requiredActivityCostIssue(
            TripDto.ActivityResponse act,
            TripDto.GenerateRequest req,
            String normalizedType,
            String normalizedName,
            String normalizedLocation,
            String normalizedNote,
            boolean bundledIntercityTransportCost) {
        String combined = String.join(" ", normalizedName, normalizedLocation, normalizedNote);
        long cost = Math.max(0, act.getEstimatedCost());

        if (isOutboundOrReturnTransport(combined, req)) {
            if (cost == 0 && !bundledIntercityTransportCost) {
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

    private boolean isRecoverableCostQualityIssue(String reason) {
        if (reason == null || reason.isBlank()) {
            return false;
        }
        return reason.startsWith("intercity transport cost is missing:")
                || reason.startsWith("vehicle rental cost is missing:");
    }

    private boolean hasBundledIntercityTransportCost(List<TripDto.DayResponse> days, TripDto.GenerateRequest req) {
        if (days == null || days.isEmpty()) {
            return false;
        }
        for (TripDto.DayResponse day : days) {
            if (day == null || day.getActivities() == null) {
                continue;
            }
            for (TripDto.ActivityResponse activity : day.getActivities()) {
                String type = normalize(activity.getType());
                if (!type.equals("transport") || Math.max(0, activity.getEstimatedCost()) == 0) {
                    continue;
                }
                String combined = normalize(String.join(" ",
                        activity.getName() != null ? activity.getName() : "",
                        activity.getLocation() != null ? activity.getLocation() : "",
                        activity.getNote() != null ? activity.getNote() : ""));
                if (isOutboundOrReturnTransport(combined, req) && mentionsBundledIntercityTransportCost(combined)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean mentionsBundledIntercityTransportCost(String normalizedText) {
        return containsAny(normalizedText,
                "khu hoi",
                "hai chieu",
                "2 chieu",
                "ca di va ve",
                "di va ve",
                "ca di ca ve",
                "round trip",
                "round-trip",
                "return ticket",
                "bao gom chieu ve",
                "bao gom ca chieu ve",
                "bao gom chuyen bay ve",
                "bao gom ve ve",
                "bao gom ca tien ve");
    }

    private boolean isVehicleRentalStartActivity(String normalizedType, String normalizedText) {
        if (!normalizedType.equals("transport")) {
            return false;
        }
        if (isVehicleRentalReturnActivity(normalizedText)) {
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

    private boolean isVehicleRentalReturnActivity(String normalizedText) {
        return containsAny(normalizedText,
                "tra xe",
                "tra lai xe",
                "hoan tra xe",
                "ban giao xe",
                "return rental",
                "return rented",
                "return motorbike",
                "return bike",
                "return bicycle",
                "return car");
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

        // "bo" removed: too short, collides with common Vietnamese words/place names
        // (bờ biển, bổ sung, etc.)
        List<String> negativeMarkers = List.of("khong muon", "khong thich", "tranh", "dung", "khong can", "loai bo",
                "khong lay", "han che", "di ung", "kieng");
        Set<String> terms = new LinkedHashSet<>();
        for (String clause : normalized.split("[,;\\.\\n]+")) {
            String trimmedClause = clause.trim();
            if (trimmedClause.isBlank())
                continue;

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
            cleaned = cleaned
                    .replaceAll("^(?:an|uong|thuong thuc|dung|di|ghe|toi|tham quan|mua|check in|trai nghiem)\\s+", "")
                    .trim();
        } while (!cleaned.equals(previous));
        return cleaned
                .replaceAll("\\s+(?:nua|trong ngay nay|trong lich trinh|cho ngay nay)$", "")
                .trim();
    }

    private boolean hasTooManyDuplicatePlaces(TripDto.DayResponse regeneratedDay,
            List<TripDto.DayResponse> currentSchedule) {
        Set<String> otherPlaces = new HashSet<>();
        for (TripDto.DayResponse existingDay : currentSchedule == null ? List.<TripDto.DayResponse>of()
                : currentSchedule) {
            if (existingDay.getDay() == regeneratedDay.getDay() || existingDay.getActivities() == null)
                continue;
            for (TripDto.ActivityResponse activity : existingDay.getActivities()) {
                String key = normalize(activity.getName() + " " + activity.getLocation());
                if (!key.isBlank())
                    otherPlaces.add(key);
            }
        }

        int duplicateCount = 0;
        int comparableCount = 0;
        for (TripDto.ActivityResponse activity : regeneratedDay.getActivities()) {
            String type = normalize(activity.getType());
            if (type.equals("transport") || type.equals("accommodation"))
                continue;
            String key = normalize(activity.getName() + " " + activity.getLocation());
            if (key.isBlank())
                continue;
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

    private boolean isValidType(String type) {
        if (type == null) return false;
        return type.equals("food") || type.equals("cafe") || type.equals("attraction") 
                || type.equals("transport") || type.equals("accommodation") || type.equals("activity");
    }

    private int parseActivityDurationMinutes(String duration) {
        if (duration == null || duration.isBlank())
            return 60;
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

    private int minimumActivitiesForDay(TripDto.DayResponse day, TripDto.GenerateRequest req) {
        if (day == null) {
            return 3;
        }
        boolean edgeDay = day.getDay() <= 1 || day.getDay() >= Math.max(1, req.getDays());
        boolean hasIntercityTransport = day.getActivities() != null && day.getActivities().stream()
                .anyMatch(activity -> isOutboundOrReturnTransport(normalize(String.join(" ",
                        nullToBlank(activity.getName()),
                        nullToBlank(activity.getLocation()),
                        nullToBlank(activity.getNote()))), req));
        return edgeDay || hasIntercityTransport || isRelaxedPacing(req)
                ? ItineraryQualityPolicy.MIN_ACTIVITIES_LIGHT_DAY
                : ItineraryQualityPolicy.MIN_ACTIVITIES_DEFAULT;
    }

    private boolean isRelaxedPacing(TripDto.GenerateRequest req) {
        String context = normalize(String.join(" ",
                nullToBlank(req.getStyle()),
                nullToBlank(req.getGroupType()),
                nullToBlank(req.getNotes())));
        return containsAny(context,
                "relaxing",
                "nghi duong",
                "family",
                "tre em",
                "nguoi lon tuoi",
                "nhe nhang",
                "thu gian");
    }

    private boolean isLogisticsType(String normalizedType) {
        return normalizedType.equals("transport") || normalizedType.equals("accommodation");
    }

    private double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return earthRadiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private boolean isLocalTransportActivity(
            String normalizedType,
            String normalizedName,
            String normalizedLocation,
            String normalizedNote,
            TripDto.GenerateRequest req) {
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

    private boolean containsLocalTransportMode(String normalizedText) {
        List<String> localTransportTerms = List.of(
                "thue xe may", "xe may", "taxi", "grab", "thue xe o to", "thue o to", "thue oto",
                "o to rieng", "oto rieng", "xe rieng", "co tai xe", "xe hop dong", "xe dua don",
                "xe dien", "xe dap", "di bo", "xe buyt", "xe bus", "shuttle");
        return localTransportTerms.stream().anyMatch(normalizedText::contains);
    }

    private boolean isOutboundOrReturnTransport(String normalizedText, TripDto.GenerateRequest req) {
        String departure = normalize(req.getDeparture());
        String destination = normalize(req.getDestination());
        if (departure.isBlank() || destination.isBlank()) {
            return false;
        }

        return normalizedText.contains(departure) && normalizedText.contains(destination);
    }

    private boolean isGenericActivity(String normalizedName, String normalizedLocation, String normalizedType) {
        boolean transportOrAccommodation = normalizedType.equals("transport") || normalizedType.equals("accommodation");
        if (transportOrAccommodation) {
            return false;
        }

        // Advanced heuristic: remove common generic filler words and check if anything
        // meaningful is left
        String cleanedName = normalizedName;
        String[] prefixesToRemove = {
                "an sang", "an trua", "an toi", "an", "uong", "thuong thuc", "trai nghiem",
                "tham quan", "kham pha", "check in", "mua sam", "dao", "nghi ngoi", "tu do",
                "thue xe may", "thue o to", "thue xe", "di chuyen"
        };
        for (String prefix : prefixesToRemove) {
            cleanedName = cleanedName.replaceAll("\\b" + prefix + "\\b", "");
        }

        String[] genericNouns = {
                "tai dia phuong", "dac san", "dia phuong", "hai san", "nha hang", "quan an", "quan",
                "khach san", "homestay", "resort", "bai bien", "cho", "ca phe", "cafe",
                "trung tam", "vung ven", "noi bat", "chinh", "van hoa", "thien nhien",
                "banh mi", "bun bo hue", "bun bo", "pho", "com", "bua", "dem", "view dep",
                "quanh", "gan", "o", "tai", "cua hang", "sieu thi", "khu vuc", "diem",
                "thue", "xe may", "o to", "oto", "xe dap", "xe dien", "xe"
        };
        for (String noun : genericNouns) {
            cleanedName = cleanedName.replaceAll("\\b" + noun + "\\b", " ");
        }

        cleanedName = cleanedName.replaceAll("\\s+", "").trim();

        // If 1 or 0 non-space characters remain after removing filler words, it's a completely generic name
        // (Allows very short specific names like "Vy", "Bo", "Oc" of length >= 2 to pass)
        return cleanedName.length() <= 1;
    }

    private String normalize(String value) {
        if (value == null)
            return "";
        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace("đ", "d")
                .replace("Đ", "D")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private record TimeRange(String start, int durationMinutes, String type) {
        java.time.LocalTime end() {
            return java.time.LocalTime.parse(start).plusMinutes(Math.max(15, durationMinutes));
        }

        boolean isLogistics() {
            return "transport".equals(type) || "accommodation".equals(type);
        }

        long overlapMinutes(TimeRange other) {
            java.time.LocalTime startTime = java.time.LocalTime.parse(start);
            java.time.LocalTime endTime = end();
            java.time.LocalTime otherStart = java.time.LocalTime.parse(other.start());
            java.time.LocalTime otherEnd = other.end();
            java.time.LocalTime overlapStart = startTime.isAfter(otherStart) ? startTime : otherStart;
            java.time.LocalTime overlapEnd = endTime.isBefore(otherEnd) ? endTime : otherEnd;
            if (!overlapStart.isBefore(overlapEnd)) {
                return 0;
            }
            return java.time.Duration.between(overlapStart, overlapEnd).toMinutes();
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
