
package com.qaryati.dataquality.repository;

import com.qaryati.dataquality.entity.VillageSource;
import com.qaryati.dataquality.entity.VillageSourceId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface VillageSourceRepository
        extends JpaRepository<VillageSource, VillageSourceId> {

    List<VillageSource> findByIdVillageId(Long villageId);

    List<VillageSource> findByIdSourceId(Long sourceId);

    /** Create only: a competing/existing citation must never have its notes replaced. */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO public.village_sources(village_id, source_id, claim_note)
            VALUES (:villageId, :sourceId, :claimNote)
            ON CONFLICT (village_id, source_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("villageId") Long villageId,
                       @Param("sourceId") Long sourceId,
                       @Param("claimNote") String claimNote);
}
