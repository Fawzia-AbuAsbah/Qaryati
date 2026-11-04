package com.qaryati.village.dto;

import com.qaryati.village.entity.LocalAuthorityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record LocalAuthorityRequest(

        @NotNull(message = "Governorate is required")
        @Positive
        Long governorateId,

        @NotBlank(message = "Arabic name is required")
        @Size(max = 180)
        String nameAr,

        @Size(max = 180)
        String nameEn,

        @NotNull(message = "Authority type is required")
        LocalAuthorityType authorityType

) {
}
