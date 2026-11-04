package com.qaryati.village.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum VillageNameType {
    ALTERNATIVE, HISTORICAL, FORMER, TRANSLITERATION;

    // Reject numeric ordinals and accept only the schema's exact enum spellings.
    @JsonCreator
    public static VillageNameType fromJson(String value) {
        return value == null ? null : valueOf(value);
    }
}