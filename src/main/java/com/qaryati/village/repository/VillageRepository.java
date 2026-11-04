package com.qaryati.village.repository;

import com.qaryati.village.entity.Governorate;
import com.qaryati.village.entity.Village;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface VillageRepository extends JpaRepository<Village, Long> {

    /**
     * Active rows with static ascending-ID ordering and a derived active-row count.
     * Callers should supply a paged, unsorted Pageable; custom sorting is not an API contract.
     * Ordering is deterministic for a stable dataset, not a cross-request snapshot guarantee.
     */
    Page<Village> findByArchivedAtIsNullOrderByIdAsc(Pageable pageable);

    // Include archived rows. The database unique constraint remains the final race guard.
    boolean existsByGovernorateAndNameAr(Governorate governorate, String nameAr);

    boolean existsByGovernorateAndNameArAndIdNot(
            Governorate governorate, String nameAr, Long id);

    /** Includes archived rows; the calling service must own the transaction and lifecycle checks. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Village v where v.id = :id")
    Optional<Village> findByIdForUpdate(@Param("id") Long id);
}
