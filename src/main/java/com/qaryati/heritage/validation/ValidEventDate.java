package com.qaryati.heritage.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = EventDateValidator.class)
public @interface ValidEventDate {
    String message() default "Date fields must match date precision and years must be ordered";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
