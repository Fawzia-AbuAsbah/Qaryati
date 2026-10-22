
package com.qaryati.dataquality.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

@Entity
@Table(name = "audit_logs", schema = "public")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_label", nullable = false, length = 160)
    private String actorLabel;

    @Column(name = "entity_type", nullable = false, length = 80)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "action", nullable = false, length = 20)
    private String action;

    @Column(name = "occurred_at",
            nullable = false,
            insertable = false,
            updatable = false)
    private OffsetDateTime occurredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_data", columnDefinition = "jsonb")
    private Map<String, Object> beforeData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_data", columnDefinition = "jsonb")
    private Map<String, Object> afterData;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    protected AuditLog() {
    }

    public AuditLog(
            Long actorUserId,
            String actorLabel,
            String entityType,
            Long entityId,
            String action,
            Map<String, Object> beforeData,
            Map<String, Object> afterData,
            String correlationId,
            String reason
    ) {
        this.actorUserId = actorUserId;
        this.actorLabel = actorLabel;
        this.entityType = entityType;
        this.entityId = entityId;
        this.action = action;
        this.beforeData = beforeData;
        this.afterData = afterData;
        this.correlationId = correlationId;
        this.reason = reason;
    }

    public Long getId() {
        return id;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public String getActorLabel() {
        return actorLabel;
    }

    public String getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public String getAction() {
        return action;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public Map<String, Object> getBeforeData() {
        return beforeData;
    }

    public Map<String, Object> getAfterData() {
        return afterData;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getReason() {
        return reason;
    }
}
