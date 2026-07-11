package com.vivuplan.vivuplan_be.service;

import com.vivuplan.vivuplan_be.dto.AdminDto;
import com.vivuplan.vivuplan_be.entity.AiUsageLog;
import com.vivuplan.vivuplan_be.entity.Role;
import com.vivuplan.vivuplan_be.entity.User;
import com.vivuplan.vivuplan_be.repository.AiUsageLogRepository;
import com.vivuplan.vivuplan_be.repository.PaymentOrderRepository;
import com.vivuplan.vivuplan_be.repository.RoleRepository;
import com.vivuplan.vivuplan_be.repository.TripRepository;
import com.vivuplan.vivuplan_be.repository.UserRepository;
import com.vivuplan.vivuplan_be.repository.UserWalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private TripRepository tripRepository;

    @Mock
    private PaymentOrderRepository paymentOrderRepository;

    @Mock
    private UserWalletRepository userWalletRepository;

    @Mock
    private AiUsageLogRepository aiUsageLogRepository;

    @Mock
    private com.vivuplan.vivuplan_be.repository.AiAttemptPayloadRepository aiAttemptPayloadRepository;

    private AdminService service() {
        return new AdminService(
                userRepository,
                roleRepository,
                tripRepository,
                paymentOrderRepository,
                userWalletRepository,
                aiUsageLogRepository,
                aiAttemptPayloadRepository);
    }

    @Test
    void updateUserLockLocksUser() {
        AdminService service = service();
        User user = User.builder()
                .id(2L)
                .name("User")
                .email("user@example.com")
                .roles(Set.of(Role.builder().name(Role.RoleName.USER).build()))
                .build();

        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        AdminDto.UserSummary response = service.updateUserLock(1L, 2L, true);

        assertThat(user.isAccountLocked()).isTrue();
        assertThat(response.getAccountLocked()).isTrue();
    }

    @Test
    void updateUserLockUnlocksUser() {
        AdminService service = service();
        User user = User.builder()
                .id(2L)
                .name("User")
                .email("user@example.com")
                .roles(Set.of(Role.builder().name(Role.RoleName.USER).build()))
                .accountLocked(true)
                .build();

        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        AdminDto.UserSummary response = service.updateUserLock(1L, 2L, false);

        assertThat(user.isAccountLocked()).isFalse();
        assertThat(response.getAccountLocked()).isFalse();
    }

    @Test
    void updateUserLockRejectsSelfLock() {
        AdminService service = service();
        User admin = User.builder()
                .id(1L)
                .name("Admin")
                .email("admin@example.com")
                .roles(Set.of(Role.builder().name(Role.RoleName.ADMIN).build()))
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.updateUserLock(1L, 1L, true))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(admin.isAccountLocked()).isFalse();
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateUserLockRejectsLastUnlockedAdmin() {
        AdminService service = service();
        User admin = User.builder()
                .id(2L)
                .name("Admin")
                .email("admin@example.com")
                .roles(Set.of(Role.builder().name(Role.RoleName.ADMIN).build()))
                .build();

        when(userRepository.findById(2L)).thenReturn(Optional.of(admin));
        when(userRepository.countUnlockedByRoleName(Role.RoleName.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> service.updateUserLock(1L, 2L, true))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(admin.isAccountLocked()).isFalse();
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void aiCostSummaryAverageCostIgnoresZeroCostHttpOnlyRequests() {
        AdminService service = service();
        LocalDate today = LocalDate.now();
        List<AiUsageLog> logs = List.of(
                aiLog("req-success", 1, AiUsageLog.Status.SUCCESS, 1_000L, 1_000, 500, 250, 0, LocalDateTime.now()),
                aiLog("req-retry", 1, AiUsageLog.Status.HTTP_ERROR, 0L, 0, 0, 0, 0, LocalDateTime.now()),
                aiLog("req-retry", 2, AiUsageLog.Status.SUCCESS, 2_000L, 2_000, 1_000, 500, 0, LocalDateTime.now()),
                aiLog("req-http-only", 1, AiUsageLog.Status.HTTP_ERROR, 0L, 0, 0, 0, 0, LocalDateTime.now())
        );

        when(aiUsageLogRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any()))
                .thenReturn(logs);

        AdminDto.AiCostSummaryResponse response = service.aiCostSummary(today, today, "PLAN_GENERATION", "ALL");

        assertThat(response.getRequests()).isEqualTo(3);
        assertThat(response.getAttempts()).isEqualTo(4);
        assertThat(response.getTotalCostVnd()).isEqualTo(3_000L);
        assertThat(response.getAverageCosts()).singleElement()
                .satisfies(item -> {
                    assertThat(item.getOperations()).isEqualTo(2);
                    assertThat(item.getAvgCostVnd()).isEqualTo(1_500L);
                });
    }

    @Test
    void aiCostSummaryAverageDurationIncludesRetryWaitAndIgnoresZeroDurationRequests() {
        AdminService service = service();
        LocalDate today = LocalDate.now();
        LocalDateTime base = LocalDateTime.now();
        List<AiUsageLog> logs = List.of(
                aiLog("req-success", 1, AiUsageLog.Status.SUCCESS, 1_000L, 1_000, 500, 250,
                        10_000, base.plusSeconds(10)),
                aiLog("req-retry", 1, AiUsageLog.Status.HTTP_ERROR, 0L, 0, 0, 0,
                        1_000, base.plusSeconds(1)),
                aiLog("req-retry", 2, AiUsageLog.Status.SUCCESS, 2_000L, 2_000, 1_000, 500,
                        2_000, base.plusSeconds(8)),
                aiLog("req-zero", 1, AiUsageLog.Status.HTTP_ERROR, 0L, 0, 0, 0,
                        0, base.plusSeconds(12))
        );

        when(aiUsageLogRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any()))
                .thenReturn(logs);

        AdminDto.AiCostSummaryResponse response = service.aiCostSummary(today, today, "PLAN_GENERATION", "ALL");

        assertThat(response.getAvgDurationMs()).isEqualTo(9_000L);
        assertThat(response.getOperationHealth()).singleElement()
                .satisfies(item -> {
                    assertThat(item.getAvgDurationMs()).isEqualTo(9_000L);
                    assertThat(item.getMaxDurationMs()).isEqualTo(10_000L);
                });
    }

    private AiUsageLog aiLog(
            String requestId,
            int attemptNumber,
            AiUsageLog.Status status,
            long costVnd,
            int promptTokens,
            int outputTokens,
            int thinkingTokens,
            long durationMs,
            LocalDateTime createdAt) {
        int totalTokens = promptTokens + outputTokens + thinkingTokens;
        return AiUsageLog.builder()
                .operation(AiUsageLog.Operation.PLAN_GENERATION)
                .status(status)
                .requestId(requestId)
                .attemptNumber(attemptNumber)
                .model("gemini-2.5-flash")
                .estimatedCostVnd(costVnd)
                .promptTokens(promptTokens)
                .outputTokens(outputTokens)
                .thinkingTokens(thinkingTokens)
                .totalTokens(totalTokens)
                .durationMs(durationMs)
                .createdAt(createdAt)
                .build();
    }
}
