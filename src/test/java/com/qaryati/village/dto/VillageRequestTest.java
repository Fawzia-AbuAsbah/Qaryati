package com.qaryati.village.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import org.springframework.validation.BeanPropertyBindingResult;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class VillageRequestTest {
    private static ValidatorFactory factory;
    private static Validator validator;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @BeforeAll
    static void initialize() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void close() { factory.close(); }

    private static Map<String, Object> minimal() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("governorateId", 1L);
        values.put("nameAr", "Village");
        values.put("locationDescription", "Location");
        return values;
    }

    private static VillageRequest request(Map<String, Object> values) {
        return JSON.readValue(JSON.writeValueAsString(values), VillageRequest.class);
    }

    @Test
    void acceptsMinimalAndCompleteRequests() {
        VillageRequest minimal = request(minimal());
        assertTrue(validator.validate(minimal).isEmpty());
        assertNull(minimal.localAuthorityId());
        assertNull(minimal.latitude());
        var values = minimal();
        values.put("localAuthorityId", 2L);
        values.put("nameAr", "A".repeat(180));
        values.put("nameEn", "E".repeat(180));
        values.put("nameOrigin", "Origin");
        values.put("latitude", new BigDecimal("32.123456"));
        values.put("longitude", new BigDecimal("35.654321"));
        values.put("elevationM", new BigDecimal("-430.25"));
        values.put("areaKm2", new BigDecimal("12.345"));
        var complete = request(values);
        assertTrue(validator.validate(complete).isEmpty());
        assertEquals(new BigDecimal("-430.25"), complete.elevationM());
        assertEquals("Origin", complete.nameOrigin());
    }

    static Stream<Arguments> invalidFields() {
        return Stream.of(
                Arguments.of("governorateId", null),
                Arguments.of("governorateId", 0L),
                Arguments.of("governorateId", -1L),
                Arguments.of("localAuthorityId", 0L),
                Arguments.of("localAuthorityId", -1L),
                Arguments.of("nameAr", null),
                Arguments.of("nameAr", ""),
                Arguments.of("nameAr", "   "),
                Arguments.of("nameAr", "A".repeat(181)),
                Arguments.of("nameEn", "E".repeat(181)),
                Arguments.of("locationDescription", null),
                Arguments.of("locationDescription", ""),
                Arguments.of("locationDescription", " \t "));
    }

    @ParameterizedTest
    @MethodSource("invalidFields")
    void reportsInvalidField(String field, Object value) {
        var values = minimal();
        values.put(field, value);
        var violations = validator.validate(request(values));
        assertEquals(1, violations.size());
        assertEquals(field, violations.iterator().next().getPropertyPath().toString());
    }

    static Stream<Arguments> numericCases() {
        return Stream.of(
                Arguments.of("latitude", "-90", null),
                Arguments.of("latitude", "90", null),
                Arguments.of("longitude", "-180", null),
                Arguments.of("longitude", "180", null),
                Arguments.of("latitude", "-90.000001", "latitude"),
                Arguments.of("latitude", "90.000001", "latitude"),
                Arguments.of("longitude", "-180.000001", "longitude"),
                Arguments.of("longitude", "180.000001", "longitude"),
                Arguments.of("latitude", "1.123456", null),
                Arguments.of("longitude", "1.123456", null),
                Arguments.of("latitude", "1.1234567", "latitudeRepresentable"),
                Arguments.of("longitude", "1.1234567", "longitudeRepresentable"),
                Arguments.of("latitude", "1.2300000", null),
                Arguments.of("longitude", "123456E-6", null),
                Arguments.of("latitude", "0.0000000", null),
                Arguments.of("elevationM", "-9999999.99", null),
                Arguments.of("elevationM", "9999999.99", null),
                Arguments.of("elevationM", "10000000", "elevationRepresentable"),
                Arguments.of("elevationM", "-10000000", "elevationRepresentable"),
                Arguments.of("elevationM", "1.001", "elevationRepresentable"),
                Arguments.of("elevationM", "-430.25000", null),
                Arguments.of("elevationM", "123E2", null),
                Arguments.of("areaKm2", "999999999.999", null),
                Arguments.of("areaKm2", "0.001", null),
                Arguments.of("areaKm2", "1000000000", "areaRepresentable"),
                Arguments.of("areaKm2", "0.0001", "areaRepresentable"),
                Arguments.of("areaKm2", "1.2340", null),
                Arguments.of("areaKm2", "1234E-3", null),
                Arguments.of("areaKm2", "0", "areaKm2"),
                Arguments.of("areaKm2", "-1", "areaKm2"));
    }

    @ParameterizedTest
    @MethodSource("numericCases")
    void validatesExactNumericValues(String field, String number, String error) {
        var values = minimal();
        if (field.equals("latitude") || field.equals("longitude")) {
            values.put("latitude", BigDecimal.ZERO);
            values.put("longitude", BigDecimal.ZERO);
        }
        values.put(field, new BigDecimal(number));
        var violations = validator.validate(request(values));
        if (error == null) {
            assertTrue(violations.isEmpty(), violations.toString());
        } else {
            assertEquals(1, violations.size(), violations.toString());
            assertEquals(error, violations.iterator().next().getPropertyPath().toString());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"latitude", "longitude"})
    void incompletePairBecomesUsefulSpringFieldError(String coordinate) {
        var values = minimal();
        values.put(coordinate, BigDecimal.ONE);
        var bean = request(values);
        var errors = new BeanPropertyBindingResult(bean, "villageRequest");
        new SpringValidatorAdapter(validator).validate(bean, errors);
        assertEquals(1, errors.getFieldErrorCount());
        assertEquals("coordinatesPaired", errors.getFieldError().getField());
        assertEquals("Latitude and longitude must both be supplied or both be null",
                errors.getFieldError().getDefaultMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"unexpected", "id", "status", "createdAt", "updatedAt", "archivedAt"})
    void rejectsUnsupportedPropertiesIncludingNull(String field) {
        for (Object value : new Object[]{null, "supplied"}) {
            var values = minimal();
            values.put(field, value);
            var failure = assertThrows(RuntimeException.class, () -> request(values));
            StringBuilder causes = new StringBuilder();
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                causes.append(cause.getMessage()).append('\n');
            }
            assertTrue(causes.toString().contains("Unsupported request field: " + field),
                    causes.toString());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"latitude", "longitude"})
    void coordinateOverflowReportsBothRangeAndStoragePrecision(String field) {
        var values = minimal();
        values.put("latitude", BigDecimal.ZERO);
        values.put("longitude", BigDecimal.ZERO);
        values.put(field, new BigDecimal("1000"));
        var violations = validator.validate(request(values));
        var paths = violations.stream().map(v -> v.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(java.util.Set.of(field, field + "Representable"), paths);
        assertEquals(2, violations.size());
    }
    @Test
    void omissionAndExplicitNullProduceSameOptionalValues() {
        var omitted = request(minimal());
        var values = minimal();
        for (String field : new String[]{"localAuthorityId", "nameEn", "nameOrigin",
                "latitude", "longitude", "elevationM", "areaKm2"}) {
            values.put(field, null);
        }
        assertEquals(omitted, request(values));
        assertTrue(validator.validate(request(values)).isEmpty());
        // TEXT has no schema length limit.
        values.put("locationDescription", "L".repeat(10000));
        assertTrue(validator.validate(request(values)).isEmpty());
    }
}