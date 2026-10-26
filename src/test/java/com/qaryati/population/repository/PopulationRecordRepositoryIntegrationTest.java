package com.qaryati.population.repository;

import com.qaryati.common.exception.DuplicateResourceException;
import com.qaryati.population.entity.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Real V1 schema, dedicated PostgreSQL database, rollback after every invocation. */
@SpringBootTest(properties = "spring.flyway.clean-disabled=true")
@ActiveProfiles("test")
@ContextConfiguration(initializers = PopulationTestDatabaseGuard.class)
@Transactional
class PopulationRecordRepositoryIntegrationTest {
    @Autowired private PopulationRecordRepository repository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    private Long villageId;
    private Long sourceId;

    @BeforeEach
    void createIsolatedFixturesAfterCheckingDatabaseIdentity() {
        assertEquals("qaryati_test_db", jdbc.queryForObject("select current_database()", String.class));
        Long governorateId = jdbc.queryForObject(
                "insert into public.governorates (name_ar) values (?) returning id", Long.class, unique("governorate"));
        villageId = jdbc.queryForObject("""
                insert into public.villages (governorate_id, name_ar, location_description)
                values (?, ?, ?) returning id
                """, Long.class, governorateId, unique("village"), "Population test fixture");
        sourceId = newSource();
    }

    @Test
    void createsAndRetrievesAllFieldsWithDraftStatusAndTimestamps() {
        Instant before = Instant.now().minusSeconds(1);
        Long id = repository.saveAndFlush(record(2020, 100)).getId();
        entityManager.clear();
        PopulationRecord found = repository.findById(id).orElseThrow();
        assertEquals(villageId, found.getVillageId());
        assertEquals(sourceId, found.getSourceId());
        assertEquals(2020, found.getReferenceYear());
        assertEquals(LocalDate.of(2020, 6, 1), found.getReferenceDate());
        assertEquals(100, found.getPopulationCount());
        assertEquals(Methodology.CENSUS, found.getMethodology());
        assertEquals("fixture notes", found.getNotes());
        assertEquals(VerificationStatus.DRAFT, found.getVerificationStatus());
        assertNotNull(found.getCreatedAt());
        assertEquals(found.getCreatedAt(), found.getUpdatedAt());
        assertFalse(found.getCreatedAt().isBefore(before));
        assertFalse(found.getCreatedAt().isAfter(Instant.now().plusSeconds(1)));
    }

    @Test
    void updatesDraftAndPreservesCreationTimestamp() {
        Long id = repository.saveAndFlush(record(2020, 100)).getId();
        // Use an older deterministic database timestamp: no sleeps or clock-resolution assumptions.
        Instant old = Instant.parse("2000-01-01T00:00:00Z");
        jdbc.update("update public.population_records set created_at=?, updated_at=? where id=?",
                Timestamp.from(old), Timestamp.from(old), id);
        entityManager.clear();
        PopulationRecord found = repository.findByIdForUpdate(id).orElseThrow();
        assertEquals(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE, entityManager.getLockMode(found));
        found.replaceObservation(villageId, 2021, null, 200, Methodology.ESTIMATE, sourceId, null);
        repository.saveAndFlush(found);
        entityManager.clear();
        PopulationRecord updated = repository.findById(id).orElseThrow();
        assertEquals(2021, updated.getReferenceYear());
        assertEquals(200, updated.getPopulationCount());
        assertEquals(Methodology.ESTIMATE, updated.getMethodology());
        assertNull(updated.getReferenceDate());
        assertNull(updated.getNotes());
        assertEquals(old, updated.getCreatedAt());
        assertTrue(updated.getUpdatedAt().isAfter(old));
        assertEquals(VerificationStatus.DRAFT, updated.getVerificationStatus());
    }

    @Test
    void deletesDraftAndKeepsParentFixtures() {
        Long id = repository.saveAndFlush(record(2020, 1)).getId();
        repository.delete(repository.findByIdForUpdate(id).orElseThrow());
        repository.flush();
        entityManager.clear();
        assertTrue(repository.findById(id).isEmpty());
        assertEquals(1, jdbc.queryForObject("select count(*) from public.villages where id=?", Integer.class, villageId));
        assertEquals(1, jdbc.queryForObject("select count(*) from public.sources where id=?", Integer.class, sourceId));
    }

    @Test
    void missingRecordAndLockLookupReturnEmpty() {
        assertTrue(repository.findById(Long.MAX_VALUE).isEmpty());
        assertTrue(repository.findByIdForUpdate(Long.MAX_VALUE).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"village_id", "source_id", "reference_year", "population_count", "methodology",
            "verification_status", "created_at", "updated_at"})
    void databaseRejectsNullRequiredColumns(String column) {
        Map<String, Object> row = validRow();
        row.put(column, null);
        assertSqlState("23502", assertThrows(DataIntegrityViolationException.class, () -> insert(row)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"village_id", "source_id"})
    void databaseRejectsMissingForeignKeys(String column) {
        Map<String, Object> row = validRow();
        row.put(column, Long.MAX_VALUE);
        assertSqlState("23503", assertThrows(DataIntegrityViolationException.class, () -> insert(row)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"villages", "sources"})
    void databaseRestrictsDeletingReferencedParents(String table) {
        repository.saveAndFlush(record(2020, 1));
        Long parentId = table.equals("villages") ? villageId : sourceId;
        assertSqlState("23503", assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("delete from public." + table + " where id=?", parentId)));
    }

    @ParameterizedTest
    @ValueSource(ints = {1799, 2201})
    void databaseRejectsOutOfRangeYears(int year) {
        Map<String, Object> row = validRow();
        row.put("reference_year", year);
        assertSqlState("23514", assertThrows(DataIntegrityViolationException.class, () -> insert(row)));
    }

    @ParameterizedTest
    @ValueSource(ints = {1800, 2200})
    void databaseAcceptsBoundaryYearsAndCounts(int year) {
        int count = year == 1800 ? 0 : Integer.MAX_VALUE;
        PopulationRecord saved = repository.saveAndFlush(record(year, count));
        entityManager.clear();
        PopulationRecord found = repository.findById(saved.getId()).orElseThrow();
        assertEquals(year, found.getReferenceYear());
        assertEquals(count, found.getPopulationCount());
    }

    @Test
    void updatesSourceAndVillageReferencesAndReloadsEveryChangedField() {
        Long id = repository.saveAndFlush(record(2020, 100)).getId();
        Long otherSource = newSource();
        Long governorateId = jdbc.queryForObject(
                "select governorate_id from public.villages where id=?", Long.class, villageId);
        Long otherVillage = jdbc.queryForObject("""
                insert into public.villages (governorate_id, name_ar, location_description)
                values (?, ?, 'Population fixture') returning id
                """, Long.class, governorateId, unique("replacement-village"));
        entityManager.clear();
        PopulationRecord found = repository.findByIdForUpdate(id).orElseThrow();
        found.replaceObservation(otherVillage, 2024, LocalDate.of(2024, 2, 29),
                321, Methodology.OTHER, otherSource, "Updated observation");
        repository.saveAndFlush(found);
        entityManager.clear();

        PopulationRecord updated = repository.findById(id).orElseThrow();
        assertEquals(otherVillage, updated.getVillageId());
        assertEquals(otherSource, updated.getSourceId());
        assertEquals(2024, updated.getReferenceYear());
        assertEquals(LocalDate.of(2024, 2, 29), updated.getReferenceDate());
        assertEquals(321, updated.getPopulationCount());
        assertEquals(Methodology.OTHER, updated.getMethodology());
        assertEquals("Updated observation", updated.getNotes());
        assertEquals(VerificationStatus.DRAFT, updated.getVerificationStatus());
        assertFalse(repository.existsByVillageIdAndReferenceYearAndMethodologyAndSourceId(
                villageId, 2020, Methodology.CENSUS, sourceId));
        assertTrue(repository.existsByVillageIdAndReferenceYearAndMethodologyAndSourceId(
                otherVillage, 2024, Methodology.OTHER, otherSource));
    }

    @Test
    void databaseRejectsNegativeCount() {
        Map<String, Object> row = validRow();
        row.put("population_count", -1);
        assertSqlState("23514", assertThrows(DataIntegrityViolationException.class, () -> insert(row)));
    }

    @Test
    void databaseRejectsReferenceDateYearMismatch() {
        Map<String, Object> row = validRow();
        row.put("reference_date", LocalDate.of(2019, 1, 1));
        assertSqlState("23514", assertThrows(DataIntegrityViolationException.class, () -> insert(row)));
    }

    @Test
    void databaseDefaultsDraftAndTimestampsAndAllowsNullOptionalFields() {
        Long id = jdbc.queryForObject("""
                insert into public.population_records
                (village_id, reference_year, population_count, methodology, source_id)
                values (?, 2020, 0, 'CENSUS', ?) returning id
                """, Long.class, villageId, sourceId);
        PopulationRecord found = repository.findById(id).orElseThrow();
        assertEquals(VerificationStatus.DRAFT, found.getVerificationStatus());
        assertNotNull(found.getCreatedAt());
        assertNotNull(found.getUpdatedAt());
        assertNull(found.getReferenceDate());
        assertNull(found.getNotes());
    }

    @ParameterizedTest
    @EnumSource(Methodology.class)
    void databaseAcceptsEveryMethodology(Methodology methodology) {
        Map<String, Object> row = validRow();
        row.put("methodology", methodology.name());
        assertEquals(methodology, repository.findById(insert(row)).orElseThrow().getMethodology());
    }

    @ParameterizedTest
    @EnumSource(VerificationStatus.class)
    void databaseAcceptsEveryStatusWithoutImplyingReviewAuthorization(VerificationStatus status) {
        Map<String, Object> row = validRow();
        row.put("verification_status", status.name());
        assertEquals(status, repository.findById(insert(row)).orElseThrow().getVerificationStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"INVALID", "census", ""})
    void databaseRejectsInvalidMethodology(String methodology) {
        Map<String, Object> row = validRow();
        row.put("methodology", methodology);
        assertSqlState("23514", assertThrows(DataIntegrityViolationException.class, () -> insert(row)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"INVALID", "draft", ""})
    void databaseRejectsInvalidStatus(String status) {
        Map<String, Object> row = validRow();
        row.put("verification_status", status);
        assertSqlState("23514", assertThrows(DataIntegrityViolationException.class, () -> insert(row)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"methodology", "verification_status"})
    void databaseRejectsOversizedEnumColumns(String column) {
        Map<String, Object> row = validRow();
        row.put(column, "X".repeat(column.equals("methodology") ? 33 : 21));
        assertSqlState("22001", assertThrows(DataIntegrityViolationException.class, () -> insert(row)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"village_id", "source_id"})
    void databaseRejectsReplacingReferenceWithMissingParent(String column) {
        Long id = repository.saveAndFlush(record(2020, 1)).getId();
        assertSqlState("23503", assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("update public.population_records set " + column + "=? where id=?",
                        Long.MAX_VALUE, id)));
    }

    @Test
    void duplicateObservationIsRejectedEvenWithDifferentDateAndCount() {
        repository.saveAndFlush(record(2020, 1));
        Map<String, Object> duplicate = validRow();
        duplicate.put("reference_date", LocalDate.of(2020, 12, 31));
        duplicate.put("population_count", 999);
        assertSqlState("23505", assertThrows(DataIntegrityViolationException.class, () -> insert(duplicate)));
    }

    @Test
    void conflictingSourcesAndDifferentMethodologiesYearsAndVillagesCoexist() {
        repository.saveAndFlush(record(2020, 1));
        Map<String, Object> otherSource = validRow();
        otherSource.put("source_id", newSource());
        otherSource.put("population_count", 999);
        assertNotNull(insert(otherSource));
        Map<String, Object> otherMethod = validRow();
        otherMethod.put("methodology", "ESTIMATE");
        assertNotNull(insert(otherMethod));
        assertNotNull(repository.saveAndFlush(record(2021, 1)).getId());
        Long governorateId = jdbc.queryForObject("select governorate_id from public.villages where id=?", Long.class, villageId);
        Long otherVillage = jdbc.queryForObject("""
                insert into public.villages (governorate_id, name_ar, location_description)
                values (?, ?, 'Population fixture') returning id
                """, Long.class, governorateId, unique("other-village"));
        Map<String, Object> otherVillageRow = validRow();
        otherVillageRow.put("village_id", otherVillage);
        assertNotNull(insert(otherVillageRow));
    }

    @Test
    void uniquenessQueriesDetectCompositeKeyAndExcludeCurrentId() {
        PopulationRecord first = repository.saveAndFlush(record(2020, 1));
        PopulationRecord second = repository.saveAndFlush(record(2021, 1));
        assertTrue(repository.existsByVillageIdAndReferenceYearAndMethodologyAndSourceId(villageId, 2020, Methodology.CENSUS, sourceId));
        assertFalse(repository.existsByVillageIdAndReferenceYearAndMethodologyAndSourceId(villageId, 2019, Methodology.CENSUS, sourceId));
        assertFalse(repository.existsByVillageIdAndReferenceYearAndMethodologyAndSourceId(villageId, 2020, Methodology.OTHER, sourceId));
        assertFalse(repository.existsByVillageIdAndReferenceYearAndMethodologyAndSourceId(villageId, 2020, Methodology.CENSUS, Long.MAX_VALUE));
        assertFalse(repository.existsByVillageIdAndReferenceYearAndMethodologyAndSourceId(Long.MAX_VALUE, 2020, Methodology.CENSUS, sourceId));
        assertFalse(repository.existsByVillageIdAndReferenceYearAndMethodologyAndSourceIdAndIdNot(villageId, 2020, Methodology.CENSUS, sourceId, first.getId()));
        assertTrue(repository.existsByVillageIdAndReferenceYearAndMethodologyAndSourceIdAndIdNot(villageId, 2020, Methodology.CENSUS, sourceId, second.getId()));
        assertFalse(repository.existsByVillageIdAndReferenceYearAndMethodologyAndSourceIdAndIdNot(villageId, 2019, Methodology.CENSUS, sourceId, second.getId()));
        assertFalse(repository.existsByVillageIdAndReferenceYearAndMethodologyAndSourceIdAndIdNot(villageId, 2020, Methodology.OTHER, sourceId, second.getId()));
        assertFalse(repository.existsByVillageIdAndReferenceYearAndMethodologyAndSourceIdAndIdNot(villageId, 2020, Methodology.CENSUS, Long.MAX_VALUE, second.getId()));
        assertFalse(repository.existsByVillageIdAndReferenceYearAndMethodologyAndSourceIdAndIdNot(Long.MAX_VALUE, 2020, Methodology.CENSUS, sourceId, second.getId()));
    }

    @Test
    void databaseRejectsUpdateToAnotherObservationKey() {
        repository.saveAndFlush(record(2020, 1));
        PopulationRecord second = repository.saveAndFlush(record(2021, 2));
        second.replaceObservation(villageId, 2020, null, 2, Methodology.CENSUS, sourceId, null);
        assertSqlState("23505", assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(second)));
    }

    @ParameterizedTest
    @EnumSource(value = VerificationStatus.class, names = "DRAFT", mode = EnumSource.Mode.EXCLUDE)
    void hydratedNonDraftRecordsRejectModificationAndDeletionEligibility(VerificationStatus status) {
        Map<String, Object> row = validRow();
        row.put("verification_status", status.name());
        Long id = insert(row);
        PopulationRecord record = repository.findByIdForUpdate(id).orElseThrow();
        assertThrows(DuplicateResourceException.class, record::requireDraft);
        assertThrows(DuplicateResourceException.class, () -> record.replaceObservation(
                villageId, 2021, null, 999, Methodology.OTHER, sourceId, null));
        assertEquals(2020, record.getReferenceYear());
        assertEquals(status, record.getVerificationStatus());
        entityManager.clear();
        assertEquals(100, repository.findById(id).orElseThrow().getPopulationCount());
    }

    @ParameterizedTest
    @EnumSource(value = VerificationStatus.class, names = "DRAFT", mode = EnumSource.Mode.EXCLUDE)
    void jpaDeletionCallbackRejectsNonDraftRecords(VerificationStatus status) {
        Map<String, Object> row = validRow();
        row.put("verification_status", status.name());
        PopulationRecord record = repository.findById(insert(row)).orElseThrow();
        assertThrows(DuplicateResourceException.class, () -> repository.delete(record));
        // Do not query after a failed persistence operation; transaction is rolled back.
    }

    @Test
    void mapperResponseIncludesPersistedServerFields() {
        Long id = repository.saveAndFlush(record(2020, 1)).getId();
        entityManager.clear();
        PopulationRecord record = repository.findById(id).orElseThrow();
        var response = new com.qaryati.population.mapper.PopulationRecordMapper().toResponse(record);
        assertEquals(id, response.id());
        assertEquals(record.getCreatedAt(), response.createdAt());
        assertEquals(record.getUpdatedAt(), response.updatedAt());
        assertEquals(VerificationStatus.DRAFT, response.verificationStatus());
    }

    private PopulationRecord record(int year, int count) {
        return new PopulationRecord(villageId, year, LocalDate.of(year, 6, 1), count,
                Methodology.CENSUS, sourceId, "fixture notes");
    }

    private Long newSource() {
        return jdbc.queryForObject("insert into public.sources (title, source_type) values (?, 'CENSUS') returning id",
                Long.class, unique("source"));
    }

    private Map<String, Object> validRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("village_id", villageId);
        row.put("reference_year", 2020);
        row.put("reference_date", null);
        row.put("population_count", 100);
        row.put("methodology", "CENSUS");
        row.put("source_id", sourceId);
        row.put("notes", null);
        row.put("verification_status", "DRAFT");
        row.put("created_at", Timestamp.from(Instant.now().truncatedTo(ChronoUnit.MICROS)));
        row.put("updated_at", row.get("created_at"));
        return row;
    }

    /** JDBC deliberately bypasses entity validation to exercise actual PostgreSQL constraints. */
    private Long insert(Map<String, Object> row) {
        String columns = String.join(",", row.keySet());
        String placeholders = String.join(",", java.util.Collections.nCopies(row.size(), "?"));
        return jdbc.queryForObject("insert into public.population_records (" + columns + ") values ("
                + placeholders + ") returning id", Long.class, row.values().toArray());
    }

    private String unique(String prefix) { return "population-test-" + prefix + "-" + UUID.randomUUID(); }

    private void assertSqlState(String expected, Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                assertEquals(expected, sql.getSQLState());
                return;
            }
        }
        fail("Expected PostgreSQL SQLSTATE " + expected, exception);
        // A constraint failure aborts the PostgreSQL transaction. No further SQL in that invocation.
    }
}
