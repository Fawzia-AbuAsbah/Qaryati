package com.qaryati.dataquality.api;

import java.util.Set;

/** Reviewed activity identity metadata; the free-text description is deliberately excluded. */
public record EconomicActivityAuditSnapshot(Long villageId, String name, String activityType) {
    public EconomicActivityAuditSnapshot {
        if (villageId == null || villageId <= 0 || name == null || name.length() > 200
                || !Set.of("AGRICULTURE", "CRAFT", "INDUSTRY", "TRADE", "TOURISM", "OTHER").contains(activityType)) {
            throw new IllegalArgumentException("Invalid economic activity audit snapshot");
        }
    }
}
