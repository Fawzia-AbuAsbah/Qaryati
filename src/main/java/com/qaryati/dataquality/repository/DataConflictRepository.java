
package com.qaryati.dataquality.repository;

import com.qaryati.dataquality.entity.DataConflict;
import com.qaryati.dataquality.enums.DataConflictStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.Optional;

import java.util.List;

public interface DataConflictRepository
        extends JpaRepository<DataConflict, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from DataConflict c where c.id = :id")
    Optional<DataConflict> findByIdForUpdate(Long id);

    List<DataConflict> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            String entityType,
            Long entityId
    );

    List<DataConflict> findByStatusOrderByCreatedAtDesc(
            DataConflictStatus status
    );
}
