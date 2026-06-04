package com.vivuplan.vivuplan_be.dto;

import com.vivuplan.vivuplan_be.entity.Trip;
import com.vivuplan.vivuplan_be.entity.User;
import com.vivuplan.vivuplan_be.entity.UserWallet;
import lombok.Data;

import java.util.ArrayList;
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
        private Boolean accountLocked;
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
            dto.setAccountLocked(user.isAccountLocked());
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
        private String createdAt;

        public static TripSummary from(Trip trip) {
            TripSummary dto = new TripSummary();
            dto.setId(trip.getId());
            dto.setUserId(trip.getUser().getId());
            dto.setUserEmail(trip.getUser().getEmail());
            dto.setDeparture(trip.getDeparture());
            dto.setDestination(trip.getDestination());
            dto.setDays(trip.getDays());
            dto.setCreatedAt(trip.getCreatedAt() != null ? trip.getCreatedAt().toString() : null);
            return dto;
        }
    }

    @Data
    public static class TripDetail {
        private TripDto.TripResponse trip;
        private UserSummary user;

        public static TripDetail of(TripDto.TripResponse trip, User user) {
            TripDetail dto = new TripDetail();
            dto.setTrip(trip);
            dto.setUser(UserSummary.from(user));
            return dto;
        }
    }

    @Data
    public static class WalletSummary {
        private Long planCredits;
        private Long editCredits;
        private Long suggestionCredits;

        public static WalletSummary from(UserWallet wallet) {
            WalletSummary dto = new WalletSummary();
            dto.setPlanCredits(wallet != null ? wallet.getPlanCredits() : 0L);
            dto.setEditCredits(wallet != null ? wallet.getEditCredits() : 0L);
            dto.setSuggestionCredits(wallet != null && wallet.getSuggestionCredits() != null ? wallet.getSuggestionCredits() : 0L);
            return dto;
        }
    }

    @Data
    public static class UserDetail {
        private UserSummary user;
        private WalletSummary wallet;
        private long totalTrips;
        private long paidOrders;
        private long totalPaid;
        private List<TripSummary> recentTrips;
        private List<TransactionSummary> recentOrders;
    }

    @Data
    public static class TransactionSummary {
        private Long id;
        private String orderCode;
        private Long userId;
        private String userEmail;
        private String packageCode;
        private Long amount;
        private Long paidAmount;
        private Long planCredits;
        private Long editCredits;
        private Long suggestionCredits;
        private String status;
        private String createdAt;
        private String paidAt;
        private String expiresAt;

        public static TransactionSummary from(com.vivuplan.vivuplan_be.entity.PaymentOrder order) {
            TransactionSummary dto = new TransactionSummary();
            dto.setId(order.getId());
            dto.setOrderCode(order.getOrderCode());
            dto.setUserId(order.getUser().getId());
            dto.setUserEmail(order.getUser().getEmail());
            dto.setPackageCode(order.getPackageCode());
            dto.setAmount(order.getAmount());
            dto.setPaidAmount(order.getPaidAmount());
            dto.setPlanCredits(order.getPlanCredits());
            dto.setEditCredits(order.getEditCredits());
            dto.setSuggestionCredits(order.getSuggestionCredits() != null ? order.getSuggestionCredits() : 0L);
            dto.setStatus(order.getStatus().name());
            dto.setCreatedAt(order.getCreatedAt() != null ? order.getCreatedAt().toString() : null);
            dto.setPaidAt(order.getPaidAt() != null ? order.getPaidAt().toString() : null);
            dto.setExpiresAt(order.getExpiresAt() != null ? order.getExpiresAt().toString() : null);
            return dto;
        }
    }

    @Data
    public static class UpdateUserRoleRequest {
        private String role;
    }

    @Data
    public static class UpdateUserLockRequest {
        private Boolean locked;
    }

    @Data
    public static class AiCostSummaryResponse {
        private long totalCostVnd;
        private long promptTokens;
        private long outputTokens;
        private long thinkingTokens;
        private long totalTokens;
        private long requests;
        private long attempts;
        private double retryRate;
        private double errorRate;
        private long avgDurationMs;
        private List<AiOperationAverage> averageCosts = new ArrayList<>();
        private List<AiOperationHealth> operationHealth = new ArrayList<>();
    }

    @Data
    public static class AiOperationAverage {
        private String operation;
        private String label;
        private long operations;
        private long avgCostVnd;
    }

    @Data
    public static class AiOperationHealth {
        private String operation;
        private String label;
        private long requests;
        private long attempts;
        private double retryRate;
        private double errorRate;
        private long avgDurationMs;
        private long maxDurationMs;
        private long totalCostVnd;
    }

    @Data
    public static class AiCostDaily {
        private String date;
        private long totalCostVnd;
    }

    @Data
    public static class AiUsageEvent {
        private Long id;
        private String requestId;
        private Integer attemptNumber;
        private String operation;
        private String status;
        private Long userId;
        private String userEmail;
        private Long tripId;
        private String model;
        private String finishReason;
        private Long durationMs;
        private Integer promptTokens;
        private Integer outputTokens;
        private Integer thinkingTokens;
        private Integer totalTokens;
        private Integer maxOutputTokens;
        private Integer thinkingBudget;
        private long estimatedCostVnd;
        private double estimatedCostUsd;
        private String errorCode;
        private String errorMessage;
        private String createdAt;
    }
}
