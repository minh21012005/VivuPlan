package com.vivuplan.vivuplan_be.dto;

import com.vivuplan.vivuplan_be.entity.Destination;
import lombok.Builder;
import lombok.Data;

import java.util.List;

public class DestinationDto {

    @Data
    @Builder
    public static class DestinationResponse {
        private Long id;
        private String name;
        private String slug;
        private String region;
        private String tourismRegion;
        private String province;
        private String category;
        private String tag;
        private String recommendedDays;
        private Double rating;
        private Integer tripCount;
        private String imageUrl;
        private String summary;
        private String description;
        private String bestTimeToVisit;
        private Long estimatedBudgetMin;
        private Long estimatedBudgetMax;
        private Double latitude;
        private Double longitude;
        private List<String> tags;
        private Boolean featured;
        private String sourceName;
        private String sourceUrl;

        public static DestinationResponse from(Destination destination) {
            return DestinationResponse.builder()
                    .id(destination.getId())
                    .name(destination.getName())
                    .slug(destination.getSlug())
                    .region(destination.getRegion().getLabel())
                    .tourismRegion(destination.getTourismRegion())
                    .province(destination.getProvince())
                    .category(destination.getCategory().name())
                    .tag(destination.getTag())
                    .recommendedDays(destination.getRecommendedDays())
                    .rating(destination.getRating())
                    .tripCount(destination.getTripCount())
                    .imageUrl(destination.getImageUrl())
                    .summary(destination.getSummary())
                    .description(destination.getDescription())
                    .bestTimeToVisit(destination.getBestTimeToVisit())
                    .estimatedBudgetMin(destination.getEstimatedBudgetMin())
                    .estimatedBudgetMax(destination.getEstimatedBudgetMax())
                    .latitude(destination.getLatitude())
                    .longitude(destination.getLongitude())
                    .tags(destination.getTags())
                    .featured(destination.getFeatured())
                    .sourceName(destination.getSourceName())
                    .sourceUrl(destination.getSourceUrl())
                    .build();
        }
    }

    @Data
    @Builder
    public static class LatLonResponse {
        private Double lat;
        private Double lon;
    }

    @Data
    @Builder
    public static class WeatherDayResponse {
        private String date;
        private int code;
        private double maxTemp;
        private double minTemp;
        private double precipitationMm;
        private int precipitationProbability;
        private double windspeedKmh;
    }
}
