package com.qaryati.community.dto;

import com.qaryati.community.entity.CommunityFacilityType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@ValidFacilityCoordinatePair
public record CommunityFacilityRequest(
        @NotNull(message = "Village ID is required")
        @Positive(message = "Village ID must be positive")
        Long villageId,

        @NotBlank(message = "Facility name is required")
        @Size(max = 200)
        String name,

        @NotNull(message = "Facility type is required")
        CommunityFacilityType facilityType,

        String description,

        @DecimalMin(value = "-90", message = "Latitude must be at least -90")
        @DecimalMax(value = "90", message = "Latitude must be at most 90")
        @Digits(integer = 3, fraction = 6, message = "Latitude must have at most 6 decimal places")
        BigDecimal latitude,

        @DecimalMin(value = "-180", message = "Longitude must be at least -180")
        @DecimalMax(value = "180", message = "Longitude must be at most 180")
        @Digits(integer = 3, fraction = 6, message = "Longitude must have at most 6 decimal places")
        BigDecimal longitude
) {
}
