package com.qaryati.heritage.repository;

import com.qaryati.heritage.entity.HistoricalEvent;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal persistence only. Service enforces authorization and visibility. */
public interface HistoricalEventRepository extends JpaRepository<HistoricalEvent, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from HistoricalEvent e where e.id = :id")
    Optional<HistoricalEvent> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
    Optional<HistoricalEvent> findByIdAndVerificationStatus(Long id, com.qaryati.heritage.entity.VerificationStatus status);
    @org.springframework.data.jpa.repository.Query("""
            select e from HistoricalEvent e
            where (:villageId is null or e.villageId = :villageId)
            and (:status is null or e.verificationStatus = :status)
            """)
    Page<HistoricalEvent> findVisiblePage(
            @org.springframework.data.repository.query.Param("villageId") Long villageId,
            @org.springframework.data.repository.query.Param("status") com.qaryati.heritage.entity.VerificationStatus status,
            Pageable pageable);
    Optional<HistoricalEvent> findByIdAndVillageId(Long id, Long villageId);
    Page<HistoricalEvent> findByVillageIdOrderByIdAsc(Long villageId, Pageable pageable);

    /** Fixed unique ordering; callers cannot inject an ambiguous sort. */
    default Page<HistoricalEvent> findVillagePage(Long villageId, int page, int size) {
        return findByVillageIdOrderByIdAsc(villageId, PageRequest.of(page, size));
    }
}
