
package com.qaryati.dataquality.entity;

import com.qaryati.dataquality.enums.DataConflictStatus;
import jakarta.persistence.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "data_conflicts", schema = "public")
public class DataConflict {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 80)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "conflicting_values", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> conflictingValues;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DataConflictStatus status = DataConflictStatus.OPEN;

    @Column(name = "resolution_notes", columnDefinition = "text")
    private String resolutionNotes;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    @org.hibernate.annotations.Generated(event = org.hibernate.generator.EventType.INSERT)
    private OffsetDateTime createdAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    protected DataConflict() {
    }

    public DataConflict(
            String entityType,
            Long entityId,
            String fieldName,
            List<Map<String, Object>> conflictingValues
    ) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.fieldName = fieldName;
        this.conflictingValues = conflictingValues;
        this.status = DataConflictStatus.OPEN;
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

    public String getFieldName() {
        return fieldName;
    }

    public List<Map<String, Object>> getConflictingValues() {
        return conflictingValues;
    }

    public DataConflictStatus getStatus() {
        return status;
    }

    public String getResolutionNotes() {
        return resolutionNotes;
    }

    public Long getResolvedBy() {
        return resolvedBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void markInReview() {
        this.status = DataConflictStatus.IN_REVIEW;
    }

    public void resolve(Long reviewerId, String notes) {
        this.status = DataConflictStatus.RESOLVED;
        this.resolvedBy = reviewerId;
        this.resolutionNotes = notes;
        this.resolvedAt = OffsetDateTime.now();
    }
}
