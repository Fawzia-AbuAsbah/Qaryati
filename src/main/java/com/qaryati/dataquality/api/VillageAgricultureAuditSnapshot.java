package com.qaryati.dataquality.api;

import java.util.Set;

/** Reviewed relationship metadata; the free-text description is deliberately excluded. */
public record VillageAgricultureAuditSnapshot(Long villageId, Long productId, String importance) {
    public VillageAgricultureAuditSnapshot {
        if (villageId == null || villageId <= 0 || productId == null || productId <= 0
                || importance != null && !Set.of("LOW", "MEDIUM", "HIGH").contains(importance)) {
            throw new IllegalArgumentException("Invalid village agriculture audit snapshot");
        }
    }
}
