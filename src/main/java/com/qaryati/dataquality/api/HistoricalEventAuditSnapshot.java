package com.qaryati.dataquality.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** Reviewed metadata only. Title must contain no secrets. Description/citation are excluded. */
public record HistoricalEventAuditSnapshot(Long villageId, String title, LocalDate eventDate,
        Integer yearFrom, Integer yearTo, String datePrecision, List<Long> sourceIds,
        String verificationStatus) {
    public HistoricalEventAuditSnapshot {
        if (villageId == null || villageId <= 0 || title == null || title.isBlank() || title.length() > 250)
            throw new IllegalArgumentException("Invalid event audit identity metadata");
        if (datePrecision == null || !Set.of("EXACT", "YEAR", "PERIOD", "UNKNOWN").contains(datePrecision))
            throw new IllegalArgumentException("Invalid date precision");
        if (verificationStatus != null && !Set.of("DRAFT", "UNDER_REVIEW", "VERIFIED", "REJECTED", "DISPUTED").contains(verificationStatus))
            throw new IllegalArgumentException("Invalid verification status");
        if (sourceIds == null || sourceIds.stream().anyMatch(id -> id == null || id <= 0))
            throw new IllegalArgumentException("Invalid source IDs");
        sourceIds = sourceIds.stream().distinct().sorted().toList();
    }
}
