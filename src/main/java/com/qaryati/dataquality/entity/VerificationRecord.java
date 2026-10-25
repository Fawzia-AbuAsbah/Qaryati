
package com.qaryati.dataquality.entity;

import com.qaryati.dataquality.enums.VerificationDecision;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "verification_records", schema = "public")
public class VerificationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 80)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "reviewer_user_id", nullable = false)
    private Long reviewerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 20)
    private VerificationDecision decision;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "reviewed_at", nullable = false,
            insertable = false, updatable = false)
    @org.hibernate.annotations.Generated(event = org.hibernate.generator.EventType.INSERT)
    private OffsetDateTime reviewedAt;

    public VerificationRecord() {
    }

    public VerificationRecord(
            String entityType,
            Long entityId,
            Long reviewerUserId,
            VerificationDecision decision,
            String reason
    ) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.reviewerUserId = reviewerUserId;
        this.decision = decision;
        this.reason = reason;
    }

    public Long getId() {
        return id;
    }

    public String getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public Long getReviewerUserId() {
        return reviewerUserId;
    }

    public VerificationDecision getDecision() {
        return decision;
    }

    public String getReason() {
        return reason;
    }

    public OffsetDateTime getReviewedAt() {
        return reviewedAt;
    }
}
