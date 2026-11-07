package com.qaryati.heritage.dto;

import com.qaryati.heritage.entity.*;
import java.util.List;

public record CulturalHeritageCrudResponse(Long id, Long villageId, String name,
        CulturalHeritageType heritageType, String description, VerificationStatus verificationStatus,
        List<Long> sourceIds) {
    public CulturalHeritageCrudResponse { sourceIds = List.copyOf(sourceIds); }
}
