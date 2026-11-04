package com.qaryati.village.repository;

import com.qaryati.village.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface VillageNameRepository extends JpaRepository<VillageName, Long> {
    List<VillageName> findByVillageIdOrderByIdAsc(Long villageId);
    Optional<VillageName> findByIdAndVillageId(Long id, Long villageId);
    boolean existsByVillageIdAndNameAndLanguageCodeAndNameType(
            Long villageId, String name, String languageCode, VillageNameType nameType);
    boolean existsByVillageIdAndNameAndLanguageCodeAndNameTypeAndIdNot(
            Long villageId, String name, String languageCode, VillageNameType nameType, Long id);
}