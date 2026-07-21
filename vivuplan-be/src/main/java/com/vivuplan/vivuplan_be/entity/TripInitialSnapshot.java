package com.vivuplan.vivuplan_be.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "trip_initial_snapshots")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TripInitialSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false, unique = true)
    private Trip trip;

    @Column(name = "normalized_snapshot", nullable = false, columnDefinition = "TEXT")
    private String normalizedSnapshot;

    @Column(name = "ai_request_id", length = 64)
    private String aiRequestId;

    @Column(name = "model", length = 100)
    private String model;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
