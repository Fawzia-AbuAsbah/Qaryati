package com.qaryati.heritage.repository;

import com.qaryati.heritage.entity.VillageHistory;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal persistence only. Callers must eventually enforce authorization and visibility. */
public interface VillageHistoryRepository extends JpaRepository<VillageHistory, Long> {
    Optional<VillageHistory> findByVillageId(Long villageId);
    Optional<VillageHistory> findByIdAndVillageId(Long id, Long villageId);
    /** Advisory only; the database unique constraint protects concurrent creation. */
    boolean existsByVillageId(Long villageId);
}
