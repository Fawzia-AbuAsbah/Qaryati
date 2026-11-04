
package com.qaryati.agriculture.mapper;

import com.qaryati.agriculture.dto.VillageAgricultureRequest;
import com.qaryati.agriculture.dto.VillageAgricultureResponse;
import com.qaryati.agriculture.entity.VillageAgriculture;
import org.springframework.stereotype.Component;

@Component
public class VillageAgricultureMapper {

    public VillageAgriculture toEntity(
            VillageAgricultureRequest request) {

        return new VillageAgriculture(
                request.villageId(),
                request.productId(),
                request.description(),
                request.importance()
        );
    }

    public VillageAgricultureResponse toResponse(
            VillageAgriculture entity) {

        return new VillageAgricultureResponse(
                entity.getId(),
                entity.getVillageId(),
                entity.getProductId(),
                entity.getDescription(),
                entity.getImportance()
        );
    }

    public void updateEntity(
            VillageAgricultureRequest request,
            VillageAgriculture entity) {

        entity.setDescription(request.description());
        entity.setImportance(request.importance());
    }
}
