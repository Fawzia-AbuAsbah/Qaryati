package com.qaryati.community.dto;

import com.qaryati.community.entity.CommunityFacilityType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommunityFacilityRequestValidationTest {

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
    void acceptsEverySchemaSupportedFacilityTypeAndCoordinatePair() {
        for (CommunityFacilityType type : CommunityFacilityType.values()) {
            assertTrue(validator.validate(new CommunityFacilityRequest(1L, "Facility", type, null,
                    new BigDecimal("31.500000"), new BigDecimal("35.200000"))).isEmpty());
        }
        assertTrue(validator.validate(new CommunityFacilityRequest(1L, "Facility",
                CommunityFacilityType.SCHOOL, null, null, null)).isEmpty());
    }

    @Test
    void rejectsInvalidFieldsAndCoordinates() {
        assertFalse(validator.validate(new CommunityFacilityRequest(0L, "Facility",
                CommunityFacilityType.SCHOOL, null, null, null)).isEmpty());
        assertFalse(validator.validate(new CommunityFacilityRequest(1L, " ",
                CommunityFacilityType.SCHOOL, null, null, null)).isEmpty());
        assertFalse(validator.validate(new CommunityFacilityRequest(1L, "Facility", null,
                null, null, null)).isEmpty());
        assertFalse(validator.validate(new CommunityFacilityRequest(1L, "Facility",
                CommunityFacilityType.SCHOOL, null, new BigDecimal("91"), new BigDecimal("35"))).isEmpty());
        assertFalse(validator.validate(new CommunityFacilityRequest(1L, "Facility",
                CommunityFacilityType.SCHOOL, null, new BigDecimal("31"), new BigDecimal("181"))).isEmpty());
        assertFalse(validator.validate(new CommunityFacilityRequest(1L, "Facility",
                CommunityFacilityType.SCHOOL, null, new BigDecimal("31"), null)).isEmpty());
        assertFalse(validator.validate(new CommunityFacilityRequest(1L, "Facility",
                CommunityFacilityType.SCHOOL, null, new BigDecimal("31.1234567"),
                new BigDecimal("35.2"))).isEmpty());
    }
}
