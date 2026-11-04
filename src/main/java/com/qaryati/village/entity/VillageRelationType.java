package com.qaryati.village.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum VillageRelationType {
    NEARBY, ADJACENT, HISTORICALLY_RELATED, OTHER;

    // Exact schema spellings only; reject numeric enum ordinals.
    @JsonCreator
    public static VillageRelationType fromJson(String value) {
        return value == null ? null : valueOf(value);
    }
}