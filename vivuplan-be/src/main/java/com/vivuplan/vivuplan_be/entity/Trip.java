package com.vivuplan.vivuplan_be.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "trips",
        indexes = {
                @Index(name = "idx_trips_user_created", columnList = "user_id, created_at"),
                @Index(name = "idx_trips_public_views", columnList = "is_public, view_count"),
                @Index(name = "idx_trips_share_code", columnList = "share_code"),
                @Index(name = "idx_trips_destination", columnList = "destination"),
                @Index(name = "idx_trips_start_date", columnList = "start_date")
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 160)
    private String destination;

    @Column(length = 160)
    private String departure;

    @Column(nullable = false)
    private Integer days;

    @Column
    private LocalDate startDate;

    @Column
    private LocalDate endDate;

    @Column(nullable = false)
    private Long budgetPerPerson;  // VND

    @Column
    private Long budgetTotal;       // VND, when user inputs total group budget

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private BudgetMode budgetMode = BudgetMode.PER_PERSON;

    @Column(nullable = false)
    @Builder.Default
    private Integer travelerCount = 1;

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
    private TransportMode transport = TransportMode.MIXED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TransportMode outboundTransport = TransportMode.MIXED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TransportMode localTransport = TransportMode.MIXED;

    @Column(nullable = false)
    @Builder.Default
    private Boolean destinationSuggested = false;

    @Column(columnDefinition = "TEXT")
    private String mustVisit;

    @Column(columnDefinition = "TEXT")
    private String avoid;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TripStatus status = TripStatus.DRAFT;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isPublic = false;

    @Column(unique = true, length = 16)
    private String shareCode;

    @Column(nullable = false)
    @Builder.Default
    private Integer viewCount = 0;

    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @OrderBy("dayNumber ASC")
    @Builder.Default
    private List<ItineraryDay> itineraryDays = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum TravelStyle { ADVENTURE, RELAXING, CULTURAL, NIGHTLIFE, FOODIE }
    public enum GroupType   { SOLO, COUPLE, FRIENDS, FAMILY }
    public enum BudgetMode { PER_PERSON, TOTAL }
    public enum TransportMode { MOTORBIKE, CAR, BUS, PLANE, TRAIN, WALKING, MIXED }
    public enum TripStatus  { DRAFT, PLANNED, COMPLETED }
}
