package com.qaryati.heritage.dto;

import com.qaryati.heritage.entity.CulturalHeritageType;
import jakarta.validation.constraints.*;
import java.util.List;

/** HTTP policy adds positive references and citations, preserving foundation text rules. */
public record CulturalHeritageCrudRequest(@NotNull @Positive Long villageId,
        @NotNull @Size(max = 200) String name, @NotNull CulturalHeritageType heritageType,
        @NotNull String description, @NotEmpty List<@NotNull @Positive Long> sourceIds) {
    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported request field");
    }
}
