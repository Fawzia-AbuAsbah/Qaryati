package com.qaryati.heritage;

import com.qaryati.heritage.dto.HeritageSiteRequest;
import com.qaryati.heritage.entity.*;
import com.qaryati.heritage.repository.HeritageSiteRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = HistoricalEventRepositoryIntegrationTest.PersistenceConfiguration.class,
        properties = {"spring.flyway.clean-disabled=true", "spring.jpa.hibernate.ddl-auto=validate"})
@ActiveProfiles("test")
@ContextConfiguration(initializers = HistoricalEventRepositoryIntegrationTest.DatabaseGuard.class)
@Transactional
class HeritageSiteRepositoryIntegrationTest {
    @Autowired HeritageSiteRepository repository;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    private Long village;
    private Long otherVillage;

    @BeforeEach void fixtures() {
        Long governorate = jdbc.queryForObject("INSERT INTO public.governorates(name_ar) VALUES (?) RETURNING id",
                Long.class, "site-test-" + UUID.randomUUID());
        village = village(governorate);
        otherVillage = village(governorate);
    }
    private Long village(Long governorate) {
        return jdbc.queryForObject("""
                INSERT INTO public.villages(governorate_id,name_ar,location_description)
                VALUES (?,?,'Isolated heritage site test fixture') RETURNING id
                """, Long.class, governorate, "site-test-" + UUID.randomUUID());
    }
    private HeritageSite draft(Long villageId) {
        return HeritageSite.draft(new HeritageSiteRequest(villageId, "Same name",
                HeritageSiteType.OTHER, null, null, null, null));
    }

    @ParameterizedTest @EnumSource(HeritageSiteType.class)
    void roundTripsAllFieldsAndRevisesDraft(HeritageSiteType type) {
        var saved = repository.saveAndFlush(HeritageSite.draft(new HeritageSiteRequest(village,
                "x".repeat(250), type, "Description", new BigDecimal("-90.000000"),
                new BigDecimal("180.000000"), "Notes")));
        Long id = saved.getId();
        entityManager.clear();
        var found = repository.findByIdAndVillageId(id, village).orElseThrow();
        assertEquals(type, found.getSiteType());
        assertEquals("x".repeat(250), found.getName());
        assertEquals("Description", found.getDescription());
        assertEquals("Notes", found.getConditionNotes());
        assertEquals(new BigDecimal("-90.000000"), found.getLatitude());
        assertEquals(new BigDecimal("180.000000"), found.getLongitude());
        assertEquals(VerificationStatus.DRAFT, found.getVerificationStatus());
        assertTrue(repository.findByIdAndVillageId(id, otherVillage).isEmpty());
        found.reviseDraft(new HeritageSiteRequest(village, "Revised", HeritageSiteType.OTHER,
                null, null, null, null));
        repository.flush();
        entityManager.clear();
        found = repository.findByIdAndVillageId(id, village).orElseThrow();
        assertEquals("Revised", found.getName());
        assertNull(found.getLatitude());
        assertNull(found.getLongitude());
        assertNull(found.getDescription());
        assertNull(found.getConditionNotes());
    }

    @Test void deterministicPaginationIsVillageScopedAndAllowsDuplicateNames() {
        List<Long> expected = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            expected.add(repository.saveAndFlush(draft(village)).getId());
            repository.saveAndFlush(draft(otherVillage));
        }
        entityManager.clear();
        List<Long> actual = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            var result = repository.findVillagePage(village, page, 2);
            assertEquals(5, result.getTotalElements());
            var ids = result.stream().map(HeritageSite::getId).toList();
            assertEquals(ids, repository.findVillagePage(village, page, 2).stream().map(HeritageSite::getId).toList());
            actual.addAll(ids);
        }
        assertEquals(expected, actual);
        assertTrue(repository.findVillagePage(village, 3, 2).isEmpty());
    }

    static Stream<Arguments> databaseCoordinates() {
        return Stream.of(Arguments.of(null, null, true), Arguments.of("0", null, false),
                Arguments.of(null, "0", false), Arguments.of("-90", "-180", true),
                Arguments.of("90", "180", true), Arguments.of("90.000001", "0", false),
                Arguments.of("-90.000001", "0", false), Arguments.of("0", "180.000001", false),
                Arguments.of("0", "-180.000001", false), Arguments.of("0.000001", "-0.000001", true));
    }
    @ParameterizedTest @MethodSource("databaseCoordinates")
    void databaseChecksCoordinatePairsAndRanges(String lat, String lon, boolean valid) {
        Runnable insert = () -> insert(village, "Name", "OTHER",
                HeritageSiteTest.decimal(lat), HeritageSiteTest.decimal(lon), "DRAFT");
        if (valid) assertDoesNotThrow(insert::run);
        else assertState("23514", insert);
    }

    @Test void databaseRoundsExcessScaleUnlikeApplicationValidation() {
        Long id = insert(village, "Name", "OTHER", new BigDecimal("32.1234567"),
                new BigDecimal("35.1234567"), "DRAFT");
        var site = repository.findByIdAndVillageId(id, village).orElseThrow();
        assertEquals(new BigDecimal("32.123457"), site.getLatitude());
        assertEquals(new BigDecimal("35.123457"), site.getLongitude());
    }
    @Test void databaseRejectsNumericOverflow() {
        assertState("22003", () -> insert(village, "Name", "OTHER", new BigDecimal("1000"), BigDecimal.ZERO, "DRAFT"));
    }
    @ParameterizedTest @ValueSource(strings = {"village_id", "name", "site_type", "verification_status"})
    void databaseRejectsRequiredNulls(String column) {
        Long id = repository.saveAndFlush(draft(village)).getId();
        assertState("23502", () -> jdbc.update("UPDATE public.heritage_sites SET " + column + "=NULL WHERE id=?", id));
    }
    @Test void databaseRejectsOversizedName() {
        assertState("22001", () -> insert(village, "x".repeat(251), "OTHER", null, null, "DRAFT"));
    }
    @Test void databaseRejectsUnknownType() {
        assertState("23514", () -> insert(village, "Name", "INVALID", null, null, "DRAFT"));
    }
    @Test void databaseRejectsUnknownStatus() {
        assertState("23514", () -> insert(village, "Name", "OTHER", null, null, "INVALID"));
    }
    @Test void databaseRejectsMissingVillage() {
        Long absent = jdbc.queryForObject("SELECT COALESCE(MAX(id),0)+1 FROM public.villages", Long.class);
        assertState("23503", () -> insert(absent, "Name", "OTHER", null, null, "DRAFT"));
    }
    @Test void databaseRestrictsDeletingReferencedVillage() {
        repository.saveAndFlush(draft(village));
        assertState("23503", () -> jdbc.update("DELETE FROM public.villages WHERE id=?", village));
    }
    @Test void databaseDefaultsStatusAndAcceptsNullableContentAndEmptyName() {
        Long id = jdbc.queryForObject("""
                INSERT INTO public.heritage_sites(village_id,name,site_type)
                VALUES (?,'','OTHER') RETURNING id
                """, Long.class, village);
        var site = repository.findByIdAndVillageId(id, village).orElseThrow();
        assertEquals(VerificationStatus.DRAFT, site.getVerificationStatus());
        assertEquals("", site.getName());
        assertNull(site.getDescription());
        assertNull(site.getConditionNotes());
        assertNull(site.getLatitude());
        assertNull(site.getLongitude());
    }
    @ParameterizedTest @EnumSource(VerificationStatus.class)
    void loadsEveryStatusAndGuardsMutation(VerificationStatus status) {
        Long id = insert(village, "Name", "OTHER", null, null, status.name());
        var site = repository.findByIdAndVillageId(id, village).orElseThrow();
        assertEquals(status, site.getVerificationStatus());
        if (status == VerificationStatus.DRAFT) assertDoesNotThrow(site::requireDraftMutation);
        else assertThrows(IllegalStateException.class, () -> site.reviseDraft(
                new HeritageSiteRequest(village, "Changed", HeritageSiteType.OTHER, null, null, null, null)));
    }
    private Long insert(Long villageId, String name, String type, BigDecimal lat, BigDecimal lon, String status) {
        return jdbc.queryForObject("""
                INSERT INTO public.heritage_sites(village_id,name,site_type,latitude,longitude,verification_status)
                VALUES (?,?,?,?,?,?) RETURNING id
                """, Long.class, villageId, name, type, lat, lon, status);
    }
    private void assertState(String state, Runnable operation) {
        RuntimeException error = assertThrows(RuntimeException.class, operation::run);
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) { assertEquals(state, sql.getSQLState()); return; }
        }
        fail("Missing SQLException", error);
        // PostgreSQL aborts the transaction on error; perform no further SQL.
    }
}
