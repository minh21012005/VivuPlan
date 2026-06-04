package com.vivuplan.vivuplan_be.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "user_wallets",
        indexes = {
                @Index(name = "idx_user_wallets_user", columnList = "user_id")
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserWallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, columnDefinition = "bigint default 0")
    @Builder.Default
    private Long planCredits = 0L;

    @Column(nullable = false, columnDefinition = "bigint default 0")
    @Builder.Default
    private Long editCredits = 0L;

    @Column(nullable = false, columnDefinition = "bigint default 0")
    @Builder.Default
    private Long suggestionCredits = 0L;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
