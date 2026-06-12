package com.vivuplan.vivuplan_be.repository;

import com.vivuplan.vivuplan_be.entity.AiUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, Long>, JpaSpecificationExecutor<AiUsageLog> {
    List<AiUsageLog> findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(LocalDateTime from, LocalDateTime to);

    Optional<AiUsageLog> findTopByRequestIdOrderByAttemptNumberDesc(String requestId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE AiUsageLog usage SET usage.trip = null WHERE usage.trip.id = :tripId")
    int detachFromTrip(@Param("tripId") Long tripId);
}
