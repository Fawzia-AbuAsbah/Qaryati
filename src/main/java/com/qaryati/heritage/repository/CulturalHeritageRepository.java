package com.qaryati.heritage.repository;

import com.qaryati.heritage.entity.*;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal persistence only; callers must enforce authorization and visibility before activation. */
public interface CulturalHeritageRepository extends JpaRepository<CulturalHeritage, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from CulturalHeritage e where e.id = :id")
    Optional<CulturalHeritage> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    Optional<CulturalHeritage> findByIdAndVerificationStatus(Long id, VerificationStatus status);

    @org.springframework.data.jpa.repository.Query("""
            select e from CulturalHeritage e
            where (:villageId is null or e.villageId = :villageId)
            and (:heritageType is null or e.heritageType = :heritageType)
            and (:status is null or e.verificationStatus = :status)
            and (:verifiedOnly = false or e.verificationStatus = com.qaryati.heritage.entity.VerificationStatus.VERIFIED)
            """)
    Page<CulturalHeritage> findVisiblePage(
            @org.springframework.data.repository.query.Param("villageId") Long villageId,
            @org.springframework.data.repository.query.Param("heritageType") CulturalHeritageType heritageType,
            @org.springframework.data.repository.query.Param("status") VerificationStatus status,
            @org.springframework.data.repository.query.Param("verifiedOnly") boolean verifiedOnly,
            Pageable pageable);
    Optional<CulturalHeritage> findByIdAndVillageId(Long id, Long villageId);
    Page<CulturalHeritage> findByVillageIdOrderByIdAsc(Long villageId, Pageable pageable);

    /** Advisory only: the database unique constraint arbitrates concurrent duplicates. */
    boolean existsByVillageIdAndNameAndHeritageType(Long villageId, String name, CulturalHeritageType heritageType);
    boolean existsByVillageIdAndNameAndHeritageTypeAndIdNot(
            Long villageId, String name, CulturalHeritageType heritageType, Long id);

    /** Fixed unique ordering for a stable dataset, without a cross-request snapshot guarantee. */
    default Page<CulturalHeritage> findVillagePage(Long villageId, int page, int size) {
        return findByVillageIdOrderByIdAsc(villageId, PageRequest.of(page, size));
    }
}
