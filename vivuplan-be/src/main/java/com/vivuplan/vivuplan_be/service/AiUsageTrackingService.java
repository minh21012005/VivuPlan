package com.vivuplan.vivuplan_be.service;

import com.vivuplan.vivuplan_be.entity.AiAttemptPayload;
import com.vivuplan.vivuplan_be.entity.AiUsageLog;
import com.vivuplan.vivuplan_be.entity.Trip;
import com.vivuplan.vivuplan_be.entity.User;
import com.vivuplan.vivuplan_be.repository.AiAttemptPayloadRepository;
import com.vivuplan.vivuplan_be.repository.AiUsageLogRepository;
import com.vivuplan.vivuplan_be.repository.TripRepository;
import com.vivuplan.vivuplan_be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class AiUsageTrackingService {

    private final AiUsageLogRepository aiUsageLogRepository;
    private final AiAttemptPayloadRepository aiAttemptPayloadRepository;
    private final UserRepository userRepository;
    private final TripRepository tripRepository;

    @Value("${app.ai.cost.input-usd-per-1m:${GEMINI_INPUT_USD_PER_1M:0.30}}")
    private BigDecimal inputUsdPer1M;

    @Value("${app.ai.cost.output-usd-per-1m:${GEMINI_OUTPUT_USD_PER_1M:2.50}}")
    private BigDecimal outputUsdPer1M;

    @Value("${app.ai.cost.usd-to-vnd-rate:${AI_COST_USD_TO_VND_RATE:26400}}")
    private BigDecimal usdToVndRate;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AiUsageRecord record) {
        int promptTokens = positive(record.promptTokens());
        int outputTokens = positive(record.outputTokens());
        int thinkingTokens = positive(record.thinkingTokens());
        int totalTokens = positive(record.totalTokens());

        BigDecimal inputCost = BigDecimal.valueOf(promptTokens)
                .multiply(inputUsdPer1M)
                .divide(BigDecimal.valueOf(1_000_000), 8, RoundingMode.HALF_UP);
        BigDecimal outputCost = BigDecimal.valueOf((long) outputTokens + thinkingTokens)
                .multiply(outputUsdPer1M)
                .divide(BigDecimal.valueOf(1_000_000), 8, RoundingMode.HALF_UP);
        BigDecimal estimatedCostUsd = inputCost.add(outputCost).setScale(8, RoundingMode.HALF_UP);
        long estimatedCostVnd = estimatedCostUsd.multiply(usdToVndRate)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();

        User user = record.userId() != null
                ? userRepository.findById(record.userId()).orElse(null)
                : null;
        Trip trip = record.tripId() != null
                ? tripRepository.findById(record.tripId()).orElse(null)
                : null;

        aiUsageLogRepository.save(AiUsageLog.builder()
                .operation(record.operation())
                .status(record.status())
                .requestId(record.requestId())
                .attemptNumber(record.attemptNumber())
                .user(user)
                .trip(trip)
                .model(record.model())
                .finishReason(blankToNull(record.finishReason()))
                .durationMs(record.durationMs())
                .promptTokens(promptTokens)
                .outputTokens(outputTokens)
                .thinkingTokens(thinkingTokens)
                .totalTokens(totalTokens)
                .maxOutputTokens(record.maxOutputTokens())
                .thinkingBudget(record.thinkingBudget())
                .inputUsdPer1M(inputUsdPer1M)
                .outputUsdPer1M(outputUsdPer1M)
                .usdToVndRate(usdToVndRate)
                .estimatedCostUsd(estimatedCostUsd)
                .estimatedCostVnd(estimatedCostVnd)
                .promptContext(record.promptContext())
                .errorCode(blankToNull(limit(record.errorCode(), 80)))
                .errorMessage(blankToNull(limit(record.errorMessage(), 300)))
                .build());
    }

    /**
     * Records the raw AI response and full error detail for a failed attempt
     * (INVALID_RESPONSE). Should be called right after markLatestAttempt so the
     * log row already exists.
     *
     * @param requestId        the requestId from AiCallContext
     * @param errorCategory    e.g. "QUALITY_CHECK", "JSON_CONTRACT"
     * @param errorDetail      full, untruncated error reason string
     * @param rawResponse      the raw JSON text returned by AI (will be capped to 8000 chars)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedPayload(
            String requestId,
            String errorCategory,
            String errorDetail,
            String rawResponse) {
        if (requestId == null || requestId.isBlank()) {
            return;
        }
        aiUsageLogRepository.findTopByRequestIdOrderByAttemptNumberDesc(requestId).ifPresent(log -> {
            // Avoid duplicate payload for the same log row
            if (aiAttemptPayloadRepository.findByAiUsageLogId(log.getId()).isEmpty()) {
                aiAttemptPayloadRepository.save(AiAttemptPayload.of(log, errorCategory, errorDetail, rawResponse));
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markLatestAttempt(String requestId, AiUsageLog.Status status, String errorCode, String errorMessage) {
        if (requestId == null || requestId.isBlank()) {
            return;
        }
        aiUsageLogRepository.findTopByRequestIdOrderByAttemptNumberDesc(requestId).ifPresent(log -> {
            log.setStatus(status);
            log.setErrorCode(blankToNull(limit(errorCode, 80)));
            log.setErrorMessage(blankToNull(limit(errorMessage, 300)));
        });
    }

    private int positive(Integer value) {
        return value != null && value > 0 ? value : 0;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    public record AiUsageRecord(
            AiUsageLog.Operation operation,
            AiUsageLog.Status status,
            String requestId,
            Integer attemptNumber,
            Long userId,
            Long tripId,
            String model,
            String finishReason,
            Long durationMs,
            Integer promptTokens,
            Integer outputTokens,
            Integer thinkingTokens,
            Integer totalTokens,
            Integer maxOutputTokens,
            Integer thinkingBudget,
            String errorCode,
            String errorMessage,
            String promptContext
    ) {
    }
}
