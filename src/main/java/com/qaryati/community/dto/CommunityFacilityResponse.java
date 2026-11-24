package com.qaryati.community.dto;

import com.qaryati.community.entity.CommunityFacilityType;

import java.math.BigDecimal;

public record CommunityFacilityResponse(
        Long id,
        Long villageId,
        String name,
        CommunityFacilityType facilityType,
        String description,
        BigDecimal latitude,
        BigDecimal longitude
) {
}
