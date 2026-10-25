
package com.qaryati.village.mapper;

import com.qaryati.village.dto.GovernorateRequest;
import com.qaryati.village.dto.GovernorateResponse;
import com.qaryati.village.entity.Governorate;
import org.springframework.stereotype.Component;

@Component
public class GovernorateMapper {

    public Governorate toEntity(GovernorateRequest request) {
        Governorate governorate = new Governorate();
        governorate.setNameAr(request.nameAr());
        governorate.setNameEn(request.nameEn());
        governorate.setCode(request.code());
        return governorate;
    }

    public GovernorateResponse toResponse(Governorate governorate) {
        return new GovernorateResponse(
                governorate.getId(),
                governorate.getNameAr(),
                governorate.getNameEn(),
                governorate.getCode()
        );
    }
}
