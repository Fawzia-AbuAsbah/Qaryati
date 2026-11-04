package com.qaryati.village.dto;

import com.qaryati.village.entity.VillageStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record VillageResponse(
        Long id,
        Long governorateId,
        Long localAuthorityId,
        String nameAr,
        String nameEn,
        String nameOrigin,
        String locationDescription,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal elevationM,
        BigDecimal areaKm2,
        VillageStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant archivedAt
) {
}