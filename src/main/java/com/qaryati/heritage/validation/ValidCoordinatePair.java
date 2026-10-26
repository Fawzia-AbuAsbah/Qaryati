package com.qaryati.heritage.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CoordinatePairValidator.class)
public @interface ValidCoordinatePair {
    String message() default "Latitude and longitude must both be present or both be absent";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
