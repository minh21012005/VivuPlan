package com.vivuplan.vivuplan_be.service;

import com.vivuplan.vivuplan_be.entity.AiUsageLog;
import com.vivuplan.vivuplan_be.repository.AiUsageLogRepository;
import com.vivuplan.vivuplan_be.repository.TripRepository;
import com.vivuplan.vivuplan_be.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AiUsageTrackingServiceTest {

    @Mock
    private AiUsageLogRepository aiUsageLogRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TripRepository tripRepository;

    @Mock
    private com.vivuplan.vivuplan_be.repository.AiAttemptPayloadRepository aiAttemptPayloadRepository;

    @Test
    void recordCalculatesCostWithPriceSnapshot() {
        AiUsageTrackingService service = service();

        service.record(new AiUsageTrackingService.AiUsageRecord(
                AiUsageLog.Operation.PLAN_GENERATION,
                AiUsageLog.Status.SUCCESS,
                "req-1",
                1,
                null,
                null,
                "gemini-2.5-flash",
                "STOP",
                1200L,
                1000,
                2000,
                500,
                3500,
                32768,
                8192,
                null,
                null,
                null));

        ArgumentCaptor<AiUsageLog> captor = ArgumentCaptor.forClass(AiUsageLog.class);
        verify(aiUsageLogRepository).save(captor.capture());
        AiUsageLog log = captor.getValue();

        assertThat(log.getInputUsdPer1M()).isEqualByComparingTo("0.30");
        assertThat(log.getOutputUsdPer1M()).isEqualByComparingTo("2.50");
        assertThat(log.getUsdToVndRate()).isEqualByComparingTo("26400");
        assertThat(log.getEstimatedCostUsd()).isEqualByComparingTo("0.00655000");
        assertThat(log.getEstimatedCostVnd()).isEqualTo(173L);
        assertThat(log.getPromptTokens()).isEqualTo(1000);
        assertThat(log.getOutputTokens()).isEqualTo(2000);
        assertThat(log.getThinkingTokens()).isEqualTo(500);
    }

    @Test
    void recordTreatsMissingOrNegativeTokensAsZero() {
        AiUsageTrackingService service = service();

        service.record(new AiUsageTrackingService.AiUsageRecord(
                AiUsageLog.Operation.DESTINATION_SUGGESTION,
                AiUsageLog.Status.HTTP_ERROR,
                "req-2",
                1,
                null,
                null,
                "gemini-2.5-flash",
                null,
                100L,
                -1,
                null,
                -5,
                null,
                4096,
                1536,
                "429",
                "Too many requests",
                null));

        ArgumentCaptor<AiUsageLog> captor = ArgumentCaptor.forClass(AiUsageLog.class);
        verify(aiUsageLogRepository).save(captor.capture());
        AiUsageLog log = captor.getValue();

        assertThat(log.getPromptTokens()).isZero();
        assertThat(log.getOutputTokens()).isZero();
        assertThat(log.getThinkingTokens()).isZero();
        assertThat(log.getTotalTokens()).isZero();
        assertThat(log.getEstimatedCostUsd()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(log.getEstimatedCostVnd()).isZero();
    }

    private AiUsageTrackingService service() {
        AiUsageTrackingService service = new AiUsageTrackingService(
                aiUsageLogRepository,
                aiAttemptPayloadRepository,
                userRepository,
                tripRepository);
        ReflectionTestUtils.setField(service, "inputUsdPer1M", new BigDecimal("0.30"));
        ReflectionTestUtils.setField(service, "outputUsdPer1M", new BigDecimal("2.50"));
        ReflectionTestUtils.setField(service, "usdToVndRate", new BigDecimal("26400"));
        return service;
    }
}
