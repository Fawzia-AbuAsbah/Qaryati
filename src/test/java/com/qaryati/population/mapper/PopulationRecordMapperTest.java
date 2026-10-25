package com.qaryati.population.mapper;

import com.qaryati.common.exception.DuplicateResourceException;
import com.qaryati.population.dto.*;
import com.qaryati.population.entity.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class PopulationRecordMapperTest {
    private final PopulationRecordMapper mapper = new PopulationRecordMapper();

    @Test
    void mapsAllBusinessFieldsAndKeepsServerFieldsUnassigned() {
        PopulationRecordRequest request = request(2020, 100, LocalDate.of(2020, 6, 1));
        PopulationRecordResponse response = mapper.toResponse(mapper.toEntity(request));
        assertEquals(new PopulationRecordResponse(null, 1L, 2020, request.referenceDate(), 100,
                Methodology.CENSUS, 2L, "notes", VerificationStatus.DRAFT, null, null), response);
    }

    @Test
    void updatesDraftBusinessFieldsIncludingClearingOptionalValues() {
        PopulationRecord record = mapper.toEntity(request(2020, 100, LocalDate.of(2020, 1, 1)));
        mapper.updateEntity(new PopulationRecordRequest(3L, 2021, null, 0, Methodology.OTHER, 4L, null), record);
        assertEquals(3L, record.getVillageId());
        assertEquals(4L, record.getSourceId());
        assertEquals(2021, record.getReferenceYear());
        assertEquals(0, record.getPopulationCount());
        assertEquals(Methodology.OTHER, record.getMethodology());
        assertNull(record.getReferenceDate());
        assertNull(record.getNotes());
        assertEquals(VerificationStatus.DRAFT, record.getVerificationStatus());
    }

    @ParameterizedTest
    @EnumSource(value = VerificationStatus.class, names = "DRAFT", mode = EnumSource.Mode.EXCLUDE)
    void refusesChangesAndDeletionEligibilityForNonDrafts(VerificationStatus status) throws Exception {
        PopulationRecord record = mapper.toEntity(request(2020, 100, null));
        // Simulate hydration of a reviewed row without adding a public workflow setter.
        var field = PopulationRecord.class.getDeclaredField("verificationStatus");
        field.setAccessible(true);
        field.set(record, status);
        assertThrows(DuplicateResourceException.class, record::requireDraft);
        assertThrows(DuplicateResourceException.class,
                () -> mapper.updateEntity(request(2021, 200, null), record));
        assertEquals(2020, record.getReferenceYear());
        assertEquals(100, record.getPopulationCount());
        assertEquals(status, record.getVerificationStatus());
    }

    @ParameterizedTest
    @ValueSource(ints = {1800, 2200})
    void acceptsYearBoundariesAndZeroCount(int year) {
        assertValid(request(year, 0, LocalDate.of(year, 1, 1)));
        assertValid(request(year, Integer.MAX_VALUE, null));
    }

    @ParameterizedTest
    @ValueSource(ints = {1799, 2201})
    void rejectsOutOfRangeYear(int year) {
        assertInvalid(request(year, 1, null));
        assertThrows(IllegalArgumentException.class, () -> mapper.toEntity(request(year, 1, null)));
    }

    @Test
    void rejectsMissingAndInvalidBusinessFields() {
        assertInvalid(new PopulationRecordRequest(null, null, null, null, null, null, null));
        assertInvalid(new PopulationRecordRequest(0L, 2020, null, -1, Methodology.CENSUS, -1L, null));
        assertInvalid(request(2020, 1, LocalDate.of(2021, 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> mapper.toEntity(request(2020, -1, null)));
        assertThrows(IllegalArgumentException.class,
                () -> mapper.toEntity(request(2020, 1, LocalDate.of(2021, 1, 1))));
    }

    @ParameterizedTest
    @EnumSource(Methodology.class)
    void acceptsEverySchemaMethodology(Methodology methodology) {
        assertValid(new PopulationRecordRequest(1L, 2020, null, 1, methodology, 2L, null));
    }

    private PopulationRecordRequest request(int year, int count, LocalDate date) {
        return new PopulationRecordRequest(1L, year, date, count, Methodology.CENSUS, 2L, "notes");
    }

    private void assertValid(PopulationRecordRequest request) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(request).isEmpty());
        }
    }

    private void assertInvalid(PopulationRecordRequest request) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertFalse(factory.getValidator().validate(request).isEmpty());
        }
    }
}
