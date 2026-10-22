
package com.qaryati.dataquality.repository;

import com.qaryati.dataquality.entity.Source;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.Optional;

import java.util.List;

public interface SourceRepository extends JpaRepository<Source, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Source s where s.id = :id")
    Optional<Source> findByIdForUpdate(Long id);

    List<Source> findBySourceType(String sourceType);

    List<Source> findByTitleContainingIgnoreCase(String title);

    List<Source> findByPublisherIgnoreCase(String publisher);
}
