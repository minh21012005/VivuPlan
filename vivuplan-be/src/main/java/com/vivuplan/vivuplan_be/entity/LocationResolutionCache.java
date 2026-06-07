package com.vivuplan.vivuplan_be.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "location_resolution_cache",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_location_resolution_provider_query", columnNames = {"provider", "normalized_query"})
        },
        indexes = {
                @Index(name = "idx_location_resolution_status", columnList = "status"),
                @Index(name = "idx_location_resolution_last_used", columnList = "last_used_at")
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LocationResolutionCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(name = "normalized_query", nullable = false, length = 320)
    private String normalizedQuery;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Status status;

    @Column
    private Double latitude;

    @Column
    private Double longitude;

    @Column(length = 500)
    private String displayName;

    @Column
    private Integer confidence;

    @Column(length = 160)
    private String errorMessage;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime lastUsedAt;

    @PrePersist
    @PreUpdate
    void touchLastUsedAt() {
        if (lastUsedAt == null) {
            lastUsedAt = LocalDateTime.now();
        }
    }

    public enum Status {
        SUCCESS,
        NO_RESULT,
        LOW_CONFIDENCE,
        ERROR
    }
}
