package com.vivuplan.vivuplan_be.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Stores the raw AI response snippet and detailed error context for each
 * INVALID_RESPONSE attempt. Linked 1-to-1 with an AiUsageLog row.
 * Only populated for failed/retried attempts — not for successful ones.
 */
@Entity
@Table(
        name = "ai_attempt_payloads",
        indexes = {
                @Index(name = "idx_ai_payload_log_id", columnList = "ai_usage_log_id"),
                @Index(name = "idx_ai_payload_created", columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiAttemptPayload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The AiUsageLog this payload belongs to.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_usage_log_id", nullable = false, unique = true)
    private AiUsageLog aiUsageLog;

    /**
     * High-level category of the failure: QUALITY_CHECK, JSON_CONTRACT, etc.
     */
    @Column(name = "error_category", nullable = false, length = 40)
    private String errorCategory;

    /**
     * Full, untruncated error detail (vs. errorMessage in AiUsageLog which is
     * capped at 300 chars).
     */
    @Column(name = "error_detail", columnDefinition = "TEXT")
    private String errorDetail;

    /**
     * Full raw JSON text returned by AI before being rejected.
     */
    @Column(name = "raw_response_snippet", columnDefinition = "TEXT")
    private String rawResponseSnippet;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // ── Static factory helpers ────────────────────────────────────────────────

    public static AiAttemptPayload of(
            AiUsageLog log,
            String errorCategory,
            String errorDetail,
            String rawResponse) {
        return AiAttemptPayload.builder()
                .aiUsageLog(log)
                .errorCategory(errorCategory)
                .errorDetail(errorDetail)
                .rawResponseSnippet(rawResponse)
                .build();
    }
}
