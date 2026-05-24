package com.vivuplan.vivuplan_be.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "sepay_transactions",
        indexes = {
                @Index(name = "idx_sepay_transactions_sepay_id", columnList = "sepay_id"),
                @Index(name = "idx_sepay_transactions_order", columnList = "payment_order_id")
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SepayTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sepay_id", nullable = false, unique = true, length = 80)
    private String sepayId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_order_id")
    private PaymentOrder paymentOrder;

    @Column(length = 80)
    private String referenceCode;

    @Column(length = 80)
    private String code;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(length = 24)
    private String transferType;

    private Long transferAmount;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String rawPayload;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
