package com.qaryati.heritage;

import com.qaryati.heritage.dto.*;
import com.qaryati.heritage.entity.*;
import com.qaryati.heritage.mapper.HistoricalEventMapper;
import jakarta.validation.*;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

class HistoricalEventTest {
    private static ValidatorFactory factory;
    private static Validator validator;
    private final HistoricalEventMapper mapper = new HistoricalEventMapper();
    @BeforeAll static void setup() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }
    @AfterAll static void close() { factory.close(); }

    static Stream<Arguments> dateCombinations() {
        List<Arguments> cases = new ArrayList<>();
        for (DatePrecision precision : DatePrecision.values()) {
            for (int mask = 0; mask < 8; mask++) {
                LocalDate date = (mask & 1) != 0 ? LocalDate.of(2500, 1, 1) : null;
                Integer from = (mask & 2) != 0 ? 1900 : null;
                Integer to = (mask & 4) != 0 ? 1901 : null;
                boolean valid = mask == switch (precision) {
                    case EXACT -> 1; case YEAR -> 2; case PERIOD -> 6; case UNKNOWN -> 0;
                };
                cases.add(Arguments.of(precision, date, from, to, valid));
            }
        }
        return cases.stream();
    }

    @ParameterizedTest @MethodSource("dateCombinations")
    void validatesEveryDateCombinationOnDtoEntityAndDomain(DatePrecision precision, LocalDate date,
            Integer from, Integer to, boolean valid) {
        var request = request(precision, date, from, to);
        assertEquals(valid, validator.validate(request).isEmpty());
        var entity = mapper.toEntity(request(DatePrecision.UNKNOWN, null, null, null));
        ReflectionTestUtils.setField(entity, "datePrecision", precision);
        ReflectionTestUtils.setField(entity, "eventDate", date);
        ReflectionTestUtils.setField(entity, "yearFrom", from);
        ReflectionTestUtils.setField(entity, "yearTo", to);
        assertEquals(valid, validator.validate(entity).isEmpty());
        if (valid) assertDoesNotThrow(() -> mapper.toEntity(request));
        else assertThrows(IllegalArgumentException.class, () -> mapper.toEntity(request));
    }

    @ParameterizedTest @CsvSource({"1,1,true", "2200,2200,true", "1900,1899,false",
            "0,1,false", "1,2201,false", "-1,1,false", "2201,2201,false", "1,0,false"})
    void validatesYearBoundsAndPeriodOrdering(int from, int to, boolean valid) {
        var request = request(DatePrecision.PERIOD, null, from, to);
        assertEquals(valid, validator.validate(request).isEmpty());
        var entity = mapper.toEntity(request(DatePrecision.UNKNOWN, null, null, null));
        ReflectionTestUtils.setField(entity, "datePrecision", DatePrecision.PERIOD);
        ReflectionTestUtils.setField(entity, "yearFrom", from);
        ReflectionTestUtils.setField(entity, "yearTo", to);
        assertEquals(valid, validator.validate(entity).isEmpty());
        if (valid) assertDoesNotThrow(() -> mapper.toEntity(request));
        else assertThrows(IllegalArgumentException.class, () -> mapper.toEntity(request));
    }

    static Stream<Arguments> invalidRequiredFields() {
        return Stream.of(
            Arguments.of(null, "Title", "Description", DatePrecision.UNKNOWN),
            Arguments.of(1L, null, "Description", DatePrecision.UNKNOWN),
            Arguments.of(1L, " ", "Description", DatePrecision.UNKNOWN),
            Arguments.of(1L, "x".repeat(251), "Description", DatePrecision.UNKNOWN),
            Arguments.of(1L, "Title", null, DatePrecision.UNKNOWN),
            Arguments.of(1L, "Title", "\t", DatePrecision.UNKNOWN),
            Arguments.of(1L, "Title", "Description", null));
    }

    @ParameterizedTest @MethodSource("invalidRequiredFields")
    void validatesRequiredFields(Long village, String title, String description, DatePrecision precision) {
        var request = new HistoricalEventRequest(village, title, description, null, null, null, precision);
        assertFalse(validator.validate(request).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> mapper.toEntity(request));
        var entity = mapper.toEntity(request(DatePrecision.UNKNOWN, null, null, null));
        ReflectionTestUtils.setField(entity, "villageId", village);
        ReflectionTestUtils.setField(entity, "title", title);
        ReflectionTestUtils.setField(entity, "description", description);
        ReflectionTestUtils.setField(entity, "datePrecision", precision);
        assertFalse(validator.validate(entity).isEmpty());
    }

    @Test void mapsAllFieldsAndPreservesServerManagedValuesDuringRevision() {
        var request = new HistoricalEventRequest(7L, "x".repeat(250), "Description",
                null, 1, 2200, DatePrecision.PERIOD);
        var entity = mapper.toEntity(request);
        assertTrue(validator.validate(entity).isEmpty());
        assertNull(entity.getId());
        assertNull(entity.getCreatedAt()); // assigned by PostgreSQL
        assertEquals(VerificationStatus.DRAFT, entity.getVerificationStatus());
        Instant created = Instant.parse("2020-01-01T00:00:00Z");
        ReflectionTestUtils.setField(entity, "id", 42L);
        ReflectionTestUtils.setField(entity, "createdAt", created);
        assertEquals(new HistoricalEventResponse(42L, 7L, request.title(), "Description", null,
                1, 2200, DatePrecision.PERIOD, VerificationStatus.DRAFT, created), mapper.toResponse(entity));
        mapper.updateDraft(new HistoricalEventRequest(7L, "Revised", "New description",
                LocalDate.of(2300, 1, 1), null, null, DatePrecision.EXACT), entity);
        assertEquals("Revised", entity.getTitle());
        assertNull(entity.getYearFrom());
        assertNull(entity.getYearTo());
        assertEquals(42L, entity.getId());
        assertEquals(created, entity.getCreatedAt());
        assertEquals(VerificationStatus.DRAFT, entity.getVerificationStatus());
    }

    @ParameterizedTest @EnumSource(value = VerificationStatus.class, names = "DRAFT", mode = EnumSource.Mode.EXCLUDE)
    void blocksEveryNonDraftMutation(VerificationStatus status) {
        var entity = mapper.toEntity(request(DatePrecision.YEAR, null, 1948, null));
        ReflectionTestUtils.setField(entity, "verificationStatus", status);
        assertTrue(validator.validate(entity).isEmpty());
        assertEquals(status, mapper.toResponse(entity).verificationStatus());
        assertThrows(IllegalStateException.class, entity::requireDraftMutation);
        assertThrows(IllegalStateException.class, () -> mapper.updateDraft(
                request(DatePrecision.UNKNOWN, null, null, null), entity));
        assertEquals(1948, entity.getYearFrom());
    }

    @Test void rejectsVillageReassignmentAndInvalidRevisionWithoutPartialMutation() {
        var entity = mapper.toEntity(request(DatePrecision.YEAR, null, 1948, null));
        assertDoesNotThrow(entity::requireDraftMutation);
        assertThrows(IllegalArgumentException.class, () -> entity.reviseDraft(
                new HistoricalEventRequest(2L, "New", "Description", null, null, null, DatePrecision.UNKNOWN)));
        assertThrows(IllegalArgumentException.class, () -> entity.reviseDraft(
                request(DatePrecision.PERIOD, null, 2000, 1900)));
        assertEquals(1948, entity.getYearFrom());
        assertEquals(1L, entity.getVillageId());
        ReflectionTestUtils.setField(entity, "verificationStatus", null);
        assertFalse(validator.validate(entity).isEmpty());
        assertThrows(IllegalStateException.class, entity::requireDraftMutation);
    }

    @Test void requestHasNoServerManagedFieldsOrEntitySetters() {
        assertEquals(Set.of("villageId", "title", "description", "eventDate", "yearFrom", "yearTo", "datePrecision"),
                new HashSet<>(Arrays.stream(HistoricalEventRequest.class.getRecordComponents()).map(c -> c.getName()).toList()));
        assertTrue(Arrays.stream(HistoricalEvent.class.getMethods()).noneMatch(m -> m.getName().startsWith("set")));
        assertEquals(Set.of("DRAFT", "UNDER_REVIEW", "VERIFIED", "REJECTED", "DISPUTED"),
                new HashSet<>(Arrays.stream(VerificationStatus.values()).map(Enum::name).toList()));
    }

    private static HistoricalEventRequest request(DatePrecision precision, LocalDate date, Integer from, Integer to) {
        return new HistoricalEventRequest(1L, "Title", "Description", date, from, to, precision);
    }
}
