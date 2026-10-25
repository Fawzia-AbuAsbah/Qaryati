
package com.qaryati.village.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GovernorateRequest(

        @NotBlank(message = "Arabic name is required")
        @Size(max = 120)
        String nameAr,

        @Size(max = 120)
        String nameEn,

        @Size(max = 32)
        String code

) {
}
