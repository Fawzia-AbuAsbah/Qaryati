
package com.qaryati.dataquality.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record LinkSourceRequest(

        @NotNull
        @Positive
        Long sourceId,

        String claimNote
) {
}
