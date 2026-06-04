package com.vivuplan.vivuplan_be.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "payment_orders",
        indexes = {
                @Index(name = "idx_payment_orders_user_created", columnList = "user_id, created_at"),
                @Index(name = "idx_payment_orders_status_expires", columnList = "status, expires_at"),
                @Index(name = "idx_payment_orders_order_code", columnList = "order_code")
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_code", nullable = false, unique = true, length = 40)
    private String orderCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 32)
    private String packageCode;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false, columnDefinition = "bigint default 0")
    @Builder.Default
    private Long planCredits = 0L;

    @Column(nullable = false, columnDefinition = "bigint default 0")
    @Builder.Default
    private Long editCredits = 0L;

    @Column(nullable = false, columnDefinition = "bigint default 0")
    @Builder.Default
    private Long suggestionCredits = 0L;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(columnDefinition = "TEXT")
    private String qrUrl;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime paidAt;

    private Long paidAmount;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum Status {
        PENDING,
        PAID,
        UNDERPAID,
        EXPIRED,
        CANCELLED
    }
}
