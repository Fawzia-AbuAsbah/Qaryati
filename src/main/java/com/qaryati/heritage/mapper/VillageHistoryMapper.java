package com.qaryati.heritage.mapper;

import com.qaryati.heritage.dto.*;
import com.qaryati.heritage.entity.VillageHistory;
import org.springframework.stereotype.Component;

@Component
public class VillageHistoryMapper {
    public VillageHistory toEntity(VillageHistoryRequest request) { return VillageHistory.draft(request); }
    public void updateDraft(VillageHistoryRequest request, VillageHistory history) { history.reviseDraft(request); }
    public VillageHistoryResponse toResponse(VillageHistory history) {
        return new VillageHistoryResponse(history.getId(), history.getVillageId(), history.getOverview(),
                history.getUpdatedAt(), history.getVerificationStatus());
    }
}
