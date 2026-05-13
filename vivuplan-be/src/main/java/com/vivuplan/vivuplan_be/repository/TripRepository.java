package com.vivuplan.vivuplan_be.repository;

import com.vivuplan.vivuplan_be.entity.Trip;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TripRepository extends JpaRepository<Trip, Long> {

    List<Trip> findByUserIdOrderByCreatedAtDesc(Long userId);

    Page<Trip> findByIsPublicTrueOrderByViewCountDesc(Pageable pageable);

    Optional<Trip> findByShareCode(String shareCode);

    boolean existsByShareCode(String shareCode);

    @Query("SELECT t FROM Trip t WHERE t.user.id = :userId AND t.destination LIKE %:destination%")
    List<Trip> findByUserIdAndDestination(Long userId, String destination);

    long countByUserId(Long userId);

    long countByIsPublicTrue();

    long countByStatus(Trip.TripStatus status);
}
