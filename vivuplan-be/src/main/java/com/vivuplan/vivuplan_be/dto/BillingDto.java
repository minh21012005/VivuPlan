package com.vivuplan.vivuplan_be.dto;

import com.vivuplan.vivuplan_be.entity.PaymentOrder;
import com.vivuplan.vivuplan_be.entity.UserWallet;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

public class BillingDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PackageResponse {
        private String code;
        private String name;
        private String description;
        private Long amount;
        private Long planCredits;
        private Long editCredits;
        private Long suggestionCredits;
        private Boolean highlighted;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WalletResponse {
        private Long planCredits;
        private Long editCredits;
        private Long suggestionCredits;

        public static WalletResponse from(UserWallet wallet) {
            return WalletResponse.builder()
                    .planCredits(safeCredits(wallet.getPlanCredits()))
                    .editCredits(safeCredits(wallet.getEditCredits()))
                    .suggestionCredits(safeCredits(wallet.getSuggestionCredits()))
                    .build();
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BillingMeResponse {
        private WalletResponse wallet;
        private List<OrderResponse> recentOrders;
    }

    @Getter
    @Setter
    public static class CreateOrderRequest {
        @NotBlank
        private String packageCode;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderResponse {
        private String orderCode;
        private String packageCode;
        private Long amount;
        private Long planCredits;
        private Long editCredits;
        private Long suggestionCredits;
        private PaymentOrder.Status status;
        private String qrUrl;
        private Instant expiresAt;
        private LocalDateTime paidAt;
        private Long paidAmount;

        public static OrderResponse from(PaymentOrder order) {
            return OrderResponse.builder()
                    .orderCode(order.getOrderCode())
                    .packageCode(order.getPackageCode())
                    .amount(order.getAmount())
                    .planCredits(safeCredits(order.getPlanCredits()))
                    .editCredits(safeCredits(order.getEditCredits()))
                    .suggestionCredits(safeCredits(order.getSuggestionCredits()))
                    .status(order.getStatus())
                    .qrUrl(order.getQrUrl())
                    .expiresAt(order.getExpiresAt() != null
                            ? order.getExpiresAt().atZone(ZoneId.systemDefault()).toInstant()
                            : null)
                    .paidAt(order.getPaidAt())
                    .paidAmount(order.getPaidAmount())
                    .build();
        }
    }

    private static long safeCredits(Long value) {
        return value != null ? value : 0L;
    }
}
