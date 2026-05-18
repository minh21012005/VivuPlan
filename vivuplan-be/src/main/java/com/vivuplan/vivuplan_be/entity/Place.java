package com.vivuplan.vivuplan_be.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    @Column(length = 240)
    private String normalizedName;

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

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private IndoorOutdoor indoorOutdoor;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private WeatherSensitivity weatherSensitivity;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CostBasis costBasis;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "place_tags", joinColumns = @JoinColumn(name = "place_id"))
    @Column(name = "tag", length = 80)
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "place_aliases", joinColumns = @JoinColumn(name = "place_id"))
    @Column(name = "alias", length = 180)
    @Builder.Default
    private List<String> aliases = new ArrayList<>();

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

    @PrePersist
    @PreUpdate
    private void normalizeBeforeSave() {
        normalizedName = normalize(name);
    }

    public enum PlaceType {
        FOOD,
        CAFE,
        ATTRACTION,
        ACCOMMODATION,
        TRANSPORT,
        ACTIVITY,
        NIGHTLIFE
    }

    public enum IndoorOutdoor {
        INDOOR,
        OUTDOOR,
        MIXED
    }

    public enum WeatherSensitivity {
        LOW,
        MEDIUM,
        HIGH
    }

    public enum CostBasis {
        PER_PERSON,
        GROUP,
        PER_NIGHT,
        PER_RIDE,
        FREE,
        INCLUDED
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace("đ", "d")
                .replace("Đ", "D")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }
}
