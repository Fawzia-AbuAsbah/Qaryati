
package com.qaryati.dataquality.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record CreateDataConflictRequest(

        @NotBlank
        @Size(max = 80)
        String entityType,

        @NotNull
        @Positive
        Long entityId,

        @NotBlank
        @Size(max = 100)
        String fieldName,

        @NotEmpty
        List<@NotEmpty Map<@NotBlank String, @NotNull Object>> conflictingValues

) {
}
