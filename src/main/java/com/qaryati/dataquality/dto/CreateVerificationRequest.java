
package com.qaryati.dataquality.dto;

import com.qaryati.dataquality.enums.VerificationDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateVerificationRequest(

        @NotBlank(message = "Entity type is required")
        @Size(max = 80, message = "Entity type must not exceed 80 characters")
        String entityType,

        @NotNull(message = "Entity ID is required")
        @Positive(message = "Entity ID must be positive")
        Long entityId,

        @NotNull(message = "Verification decision is required")
        VerificationDecision decision,

        String reason

) {
}
