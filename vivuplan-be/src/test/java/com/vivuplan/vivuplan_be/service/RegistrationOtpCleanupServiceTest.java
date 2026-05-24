package com.vivuplan.vivuplan_be.service;

import com.vivuplan.vivuplan_be.repository.RegistrationOtpRepository;
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

    @Test
    void cleanupExpiredOtpsDeletesRecordsPastExpiry() {
        RegistrationOtpCleanupService service = new RegistrationOtpCleanupService(registrationOtpRepository);
        when(registrationOtpRepository.deleteByExpiresAtBefore(any(LocalDateTime.class))).thenReturn(2L);

        service.cleanupExpiredOtps();

        verify(registrationOtpRepository).deleteByExpiresAtBefore(any(LocalDateTime.class));
    }
}
