package com.vivuplan.vivuplan_be.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "credit_ledger",
        indexes = {
                @Index(name = "idx_credit_ledger_user_created", columnList = "user_id, created_at"),
                @Index(name = "idx_credit_ledger_type", columnList = "type")
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CreditLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CreditType type;

    @Column(nullable = false)
    private Long delta;

    @Column(nullable = false, length = 64)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_order_id")
    private PaymentOrder paymentOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    public enum CreditType {
        PLAN,
        EDIT
    }
}
