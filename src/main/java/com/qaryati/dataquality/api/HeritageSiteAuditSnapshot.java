package com.qaryati.dataquality.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/** Reviewed metadata; text must contain no secrets. Enum names avoid Heritage internals. */
public record HeritageSiteAuditSnapshot(Long villageId, String name, String siteType,
        String description, BigDecimal latitude, BigDecimal longitude, String conditionNotes,
        List<Long> sourceIds, String verificationStatus) {
    public HeritageSiteAuditSnapshot {
        if (villageId == null || villageId <= 0 || name == null || name.length() > 250)
            throw new IllegalArgumentException("Invalid site identity metadata");
        if (siteType == null || !Set.of("ARCHAEOLOGICAL", "TRADITIONAL_BUILDING", "SPRING",
                "RELIGIOUS", "VIEWPOINT", "OTHER").contains(siteType))
            throw new IllegalArgumentException("Invalid site type");
        if (verificationStatus == null || !Set.of("DRAFT", "UNDER_REVIEW", "VERIFIED", "REJECTED", "DISPUTED").contains(verificationStatus))
            throw new IllegalArgumentException("Invalid verification status");
        if ((latitude == null) != (longitude == null) || !coordinate(latitude, 90) || !coordinate(longitude, 180))
            throw new IllegalArgumentException("Invalid coordinates");
        if (sourceIds == null || sourceIds.stream().anyMatch(id -> id == null || id <= 0))
            throw new IllegalArgumentException("Invalid source IDs");
        sourceIds = sourceIds.stream().distinct().sorted().toList();
    }

    private static boolean coordinate(BigDecimal value, int bound) {
        return value == null || value.scale() <= 6 && value.precision() - (long) value.scale() <= 3
                && value.abs().compareTo(BigDecimal.valueOf(bound)) <= 0;
    }
}
