package com.qaryati.heritage.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CoordinatePairValidator implements ConstraintValidator<ValidCoordinatePair, CoordinateFields> {
    @Override
    public boolean isValid(CoordinateFields value, ConstraintValidatorContext context) {
        return value == null || (value.getLatitude() == null) == (value.getLongitude() == null);
    }
}
