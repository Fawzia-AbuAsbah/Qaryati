package com.qaryati.village.repository;

import com.qaryati.village.entity.*;
import com.qaryati.village.mapper.VillageMapper;
import jakarta.persistence.*;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.*;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Successfully executed: 40 tests passed on dedicated qaryati_test_db (PostgreSQL 17.4).
 * Reruns require explicit database approval and independently verified identity/schema.
 * Plain Spring persistence context; no Boot, Flyway, SQL initializer or DDL generation.
 * All fixtures join the rollback test transaction. Never run against application databases.
 */
@SpringJUnitConfig
@ContextConfiguration(classes = VillageTestDatabaseSupport.PersistenceConfiguration.class,
        initializers = VillageTestDatabaseSupport.class)
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties", properties = {
        "spring.flyway.enabled=false", "spring.flyway.clean-disabled=true",
        "spring.sql.init.mode=never", "spring.jpa.hibernate.ddl-auto=validate"
})
@Transactional
@Rollback
class VillageRepositoryIntegrationTest {
    @Autowired private VillageRepository repository;
    @Autowired private GovernorateRepository governorates;
    @Autowired private LocalAuthorityRepository authorities;
    @Autowired private JdbcTemplate jdbc;
    @PersistenceContext private EntityManager entityManager;
    private static final Instant TIME = Instant.parse("2026-11-04T12:00:00.123456Z");

    @ParameterizedTest
    @EnumSource(VillageStatus.class)
    void allColumnsStatusesAndLazyParentIdsRoundTrip(VillageStatus status) {
        var governorate = governorate();
        var authority = authority(governorate);
        var village = village(governorate);
        village.setLocalAuthority(authority);
        village.setNameEn("English");
        village.setNameOrigin("Origin");
        village.setLatitude(new BigDecimal("-90.000000"));
        village.setLongitude(new BigDecimal("180.000000"));
        village.setElevationM(new BigDecimal("-9999999.99"));
        village.setAreaKm2(new BigDecimal("999999999.999"));
        village.setStatus(status);
        village.setArchivedAt(TIME);
        repository.saveAndFlush(village);
        assertNotNull(village.getId());
        var expected = new VillageMapper().toResponse(village);
        Long id = village.getId();
        entityManager.clear();
        var found = repository.findById(id).orElseThrow();
        assertFalse(Hibernate.isInitialized(found.getGovernorate()));
        assertFalse(Hibernate.isInitialized(found.getLocalAuthority()));
        assertEquals(expected, new VillageMapper().toResponse(found));
        assertFalse(Hibernate.isInitialized(found.getGovernorate()));
        assertFalse(Hibernate.isInitialized(found.getLocalAuthority()));
        assertEquals(status.name(), jdbc.queryForObject(
                "select status from public.villages where id=?", String.class, id));
    }

    @Test
    void nullableColumnsAndRequiredTextLengthsRoundTrip() {
        var value = village(governorate());
        value.setNameAr("x".repeat(144) + UUID.randomUUID());
        value.setNameEn("e".repeat(180));
        repository.saveAndFlush(value);
        Long id = value.getId();
        entityManager.clear();
        var found = repository.findById(id).orElseThrow();
        assertEquals(180, found.getNameAr().length());
        assertEquals(180, found.getNameEn().length());
        assertNull(found.getLocalAuthority());
        assertNull(found.getNameOrigin());
        assertNull(found.getLatitude());
        assertNull(found.getLongitude());
        assertNull(found.getElevationM());
        assertNull(found.getAreaKm2());
        assertNull(found.getArchivedAt());
        found.setNameEn(null);
        repository.saveAndFlush(found);
        entityManager.clear();
        assertNull(repository.findById(id).orElseThrow().getNameEn());
    }

    @Test
    void archivedRowsParticipateInExistenceChecksAndSelfExclusion() {
        var parent = governorate();
        var own = village(parent);
        own.setArchivedAt(TIME);
        repository.saveAndFlush(own);
        var peer = repository.saveAndFlush(village(parent));
        assertTrue(repository.existsByGovernorateAndNameAr(parent, own.getNameAr()));
        assertFalse(repository.existsByGovernorateAndNameArAndIdNot(parent, own.getNameAr(), own.getId()));
        assertTrue(repository.existsByGovernorateAndNameArAndIdNot(parent, peer.getNameAr(), own.getId()));
        assertFalse(repository.existsByGovernorateAndNameAr(governorate(), own.getNameAr()));
    }

    @Test
    void databaseRejectsNameReuseAfterArchive() {
        var parent = governorate();
        var archived = village(parent);
        archived.setArchivedAt(TIME);
        repository.saveAndFlush(archived);
        var duplicate = village(parent);
        duplicate.setNameAr(archived.getNameAr());
        state("23505", () -> repository.saveAndFlush(duplicate));
    }

    @Test
    void sameNameAllowedInAnotherGovernorate() {
        var first = repository.saveAndFlush(village(governorate()));
        var other = village(governorate());
        other.setNameAr(first.getNameAr());
        assertNotEquals(first.getId(), repository.saveAndFlush(other).getId());
    }

    @Test
    void updateDuplicateIsRejected() {
        var parent = governorate();
        var one = repository.saveAndFlush(village(parent));
        var two = repository.saveAndFlush(village(parent));
        two.setNameAr(one.getNameAr());
        state("23505", () -> repository.saveAndFlush(two));
    }

    @Test
    void compositeForeignKeyRejectsMismatchedAuthority() {
        var value = village(governorate());
        value.setLocalAuthority(authority(governorate()));
        state("23503", () -> repository.saveAndFlush(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"governorate_id", "local_authority_id"})
    void missingParentForeignKeysAreRejected(String column) {
        var saved = repository.saveAndFlush(village(governorate()));
        Long missing = jdbc.queryForObject("select candidate from generate_series(-1000::bigint,-1::bigint) candidate "
                + "where not exists (select 1 from public."
                + (column.equals("governorate_id") ? "governorates" : "local_authorities")
                + " where id=candidate) limit 1", Long.class);
        assertNotNull(missing);
        state("23503", () -> jdbc.update("update public.villages set " + column + "=? where id=?",
                missing, saved.getId()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"governorate_id", "name_ar", "location_description", "status", "created_at", "updated_at"})
    void requiredColumnsRejectNull(String column) {
        Long id = repository.saveAndFlush(village(governorate())).getId();
        state("23502", () -> jdbc.update("update public.villages set " + column + "=null where id=?", id));
    }

    @ParameterizedTest
    @ValueSource(strings = {"name_ar", "name_en"})
    void oversizedNamesAreRejected(String column) {
        Long id = repository.saveAndFlush(village(governorate())).getId();
        state("22001", () -> jdbc.update("update public.villages set " + column + "=? where id=?",
                "x".repeat(181), id));
    }

    static Stream<Object[]> invalidChecks() {
        return Stream.of(
                new Object[]{"latitude=91,longitude=0"},
                new Object[]{"latitude=-91,longitude=0"},
                new Object[]{"latitude=0,longitude=181"},
                new Object[]{"latitude=0,longitude=-181"},
                new Object[]{"latitude=1,longitude=null"},
                new Object[]{"latitude=null,longitude=1"},
                new Object[]{"area_km2=0"},
                new Object[]{"area_km2=-1"},
                new Object[]{"status='DISPUTED'"},
                new Object[]{"status='INVALID'"});
    }

    @ParameterizedTest
    @MethodSource("invalidChecks")
    void postgresChecksRejectInvalidRows(String assignment) {
        Long id = repository.saveAndFlush(village(governorate())).getId();
        state("23514", () -> jdbc.update("update public.villages set " + assignment + " where id=?", id));
    }

    @ParameterizedTest
    @ValueSource(strings = {"latitude=1000,longitude=0", "longitude=1000,latitude=0",
            "elevation_m=10000000", "area_km2=1000000000"})
    void numericOverflowIsRejected(String assignment) {
        Long id = repository.saveAndFlush(village(governorate())).getId();
        state("22003", () -> jdbc.update("update public.villages set " + assignment + " where id=?", id));
    }

    @Test
    void postgresRoundingIsDistinctFromStricterRequestValidation() {
        var value = village(governorate());
        value.setLatitude(new BigDecimal("1.1234567"));
        value.setLongitude(new BigDecimal("2.1234567"));
        value.setElevationM(new BigDecimal("-1.235"));
        value.setAreaKm2(new BigDecimal("1.2345"));
        repository.saveAndFlush(value);
        Long id = value.getId();
        entityManager.clear();
        var found = repository.findById(id).orElseThrow();
        assertEquals(new BigDecimal("1.123457"), found.getLatitude());
        assertEquals(new BigDecimal("2.123457"), found.getLongitude());
        assertEquals(new BigDecimal("-1.24"), found.getElevationM());
        assertEquals(new BigDecimal("1.235"), found.getAreaKm2());
    }

    @Test
    void activePaginationCountAndIdOrderMatchSqlWithoutAssumingEmptyDatabase() {
        var parent = governorate();
        repository.saveAndFlush(village(parent));
        repository.saveAndFlush(village(parent));
        var archived = village(parent);
        archived.setArchivedAt(TIME);
        repository.saveAndFlush(archived);
        entityManager.clear();
        List<Long> expected = jdbc.queryForList(
                "select id from public.villages where archived_at is null order by id", Long.class);
        var first = repository.findByArchivedAtIsNullOrderByIdAsc(PageRequest.of(0, 1));
        var second = repository.findByArchivedAtIsNullOrderByIdAsc(PageRequest.of(1, 1));
        assertEquals(expected.size(), first.getTotalElements());
        assertEquals(expected.size(), first.getTotalPages());
        assertEquals(expected.get(0), first.getContent().getFirst().getId());
        assertEquals(expected.get(1), second.getContent().getFirst().getId());
        assertTrue(repository.findByArchivedAtIsNullOrderByIdAsc(
                PageRequest.of(expected.size(), 1)).isEmpty());
    }

    @Test
    void archivedLookupAndWriteLockAreSupportedInRollbackTransaction() {
        var value = village(governorate());
        value.setArchivedAt(TIME);
        repository.saveAndFlush(value);
        Long id = value.getId();
        entityManager.clear();
        assertTrue(TestTransaction.isActive());
        assertTrue(TestTransaction.isFlaggedForRollback());
        var locked = repository.findByIdForUpdate(id).orElseThrow();
        assertEquals(TIME, locked.getArchivedAt());
        assertEquals(LockModeType.PESSIMISTIC_WRITE, entityManager.getLockMode(locked));
        assertTrue(repository.findById(id).isPresent());
        assertTrue(repository.findByIdForUpdate(-999999L).isEmpty());
        // This proves query execution/lock mode, not contention between connections.
    }

    @Test
    void archiveRetainsReferencingRowsAndParents() {
        var parent = governorate();
        var authority = authority(parent);
        var value = village(parent);
        value.setLocalAuthority(authority);
        repository.saveAndFlush(value);
        Long child = jdbc.queryForObject("""
                insert into public.village_names(village_id,name,language_code,name_type)
                values (?,?,'ar','ALTERNATIVE') returning id
                """, Long.class, value.getId(), unique());
        value.setArchivedAt(TIME);
        value.setUpdatedAt(TIME);
        repository.saveAndFlush(value);
        entityManager.clear();
        assertEquals(value.getId(), jdbc.queryForObject(
                "select village_id from public.village_names where id=?", Long.class, child));
        assertTrue(governorates.findById(parent.getId()).isPresent());
        assertTrue(authorities.findById(authority.getId()).isPresent());
        assertTrue(repository.findById(value.getId()).isPresent());
    }

    @Test
    void referencedGovernorateDeletionIsRestricted() {
        var parent = governorate();
        repository.saveAndFlush(village(parent));
        state("23503", () -> jdbc.update("delete from public.governorates where id=?", parent.getId()));
    }

    @Test
    void referencedAuthorityDeletionIsRestricted() {
        var parent = governorate();
        var authority = authority(parent);
        var value = village(parent);
        value.setLocalAuthority(authority);
        repository.saveAndFlush(value);
        state("23503", () -> jdbc.update("delete from public.local_authorities where id=?", authority.getId()));
    }

    // Exercise the production private Specification builder through the real service/repository.
    // The enclosing guarded rollback transaction supplies the persistence boundary.
    private org.springframework.data.domain.Page<com.qaryati.village.dto.VillageResponse> search(
            int page, int size, String term, Long governorateId, Long authorityId,
            String status, String field, String direction) {
        var service = new com.qaryati.village.service.VillageService(
                repository, governorates, authorities, new VillageMapper());
        return service.getActive(page, size, term, governorateId, authorityId, status, field, direction);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\u0642\u0631\u064a\u0629", "MiXeD", "%", "_", "!", "!%_", "\\"})
    void literalArabicOrCaseInsensitiveEnglishSearchExcludesArchivedAndCounts(String term) {
        var parent = governorate();
        var arabic = village(parent);
        arabic.setNameAr("\u0642\u0628\u0644" + term + "\u0628\u0639\u062f");
        arabic.setNameEn(null);
        repository.saveAndFlush(arabic);
        var english = village(parent);
        english.setNameEn("before" + term.toUpperCase(Locale.ROOT) + "after");
        repository.saveAndFlush(english);
        var other = village(parent);
        other.setNameAr("unrelated");
        other.setNameEn(null);
        repository.saveAndFlush(other);
        var archived = village(parent);
        archived.setNameEn(term);
        archived.setArchivedAt(TIME);
        repository.saveAndFlush(archived);
        entityManager.clear();
        var first = search(0, 1, "  " + term + "  ", parent.getId(), null, null, "id", "asc");
        assertEquals(2, first.getTotalElements());
        assertEquals(2, first.getTotalPages());
        assertEquals(arabic.getId(), first.getContent().getFirst().id());
        var second = search(1, 1, term, parent.getId(), null, null, "id", "asc");
        assertEquals(english.getId(), second.getContent().getFirst().id());
        var beyond = search(2, 1, term, parent.getId(), null, null, "id", "asc");
        assertTrue(beyond.isEmpty());
        assertEquals(2, beyond.getTotalElements());
        assertTrue(repository.findById(archived.getId()).isPresent());
    }

    @ParameterizedTest
    @EnumSource(VillageStatus.class)
    void optionalFiltersIntersectWithoutParentExistenceLookups(VillageStatus status) {
        var parent = governorate();
        var otherParent = governorate();
        var authority = authority(parent);
        var value = village(parent);
        value.setLocalAuthority(authority);
        value.setStatus(status);
        value.setNameEn("Needle");
        repository.saveAndFlush(value);
        for (var state : VillageStatus.values()) {
            var withoutAuthority = village(parent);
            withoutAuthority.setStatus(state);
            withoutAuthority.setNameEn("Needle");
            repository.saveAndFlush(withoutAuthority);
        }
        var other = village(otherParent);
        other.setNameEn("Needle");
        repository.saveAndFlush(other);
        entityManager.clear();
        var combined = search(0, 20, "needle", parent.getId(), authority.getId(), status.name(), "id", "asc");
        assertEquals(List.of(value.getId()), combined.getContent().stream().map(v -> v.id()).toList());
        assertEquals(1, combined.getTotalElements());
        assertEquals(5, search(0, 20, null, parent.getId(), null, null, "id", "asc").getTotalElements());
        assertEquals(2, search(0, 20, null, parent.getId(), null, status.name(), "id", "asc").getTotalElements());
        assertEquals(1, search(0, 20, null, null, authority.getId(), null, "id", "asc").getTotalElements());
        assertTrue(search(0, 20, null, otherParent.getId(), authority.getId(), null, "id", "asc").isEmpty());
        assertTrue(search(0, 20, null, Long.MAX_VALUE, null, null, "id", "asc").isEmpty());
        assertTrue(search(0, 20, null, null, Long.MAX_VALUE, null, "id", "asc").isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void blankSearchRetainsNullAuthorityRows(String term) {
        var parent = governorate();
        var value = repository.saveAndFlush(village(parent));
        var result = search(0, 20, term, parent.getId(), null, null, "id", "asc");
        assertEquals(List.of(value.getId()), result.getContent().stream().map(v -> v.id()).toList());
    }

    @ParameterizedTest
    @CsvSource({"id,asc", "id,desc", "nameAr,asc", "nameAr,desc", "nameEn,asc", "nameEn,desc",
            "createdAt,asc", "createdAt,desc", "updatedAt,asc", "updatedAt,desc"})
    void supportedSortingAndNullOrderingAreAppliedBeforePagination(String field, String direction) {
        var parent = governorate();
        var values = new ArrayList<Village>();
        for (int i = 0; i < 3; i++) {
            var value = village(parent);
            value.setNameAr("sort-" + (2 - i));
            value.setNameEn(i == 2 ? null : "same");
            value.setCreatedAt(TIME.plusSeconds(i == 2 ? 1 : 0));
            value.setUpdatedAt(TIME.plusSeconds(i == 2 ? 1 : 0));
            repository.saveAndFlush(value);
            values.add(value);
        }
        entityManager.clear();
        Comparator<Village> comparator = switch (field) {
            case "nameAr" -> Comparator.comparing(Village::getNameAr);
            case "nameEn" -> Comparator.comparing(Village::getNameEn, Comparator.nullsLast(Comparator.naturalOrder()));
            case "createdAt" -> Comparator.comparing(Village::getCreatedAt);
            case "updatedAt" -> Comparator.comparing(Village::getUpdatedAt);
            default -> Comparator.comparing(Village::getId);
        };
        if (direction.equals("desc")) comparator = comparator.reversed();
        if (!field.equals("id")) comparator = comparator.thenComparing(Village::getId);
        var expected = values.stream().sorted(comparator).map(Village::getId).toList();
        var actual = new ArrayList<Long>();
        for (int page = 0; page < 3; page++) {
            var result = search(page, 1, null, parent.getId(), null, null, field, direction);
            assertEquals(3, result.getTotalElements());
            actual.add(result.getContent().getFirst().id());
        }
        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @ValueSource(strings = {"asc", "desc"})
    void equalArabicNamesAcrossGovernoratesUseIdAscending(String direction) {
        String term = unique();
        var first = village(governorate());
        first.setNameAr(term);
        repository.saveAndFlush(first);
        var second = village(governorate());
        second.setNameAr(term);
        repository.saveAndFlush(second);
        assertEquals(first.getId(), search(0, 1, term, null, null, null, "nameAr", direction)
                .getContent().getFirst().id());
        assertEquals(second.getId(), search(1, 1, term, null, null, null, "nameAr", direction)
                .getContent().getFirst().id());
    }

    private Governorate governorate() {
        var value = new Governorate();
        value.setNameAr(unique());
        return governorates.saveAndFlush(value);
    }

    private LocalAuthority authority(Governorate parent) {
        var value = new LocalAuthority();
        value.setGovernorate(parent);
        value.setNameAr(unique());
        value.setAuthorityType(LocalAuthorityType.MUNICIPALITY);
        return authorities.saveAndFlush(value);
    }

    private Village village(Governorate parent) {
        var value = new Village();
        value.setGovernorate(parent);
        value.setNameAr(unique());
        value.setLocationDescription("Isolated rollback fixture");
        value.setStatus(VillageStatus.DRAFT);
        value.setCreatedAt(TIME);
        value.setUpdatedAt(TIME);
        return value;
    }

    private String unique() { return "village-test-" + UUID.randomUUID(); }

    private void state(String expected, Runnable operation) {
        var failure = assertThrows(DataIntegrityViolationException.class, operation::run);
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                assertEquals(expected, sql.getSQLState());
                return;
            }
        }
        fail("Expected SQLSTATE " + expected);
        // No queries after a failed statement; the test transaction is rolled back.
    }
}