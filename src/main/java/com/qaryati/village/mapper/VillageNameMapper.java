package com.qaryati.village.mapper;

import com.qaryati.village.dto.*;
import com.qaryati.village.entity.VillageName;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.stereotype.Component;

@Component
public class VillageNameMapper {
    public VillageName toEntity(VillageNameRequest request) {
        var entity = new VillageName();
        updateEntity(request, entity);
        return entity;
    }

    // Identity and parent resolution belong to the service. Full PUT clears omitted notes.
    public void updateEntity(VillageNameRequest request, VillageName entity) {
        entity.setName(request.name());
        entity.setLanguageCode(request.languageCode());
        entity.setNameType(request.nameType());
        entity.setNotes(request.notes());
    }

    public VillageNameResponse toResponse(VillageName entity) {
        var parent = entity.getVillage();
        Long villageId = parent instanceof HibernateProxy proxy
                ? (Long) proxy.getHibernateLazyInitializer().getInternalIdentifier() : parent.getId();
        return new VillageNameResponse(entity.getId(), villageId, entity.getName(),
                entity.getLanguageCode(), entity.getNameType(), entity.getNotes());
    }
}