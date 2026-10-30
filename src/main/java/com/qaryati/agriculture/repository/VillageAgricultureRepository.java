
package com.qaryati.agriculture.repository;

import com.qaryati.agriculture.entity.VillageAgriculture;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VillageAgricultureRepository
        extends JpaRepository<VillageAgriculture, Long> {

    boolean existsByVillageIdAndProductId(
            Long villageId,
            Long productId
    );

    Optional<VillageAgriculture> findByVillageIdAndProductId(
            Long villageId,
            Long productId
    );

    List<VillageAgriculture> findByVillageId(Long villageId);

    List<VillageAgriculture> findByProductId(Long productId);
}
