package com.qaryati.village.dto;

import com.qaryati.village.entity.LocalAuthorityType;

public record LocalAuthorityResponse(
        Long id,
        Long governorateId,
        String nameAr,
        String nameEn,
        LocalAuthorityType authorityType
) {
}
