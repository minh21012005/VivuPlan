package com.vivuplan.vivuplan_be.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "places",
        indexes = {
                @Index(name = "idx_places_destination", columnList = "destination"),
                @Index(name = "idx_places_type", columnList = "type"),
                @Index(name = "idx_places_verified", columnList = "verified"),
                @Index(name = "idx_places_google_place_id", columnList = "google_place_id"),
                @Index(name = "idx_places_destination_type", columnList = "destination, type")
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Place {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 220)
    private String name;

    @Column(nullable = false, length = 160)
    private String destination;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PlaceType type;

    @Column(length = 320)
    private String address;

    @Column(length = 80)
    private String priceLevel;

    @Column
    private Long estimatedCostMin;

    @Column
    private Long estimatedCostMax;

    @Column
    private Double rating;

    @Column
    private Double latitude;

    @Column
    private Double longitude;

    @Column(unique = true, length = 160)
    private String googlePlaceId;

    @Column(length = 500)
    private String imageUrl;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String openingHours;

    @Column(nullable = false)
    @Builder.Default
    private Boolean verified = false;

    @Column(length = 120)
    private String source;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum PlaceType {
        FOOD,
        CAFE,
        ATTRACTION,
        ACCOMMODATION,
        TRANSPORT,
        ACTIVITY,
        NIGHTLIFE
    }
}
