package com.qaryati.community.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Constraint(validatedBy = FacilityCoordinatePairValidator.class)
@Target(TYPE)
@Retention(RUNTIME)
public @interface ValidFacilityCoordinatePair {
    String message() default "Latitude and longitude must be supplied together";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
