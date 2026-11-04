
package com.qaryati.agriculture.dto;

import com.qaryati.agriculture.entity.AgricultureImportance;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VillageAgricultureRequestValidationTest {

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
    void shouldAcceptValidRequest() {

        var request = new VillageAgricultureRequest(
                10L,
                5L,
                "Olive cultivation",
                AgricultureImportance.HIGH
        );

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void shouldRejectMissingVillageId() {

        var request = new VillageAgricultureRequest(
                null, 5L, "Olives", AgricultureImportance.HIGH
        );

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void shouldRejectMissingProductId() {

        var request = new VillageAgricultureRequest(
                10L, null, "Olives", AgricultureImportance.HIGH
        );

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void shouldRejectNegativeVillageId() {

        var request = new VillageAgricultureRequest(
                -10L, 5L, "Olives", AgricultureImportance.HIGH
        );

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void shouldRejectZeroProductId() {

        var request = new VillageAgricultureRequest(
                10L, 0L, "Olives", AgricultureImportance.HIGH
        );

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void shouldAcceptNullOptionalFields() {

        var request = new VillageAgricultureRequest(
                10L, 5L, null, null
        );

        assertTrue(validator.validate(request).isEmpty());
    }
}
