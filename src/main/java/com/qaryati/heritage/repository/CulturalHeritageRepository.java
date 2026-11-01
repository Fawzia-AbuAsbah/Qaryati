package com.qaryati.heritage.repository;

import com.qaryati.heritage.entity.*;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal persistence only; callers must enforce authorization and visibility before activation. */
public interface CulturalHeritageRepository extends JpaRepository<CulturalHeritage, Long> {
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
