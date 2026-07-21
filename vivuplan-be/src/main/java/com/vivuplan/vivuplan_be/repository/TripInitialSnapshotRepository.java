package com.vivuplan.vivuplan_be.repository;

import com.vivuplan.vivuplan_be.entity.TripInitialSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TripInitialSnapshotRepository extends JpaRepository<TripInitialSnapshot, Long> {

    Optional<TripInitialSnapshot> findByTripId(Long tripId);
}
