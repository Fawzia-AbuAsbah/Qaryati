package com.qaryati.community.mapper;

import com.qaryati.community.dto.EconomicActivityRequest;
import com.qaryati.community.dto.EconomicActivityResponse;
import com.qaryati.community.entity.EconomicActivity;
import org.springframework.stereotype.Component;

@Component
public class EconomicActivityMapper {

    public EconomicActivity toEntity(EconomicActivityRequest request) {
        return new EconomicActivity(request.villageId(), request.name(), request.activityType(),
                request.description());
    }

    public EconomicActivityResponse toResponse(EconomicActivity entity) {
        return new EconomicActivityResponse(entity.getId(), entity.getVillageId(), entity.getName(),
                entity.getActivityType(), entity.getDescription());
    }

    public void updateEntity(EconomicActivityRequest request, EconomicActivity entity) {
        entity.setVillageId(request.villageId());
        entity.setName(request.name());
        entity.setActivityType(request.activityType());
        entity.setDescription(request.description());
    }
}
