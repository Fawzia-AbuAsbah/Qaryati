
package com.qaryati.agriculture.dto;

import com.qaryati.agriculture.entity.AgriculturalProductType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AgriculturalProductRequest(

        @NotBlank(message = "Arabic name is required")
        @Size(max = 160)
        String nameAr,

        @Size(max = 160)
        String nameEn,

        @NotNull(message = "Product type is required")
        AgriculturalProductType productType

) {
    /** Retains source compatibility for internal Java callers; JSON binds to the enum above. */
    public AgriculturalProductRequest(String nameAr, String nameEn, String productType) {
        this(nameAr, nameEn, productType == null ? null : AgriculturalProductType.valueOf(productType));
    }
}
