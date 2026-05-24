package com.vivuplan.vivuplan_be.service;

import com.vivuplan.vivuplan_be.repository.RegistrationOtpRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationOtpCleanupService {

    private final RegistrationOtpRepository registrationOtpRepository;

    @Scheduled(fixedDelayString = "${app.auth.registration-otp-cleanup-scan-ms:${REGISTRATION_OTP_CLEANUP_SCAN_MS:10800000}}")
    @Transactional
    public void cleanupExpiredOtps() {
        long deleted = registrationOtpRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        if (deleted > 0) {
            log.info("Deleted {} expired registration OTP records", deleted);
        }
    }
}
