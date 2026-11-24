package com.qaryati.agriculture.dto;

import com.qaryati.agriculture.entity.AgriculturalProductType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgriculturalProductRequestValidationTest {

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
    void acceptsEveryDatabaseSupportedProductType() {
        for (AgriculturalProductType type : AgriculturalProductType.values()) {
            var request = new AgriculturalProductRequest("Product " + type, null, type);
            assertTrue(validator.validate(request).isEmpty(), type::name);
        }
    }

    @Test
    void rejectsMissingProductType() {
        var request = new AgriculturalProductRequest("Product", null, (AgriculturalProductType) null);
        assertFalse(validator.validate(request).isEmpty());
    }
}
