package com.vivuplan.vivuplan_be.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "itinerary_days",
        indexes = {
                @Index(name = "idx_itinerary_days_trip_day", columnList = "trip_id, day_number")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_itinerary_days_trip_day", columnNames = {"trip_id", "day_number"})
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ItineraryDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(nullable = false)
    private Integer dayNumber;

    @Column(nullable = false, length = 220)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @OneToMany(mappedBy = "itineraryDay", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<Activity> activities = new ArrayList<>();
}
