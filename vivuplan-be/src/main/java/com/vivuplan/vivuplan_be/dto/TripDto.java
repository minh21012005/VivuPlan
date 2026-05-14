package com.vivuplan.vivuplan_be.dto;

import com.vivuplan.vivuplan_be.entity.Activity;
import com.vivuplan.vivuplan_be.entity.Trip;
import lombok.Data;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

public class TripDto {

    @Data
    public static class CreateRequest {
        @NotBlank(message = "Điểm đến không được để trống")
        private String destination;

        @NotBlank(message = "Điểm xuất phát không được để trống")
        private String departure;

        @Min(1) @Max(30)
        private int days;

        private LocalDate startDate;
        private LocalDate endDate;

        @Min(500000)
        private long budgetPerPerson;
        private Long budgetTotal;
        private String budgetMode = "PER_PERSON";
        @Min(1) @Max(30)
        private Integer travelerCount = 1;

        private String style = "RELAXING";
        private String groupType = "FRIENDS";
        private String transport = "MIXED";
        private String outboundTransport = "MIXED";
        private String localTransport = "MIXED";
        private Boolean destinationSuggested = false;
        private String mustVisit;
        private String avoid;
        private String notes;
    }

    @Data
    public static class TripResponse {
        private Long id;
        private String destination;
        private String departure;
        private int days;
        private String startDate;
        private String endDate;
        private long budgetPerPerson;
        private Long budgetTotal;
        private String budgetMode;
        private Integer travelerCount;
        private String style;
        private String groupType;
        private String transport;
        private String outboundTransport;
        private String localTransport;
        private Boolean destinationSuggested;
        private String mustVisit;
        private String avoid;
        private String status;
        private boolean isPublic;
        private String shareCode;
        private int viewCount;
        private List<DayResponse> schedule;
        private BudgetBreakdown budget;
        private String createdAt;

        public static TripResponse from(Trip trip) {
            TripResponse r = new TripResponse();
            r.setId(trip.getId());
            r.setDestination(trip.getDestination());
            r.setDeparture(trip.getDeparture());
            r.setDays(trip.getDays());
            r.setStartDate(trip.getStartDate() != null ? trip.getStartDate().toString() : null);
            r.setEndDate(trip.getEndDate() != null ? trip.getEndDate().toString() : null);
            r.setBudgetPerPerson(trip.getBudgetPerPerson());
            r.setBudgetTotal(trip.getBudgetTotal());
            r.setBudgetMode(trip.getBudgetMode().name());
            r.setTravelerCount(trip.getTravelerCount());
            r.setStyle(trip.getStyle().name());
            r.setGroupType(trip.getGroupType().name());
            r.setTransport(trip.getTransport().name());
            r.setOutboundTransport(trip.getOutboundTransport().name());
            r.setLocalTransport(trip.getLocalTransport().name());
            r.setDestinationSuggested(trip.getDestinationSuggested());
            r.setMustVisit(trip.getMustVisit());
            r.setAvoid(trip.getAvoid());
            r.setStatus(trip.getStatus().name());
            r.setPublic(trip.getIsPublic());
            r.setShareCode(trip.getShareCode());
            r.setViewCount(trip.getViewCount());
            r.setCreatedAt(trip.getCreatedAt() != null ? trip.getCreatedAt().toString() : null);
            return r;
        }
    }

    @Data
    public static class DayResponse {
        private int day;
        private String title;
        private String summary;
        private List<ActivityResponse> activities;
    }

    @Data
    public static class ActivityResponse {
        private Long id;
        private String time;
        private String name;
        private String type;
        private String location;
        private String duration;
        private long estimatedCost;
        private String note;
        private double rating;
        private Double latitude;
        private Double longitude;
        private Long placeId;
        private String googlePlaceId;
        private int sortOrder;

        public static ActivityResponse from(Activity a) {
            ActivityResponse r = new ActivityResponse();
            r.setId(a.getId());
            r.setTime(a.getTime());
            r.setName(a.getName());
            r.setType(a.getType().name());
            r.setLocation(a.getLocation());
            r.setDuration(a.getDuration());
            r.setEstimatedCost(a.getEstimatedCost() != null ? a.getEstimatedCost() : 0);
            r.setNote(a.getNote());
            r.setRating(a.getRating() != null ? a.getRating() : 0);
            r.setLatitude(a.getLatitude());
            r.setLongitude(a.getLongitude());
            r.setPlaceId(a.getPlace() != null ? a.getPlace().getId() : null);
            r.setGooglePlaceId(a.getGooglePlaceId());
            r.setSortOrder(a.getSortOrder());
            return r;
        }
    }

    @Data
    public static class BudgetBreakdown {
        private long total;
        private long transport;
        private long accommodation;
        private long food;
        private long activities;
    }

    @Data
    public static class UpdateActivityRequest {
        private String time;
        private String name;
        private String type;
        private String location;
        private String duration;
        private Long estimatedCost;
        private String note;
        private Double latitude;
        private Double longitude;
        private String googlePlaceId;
        private int sortOrder;
    }

    @Data
    public static class GenerateRequest {
        @NotBlank
        private String destination;
        @NotBlank
        private String departure;
        private LocalDate startDate;
        private LocalDate endDate;
        private int days;
        private long budgetPerPerson;
        private Long budgetTotal;
        private String budgetMode;
        private Integer travelerCount;
        private String style;
        private String groupType;
        private String transport;
        private String outboundTransport;
        private String localTransport;
        private Boolean destinationSuggested;
        private String mustVisit;
        private String avoid;
        private String notes;
    }
}
