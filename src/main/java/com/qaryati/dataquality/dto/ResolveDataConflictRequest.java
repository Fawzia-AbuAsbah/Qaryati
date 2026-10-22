
package com.qaryati.dataquality.dto;

import jakarta.validation.constraints.NotBlank;

public record ResolveDataConflictRequest(

        @NotBlank(message = "Resolution notes are required")
        String resolutionNotes

) {
}
