package com.qaryati.village.repository;
import com.qaryati.village.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface VillageRelationRepository extends JpaRepository<VillageRelation, Long> {
    List<VillageRelation> findByVillageIdOrderByIdAsc(Long villageId);
    Optional<VillageRelation> findByIdAndVillageId(Long id, Long villageId);
    boolean existsByVillageIdAndRelatedVillageIdAndRelationType(Long villageId, Long targetId, VillageRelationType type);
    boolean existsByVillageIdAndRelatedVillageIdAndRelationTypeAndIdNot(Long villageId, Long targetId, VillageRelationType type, Long id);
    boolean existsByVillageIdAndRelatedPlaceNameAndRelationType(Long villageId, String place, VillageRelationType type);
    boolean existsByVillageIdAndRelatedPlaceNameAndRelationTypeAndIdNot(Long villageId, String place, VillageRelationType type, Long id);
}
