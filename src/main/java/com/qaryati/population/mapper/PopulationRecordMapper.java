package com.qaryati.population.mapper;

import com.qaryati.population.dto.*;
import com.qaryati.population.entity.PopulationRecord;
import org.springframework.stereotype.Component;

@Component
public class PopulationRecordMapper {
    public PopulationRecord toEntity(PopulationRecordRequest request) {
        return new PopulationRecord(request.villageId(), request.referenceYear(), request.referenceDate(),
                request.populationCount(), request.methodology(), request.sourceId(), request.notes());
    }

    public void updateEntity(PopulationRecordRequest request, PopulationRecord record) {
        record.replaceObservation(request.villageId(), request.referenceYear(), request.referenceDate(),
                request.populationCount(), request.methodology(), request.sourceId(), request.notes());
    }

    public PopulationRecordResponse toResponse(PopulationRecord record) {
        return new PopulationRecordResponse(record.getId(), record.getVillageId(), record.getReferenceYear(),
                record.getReferenceDate(), record.getPopulationCount(), record.getMethodology(), record.getSourceId(),
                record.getNotes(), record.getVerificationStatus(), record.getCreatedAt(), record.getUpdatedAt());
    }
}
