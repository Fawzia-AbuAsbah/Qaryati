package com.qaryati.community.dto;

import com.qaryati.community.entity.EconomicActivityType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomicActivityRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void acceptsSchemaSupportedValues() {
        for (EconomicActivityType type : EconomicActivityType.values()) {
            assertTrue(validator.validate(new EconomicActivityRequest(1L, "Activity", type, null)).isEmpty());
        }
    }

    @Test
    void rejectsMissingOrInvalidRequiredFields() {
        assertFalse(validator.validate(new EconomicActivityRequest(null, "Activity",
                EconomicActivityType.CRAFT, null)).isEmpty());
        assertFalse(validator.validate(new EconomicActivityRequest(0L, "Activity",
                EconomicActivityType.CRAFT, null)).isEmpty());
        assertFalse(validator.validate(new EconomicActivityRequest(1L, " ",
                EconomicActivityType.CRAFT, null)).isEmpty());
        assertFalse(validator.validate(new EconomicActivityRequest(1L, "Activity", null, null)).isEmpty());
        assertFalse(validator.validate(new EconomicActivityRequest(1L, "x".repeat(201),
                EconomicActivityType.CRAFT, null)).isEmpty());
    }
}
