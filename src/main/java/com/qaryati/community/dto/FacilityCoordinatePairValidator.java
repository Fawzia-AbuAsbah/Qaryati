package com.qaryati.community.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class FacilityCoordinatePairValidator
        implements ConstraintValidator<ValidFacilityCoordinatePair, CommunityFacilityRequest> {

    @Override
    public boolean isValid(CommunityFacilityRequest value, ConstraintValidatorContext context) {
        return value == null || (value.latitude() == null) == (value.longitude() == null);
    }
}
