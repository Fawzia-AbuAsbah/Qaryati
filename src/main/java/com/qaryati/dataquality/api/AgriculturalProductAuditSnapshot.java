package com.qaryati.dataquality.api;

import java.util.Set;

/** Reviewed product identity metadata; free-form request data is never retained here. */
public record AgriculturalProductAuditSnapshot(String nameAr, String nameEn, String productType) {
    public AgriculturalProductAuditSnapshot {
        if (nameAr == null || nameAr.length() > 160 || nameEn != null && nameEn.length() > 160
                || !Set.of("CROP", "LIVESTOCK", "PROCESSED_PRODUCT", "OTHER").contains(productType)) {
            throw new IllegalArgumentException("Invalid agricultural product audit snapshot");
        }
    }
}
