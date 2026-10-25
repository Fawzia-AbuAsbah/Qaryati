package com.qaryati.population.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.time.LocalDate;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class PopulationRecordTest {
    static Stream<Object[]> invalidObservations() {
        return Stream.of(
                new Object[]{null, 2020, null, 1, Methodology.CENSUS, 2L},
                new Object[]{0L, 2020, null, 1, Methodology.CENSUS, 2L},
                new Object[]{-1L, 2020, null, 1, Methodology.CENSUS, 2L},
                new Object[]{1L, null, null, 1, Methodology.CENSUS, 2L},
                new Object[]{1L, 1799, null, 1, Methodology.CENSUS, 2L},
                new Object[]{1L, 2201, null, 1, Methodology.CENSUS, 2L},
                new Object[]{1L, 2020, LocalDate.of(2021, 1, 1), 1, Methodology.CENSUS, 2L},
                new Object[]{1L, 2020, null, null, Methodology.CENSUS, 2L},
                new Object[]{1L, 2020, null, -1, Methodology.CENSUS, 2L},
                new Object[]{1L, 2020, null, 1, null, 2L},
                new Object[]{1L, 2020, null, 1, Methodology.CENSUS, null},
                new Object[]{1L, 2020, null, 1, Methodology.CENSUS, 0L},
                new Object[]{1L, 2020, null, 1, Methodology.CENSUS, -1L});
    }

    @ParameterizedTest
    @MethodSource("invalidObservations")
    void constructionAndReplacementRejectEachInvalidFieldWithoutPartialMutation(
            Long village, Integer year, LocalDate date, Integer count, Methodology method, Long source) {
        assertThrows(IllegalArgumentException.class,
                () -> new PopulationRecord(village, year, date, count, method, source, "invalid"));
        PopulationRecord existing = new PopulationRecord(1L, 2020, null, 100, Methodology.CENSUS, 2L, "original");
        assertThrows(IllegalArgumentException.class,
                () -> existing.replaceObservation(village, year, date, count, method, source, "invalid"));
        assertEquals(1L, existing.getVillageId());
        assertEquals(2L, existing.getSourceId());
        assertEquals(2020, existing.getReferenceYear());
        assertNull(existing.getReferenceDate());
        assertEquals(100, existing.getPopulationCount());
        assertEquals(Methodology.CENSUS, existing.getMethodology());
        assertEquals("original", existing.getNotes());
        assertEquals(VerificationStatus.DRAFT, existing.getVerificationStatus());
    }

    @Test
    void draftAllowsDeletionEligibilityAndHasNoClientSettersForServerFields() {
        PopulationRecord record = new PopulationRecord(1L, 2020, null, 0, Methodology.OTHER, 2L, null);
        assertDoesNotThrow(record::requireDraft);
        assertDoesNotThrow(record::guardDeletion);
        assertNull(record.getId());
        assertNull(record.getCreatedAt());
        assertNull(record.getUpdatedAt());
        for (var method : PopulationRecord.class.getMethods()) {
            assertFalse(java.util.Set.of("setId", "setCreatedAt", "setUpdatedAt", "setVerificationStatus")
                    .contains(method.getName()));
        }
    }
}
