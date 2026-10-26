package com.qaryati.heritage.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class EventDateValidator implements ConstraintValidator<ValidEventDate, EventDateFields> {
    @Override
    public boolean isValid(EventDateFields value, ConstraintValidatorContext context) {
        if (value == null || value.getDatePrecision() == null) return true; // @NotNull owns this error
        var date = value.getEventDate();
        var from = value.getYearFrom();
        var to = value.getYearTo();
        return switch (value.getDatePrecision()) {
            case EXACT -> date != null && from == null && to == null;
            case YEAR -> date == null && from != null && to == null;
            case PERIOD -> date == null && from != null && to != null && to >= from;
            case UNKNOWN -> date == null && from == null && to == null;
        };
    }
}
