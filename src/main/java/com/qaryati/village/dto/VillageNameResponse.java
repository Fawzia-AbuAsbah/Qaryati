package com.qaryati.village.dto;

import com.qaryati.village.entity.VillageNameType;

public record VillageNameResponse(Long id, Long villageId, String name,
                                  String languageCode, VillageNameType nameType, String notes) {
}