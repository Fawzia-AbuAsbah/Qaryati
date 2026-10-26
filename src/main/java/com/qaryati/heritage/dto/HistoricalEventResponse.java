package com.qaryati.heritage.dto;

import com.qaryati.heritage.entity.DatePrecision;
import com.qaryati.heritage.entity.VerificationStatus;
import java.time.Instant;
import java.time.LocalDate;

public record HistoricalEventResponse(Long id, Long villageId, String title, String description,
        LocalDate eventDate, Integer yearFrom, Integer yearTo, DatePrecision datePrecision,
        VerificationStatus verificationStatus, Instant createdAt) {}
