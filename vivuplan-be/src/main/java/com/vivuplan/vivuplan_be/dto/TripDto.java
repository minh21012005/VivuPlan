package com.vivuplan.vivuplan_be.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.vivuplan.vivuplan_be.entity.Activity;
import com.vivuplan.vivuplan_be.entity.Trip;
import lombok.Data;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

public class TripDto {
    public static final int MAX_TRIP_DAYS = 7;
    public static final int MAX_TRAVELERS = 10;

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
        private String outboundTransport;
        private String localTransport;
        private Boolean destinationSuggested;
        private String mustVisit;
        private String avoid;
        private String notes;
        private String status;
        private boolean isPublic;
        private String shareCode;
        private int viewCount;
        private List<DayResponse> schedule;
        private BudgetBreakdown budget;
        private List<String> warnings;
        private RequestFulfillment requestFulfillment;
        private String createdAt;

        @JsonProperty("isPublic")
        public boolean isPublic() {
            return isPublic;
        }

        @JsonProperty("isPublic")
        public void setPublic(boolean isPublic) {
            this.isPublic = isPublic;
        }

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
            r.setOutboundTransport(trip.getOutboundTransport().name());
            r.setLocalTransport(trip.getLocalTransport().name());
            r.setDestinationSuggested(trip.getDestinationSuggested());
            r.setMustVisit(trip.getMustVisit());
            r.setAvoid(trip.getAvoid());
            r.setNotes(trip.getNotes());
            r.setStatus(trip.getStatus().name());
            r.setPublic(trip.getIsPublic());
            r.setShareCode(trip.getShareCode());
            r.setViewCount(trip.getViewCount());
            r.setWarnings(parseWarnings(trip.getAiWarnings()));
            r.setCreatedAt(trip.getCreatedAt() != null ? trip.getCreatedAt().toString() : null);
            return r;
        }

        private static List<String> parseWarnings(String rawWarnings) {
            if (rawWarnings == null || rawWarnings.isBlank()) {
                return List.of();
            }
            return Arrays.stream(rawWarnings.split("\\R"))
                    .map(String::trim)
                    .filter(warning -> !warning.isBlank())
                    .toList();
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
        private String costEstimateStatus;
        private String costEstimateMessage;
        private String note;
        private double rating;
        private Double latitude;
        private Double longitude;
        private Long placeId;
        private String googlePlaceId;
        private String coordinateSource;
        private String coordinateConfidence;
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
            r.setNote(stripLegacyCostReviewNote(a.getNote()));
            r.setRating(a.getRating() != null ? a.getRating() : 0);
            r.setLatitude(a.getLatitude());
            r.setLongitude(a.getLongitude());
            r.setPlaceId(a.getPlace() != null ? a.getPlace().getId() : null);
            r.setGooglePlaceId(a.getGooglePlaceId());
            r.setCoordinateSource(a.getCoordinateSource() != null ? a.getCoordinateSource().name() : null);
            r.setCoordinateConfidence(a.getCoordinateConfidence() != null ? a.getCoordinateConfidence().name() : null);
            r.setSortOrder(a.getSortOrder());
            return r;
        }

        private static String stripLegacyCostReviewNote(String note) {
            if (note == null || note.isBlank()) {
                return note;
            }
            String cleaned = note.replace(
                    "Chi phí cần kiểm tra: hoạt động này có thể phát sinh phí, nhưng AI chưa đưa ra mức ước tính đáng tin cậy.",
                    "")
                    .replaceAll("\\s{2,}", " ")
                    .trim();
            return cleaned.isBlank() ? null : cleaned;
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
        private String coordinateSource;
        private String coordinateConfidence;
        private int sortOrder;
    }

    @Data
    public static class RegenerateDayRequest {
        private String intent = "REGENERATE";
        @NotBlank(message = "Vui lòng nhập điều bạn muốn chỉnh trong lịch trình")
        @Size(max = 500, message = "Yêu cầu chỉnh ngày tối đa 500 ký tự")
        private String instruction;
    }

    @Data
    public static class ApplyRegenerateDayRequest {
        @NotBlank
        private String proposalId;
        private List<Integer> selectedActivityIndexes;
    }

    @Data
    public static class RegenerateDayPreviewResponse {
        private String proposalId;
        private Integer dayNumber;
        private DayResponse day;
        private long oldBudget;
        private long newBudget;
        private List<String> warnings;
        private RequestFulfillment requestFulfillment;
    }

    @Data
    public static class RequestFulfillment {
        /**
         * FULFILLED | PARTIAL | NOT_FULFILLED | UNCLEAR | NO_REQUEST
         */
        private String overallStatus;
        private List<RequestFulfillmentItem> items;
    }

    @Data
    public static class RequestFulfillmentItem {
        private String requestedText;
        /**
         * FULFILLED | PARTIAL | NOT_APPLIED | UNCLEAR
         */
        private String status;
        /**
         * APPLIED | WEATHER_SAFETY | BUDGET | TIME_CONFLICT | DUPLICATE |
         * CONSTRAINT | UNCLEAR | OTHER
         */
        private String reasonCode;
        private String userMessage;
    }

    @Data
    public static class GenerateRequest {
        @Size(max = 100, message = "Điểm đến tối đa 100 ký tự")
        private String destination;
        @NotBlank
        @Size(max = 100, message = "Điểm xuất phát tối đa 100 ký tự")
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
        private String outboundTransport;
        private String localTransport;
        private Boolean destinationSuggested;
        @Size(max = 300, message = "Nơi muốn ghé tối đa 300 ký tự")
        private String mustVisit;
        @Size(max = 300, message = "Điều muốn tránh tối đa 300 ký tự")
        private String avoid;
        @Size(max = 800, message = "Ghi chú tối đa 800 ký tự")
        private String notes;
        private String weatherForecast;
        private String verifiedPlacesContext;
    }

    @Data
    public static class DestinationSuggestionRequest {
        @Size(max = 100, message = "Điểm xuất phát tối đa 100 ký tự")
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
        private String outboundTransport;
        private String localTransport;
        @Size(max = 300, message = "Nơi muốn ghé tối đa 300 ký tự")
        private String mustVisit;
        @Size(max = 300, message = "Điều muốn tránh tối đa 300 ký tự")
        private String avoid;
        @Size(max = 800, message = "Ghi chú tối đa 800 ký tự")
        private String notes;
    }

    @Data
    public static class DestinationSuggestionResponse {
        private List<DestinationSuggestion> suggestions;
    }

    @Data
    public static class DestinationSuggestion {
        private String name;
        private String region;
        private String reason;
        private String overallFit;
        private String overallNote;
        private String budgetFit;
        private String budgetNote;
        private String durationFit;
        private String durationNote;
        private String travelFit;
        private String travelNote;
        private String styleFit;
        private String styleNote;
        private Boolean fromCatalog;
    }
}
