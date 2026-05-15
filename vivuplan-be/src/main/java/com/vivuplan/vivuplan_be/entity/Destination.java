package com.vivuplan.vivuplan_be.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "destinations",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_destinations_slug", columnNames = "slug")
        },
        indexes = {
                @Index(name = "idx_destinations_name", columnList = "name"),
                @Index(name = "idx_destinations_region", columnList = "region"),
                @Index(name = "idx_destinations_featured", columnList = "featured"),
                @Index(name = "idx_destinations_active", columnList = "active"),
                @Index(name = "idx_destinations_display_order", columnList = "display_order")
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Destination {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 180)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Region region;

    @Column(length = 120)
    private String tourismRegion;

    @Column(length = 120)
    private String province;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private DestinationCategory category;

    @Column(length = 180)
    private String tag;

    @Column(length = 80)
    private String recommendedDays;

    @Column
    private Double rating;

    @Column
    private Integer tripCount;

    @Column(length = 500)
    private String imageUrl;

    @Column(length = 360)
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 180)
    private String bestTimeToVisit;

    @Column
    private Long estimatedBudgetMin;

    @Column
    private Long estimatedBudgetMax;

    @Column
    private Double latitude;

    @Column
    private Double longitude;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "destination_tags", joinColumns = @JoinColumn(name = "destination_id"))
    @Column(name = "tag", length = 80)
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    @Column(nullable = false)
    @Builder.Default
    private Boolean featured = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(length = 140)
    private String sourceName;

    @Column(length = 500)
    private String sourceUrl;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum Region {
        MIEN_BAC("Miền Bắc"),
        MIEN_TRUNG("Miền Trung"),
        MIEN_NAM("Miền Nam");

        private final String label;

        Region(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum DestinationCategory {
        CITY,
        BEACH,
        ISLAND,
        MOUNTAIN,
        HERITAGE,
        NATURE,
        CULTURE,
        FOOD,
        SPIRITUAL
    }
}
