
package com.qaryati.dataquality.repository;

import com.qaryati.dataquality.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository
        extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByEntityTypeAndEntityIdOrderByOccurredAtDesc(
            String entityType,
            Long entityId
    );

    List<AuditLog> findByActorUserIdOrderByOccurredAtDesc(
            Long actorUserId
    );

    List<AuditLog> findByActionOrderByOccurredAtDesc(
            String action
    );
}
