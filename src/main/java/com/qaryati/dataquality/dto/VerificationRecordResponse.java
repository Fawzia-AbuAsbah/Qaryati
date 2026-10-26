
package com.qaryati.dataquality.dto;

import com.qaryati.dataquality.entity.VerificationRecord;
import com.qaryati.dataquality.enums.VerificationDecision;

import java.time.OffsetDateTime;

public record VerificationRecordResponse(
        Long id,
        String entityType,
        Long entityId,
        Long reviewerUserId,
        VerificationDecision decision,
        String reason,
        OffsetDateTime reviewedAt
) {

    public static VerificationRecordResponse fromEntity(
            VerificationRecord record
    ) {
        return new VerificationRecordResponse(
                record.getId(),
                record.getEntityType(),
                record.getEntityId(),
                record.getReviewerUserId(),
                record.getDecision(),
                record.getReason(),
                record.getReviewedAt()
        );
    }
}
