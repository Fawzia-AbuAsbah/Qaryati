package com.qaryati.heritage;

import com.qaryati.heritage.dto.CulturalHeritageRequest;
import com.qaryati.heritage.entity.*;
import com.qaryati.heritage.repository.CulturalHeritageRepository;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

/** Requires independent dedicated database verification; excluded from database-free execution. */
@SpringBootTest(classes = OverviewHeritageRepositoryTestConfig.class,
        properties = {"spring.flyway.clean-disabled=true", "spring.jpa.hibernate.ddl-auto=validate"})
@ActiveProfiles("test")
@ContextConfiguration(initializers = OverviewHeritageRepositoryTestConfig.Guard.class)
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "qaryati.heritage-tests.database-verified", matches = "true")
@Transactional
class CulturalHeritageRepositoryIntegrationTest {
    @Autowired CulturalHeritageRepository repository;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    private Long village;
    private Long otherVillage;

    @BeforeEach void fixtures() {
        Long governorate = jdbc.queryForObject("INSERT INTO public.governorates(name_ar) VALUES (?) RETURNING id",
                Long.class, "cultural-test-" + UUID.randomUUID());
        village = village(governorate);
        otherVillage = village(governorate);
    }
    private Long village(Long governorate) {
        return jdbc.queryForObject("""
                INSERT INTO public.villages(governorate_id,name_ar,location_description)
                VALUES (?,?,'Isolated cultural heritage fixture') RETURNING id
                """, Long.class, governorate, "cultural-test-" + UUID.randomUUID());
    }
    private CulturalHeritage draft(Long villageId, String name) {
        return CulturalHeritage.draft(new CulturalHeritageRequest(villageId, name, CulturalHeritageType.CRAFT, "Description"));
    }

    @ParameterizedTest @EnumSource(CulturalHeritageType.class)
    void roundTripsAllFieldsAndRevisesDraft(CulturalHeritageType type) {
        String text = "  تراث القرية\nحرف وطعام\n".repeat(4000);
        var saved = repository.saveAndFlush(CulturalHeritage.draft(
                new CulturalHeritageRequest(village, "ن".repeat(200), type, text)));
        Long id = saved.getId();
        assertNotNull(id);
        entityManager.clear();
        var found = repository.findByIdAndVillageId(id, village).orElseThrow();
        assertEquals(village, found.getVillageId());
        assertEquals("ن".repeat(200), found.getName());
        assertEquals(type, found.getHeritageType());
        assertEquals(text, found.getDescription());
        assertEquals(VerificationStatus.DRAFT, found.getVerificationStatus());
        assertTrue(repository.findByIdAndVillageId(id, otherVillage).isEmpty());
        found.reviseDraft(new CulturalHeritageRequest(village, " Revised ", CulturalHeritageType.OTHER, "\n"));
        repository.flush();
        entityManager.clear();
        found = repository.findByIdAndVillageId(id, village).orElseThrow();
        assertEquals(" Revised ", found.getName());
        assertEquals(CulturalHeritageType.OTHER, found.getHeritageType());
        assertEquals("\n", found.getDescription());
        assertEquals(VerificationStatus.DRAFT, found.getVerificationStatus());
    }

    @Test void deterministicPaginationIsVillageScoped() {
        List<Long> expected = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            expected.add(repository.saveAndFlush(draft(village, "Name-" + i)).getId());
            repository.saveAndFlush(draft(otherVillage, "Name-" + i));
        }
        entityManager.clear();
        List<Long> actual = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            var result = repository.findVillagePage(village, page, 2);
            assertEquals(5, result.getTotalElements());
            var ids = result.stream().map(CulturalHeritage::getId).toList();
            assertEquals(ids, repository.findVillagePage(village, page, 2).stream().map(CulturalHeritage::getId).toList());
            actual.addAll(ids);
        }
        assertEquals(expected, actual);
        assertTrue(repository.findVillagePage(village, 3, 2).isEmpty());
    }

    @Test void duplicateChecksAreVillageAndTypeScopedAndExcludeCurrentRecord() {
        var saved = repository.saveAndFlush(draft(village, "Name"));
        assertTrue(repository.existsByVillageIdAndNameAndHeritageType(village, "Name", CulturalHeritageType.CRAFT));
        assertFalse(repository.existsByVillageIdAndNameAndHeritageType(otherVillage, "Name", CulturalHeritageType.CRAFT));
        assertFalse(repository.existsByVillageIdAndNameAndHeritageType(village, "Name", CulturalHeritageType.FOOD));
        assertFalse(repository.existsByVillageIdAndNameAndHeritageTypeAndIdNot(village, "Name", CulturalHeritageType.CRAFT, saved.getId()));
        var other = repository.saveAndFlush(draft(village, "Other"));
        assertTrue(repository.existsByVillageIdAndNameAndHeritageTypeAndIdNot(village, "Name", CulturalHeritageType.CRAFT, other.getId()));
        assertDoesNotThrow(() -> insert(otherVillage, "Name", "CRAFT", "Text", "DRAFT"));
        assertDoesNotThrow(() -> insert(village, "Name", "FOOD", "Text", "DRAFT"));
    }
    @Test void databaseRejectsDuplicateCompositeKey() {
        repository.saveAndFlush(draft(village, "Name"));
        assertState("23505", () -> insert(village, "Name", "CRAFT", "Different text", "DRAFT"));
    }
    @Test void databaseRejectsRevisionToDuplicateCompositeKey() {
        repository.saveAndFlush(draft(village, "Name"));
        var other = repository.saveAndFlush(draft(village, "Other"));
        other.reviseDraft(new CulturalHeritageRequest(village, "Name", CulturalHeritageType.CRAFT, "Changed"));
        assertState("23505", repository::flush);
    }
    @ParameterizedTest @ValueSource(strings = {"id", "village_id", "name", "heritage_type", "description", "verification_status"})
    void databaseRejectsRequiredNulls(String column) {
        Long id = repository.saveAndFlush(draft(village, "Name")).getId();
        assertState("23502", () -> jdbc.update("UPDATE public.cultural_heritage SET " + column + "=NULL WHERE id=?", id));
    }
    @Test void databaseRejectsOversizedName() {
        assertState("22001", () -> insert(village, "x".repeat(201), "CRAFT", "Text", "DRAFT"));
    }
    @Test void databaseRejectsUnknownCategory() {
        assertState("23514", () -> insert(village, "Name", "INVALID", "Text", "DRAFT"));
    }
    @Test void databaseRejectsUnknownStatus() {
        assertState("23514", () -> insert(village, "Name", "CRAFT", "Text", "INVALID"));
    }
    @Test void databaseRequiresExistingVillage() {
        Long absent = MissingFixtureId.in(jdbc, "villages");
        assertState("23503", () -> insert(absent, "Name", "CRAFT", "Text", "DRAFT"));
    }
    @Test void databaseRestrictsVillageDeletion() {
        repository.saveAndFlush(draft(village, "Name"));
        assertState("23503", () -> jdbc.update("DELETE FROM public.villages WHERE id=?", village));
    }
    @ParameterizedTest @ValueSource(strings = {"", " \t\n"})
    void databaseDefaultsStatusAndPreservesBlankText(String text) {
        Long id = jdbc.queryForObject("""
                INSERT INTO public.cultural_heritage(village_id,name,heritage_type,description)
                VALUES (?,?,'OTHER',?) RETURNING id
                """, Long.class, village, text, text);
        var found = repository.findByIdAndVillageId(id, village).orElseThrow();
        assertEquals(text, found.getName());
        assertEquals(text, found.getDescription());
        assertEquals(VerificationStatus.DRAFT, found.getVerificationStatus());
    }
    @ParameterizedTest @EnumSource(VerificationStatus.class)
    void loadsEveryStatusWithoutRequiringSourcesAndGuardsMutation(VerificationStatus status) {
        Long id = insert(village, "Name", "CRAFT", "Text", status.name());
        var found = repository.findByIdAndVillageId(id, village).orElseThrow();
        assertEquals(status, found.getVerificationStatus());
        if (status == VerificationStatus.DRAFT) assertDoesNotThrow(found::requireDraftMutation);
        else assertThrows(IllegalStateException.class, () -> found.reviseDraft(
                new CulturalHeritageRequest(village, "Changed", CulturalHeritageType.FOOD, "Changed")));
    }

    // SQL fixtures verify V1 relationships only; production citation operations remain deferred.
    @Test void sourceLinksCascadeOnHeritageDeletionAndRetainSource() {
        Long heritage = insert(village, "Name", "CRAFT", "Text", "DRAFT");
        Long source = source();
        link(heritage, source);
        repository.deleteById(heritage);
        repository.flush();
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM public.cultural_heritage_sources WHERE cultural_heritage_id=?", Long.class, heritage));
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM public.sources WHERE id=?", Long.class, source));
    }
    @Test void sourceLinksRejectDuplicates() {
        Long heritage = insert(village, "Name", "CRAFT", "Text", "DRAFT");
        Long source = source();
        link(heritage, source);
        assertState("23505", () -> link(heritage, source));
    }
    @Test void sourceLinksRestrictSourceDeletion() {
        Long heritage = insert(village, "Name", "CRAFT", "Text", "DRAFT");
        Long source = source();
        link(heritage, source);
        assertState("23503", () -> jdbc.update("DELETE FROM public.sources WHERE id=?", source));
    }
    @Test void sourceLinksRequireExistingSource() {
        Long heritage = insert(village, "Name", "CRAFT", "Text", "DRAFT");
        Long absent = MissingFixtureId.in(jdbc, "sources");
        assertState("23503", () -> link(heritage, absent));
    }
    @Test void sourceLinksRequireExistingHeritage() {
        Long source = source();
        Long absent = MissingFixtureId.in(jdbc, "cultural_heritage");
        assertState("23503", () -> link(absent, source));
    }
    @Test void sourcesAreReusableAndHeritageMayHaveMultipleSources() {
        Long first = insert(village, "First", "CRAFT", "Text", "DRAFT");
        Long second = insert(village, "Second", "CRAFT", "Text", "DRAFT");
        Long source = source();
        link(first, source);
        link(second, source);
        link(first, source());
        assertEquals(2L, jdbc.queryForObject("SELECT COUNT(*) FROM public.cultural_heritage_sources WHERE cultural_heritage_id=?", Long.class, first));
        assertEquals(2L, jdbc.queryForObject("SELECT COUNT(*) FROM public.cultural_heritage_sources WHERE source_id=?", Long.class, source));
    }
    private Long source() {
        return jdbc.queryForObject("INSERT INTO public.sources(title,source_type) VALUES (?,'BOOK') RETURNING id",
                Long.class, "cultural-source-" + UUID.randomUUID());
    }
    private void link(Long heritage, Long source) {
        jdbc.update("INSERT INTO public.cultural_heritage_sources(cultural_heritage_id,source_id) VALUES (?,?)", heritage, source);
    }
    private Long insert(Long villageId, String name, String type, String description, String status) {
        return jdbc.queryForObject("""
                INSERT INTO public.cultural_heritage(village_id,name,heritage_type,description,verification_status)
                VALUES (?,?,?,?,?) RETURNING id
                """, Long.class, villageId, name, type, description, status);
    }
    private void assertState(String state, Runnable operation) {
        RuntimeException error = assertThrows(RuntimeException.class, operation::run);
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) { assertEquals(state, sql.getSQLState()); return; }
        }
        fail("Missing SQLException", error);
        // No further SQL after PostgreSQL aborts the transaction; fixtures roll back.
    }
}
