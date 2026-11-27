package com.qaryati.village.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Public catalogue fields only; Governorate management fields stay private. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record PublicGovernorateResponse(Long id, String nameAr, String nameEn) {
}
