package com.vivuplan.vivuplan_be.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "activities",
        indexes = {
                @Index(name = "idx_activities_day_order", columnList = "itinerary_day_id, sort_order"),
                @Index(name = "idx_activities_place", columnList = "place_id"),
                @Index(name = "idx_activities_type", columnList = "type"),
                @Index(name = "idx_activities_google_place_id", columnList = "google_place_id")
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Activity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "itinerary_day_id", nullable = false)
    private ItineraryDay itineraryDay;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id")
    private Place place;

    @Column(nullable = false, length = 220)
    private String name;

    @Column(nullable = false, length = 16)
    private String time;           // "08:30"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActivityType type;

    @Column(length = 320)
    private String location;

    @Column(length = 80)
    private String duration;       // "1 giờ 30 phút"

    @Column
    private Long estimatedCost;    // VND

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column
    private Double rating;

    @Column
    private Double latitude;

    @Column
    private Double longitude;

    @Column(length = 160)
    private String googlePlaceId;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private CoordinateSource coordinateSource;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private CoordinateConfidence coordinateConfidence;

    @Column(nullable = false)
    private Integer sortOrder;

    public enum ActivityType { FOOD, CAFE, ATTRACTION, TRANSPORT, ACCOMMODATION, ACTIVITY, NIGHTLIFE }
    public enum CoordinateSource { VERIFIED_PLACE, AI_PROVIDED, GEOCODED_LOCATION, MANUAL }
    public enum CoordinateConfidence { HIGH, MEDIUM, LOW }
}
