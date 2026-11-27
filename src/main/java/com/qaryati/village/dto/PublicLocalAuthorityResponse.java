package com.qaryati.village.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Public catalogue fields only; no management type or nested entity is serialized. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record PublicLocalAuthorityResponse(Long id, String nameAr, String nameEn, Long governorateId) {
}
