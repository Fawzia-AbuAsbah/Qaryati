package com.qaryati.heritage.repository;

import com.qaryati.heritage.entity.HistoricalEvent;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal persistence only. Callers must eventually enforce authorization and visibility. */
public interface HistoricalEventRepository extends JpaRepository<HistoricalEvent, Long> {
    Optional<HistoricalEvent> findByIdAndVillageId(Long id, Long villageId);
    Page<HistoricalEvent> findByVillageIdOrderByIdAsc(Long villageId, Pageable pageable);

    /** Fixed unique ordering; callers cannot inject an ambiguous sort. */
    default Page<HistoricalEvent> findVillagePage(Long villageId, int page, int size) {
        return findByVillageIdOrderByIdAsc(villageId, PageRequest.of(page, size));
    }
}
