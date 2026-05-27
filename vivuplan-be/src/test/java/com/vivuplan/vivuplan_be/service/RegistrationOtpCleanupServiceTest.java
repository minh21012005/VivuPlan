package com.vivuplan.vivuplan_be.service;

import com.vivuplan.vivuplan_be.repository.RegistrationOtpRepository;
import com.vivuplan.vivuplan_be.repository.PasswordResetOtpRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationOtpCleanupServiceTest {

    @Mock
    private RegistrationOtpRepository registrationOtpRepository;

    @Mock
    private PasswordResetOtpRepository passwordResetOtpRepository;

    @Test
    void cleanupExpiredOtpsDeletesRecordsPastExpiry() {
        RegistrationOtpCleanupService service = new RegistrationOtpCleanupService(registrationOtpRepository, passwordResetOtpRepository);
        when(registrationOtpRepository.deleteByExpiresAtBefore(any(LocalDateTime.class))).thenReturn(2L);
        when(passwordResetOtpRepository.deleteByExpiresAtBefore(any(LocalDateTime.class))).thenReturn(1L);

        service.cleanupExpiredOtps();

        verify(registrationOtpRepository).deleteByExpiresAtBefore(any(LocalDateTime.class));
        verify(passwordResetOtpRepository).deleteByExpiresAtBefore(any(LocalDateTime.class));
    }
}
