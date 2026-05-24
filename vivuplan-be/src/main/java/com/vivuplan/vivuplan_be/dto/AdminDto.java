package com.vivuplan.vivuplan_be.dto;

import com.vivuplan.vivuplan_be.entity.Trip;
import com.vivuplan.vivuplan_be.entity.User;
import lombok.Data;

import java.util.List;

public class AdminDto {

    @Data
    public static class StatsResponse {
        private long totalUsers;
        private long adminUsers;
        private long totalTrips;
        private long publicTrips;
        private long draftTrips;
        private long plannedTrips;
        private long completedTrips;
        private long paidOrders;
        private long totalRevenue;
    }

    @Data
    public static class UserSummary {
        private Long id;
        private String name;
        private String email;
        private String avatarUrl;
        private String role;
        private List<String> roles;
        private String provider;
        private Boolean emailVerified;
        private String createdAt;

        public static UserSummary from(User user) {
            UserSummary dto = new UserSummary();
            dto.setId(user.getId());
            dto.setName(user.getName());
            dto.setEmail(user.getEmail());
            dto.setAvatarUrl(user.getAvatarUrl());
            dto.setRole(user.getPrimaryRoleName());
            dto.setRoles(user.getRoleNames().stream().sorted().toList());
            dto.setProvider(user.getProvider().name());
            dto.setEmailVerified(user.getEmailVerified());
            dto.setCreatedAt(user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);
            return dto;
        }
    }

    @Data
    public static class TripSummary {
        private Long id;
        private Long userId;
        private String userEmail;
        private String departure;
        private String destination;
        private int days;
        private String status;
        private boolean isPublic;
        private int viewCount;
        private String createdAt;

        public static TripSummary from(Trip trip) {
            TripSummary dto = new TripSummary();
            dto.setId(trip.getId());
            dto.setUserId(trip.getUser().getId());
            dto.setUserEmail(trip.getUser().getEmail());
            dto.setDeparture(trip.getDeparture());
            dto.setDestination(trip.getDestination());
            dto.setDays(trip.getDays());
            dto.setStatus(trip.getStatus().name());
            dto.setPublic(trip.getIsPublic());
            dto.setViewCount(trip.getViewCount());
            dto.setCreatedAt(trip.getCreatedAt() != null ? trip.getCreatedAt().toString() : null);
            return dto;
        }
    }

    @Data
    public static class UpdateUserRoleRequest {
        private String role;
    }
}
