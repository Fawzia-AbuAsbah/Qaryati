package com.qaryati.heritage.repository;

import com.qaryati.heritage.entity.VillageHistory;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;

/** Internal persistence only. Callers must eventually enforce authorization and visibility. */
public interface VillageHistoryRepository extends JpaRepository<VillageHistory, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from VillageHistory e where e.id = :id")
    Optional<VillageHistory> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
    Optional<VillageHistory> findByIdAndVerificationStatus(Long id, com.qaryati.heritage.entity.VerificationStatus status);
    @org.springframework.data.jpa.repository.Query("""
            select e from VillageHistory e
            where (:villageId is null or e.villageId = :villageId)
            and (:status is null or e.verificationStatus = :status)
            """)
    Page<VillageHistory> findVisiblePage(
            @org.springframework.data.repository.query.Param("villageId") Long villageId,
            @org.springframework.data.repository.query.Param("status") com.qaryati.heritage.entity.VerificationStatus status,
            Pageable pageable);
    Optional<VillageHistory> findByVillageId(Long villageId);
    Optional<VillageHistory> findByIdAndVillageId(Long id, Long villageId);
    /** Advisory only; the database unique constraint protects concurrent creation. */
    boolean existsByVillageId(Long villageId);
}
