package com.qaryati.heritage.repository;

import com.qaryati.heritage.entity.HeritageSite;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal persistence only. Callers must enforce authorization and visibility before activation. */
public interface HeritageSiteRepository extends JpaRepository<HeritageSite, Long> {
    Optional<HeritageSite> findByIdAndVillageId(Long id, Long villageId);
    Page<HeritageSite> findByVillageIdOrderByIdAsc(Long villageId, Pageable pageable);

    /** Fixed unique ordering for a stable dataset. */
    default Page<HeritageSite> findVillagePage(Long villageId, int page, int size) {
        return findByVillageIdOrderByIdAsc(villageId, PageRequest.of(page, size));
    }
}
