package com.qaryati.dataquality.api;

import java.util.List;
import java.util.Set;

/** Complete cultural state. Exact free text is retained in audit JSON; review storage/retention. */
public record CulturalHeritageAuditSnapshot(Long villageId, String name, String heritageType,
        String description, List<Long> sourceIds, String verificationStatus) {
    public CulturalHeritageAuditSnapshot {
        if (villageId == null || villageId <= 0 || name == null || name.length() > 200
                || description == null || heritageType == null
                || !Set.of("CRAFT", "FOOD", "TRADITION", "PRACTICE", "OTHER").contains(heritageType)
                || verificationStatus == null
                || !Set.of("DRAFT", "UNDER_REVIEW", "VERIFIED", "REJECTED", "DISPUTED").contains(verificationStatus))
            throw new IllegalArgumentException("Invalid cultural heritage snapshot");
        if (sourceIds == null || sourceIds.stream().anyMatch(id -> id == null || id <= 0))
            throw new IllegalArgumentException("Invalid source IDs");
        sourceIds = sourceIds.stream().distinct().sorted().toList();
    }
}
