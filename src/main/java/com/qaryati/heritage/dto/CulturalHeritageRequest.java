package com.qaryati.heritage.dto;

import com.qaryati.heritage.entity.CulturalHeritageType;
import jakarta.validation.constraints.*;

public record CulturalHeritageRequest(
        @NotNull Long villageId,
        @NotNull @Size(max = 200) String name,
        @NotNull CulturalHeritageType heritageType,
        @NotNull String description) {}
