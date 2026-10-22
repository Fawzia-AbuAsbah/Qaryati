
package com.qaryati.dataquality.repository;

import com.qaryati.dataquality.entity.DataConflict;
import com.qaryati.dataquality.enums.DataConflictStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DataConflictRepository
        extends JpaRepository<DataConflict, Long> {

    List<DataConflict> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            String entityType,
            Long entityId
    );

    List<DataConflict> findByStatusOrderByCreatedAtDesc(
            DataConflictStatus status
    );
}
