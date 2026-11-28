package com.qaryati.dataquality.api;

import java.math.BigDecimal;
import java.util.Set;

/** Reviewed facility metadata; the free-text description is deliberately excluded. */
public record CommunityFacilityAuditSnapshot(Long villageId, String name, String facilityType,
                                             BigDecimal latitude, BigDecimal longitude) {
    public CommunityFacilityAuditSnapshot {
        if (villageId == null || villageId <= 0 || name == null || name.length() > 200
                || !Set.of("SCHOOL", "CLINIC", "LIBRARY", "COMMUNITY_CENTER", "PARK", "SPORTS", "OTHER").contains(facilityType)
                || (latitude == null) != (longitude == null) || !coordinate(latitude, 90) || !coordinate(longitude, 180)) {
            throw new IllegalArgumentException("Invalid community facility audit snapshot");
        }
    }

    private static boolean coordinate(BigDecimal value, int bound) {
        return value == null || value.scale() <= 6 && value.precision() - (long) value.scale() <= 3
                && value.abs().compareTo(BigDecimal.valueOf(bound)) <= 0;
    }
}
