package com.qaryati.heritage.dto;

import com.qaryati.heritage.entity.DatePrecision;
import com.qaryati.heritage.entity.VerificationStatus;
import java.time.Instant;
import java.time.LocalDate;

public record HistoricalEventResponse(Long id, Long villageId, String title, String description,
        LocalDate eventDate, Integer yearFrom, Integer yearTo, DatePrecision datePrecision,
        VerificationStatus verificationStatus, Instant createdAt, java.util.List<Long> sourceIds) {
    public HistoricalEventResponse { sourceIds = java.util.List.copyOf(sourceIds); }
    public HistoricalEventResponse(Long id, Long villageId, String title, String description,
            LocalDate eventDate, Integer yearFrom, Integer yearTo, DatePrecision datePrecision,
            VerificationStatus verificationStatus, Instant createdAt) {
        this(id, villageId, title, description, eventDate, yearFrom, yearTo, datePrecision,
                verificationStatus, createdAt, java.util.List.of());
    }
}
