
package com.qaryati.agriculture.dto;

import com.qaryati.agriculture.entity.AgricultureImportance;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record VillageAgricultureRequest(

        @NotNull
        @Positive
        Long villageId,

        @NotNull
        @Positive
        Long productId,

        String description,

        AgricultureImportance importance

) {
}
