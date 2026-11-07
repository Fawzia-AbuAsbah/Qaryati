package com.qaryati.heritage;

import com.qaryati.dataquality.api.*;
import com.qaryati.heritage.dto.HeritageSiteRequest;
import com.qaryati.heritage.entity.*;
import com.qaryati.heritage.repository.HeritageSiteRepository;
import com.qaryati.heritage.service.HeritageSiteService;
import com.qaryati.village.api.*;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Opt-in only. Real JPA, PostgreSQL and public source API; controlled village/audit failures. */
@EnabledIfEnvironmentVariable(named = "QARYATI_SITE_CRUD_TESTS", matches = "verified-test-db")
@SpringBootTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate",
        "app.jwt.secret=heritage-site-integration-test-only-secret-at-least-32-bytes"})
@ActiveProfiles("test")
@ContextConfiguration(initializers = HeritageSiteCrudPostgresIntegrationTest.Guard.class)
class HeritageSiteCrudPostgresIntegrationTest {
    @Autowired HeritageSiteService service;
    @Autowired HeritageSiteRepository repository;
    @Autowired HeritageSiteSourceLinks links;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean VillageLookupApi villages;
    @MockitoBean AuditWriter audits;

    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }

    private HeritageSiteRequest request(long village, long source, String name) {
        return new HeritageSiteRequest(village, name, HeritageSiteType.SPRING, "Description",
                null, null, "Notes", List.of(source));
    }

    private long village() {
        long governorate = jdbc.queryForObject("INSERT INTO public.governorates(name_ar) VALUES (?) RETURNING id",
                Long.class, "site-crud-" + UUID.randomUUID());
        long id = jdbc.queryForObject("INSERT INTO public.villages(governorate_id,name_ar,location_description)"
                + " VALUES (?,'test','site CRUD test') RETURNING id", Long.class, governorate);
        when(villages.findById(id)).thenReturn(Optional.of(new VillageLookupResult(id, VillageLookupStatus.VERIFIED, false)));
        return id;
    }

    private long source() {
        return jdbc.queryForObject("INSERT INTO public.sources(title,source_type) VALUES ('site CRUD test','BOOK') RETURNING id", Long.class);
    }

    @Test void visibilityPaginationLinksAndDeleteCascade() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            HeritageSiteServiceTest.role("EDITOR");
            long village = village();
            long source = source();
            var first = service.create(request(village, source, "First"));
            var second = service.create(request(village, source, "Second"));
            var draft = service.create(request(village, source, "Draft"));
            jdbc.update("UPDATE public.heritage_sites SET verification_status='VERIFIED' WHERE id IN (?,?)", first.id(), second.id());
            entityManager.clear();
            HeritageSiteServiceTest.role("VIEWER");
            var page = service.getAll(0, 1, village);
            assertEquals(2, page.totalElements());
            assertEquals(List.of(first.id()), page.content().stream().map(e -> e.id()).toList());
            assertEquals(List.of(source), page.content().getFirst().sourceIds());
            assertEquals(second.id(), service.getAll(1, 1, village).content().getFirst().id());
            assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.getById(draft.id()));
            HeritageSiteServiceTest.role("ADMIN");
            assertEquals(3, service.getAll(0, 20, village).totalElements());
            long replacement = source();
            service.update(draft.id(), request(village, replacement, "Updated"));
            assertEquals(List.of(replacement), links.sourceIds(draft.id()));
            service.delete(draft.id());
            assertFalse(repository.existsById(draft.id()));
            // The source FK would reject this if deletion had left an association behind.
            assertEquals(1, jdbc.update("DELETE FROM public.sources WHERE id=?", replacement));
            tx.setRollbackOnly();
        });
    }

    @Test void rowLockBlocksAnIndependentPostgresWriter() {
        var fixture = committedFixture();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                repository.findByIdForUpdate(fixture.site()).orElseThrow();
                try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                    connection.setAutoCommit(false);
                    statement.execute("SET LOCAL lock_timeout='250ms'");
                    try (var update = connection.prepareStatement("UPDATE public.heritage_sites SET name='blocked' WHERE id=?")) {
                        update.setLong(1, fixture.site());
                        var failure = assertThrows(java.sql.SQLException.class, update::executeUpdate);
                        assertEquals("55P03", failure.getSQLState());
                    } finally { connection.rollback(); }
                } catch (java.sql.SQLException failure) { throw new AssertionError(failure); }
                tx.setRollbackOnly();
            });
        } finally { cleanup(fixture); }
    }

    @Test void failedUpdateAndDeleteRestoreCommittedMetadataAndSources() {
        var fixture = committedFixture();
        try {
            doThrow(new IllegalStateException("controlled audit failure")).when(audits)
                    .recordHeritageSite(anyLong(), any(), nullable(HeritageSiteAuditSnapshot.class), nullable(HeritageSiteAuditSnapshot.class));
            assertThrows(IllegalStateException.class, () -> service.update(fixture.site(), request(fixture.village(), fixture.replacement(), "Changed")));
            assertEquals("Before", service.getById(fixture.site()).name());
            assertEquals(List.of(fixture.source()), links.sourceIds(fixture.site()));
            assertThrows(IllegalStateException.class, () -> service.delete(fixture.site()));
            assertTrue(repository.existsById(fixture.site()));
            assertEquals(List.of(fixture.source()), links.sourceIds(fixture.site()));
            reset(audits);
            assertThrows(org.springframework.web.server.ResponseStatusException.class,
                    () -> service.create(request(fixture.village(), Long.MAX_VALUE, "Invalid source")));
            assertEquals(1, service.getAll(0, 20, fixture.village()).totalElements());
            assertThrows(org.springframework.web.server.ResponseStatusException.class,
                    () -> service.update(fixture.site(), request(fixture.village(), Long.MAX_VALUE, "Changed")));
            assertEquals("Before", service.getById(fixture.site()).name());
            assertEquals(List.of(fixture.source()), links.sourceIds(fixture.site()));
        } finally { cleanup(fixture); }
    }

    private record Fixture(long site, long village, long source, long replacement, long governorate) {}

    private Fixture committedFixture() {
        HeritageSiteServiceTest.role("ADMIN");
        return new TransactionTemplate(transactions).execute(tx -> {
            long village = village();
            long source = source();
            long replacement = source();
            long governorate = jdbc.queryForObject("SELECT governorate_id FROM public.villages WHERE id=?", Long.class, village);
            long id = service.create(request(village, source, "Before")).id();
            return new Fixture(id, village, source, replacement, governorate);
        });
    }

    private void cleanup(Fixture fixture) {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            jdbc.update("DELETE FROM public.heritage_sites WHERE id=?", fixture.site());
            jdbc.update("DELETE FROM public.sources WHERE id=?", fixture.source());
            jdbc.update("DELETE FROM public.sources WHERE id=?", fixture.replacement());
            jdbc.update("DELETE FROM public.villages WHERE id=?", fixture.village());
            jdbc.update("DELETE FROM public.governorates WHERE id=?", fixture.governorate());
        });
    }

    @Test void sourceAndAuditFailuresRollBackSiteAndAssociations() {
        for (boolean auditFailure : List.of(false, true)) {
            long[] id = {0};
            doAnswer(call -> { id[0] = call.getArgument(0); throw new IllegalStateException("controlled audit failure"); })
                    .when(audits).recordHeritageSite(anyLong(), any(), nullable(HeritageSiteAuditSnapshot.class), nullable(HeritageSiteAuditSnapshot.class));
            HeritageSiteServiceTest.role("ADMIN");
            assertThrows(RuntimeException.class, () -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                long village = village();
                long source = source();
                if (auditFailure) service.create(request(village, source, "Rolled back"));
                else {
                    // Existing row and valid association must also roll back on replacement failure.
                    reset(audits);
                    id[0] = service.create(request(village, source, "Before")).id();
                    service.update(id[0], request(village, Long.MAX_VALUE, "After"));
                }
            }));
            assertFalse(repository.existsById(id[0]));
            assertThrows(NoSuchElementException.class, () -> links.sourceIds(id[0]));
            reset(audits);
        }
    }

    /** Guard before context refresh; migrations are disabled even after dedicated DB verification. */
    static class Guard implements org.springframework.context.ApplicationContextInitializer<org.springframework.context.ConfigurableApplicationContext> {
        @Override public void initialize(org.springframework.context.ConfigurableApplicationContext context) {
            if (!"verified-test-db".equals(System.getenv("QARYATI_SITE_CRUD_TESTS")))
                throw new IllegalStateException("Explicit dedicated database opt-in required");
            new HistoricalEventRepositoryIntegrationTest.DatabaseGuard().initialize(context);
            context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
                    "site-crud-no-migrations", Map.of("spring.flyway.enabled", "false")));
        }
    }
}
