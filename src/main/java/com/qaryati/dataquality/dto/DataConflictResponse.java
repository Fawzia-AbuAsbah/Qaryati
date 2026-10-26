
package com.qaryati.dataquality.dto;

import com.qaryati.dataquality.entity.DataConflict;
import com.qaryati.dataquality.enums.DataConflictStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record DataConflictResponse(

        Long id,
        String entityType,
        Long entityId,
        String fieldName,
        List<Map<String, Object>> conflictingValues,
        DataConflictStatus status,
        String resolutionNotes,
        Long resolvedBy,
        OffsetDateTime createdAt,
        OffsetDateTime resolvedAt

) {

    public static DataConflictResponse fromEntity(DataConflict conflict) {
        return new DataConflictResponse(
                conflict.getId(),
                conflict.getEntityType(),
                conflict.getEntityId(),
                conflict.getFieldName(),
                conflict.getConflictingValues(),
                conflict.getStatus(),
                conflict.getResolutionNotes(),
                conflict.getResolvedBy(),
                conflict.getCreatedAt(),
                conflict.getResolvedAt()
        );
    }
}
