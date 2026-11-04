package com.qaryati.village.mapper;
import com.qaryati.village.dto.*;
import com.qaryati.village.entity.*;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.stereotype.Component;

@Component
public class VillageRelationMapper {
    public VillageRelation toEntity(VillageRelationRequest request) {
        var entity = new VillageRelation();
        updateEntity(request, entity);
        return entity;
    }
    // Association resolution belongs to the service.
    public void updateEntity(VillageRelationRequest request, VillageRelation entity) {
        entity.setRelatedPlaceName(request.relatedPlaceName());
        entity.setRelationType(request.relationType());
        entity.setDistanceKm(request.distanceKm());
    }
    public VillageRelationResponse toResponse(VillageRelation entity) {
        return new VillageRelationResponse(entity.getId(), id(entity.getVillage()),
                id(entity.getRelatedVillage()), entity.getRelatedPlaceName(),
                entity.getRelationType(), entity.getDistanceKm());
    }
    private Long id(Village village) {
        if (village == null) return null;
        return village instanceof HibernateProxy proxy
                ? (Long) proxy.getHibernateLazyInitializer().getInternalIdentifier() : village.getId();
    }
}
