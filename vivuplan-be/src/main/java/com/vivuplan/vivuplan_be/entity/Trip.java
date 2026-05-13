package com.vivuplan.vivuplan_be.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "trips")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String destination;

    @Column(nullable = false)
    private Integer days;

    @Column(nullable = false)
    private Long budgetPerPerson;  // VND

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TravelStyle style = TravelStyle.RELAXING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private GroupType groupType = GroupType.FRIENDS;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TransportMode transport = TransportMode.MOTORBIKE;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TripStatus status = TripStatus.DRAFT;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isPublic = false;

    @Column
    private String shareCode;

    @Column(nullable = false)
    @Builder.Default
    private Integer viewCount = 0;

    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ItineraryDay> itineraryDays = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum TravelStyle { ADVENTURE, RELAXING, CULTURAL, NIGHTLIFE, FOODIE }
    public enum GroupType   { SOLO, COUPLE, FRIENDS, FAMILY }
    public enum TransportMode { MOTORBIKE, CAR, BUS, MIXED }
    public enum TripStatus  { DRAFT, PLANNED, COMPLETED }
}
