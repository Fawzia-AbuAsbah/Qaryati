package com.qaryati.heritage.dto;

import com.qaryati.heritage.entity.*;

public record CulturalHeritageResponse(Long id, Long villageId, String name,
        CulturalHeritageType heritageType, String description, VerificationStatus verificationStatus) {}
