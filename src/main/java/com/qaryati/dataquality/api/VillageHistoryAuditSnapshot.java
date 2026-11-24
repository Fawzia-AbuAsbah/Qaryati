package com.qaryati.dataquality.api;
import java.util.*;
/** Complete overview state, including legacy blank text. New input is validated by the owning service.
 * Timestamp excluded: source writes do not dirty the history entity. */
public record VillageHistoryAuditSnapshot(Long villageId, String overview, List<Long> sourceIds,
        String verificationStatus) {
    public VillageHistoryAuditSnapshot {
        if (villageId == null || villageId <= 0 || overview == null
                || verificationStatus == null || !Set.of("DRAFT", "UNDER_REVIEW", "VERIFIED", "REJECTED", "DISPUTED").contains(verificationStatus))
            throw new IllegalArgumentException("Invalid history snapshot");
        if (sourceIds == null || sourceIds.stream().anyMatch(id -> id == null || id <= 0))
            throw new IllegalArgumentException("Invalid source IDs");
        sourceIds = sourceIds.stream().distinct().sorted().toList();
    }
}
