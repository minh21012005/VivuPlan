package com.vivuplan.vivuplan_be.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "activities")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Activity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "itinerary_day_id", nullable = false)
    private ItineraryDay itineraryDay;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String time;           // "08:30"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActivityType type;

    @Column
    private String location;

    @Column
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

    @Column
    private String googlePlaceId;

    @Column(nullable = false)
    private Integer sortOrder;

    public enum ActivityType { FOOD, CAFE, ATTRACTION, TRANSPORT, ACCOMMODATION, ACTIVITY }
}
