package com.qaryati.village.dto;
import com.qaryati.village.entity.VillageRelationType;
import java.math.BigDecimal;
public record VillageRelationResponse(Long id, Long villageId, Long relatedVillageId,
        String relatedPlaceName, VillageRelationType relationType, BigDecimal distanceKm) {}
