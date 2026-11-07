package com.qaryati.heritage.repository;

import com.qaryati.heritage.entity.HeritageSite;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal persistence only. Service enforces authorization and visibility. */
public interface HeritageSiteRepository extends JpaRepository<HeritageSite, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from HeritageSite e where e.id = :id")
    Optional<HeritageSite> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
    Optional<HeritageSite> findByIdAndVerificationStatus(Long id, com.qaryati.heritage.entity.VerificationStatus status);
    @org.springframework.data.jpa.repository.Query("""
            select e from HeritageSite e
            where (:villageId is null or e.villageId = :villageId)
            and (:status is null or e.verificationStatus = :status)
            """)
    Page<HeritageSite> findVisiblePage(
            @org.springframework.data.repository.query.Param("villageId") Long villageId,
            @org.springframework.data.repository.query.Param("status") com.qaryati.heritage.entity.VerificationStatus status,
            Pageable pageable);
    Optional<HeritageSite> findByIdAndVillageId(Long id, Long villageId);
    Page<HeritageSite> findByVillageIdOrderByIdAsc(Long villageId, Pageable pageable);

    /** Fixed unique ordering for a stable dataset. */
    default Page<HeritageSite> findVillagePage(Long villageId, int page, int size) {
        return findByVillageIdOrderByIdAsc(villageId, PageRequest.of(page, size));
    }
}
