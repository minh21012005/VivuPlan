package com.vivuplan.vivuplan_be.repository;

import com.vivuplan.vivuplan_be.entity.RegistrationOtp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RegistrationOtpRepository extends JpaRepository<RegistrationOtp, Long> {
    Optional<RegistrationOtp> findByEmail(String email);

    long deleteByExpiresAtBefore(LocalDateTime before);
}
