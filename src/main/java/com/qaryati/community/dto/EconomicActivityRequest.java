package com.qaryati.community.dto;

import com.qaryati.community.entity.EconomicActivityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record EconomicActivityRequest(
        @NotNull(message = "Village ID is required")
        @Positive(message = "Village ID must be positive")
        Long villageId,

        @NotBlank(message = "Activity name is required")
        @Size(max = 200)
        String name,

        @NotNull(message = "Activity type is required")
        EconomicActivityType activityType,

        String description
) {
}
