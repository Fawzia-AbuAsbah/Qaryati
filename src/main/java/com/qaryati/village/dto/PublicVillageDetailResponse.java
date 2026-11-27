package com.qaryati.village.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/** Public detail allowlist; collection responses retain their separate seven-field contract. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record PublicVillageDetailResponse(
        Long id,
        String nameAr,
        String nameEn,
        Long governorateId,
        Long localAuthorityId,
        BigDecimal latitude,
        BigDecimal longitude,
        String nameOrigin,
        String locationDescription,
        BigDecimal elevationM,
        BigDecimal areaKm2) {

    public static PublicVillageDetailResponse from(VillageResponse village) {
        return new PublicVillageDetailResponse(village.id(), village.nameAr(), village.nameEn(),
                village.governorateId(), village.localAuthorityId(), village.latitude(), village.longitude(),
                village.nameOrigin(), village.locationDescription(), village.elevationM(), village.areaKm2());
    }
}
