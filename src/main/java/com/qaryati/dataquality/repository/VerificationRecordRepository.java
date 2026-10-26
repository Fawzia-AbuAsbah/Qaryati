
package com.qaryati.dataquality.repository;

import com.qaryati.dataquality.entity.VerificationRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VerificationRecordRepository
        extends JpaRepository<VerificationRecord, Long> {

    List<VerificationRecord>
    findByEntityTypeAndEntityIdOrderByReviewedAtDesc(
            String entityType,
            Long entityId
    );

    List<VerificationRecord>
    findByReviewerUserIdOrderByReviewedAtDesc(
            Long reviewerUserId
    );
}
