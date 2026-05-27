package com.vivuplan.vivuplan_be.repository;

import com.vivuplan.vivuplan_be.entity.PasswordResetOtp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetOtpRepository extends JpaRepository<PasswordResetOtp, Long> {
    Optional<PasswordResetOtp> findByEmail(String email);

    long deleteByExpiresAtBefore(LocalDateTime before);
}
