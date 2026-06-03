package com.vivuplan.vivuplan_be.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "ai_usage_logs",
        indexes = {
                @Index(name = "idx_ai_usage_created", columnList = "created_at"),
                @Index(name = "idx_ai_usage_operation_created", columnList = "operation, created_at"),
                @Index(name = "idx_ai_usage_status_created", columnList = "status, created_at"),
                @Index(name = "idx_ai_usage_request", columnList = "request_id"),
                @Index(name = "idx_ai_usage_user_created", columnList = "user_id, created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiUsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Operation operation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Status status;

    @Column(name = "request_id", nullable = false, length = 64)
    private String requestId;

    @Column(nullable = false)
    private Integer attemptNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @Column(nullable = false, length = 80)
    private String model;

    @Column(length = 40)
    private String finishReason;

    private Long durationMs;

    private Integer promptTokens;

    private Integer outputTokens;

    private Integer thinkingTokens;

    private Integer totalTokens;

    private Integer maxOutputTokens;

    private Integer thinkingBudget;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal inputUsdPer1M;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal outputUsdPer1M;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal usdToVndRate;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal estimatedCostUsd;

    @Column(nullable = false)
    private Long estimatedCostVnd;

    @Column(length = 80)
    private String errorCode;

    @Column(length = 300)
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum Operation {
        PLAN_GENERATION,
        DAY_REGENERATION,
        DESTINATION_SUGGESTION
    }

    public enum Status {
        SUCCESS,
        INVALID_RESPONSE,
        HTTP_ERROR,
        PARSE_ERROR,
        FAILED
    }
}
