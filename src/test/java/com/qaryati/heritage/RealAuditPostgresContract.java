package com.qaryati.heritage;

import com.qaryati.village.api.*;
import java.sql.*;
import java.time.OffsetDateTime;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** No test-level rollback: service calls commit; all postconditions use physical JDBC reads. */
abstract class RealAuditPostgresContract {
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactions;
    @Autowired VillageLookupApi villages;
    long governorate, village, first, second, actor;
    String username;
    OffsetDateTime started;
    final RealAuditFixtureOwnership ownership = new RealAuditFixtureOwnership();
    abstract String table();
    abstract String linkTable();
    abstract String parentColumn();
    abstract String entityType();
    abstract String textColumn();
    abstract long create(String text, List<Long> sources);
    abstract void update(long id, String text, List<Long> sources);
    abstract void delete(long id);
    abstract Map<String, Object> snapshot(String text, List<Long> sources);
    abstract Map<Long, List<Long>> batch(List<Long> ids);
    abstract void replaceSources(long id, List<Long> sources);

    @BeforeEach void fixture() {
        started = OffsetDateTime.now().minusSeconds(2);
        username = "audit-it-" + UUID.randomUUID();
        boundedTransaction().executeWithoutResult(tx -> {
            localTimeouts();
            governorate = ownership.register("governorates", insert("INSERT INTO public.governorates(name_ar) VALUES (?) RETURNING id", username));
            village = ownership.register("villages", insert("INSERT INTO public.villages(governorate_id,name_ar,location_description) VALUES (?,?,?) RETURNING id", governorate, username, username));
            first = ownership.register("sources", insert("INSERT INTO public.sources(title,source_type) VALUES (?,'BOOK') RETURNING id", username));
            second = ownership.register("sources", insert("INSERT INTO public.sources(title,source_type) VALUES (?,'BOOK') RETURNING id", username));
            actor = ownership.register("users", insert("INSERT INTO public.users(username,email,password_hash,display_name,enabled) VALUES (?,?,'unused-test-hash','Isolated audit actor',true) RETURNING id", username, username + "@example.invalid"));
        });
        when(villages.findById(village)).thenReturn(Optional.of(new VillageLookupResult(village, VillageLookupStatus.VERIFIED, false)));
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                username, null, List.of(new SimpleGrantedAuthority("ROLE_EDITOR"))));
    }

    @AfterEach void cleanup() {
        try {
            boundedTransaction().executeWithoutResult(tx -> {
                localTimeouts();
                // Both allocation evidence and database ownership markers are required.
                if (ownership.owns("users", actor))
                    jdbc.update("DELETE FROM public.audit_logs WHERE actor_user_id=? AND actor_label=? AND EXISTS (SELECT 1 FROM public.users WHERE id=? AND username=? AND email=?)",
                            actor, username, actor, username, username + "@example.invalid");
                if (ownership.owns("villages", village))
                    jdbc.update("DELETE FROM public." + table() + " WHERE village_id=? AND EXISTS (SELECT 1 FROM public.villages WHERE id=? AND name_ar=? AND location_description=?)",
                            village, village, username, username);
                for (long source : new long[]{first, second})
                    if (ownership.owns("sources", source))
                        jdbc.update("DELETE FROM public.sources WHERE id=? AND title=?", source, username);
                if (ownership.owns("villages", village))
                    jdbc.update("DELETE FROM public.villages WHERE id=? AND name_ar=? AND location_description=?", village, username, username);
                if (ownership.owns("governorates", governorate))
                    jdbc.update("DELETE FROM public.governorates WHERE id=? AND name_ar=?", governorate, username);
                if (ownership.owns("users", actor))
                    jdbc.update("DELETE FROM public.users WHERE id=? AND username=? AND email=?", actor, username, username + "@example.invalid");
            });
        } finally { SecurityContextHolder.clearContext(); reset(villages); }
    }

    TransactionTemplate boundedTransaction() {
        var template = new TransactionTemplate(transactions);
        template.setTimeout(10);
        return template;
    }

    void localTimeouts() {
        jdbc.execute("SET LOCAL lock_timeout = '300ms'");
        jdbc.execute("SET LOCAL statement_timeout = '5s'");
    }

    long insert(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }
    long count(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }
    List<Long> sources(long id) {
        return jdbc.queryForList("SELECT source_id FROM public." + linkTable() + " WHERE " + parentColumn() + "=? ORDER BY source_id", Long.class, id);
    }
    long auditCount() { return count("SELECT count(*) FROM public.audit_logs WHERE actor_user_id=?", actor); }
    void unchanged(long id) {
        assertEquals("Original", jdbc.queryForObject("SELECT " + textColumn() + " FROM public." + table() + " WHERE id=?", String.class, id));
        assertEquals(List.of(first), sources(id));
        if (table().equals("cultural_heritage")) {
            assertEquals("Original", jdbc.queryForObject("SELECT description FROM public.cultural_heritage WHERE id=?", String.class, id));
            assertEquals("CRAFT", jdbc.queryForObject("SELECT heritage_type FROM public.cultural_heritage WHERE id=?", String.class, id));
        }
        assertEquals(1, auditCount());
        audit(id, "CREATE", null, snapshot("Original", List.of(first)));
    }

    @Test void committedCrudPersistsExactJsonbAndEnabledDatabaseActor() {
        long id = create("Original", List.of(first));
        assertEquals("DRAFT", jdbc.queryForObject("SELECT verification_status FROM public." + table() + " WHERE id=?", String.class, id));
        unchanged(id);
        String changed = "تراث القرية\nMémoire 🌿";
        update(id, changed, List.of(second));
        assertEquals(changed, jdbc.queryForObject("SELECT " + textColumn() + " FROM public." + table() + " WHERE id=?", String.class, id));
        assertEquals(List.of(second), sources(id));
        audit(id, "UPDATE", snapshot("Original", List.of(first)), snapshot(changed, List.of(second)));
        // A separate source-only change must produce a separate physical UPDATE row.
        update(id, changed, List.of(first, second));
        assertEquals(3, auditCount());
        long sourceOnlyAudit = jdbc.queryForObject("SELECT max(id) FROM public.audit_logs WHERE actor_user_id=? AND action='UPDATE'", Long.class, actor);
        auditRow(jdbc.queryForMap("SELECT * FROM public.audit_logs WHERE id=?", sourceOnlyAudit), id, "UPDATE",
                snapshot(changed, List.of(second)), snapshot(changed, List.of(first, second)));
        assertEquals(1, count("SELECT count(*) FROM public.audit_logs WHERE actor_user_id=? AND action='UPDATE' AND before_data->'sourceIds'=CAST(? AS jsonb) AND after_data->'sourceIds'=CAST(? AS jsonb)", actor, "[" + second + "]", "[" + first + "," + second + "]"));
        delete(id);
        assertEquals(0, count("SELECT count(*) FROM public." + table() + " WHERE id=?", id));
        assertTrue(sources(id).isEmpty());
        assertEquals(4, auditCount());
        assertEquals(List.of("CREATE", "UPDATE", "UPDATE", "DELETE"), jdbc.queryForList("SELECT action FROM public.audit_logs WHERE actor_user_id=? ORDER BY id", String.class, actor));
        audit(id, "DELETE", snapshot(changed, List.of(first, second)), null);
    }

    void audit(long id, String action, Map<String, Object> before, Map<String, Object> after) {
        var rows = jdbc.queryForList("SELECT * FROM public.audit_logs WHERE actor_user_id=? AND entity_type=? AND entity_id=? AND action=? ORDER BY id", actor, entityType(), id, action);
        assertEquals(1, rows.size());
        auditRow(rows.getFirst(), id, action, before, after);
    }

    void auditRow(Map<String, Object> row, long id, String action, Map<String, Object> before, Map<String, Object> after) {
        assertEquals(actor, ((Number) row.get("actor_user_id")).longValue());
        assertEquals(username, row.get("actor_label"));
        assertEquals(action, row.get("action"));
        assertEquals(entityType(), row.get("entity_type"));
        assertEquals(id, ((Number) row.get("entity_id")).longValue());
        var timestamp = jdbc.queryForObject("SELECT occurred_at FROM public.audit_logs WHERE id=?", OffsetDateTime.class, row.get("id"));
        assertNotNull(timestamp);
        assertFalse(timestamp.isBefore(started));
        assertFalse(timestamp.isAfter(OffsetDateTime.now().plusSeconds(2)));
        json(row.get("id"), "before_data", before);
        json(row.get("id"), "after_data", after);
        assertEquals(1, count("SELECT count(*) FROM public.users WHERE id=? AND username=? AND enabled", actor, username));
    }

    void json(Object id, String column, Map<String, Object> expected) {
        if (expected == null) {
            assertEquals(1, count("SELECT count(*) FROM public.audit_logs WHERE id=? AND " + column + " IS NULL", id));
            return;
        }
        // Exact JSONB equality checks the full allowlist, values and absence of extra fields.
        String encoded;
        try { encoded = new tools.jackson.databind.ObjectMapper().writeValueAsString(expected); }
        catch (Exception e) { throw new AssertionError(e); }
        assertEquals(1, count("SELECT count(*) FROM public.audit_logs WHERE id=? AND pg_typeof(" + column + ")::text='jsonb' AND " + column + "=CAST(? AS jsonb)", id, encoded));
    }

    /** FK INSERT takes KEY SHARE; a fixture-only FOR UPDATE lock forces SQLSTATE 55P03.
     * Plain MVCC source/user lookup still succeeds. No trigger, role or schema changes.
     */
    void blocked(String target, long targetId, Runnable mutation) throws SQLException {
        try (var blocker = dataSource.getConnection()) {
            blocker.setAutoCommit(false);
            try {
                try (var settings = blocker.createStatement()) {
                    settings.setQueryTimeout(5);
                    settings.execute("SET LOCAL lock_timeout = '300ms'");
                    settings.execute("SET LOCAL statement_timeout = '5s'");
                }
                try (var lock = blocker.prepareStatement("SELECT id FROM public." + target + " WHERE id=? FOR UPDATE")) {
                    lock.setQueryTimeout(5);
                    lock.setLong(1, targetId);
                    try (var rows = lock.executeQuery()) { assertTrue(rows.next()); }
                }
                RuntimeException failure = assertThrows(RuntimeException.class, () -> boundedTransaction().executeWithoutResult(tx -> {
                    localTimeouts();
                    mutation.run();
                }));
                boolean lockTimeout = false;
                for (Throwable cause = failure; cause != null; cause = cause.getCause())
                    if (cause instanceof SQLException sql && "55P03".equals(sql.getSQLState())) lockTimeout = true;
                assertTrue(lockTimeout, "Expected a PostgreSQL FK lock timeout, not an application validation failure");
            } finally { blocker.rollback(); }
        }
    }

    @Test @Timeout(15) void auditInsertFailureRollsBackCreate() throws SQLException {
        blocked("users", actor, () -> create("Original", List.of(first)));
        assertNoMutation();
    }
    @Test @Timeout(15) void sourceInsertFailureRollsBackCreate() throws SQLException {
        blocked("sources", first, () -> create("Original", List.of(first)));
        assertNoMutation();
    }
    void assertNoMutation() {
        assertEquals(0, count("SELECT count(*) FROM public." + table() + " WHERE village_id=?", village));
        assertEquals(0, count("SELECT count(*) FROM public." + linkTable() + " WHERE source_id IN (?,?)", first, second));
        assertEquals(0, auditCount());
    }
    @Test void laterFailureRollsBackAnAlreadyInsertedAudit() {
        assertThrows(org.springframework.dao.DataAccessException.class, () -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            long id = create("Original", List.of(first));
            assertEquals(List.of(first), sources(id));
            audit(id, "CREATE", null, snapshot("Original", List.of(first)));
            jdbc.execute("SELECT 1 / 0");
        }));
        assertNoMutation();
    }
    @Test @Timeout(15) void auditInsertFailureRestoresUpdateAndSources() throws SQLException {
        long id = create("Original", List.of(first));
        blocked("users", actor, () -> update(id, "Changed", List.of(second)));
        unchanged(id);
    }
    @Test @Timeout(15) void sourceReplacementFailureRestoresUpdateAndSources() throws SQLException {
        long id = create("Original", List.of(first));
        blocked("sources", second, () -> update(id, "Changed", List.of(second)));
        unchanged(id);
    }
    @Test @Timeout(15) void productionSourceReplacementLocksTheParent() throws SQLException {
        long id = create("Original", List.of(first));
        blocked(table(), id, () -> replaceSources(id, List.of(second)));
        unchanged(id);
    }
    @Test @Timeout(15) void auditInsertFailureRestoresDeletedParentAndCascade() throws SQLException {
        long id = create("Original", List.of(first));
        blocked("users", actor, () -> delete(id));
        unchanged(id);
        assertEquals(0, count("SELECT count(*) FROM public.audit_logs WHERE actor_user_id=? AND action='DELETE'", actor));
    }

    @Test void laterFailureRestoresUpdateAndRollsBackSuccessfulUpdateAudit() {
        long id = create("Original", List.of(first));
        assertThrows(org.springframework.dao.DataAccessException.class, () -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            update(id, "Changed", List.of(second));
            audit(id, "UPDATE", snapshot("Original", List.of(first)), snapshot("Changed", List.of(second)));
            assertEquals(List.of(second), sources(id));
            jdbc.execute("SELECT 1 / 0");
        }));
        unchanged(id);
    }

    @Test void laterFailureRestoresDeleteAndRollsBackSuccessfulDeleteAudit() {
        long id = create("Original", List.of(first));
        assertThrows(org.springframework.dao.DataAccessException.class, () -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            delete(id);
            assertTrue(sources(id).isEmpty());
            assertEquals(0, count("SELECT count(*) FROM public." + table() + " WHERE id=?", id));
            audit(id, "DELETE", snapshot("Original", List.of(first)), null);
            jdbc.execute("SELECT 1 / 0");
        }));
        unchanged(id);
    }

    @Test void batchQueriesIncludeEmptyParentsDeduplicateInputsAndRejectMissingParents() {
        long cited = create("Original", List.of(first, second));
        // A second real parent in a separate village avoids history's one-overview constraint.
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            long emptyVillage = insert("INSERT INTO public.villages(governorate_id,name_ar,location_description) VALUES (?,'empty','test') RETURNING id", governorate);
            try {
                String columns = table().equals("village_histories") ? "village_id,overview" : "village_id,name,heritage_type,description";
                String values = table().equals("village_histories") ? "?,'empty'" : "?,'empty','CRAFT','empty'";
                long empty = insert("INSERT INTO public." + table() + "(" + columns + ") VALUES (" + values + ") RETURNING id", emptyVillage);
                assertEquals(Map.of(cited, List.of(first, second), empty, List.of()), batch(List.of(empty, cited, cited)));
                // Allocate and remove an owned parent to obtain a guaranteed absent positive ID.
                jdbc.update("DELETE FROM public." + table() + " WHERE id=?", empty);
                assertThrows(NoSuchElementException.class, () -> batch(List.of(cited, empty)));
            } finally { tx.setRollbackOnly(); }
        });
    }
}
