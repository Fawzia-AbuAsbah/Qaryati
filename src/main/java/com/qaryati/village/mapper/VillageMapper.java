package com.qaryati.village.mapper;

import com.qaryati.village.dto.VillageRequest;
import com.qaryati.village.dto.VillageResponse;
import com.qaryati.village.entity.Governorate;
import com.qaryati.village.entity.LocalAuthority;
import com.qaryati.village.entity.Village;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.stereotype.Component;

@Component
public class VillageMapper {

    public Village toEntity(VillageRequest request) {
        Village village = new Village();
        updateEntity(request, village);
        return village;
    }

    // Parent resolution and all lifecycle fields belong to the service.
    // Null optional scalar values intentionally clear previous values for full PUT.
    public void updateEntity(VillageRequest request, Village village) {
        village.setNameAr(request.nameAr());
        village.setNameEn(request.nameEn());
        village.setNameOrigin(request.nameOrigin());
        village.setLocationDescription(request.locationDescription());
        village.setLatitude(request.latitude());
        village.setLongitude(request.longitude());
        village.setElevationM(request.elevationM());
        village.setAreaKm2(request.areaKm2());
    }

    // The Village itself must be initialized; parent proxies need not be initialized.
    public VillageResponse toResponse(Village village) {
        return new VillageResponse(
                village.getId(),
                governorateId(village.getGovernorate()),
                localAuthorityId(village.getLocalAuthority()),
                village.getNameAr(),
                village.getNameEn(),
                village.getNameOrigin(),
                village.getLocationDescription(),
                village.getLatitude(),
                village.getLongitude(),
                village.getElevationM(),
                village.getAreaKm2(),
                village.getStatus(),
                village.getCreatedAt(),
                village.getUpdatedAt(),
                village.getArchivedAt()
        );
    }

    private Long governorateId(Governorate governorate) {
        if (governorate == null) {
            return null;
        }
        if (governorate instanceof HibernateProxy proxy) {
            return (Long) proxy.getHibernateLazyInitializer().getInternalIdentifier();
        }
        return governorate.getId();
    }

    private Long localAuthorityId(LocalAuthority localAuthority) {
        if (localAuthority == null) {
            return null;
        }
        if (localAuthority instanceof HibernateProxy proxy) {
            return (Long) proxy.getHibernateLazyInitializer().getInternalIdentifier();
        }
        return localAuthority.getId();
    }
}