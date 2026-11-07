package com.qaryati.heritage.dto;
import com.qaryati.heritage.entity.VerificationStatus;
import java.time.Instant;
import java.util.List;
public record VillageHistoricalOverviewResponse(Long id, Long villageId, String overview,
        Instant updatedAt, VerificationStatus verificationStatus, List<Long> sourceIds) {}
