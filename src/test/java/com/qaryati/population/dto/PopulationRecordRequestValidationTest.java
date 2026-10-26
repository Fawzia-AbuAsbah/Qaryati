package com.qaryati.population.dto;

import com.qaryati.population.entity.Methodology;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.time.LocalDate;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class PopulationRecordRequestValidationTest {
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void initializeValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() { factory.close(); }

    static Stream<Object[]> invalidFields() {
        return Stream.of(
                invalid("villageId", null, 2020, null, 1, Methodology.CENSUS, 2L),
                invalid("villageId", 0L, 2020, null, 1, Methodology.CENSUS, 2L),
                invalid("sourceId", 1L, 2020, null, 1, Methodology.CENSUS, null),
                invalid("sourceId", 1L, 2020, null, 1, Methodology.CENSUS, -1L),
                invalid("referenceYear", 1L, null, null, 1, Methodology.CENSUS, 2L),
                invalid("referenceYear", 1L, 1799, null, 1, Methodology.CENSUS, 2L),
                invalid("referenceYear", 1L, 2201, null, 1, Methodology.CENSUS, 2L),
                invalid("populationCount", 1L, 2020, null, null, Methodology.CENSUS, 2L),
                invalid("populationCount", 1L, 2020, null, -1, Methodology.CENSUS, 2L),
                invalid("methodology", 1L, 2020, null, 1, null, 2L),
                invalid("referenceDateConsistent", 1L, 2020, LocalDate.of(2019, 1, 1), 1, Methodology.CENSUS, 2L));
    }

    @ParameterizedTest
    @MethodSource("invalidFields")
    void identifiesEachInvalidField(String field, PopulationRecordRequest request) {
        var violations = validator.validate(request);
        assertEquals(1, violations.size());
        assertEquals(field, violations.iterator().next().getPropertyPath().toString());
    }

    @Test
    void missingYearIsReportedWithoutDuplicateDateConsistencyError() {
        var request = new PopulationRecordRequest(1L, null, LocalDate.of(2020, 1, 1), 0,
                Methodology.CENSUS, 2L, null);
        assertEquals(1, validator.validate(request).size());
        assertTrue(request.isReferenceDateConsistent());
    }

    private static Object[] invalid(String field, Long village, Integer year, LocalDate date,
                                    Integer count, Methodology method, Long source) {
        return new Object[]{field, new PopulationRecordRequest(village, year, date, count, method, source, null)};
    }
}
