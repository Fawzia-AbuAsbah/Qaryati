package com.qaryati.heritage.dto;

import com.qaryati.heritage.entity.VerificationStatus;
import java.time.Instant;

public record VillageHistoryResponse(Long id, Long villageId, String overview,
        Instant updatedAt, VerificationStatus verificationStatus) {}
