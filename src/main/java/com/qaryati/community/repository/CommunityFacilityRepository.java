package com.qaryati.community.repository;

import com.qaryati.community.entity.CommunityFacility;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommunityFacilityRepository extends JpaRepository<CommunityFacility, Long> {
    List<CommunityFacility> findByVillageId(Long villageId);
}
