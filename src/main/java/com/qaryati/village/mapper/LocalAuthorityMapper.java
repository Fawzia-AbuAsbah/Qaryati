package com.qaryati.village.mapper;

import com.qaryati.village.dto.LocalAuthorityRequest;
import com.qaryati.village.dto.LocalAuthorityResponse;
import com.qaryati.village.entity.LocalAuthority;
import org.springframework.stereotype.Component;

@Component
public class LocalAuthorityMapper {

    public LocalAuthority toEntity(LocalAuthorityRequest request) {
        LocalAuthority localAuthority = new LocalAuthority();
        updateEntity(request, localAuthority);
        return localAuthority;
    }

    public void updateEntity(LocalAuthorityRequest request, LocalAuthority localAuthority) {
        localAuthority.setNameAr(request.nameAr());
        localAuthority.setNameEn(request.nameEn());
        localAuthority.setAuthorityType(request.authorityType());
    }

    public LocalAuthorityResponse toResponse(LocalAuthority localAuthority) {
        return new LocalAuthorityResponse(
                localAuthority.getId(),
                localAuthority.getGovernorate().getId(),
                localAuthority.getNameAr(),
                localAuthority.getNameEn(),
                localAuthority.getAuthorityType()
        );
    }
}
