package com.qaryati.heritage;

import com.qaryati.heritage.dto.VillageHistoryRequest;
import com.qaryati.heritage.entity.*;
import com.qaryati.heritage.repository.VillageHistoryRepository;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

/** Requires independent dedicated database verification; never part of the database-free selection. */
@SpringBootTest(classes = HistoricalEventRepositoryIntegrationTest.PersistenceConfiguration.class,
        properties = {"spring.flyway.clean-disabled=true", "spring.jpa.hibernate.ddl-auto=validate"})
@ActiveProfiles("test")
@ContextConfiguration(initializers = HistoricalEventRepositoryIntegrationTest.DatabaseGuard.class)
@Transactional
class VillageHistoryRepositoryIntegrationTest {
    @Autowired VillageHistoryRepository repository;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    private Long village;
    private Long otherVillage;

    @BeforeEach void fixtures() {
        Long governorate = jdbc.queryForObject("INSERT INTO public.governorates(name_ar) VALUES (?) RETURNING id",
                Long.class, "history-test-" + UUID.randomUUID());
        village = village(governorate);
        otherVillage = village(governorate);
    }
    private Long village(Long governorate) {
        return jdbc.queryForObject("""
                INSERT INTO public.villages(governorate_id,name_ar,location_description)
                VALUES (?,?,'Isolated village history fixture') RETURNING id
                """, Long.class, governorate, "history-test-" + UUID.randomUUID());
    }
    private VillageHistory draft() { return VillageHistory.draft(new VillageHistoryRequest(village, "Overview")); }

    @Test void roundTripsTextMaintainsTimestampAndScopesLookups() {
        String text = "  تاريخ القرية\nتراث أهلها\n".repeat(4000) + "  ";
        var saved = repository.saveAndFlush(VillageHistory.draft(new VillageHistoryRequest(village, text)));
        Long id = saved.getId();
        assertNotNull(id);
        assertNotNull(saved.getUpdatedAt());
        entityManager.clear();
        var found = repository.findByVillageId(village).orElseThrow();
        assertEquals(id, found.getId());
        assertEquals(village, found.getVillageId());
        assertEquals(text, found.getOverview());
        assertEquals(VerificationStatus.DRAFT, found.getVerificationStatus());
        assertTrue(repository.existsByVillageId(village));
        assertFalse(repository.existsByVillageId(otherVillage));
        assertTrue(repository.findByVillageId(otherVillage).isEmpty());
        assertTrue(repository.findByIdAndVillageId(id, otherVillage).isEmpty());
        entityManager.clear();
        jdbc.update("UPDATE public.village_histories SET updated_at=TIMESTAMPTZ '2000-01-01 00:00:00+00' WHERE id=?", id);
        found = repository.findByIdAndVillageId(id, village).orElseThrow();
        Instant old = found.getUpdatedAt();
        found.reviseDraft(new VillageHistoryRequest(village, " Revised\nنص "));
        repository.flush();
        entityManager.clear();
        found = repository.findByVillageId(village).orElseThrow();
        assertEquals(" Revised\nنص ", found.getOverview());
        assertTrue(found.getUpdatedAt().isAfter(old));
        assertEquals(id, found.getId());
    }

    @Test void databaseEnforcesOneOverviewPerVillage() {
        repository.saveAndFlush(draft());
        assertState("23505", () -> insert(village, "Other", "DRAFT"));
    }
    @ParameterizedTest @ValueSource(strings = {"id", "village_id", "overview", "updated_at", "verification_status"})
    void databaseRejectsRequiredNulls(String column) {
        Long id = repository.saveAndFlush(draft()).getId();
        assertState("23502", () -> jdbc.update("UPDATE public.village_histories SET " + column + "=NULL WHERE id=?", id));
    }
    @Test void databaseRejectsUnknownStatus() {
        assertState("23514", () -> insert(village, "Overview", "INVALID"));
    }
    @Test void databaseRejectsMissingVillage() {
        Long absent = jdbc.queryForObject("SELECT COALESCE(MAX(id),0)+1 FROM public.villages", Long.class);
        assertState("23503", () -> insert(absent, "Overview", "DRAFT"));
    }
    @Test void databaseRestrictsVillageDeletion() {
        repository.saveAndFlush(draft());
        assertState("23503", () -> jdbc.update("DELETE FROM public.villages WHERE id=?", village));
    }
    @ParameterizedTest @ValueSource(strings = {"", " \t\n"})
    void databaseDefaultsAndBlankTextDifferFromApplicationValidation(String overview) {
        Long id = jdbc.queryForObject("INSERT INTO public.village_histories(village_id,overview) VALUES (?,?) RETURNING id",
                Long.class, village, overview);
        var history = repository.findByVillageId(village).orElseThrow();
        assertEquals(id, history.getId());
        assertEquals(overview, history.getOverview());
        assertEquals(VerificationStatus.DRAFT, history.getVerificationStatus());
        assertNotNull(history.getUpdatedAt());
    }
    @Test void directSqlDoesNotAutomaticallyMaintainTimestamp() {
        Long id = insert(village, "Overview", "DRAFT");
        jdbc.update("UPDATE public.village_histories SET updated_at=TIMESTAMPTZ '2000-01-01 00:00:00+00' WHERE id=?", id);
        jdbc.update("UPDATE public.village_histories SET overview='Changed' WHERE id=?", id);
        assertEquals(Instant.parse("2000-01-01T00:00:00Z"), repository.findByVillageId(village).orElseThrow().getUpdatedAt());
    }
    @ParameterizedTest @EnumSource(VerificationStatus.class)
    void loadsEveryStatusAndGuardsMutation(VerificationStatus status) {
        insert(village, "Overview", status.name());
        var history = repository.findByVillageId(village).orElseThrow();
        assertEquals(status, history.getVerificationStatus());
        if (status == VerificationStatus.DRAFT) assertDoesNotThrow(history::requireDraftMutation);
        else assertThrows(IllegalStateException.class,
                () -> history.reviseDraft(new VillageHistoryRequest(village, "Changed")));
    }

    @Test void sourceLinksCascadeOnHistoryDeletionAndRetainSource() {
        Long history = insert(village, "Overview", "DRAFT");
        Long source = source();
        link(history, source);
        repository.deleteById(history);
        repository.flush();
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM public.history_sources WHERE village_history_id=?", Long.class, history));
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM public.sources WHERE id=?", Long.class, source));
    }
    @Test void sourceLinksRejectDuplicates() {
        Long history = insert(village, "Overview", "DRAFT");
        Long source = source();
        link(history, source);
        assertState("23505", () -> link(history, source));
    }
    @Test void sourceLinksRestrictSourceDeletion() {
        Long history = insert(village, "Overview", "DRAFT");
        Long source = source();
        link(history, source);
        assertState("23503", () -> jdbc.update("DELETE FROM public.sources WHERE id=?", source));
    }
    @Test void sourceLinksRequireExistingSource() {
        Long history = insert(village, "Overview", "DRAFT");
        Long absent = jdbc.queryForObject("SELECT COALESCE(MAX(id),0)+1 FROM public.sources", Long.class);
        assertState("23503", () -> link(history, absent));
    }
    @Test void sourceLinksRequireExistingHistory() {
        Long source = source();
        Long absent = jdbc.queryForObject("SELECT COALESCE(MAX(id),0)+1 FROM public.village_histories", Long.class);
        assertState("23503", () -> link(absent, source));
    }
    private Long source() {
        return jdbc.queryForObject("INSERT INTO public.sources(title,source_type) VALUES (?,'BOOK') RETURNING id",
                Long.class, "history-source-" + UUID.randomUUID());
    }
    private void link(Long history, Long source) {
        jdbc.update("INSERT INTO public.history_sources(village_history_id,source_id) VALUES (?,?)", history, source);
    }
    private Long insert(Long villageId, String overview, String status) {
        return jdbc.queryForObject("INSERT INTO public.village_histories(village_id,overview,verification_status) VALUES (?,?,?) RETURNING id",
                Long.class, villageId, overview, status);
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
