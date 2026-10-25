
package com.qaryati.dataquality.dto;

import com.qaryati.dataquality.entity.AuditLog;

import java.time.OffsetDateTime;
import java.util.Map;

public record AuditLogResponse(
        Long id,
        Long actorUserId,
        String actorLabel,
        String entityType,
        Long entityId,
        String action,
        OffsetDateTime occurredAt,
        Map<String, Object> beforeData,
        Map<String, Object> afterData,
        String correlationId,
        String reason
) {

    public static AuditLogResponse fromEntity(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getId(),
                auditLog.getActorUserId(),
                auditLog.getActorLabel(),
                auditLog.getEntityType(),
                auditLog.getEntityId(),
                auditLog.getAction(),
                auditLog.getOccurredAt(),
                auditLog.getBeforeData(),
                auditLog.getAfterData(),
                auditLog.getCorrelationId(),
                auditLog.getReason()
        );
    }
}
