package com.qaryati.heritage.mapper;

import com.qaryati.heritage.dto.*;
import com.qaryati.heritage.entity.HeritageSite;
import org.springframework.stereotype.Component;

@Component
public class HeritageSiteMapper {
    public HeritageSite toEntity(HeritageSiteRequest request) { return HeritageSite.draft(request); }
    public void updateDraft(HeritageSiteRequest request, HeritageSite site) { site.reviseDraft(request); }
    public HeritageSiteResponse toResponse(HeritageSite site) {
        return new HeritageSiteResponse(site.getId(), site.getVillageId(), site.getName(), site.getSiteType(),
                site.getDescription(), site.getLatitude(), site.getLongitude(), site.getConditionNotes(),
                site.getVerificationStatus());
    }
}
