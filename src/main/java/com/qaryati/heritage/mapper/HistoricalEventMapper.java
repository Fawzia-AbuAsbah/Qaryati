package com.qaryati.heritage.mapper;

import com.qaryati.heritage.dto.*;
import com.qaryati.heritage.entity.HistoricalEvent;
import org.springframework.stereotype.Component;

@Component
public class HistoricalEventMapper {
    public HistoricalEvent toEntity(HistoricalEventRequest request) { return HistoricalEvent.draft(request); }
    public void updateDraft(HistoricalEventRequest request, HistoricalEvent event) { event.reviseDraft(request); }
    public HistoricalEventResponse toResponse(HistoricalEvent event) {
        return new HistoricalEventResponse(event.getId(), event.getVillageId(), event.getTitle(),
                event.getDescription(), event.getEventDate(), event.getYearFrom(), event.getYearTo(),
                event.getDatePrecision(), event.getVerificationStatus(), event.getCreatedAt());
    }
}
