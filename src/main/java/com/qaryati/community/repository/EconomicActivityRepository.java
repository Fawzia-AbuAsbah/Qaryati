package com.qaryati.community.repository;

import com.qaryati.community.entity.EconomicActivity;
import com.qaryati.community.entity.EconomicActivityType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EconomicActivityRepository extends JpaRepository<EconomicActivity, Long> {

    List<EconomicActivity> findByVillageId(Long villageId);

    boolean existsByVillageIdAndNameAndActivityType(Long villageId, String name,
                                                    EconomicActivityType activityType);

    boolean existsByVillageIdAndNameAndActivityTypeAndIdNot(Long villageId, String name,
                                                            EconomicActivityType activityType, Long id);
}
