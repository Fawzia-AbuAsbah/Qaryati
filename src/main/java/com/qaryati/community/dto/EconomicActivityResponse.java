package com.qaryati.community.dto;

import com.qaryati.community.entity.EconomicActivityType;

public record EconomicActivityResponse(
        Long id,
        Long villageId,
        String name,
        EconomicActivityType activityType,
        String description
) {
}
