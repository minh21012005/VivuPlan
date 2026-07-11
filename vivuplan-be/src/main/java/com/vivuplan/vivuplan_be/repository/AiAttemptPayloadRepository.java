package com.vivuplan.vivuplan_be.repository;

import com.vivuplan.vivuplan_be.entity.AiAttemptPayload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AiAttemptPayloadRepository extends JpaRepository<AiAttemptPayload, Long> {

    Optional<AiAttemptPayload> findByAiUsageLogId(Long aiUsageLogId);
}
