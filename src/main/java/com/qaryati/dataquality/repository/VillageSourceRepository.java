
package com.qaryati.dataquality.repository;

import com.qaryati.dataquality.entity.VillageSource;
import com.qaryati.dataquality.entity.VillageSourceId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VillageSourceRepository
        extends JpaRepository<VillageSource, VillageSourceId> {

    List<VillageSource> findByIdVillageId(Long villageId);

    List<VillageSource> findByIdSourceId(Long sourceId);
}
