package com.qaryati.community.mapper;

import com.qaryati.community.dto.CommunityFacilityRequest;
import com.qaryati.community.dto.CommunityFacilityResponse;
import com.qaryati.community.entity.CommunityFacility;
import org.springframework.stereotype.Component;

@Component
public class CommunityFacilityMapper {

    public CommunityFacility toEntity(CommunityFacilityRequest request) {
        return new CommunityFacility(request.villageId(), request.name(), request.facilityType(),
                request.description(), request.latitude(), request.longitude());
    }

    public CommunityFacilityResponse toResponse(CommunityFacility entity) {
        return new CommunityFacilityResponse(entity.getId(), entity.getVillageId(), entity.getName(),
                entity.getFacilityType(), entity.getDescription(), entity.getLatitude(), entity.getLongitude());
    }

    public void updateEntity(CommunityFacilityRequest request, CommunityFacility entity) {
        entity.setVillageId(request.villageId());
        entity.setName(request.name());
        entity.setFacilityType(request.facilityType());
        entity.setDescription(request.description());
        entity.setLatitude(request.latitude());
        entity.setLongitude(request.longitude());
    }
}
