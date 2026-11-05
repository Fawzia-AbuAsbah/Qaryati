package com.qaryati.population.repository;

import com.qaryati.population.entity.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface PopulationRecordRepository extends JpaRepository<PopulationRecord, Long> {
    org.springframework.data.domain.Page<PopulationRecord> findByVerificationStatus(
            VerificationStatus status, org.springframework.data.domain.Pageable pageable);

    Optional<PopulationRecord> findByIdAndVerificationStatus(Long id, VerificationStatus status);

    boolean existsByVillageIdAndReferenceYearAndMethodologyAndSourceId(
            Long villageId, Integer referenceYear, Methodology methodology, Long sourceId);

    boolean existsByVillageIdAndReferenceYearAndMethodologyAndSourceIdAndIdNot(
            Long villageId, Integer referenceYear, Methodology methodology, Long sourceId, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PopulationRecord p where p.id = :id")
    Optional<PopulationRecord> findByIdForUpdate(@Param("id") Long id);
}
