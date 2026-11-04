package com.qaryati.village.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.qaryati.village.entity.VillageNameType;
import jakarta.validation.constraints.*;

public record VillageNameRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 12) String languageCode,
        @NotNull VillageNameType nameType,
        String notes
) {
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported request field: " + name);
    }
}