
package com.qaryati.agriculture.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgriculturalProductRequest(

        @NotBlank(message = "Arabic name is required")
        @Size(max = 160)
        String nameAr,

        @Size(max = 160)
        String nameEn,

        @NotBlank(message = "Product type is required")
        @Size(max = 25)
        String productType

) {
}
