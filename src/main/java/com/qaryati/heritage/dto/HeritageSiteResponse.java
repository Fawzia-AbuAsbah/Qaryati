package com.qaryati.heritage.dto;

import com.qaryati.heritage.entity.*;
import java.math.BigDecimal;

public record HeritageSiteResponse(Long id, Long villageId, String name, HeritageSiteType siteType,
        String description, BigDecimal latitude, BigDecimal longitude, String conditionNotes,
        VerificationStatus verificationStatus) {}
