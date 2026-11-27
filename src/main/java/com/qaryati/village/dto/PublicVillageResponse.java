package com.qaryati.village.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/** Public field allowlist; editorial DTO changes cannot add fields to this response. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record PublicVillageResponse(
        Long id,
        String nameAr,
        String nameEn,
        Long governorateId,
        Long localAuthorityId,
        BigDecimal latitude,
        BigDecimal longitude) {

    public static PublicVillageResponse from(VillageResponse village) {
        return new PublicVillageResponse(village.id(), village.nameAr(), village.nameEn(),
                village.governorateId(), village.localAuthorityId(), village.latitude(), village.longitude());
    }
}
