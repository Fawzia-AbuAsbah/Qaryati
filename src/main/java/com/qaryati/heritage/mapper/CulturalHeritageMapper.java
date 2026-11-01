package com.qaryati.heritage.mapper;

import com.qaryati.heritage.dto.*;
import com.qaryati.heritage.entity.CulturalHeritage;
import org.springframework.stereotype.Component;

@Component
public class CulturalHeritageMapper {
    public CulturalHeritage toEntity(CulturalHeritageRequest request) { return CulturalHeritage.draft(request); }
    public void updateDraft(CulturalHeritageRequest request, CulturalHeritage heritage) { heritage.reviseDraft(request); }
    public CulturalHeritageResponse toResponse(CulturalHeritage heritage) {
        return new CulturalHeritageResponse(heritage.getId(), heritage.getVillageId(), heritage.getName(),
                heritage.getHeritageType(), heritage.getDescription(), heritage.getVerificationStatus());
    }
}
