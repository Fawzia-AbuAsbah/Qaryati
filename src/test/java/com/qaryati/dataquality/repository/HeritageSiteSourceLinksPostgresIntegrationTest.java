package com.qaryati.dataquality.repository;

import com.qaryati.dataquality.api.*;
import com.qaryati.dataquality.service.HeritageSiteSourceLinksService;
import com.qaryati.dataquality.service.AuditWriterService;
import com.qaryati.dataquality.service.AuditLogService;
import com.qaryati.dataquality.entity.Source;
import com.qaryati.heritage.DedicatedPostgresSafety;
import jakarta.persistence.EntityManager;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in only. Requires an independently verified, already migrated dedicated database. */
@EnabledIfEnvironmentVariable(named = "QARYATI_SITE_LINK_TESTS", matches = "verified-test-db")
@SpringBootTest(classes = HeritageSiteSourceLinksPostgresIntegrationTest.Config.class)
@ActiveProfiles("test")
@ContextConfiguration(initializers = HeritageSiteSourceLinksPostgresIntegrationTest.Guard.class)
class HeritageSiteSourceLinksPostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired HeritageSiteSourceLinks links;
    @Autowired PlatformTransactionManager transactions;
    @Test void persistsReplacesAndCascadesWithinRolledBackFixture() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            long event = event();
            long first = source();
            long second = source();
            links.linkNewSite(event, List.of(second, first, first));
            assertEquals(List.of(first, second), links.sourceIds(event));
            assertEquals(Map.of(event, List.of(first, second)), links.sourceIdsBySiteIds(List.of(event)));
            links.replaceSources(event, List.of(second));
            assertEquals(List.of(second), links.sourceIds(event));
            jdbc.update("DELETE FROM public.heritage_sites WHERE id=?", event);
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM public.heritage_site_sources WHERE heritage_site_id=?", Integer.class, event));
            tx.setRollbackOnly();
        });
    }
    @Test void constraintsAndFailureRollbackPreserveOriginalSet() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            long event = event();
            long source = source();
            links.linkNewSite(event, List.of(source));
            constraint("23505", "INSERT INTO public.heritage_site_sources VALUES (?,?)", event, source);
            constraint("23503", "DELETE FROM public.sources WHERE id=?", source);
            constraint("23503", "INSERT INTO public.heritage_site_sources VALUES (?,?)", event, -1L);
            constraint("23503", "INSERT INTO public.heritage_site_sources VALUES (?,?)", -1L, source);
            assertEquals(List.of(source), links.sourceIds(event));
            tx.setRollbackOnly();
        });
    }
    private void constraint(String state, String sql, Long... ids) {
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            var savepoint = connection.setSavepoint();
            try (var statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < ids.length; i++) statement.setLong(i + 1, ids[i]);
                var error = assertThrows(SQLException.class, statement::executeUpdate);
                assertEquals(state, error.getSQLState());
            } finally {
                connection.rollback(savepoint);
                connection.releaseSavepoint(savepoint);
            }
            return null;
        });
    }
    @Test void enclosingFailureRollsBackNewEventAndLinks() {
        long[] id = {0};
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            id[0] = event();
            links.linkNewSite(id[0], List.of(source()));
            throw new IllegalStateException("enclosing audit/mutation failure");
        }));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM public.heritage_sites WHERE id=?", Integer.class, id[0]));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM public.heritage_site_sources WHERE heritage_site_id=?", Integer.class, id[0]));
    }
    private long event() {
        long governorate = jdbc.queryForObject("INSERT INTO public.governorates(name_ar) VALUES (?) RETURNING id", Long.class, UUID.randomUUID().toString());
        long village = jdbc.queryForObject("INSERT INTO public.villages(governorate_id,name_ar,location_description) VALUES (?,'test','test') RETURNING id", Long.class, governorate);
        return jdbc.queryForObject("INSERT INTO public.heritage_sites(village_id,name,description,site_type) VALUES (?,'test','test','OTHER') RETURNING id", Long.class, village);
    }
    @Test void linkingAndRealAuditFailuresRollBackTheEnclosingMutation() {
        for (boolean auditFailure : List.of(false, true)) {
            long[] id = {0};
            assertThrows(RuntimeException.class, () -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                try {
                    id[0] = event();
                    long source = source();
                    links.linkNewSite(id[0], List.of(source));
                    if (auditFailure) {
                        org.springframework.security.core.context.SecurityContextHolder.clearContext();
                        var snapshot = new HeritageSiteAuditSnapshot(1L, "test", "OTHER", null,
                                null, null, null, List.of(source), "DRAFT");
                        audit.recordHeritageSite(id[0], AuditAction.CREATE, null, snapshot);
                    } else {
                        links.replaceSources(id[0], List.of(Long.MAX_VALUE));
                    }
                } finally {
                    tx.setRollbackOnly();
                }
            }));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM public.heritage_sites WHERE id=?", Integer.class, id[0]));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM public.heritage_site_sources WHERE heritage_site_id=?", Integer.class, id[0]));
        }
    }

    @Autowired AuditWriter audit;

    @Test void linkWriteAcquiresPostgresParentLockUntilTransactionEnds() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            long site = event();
            links.linkNewSite(site, List.of(source()));
            assertTrue(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM pg_locks WHERE pid=pg_backend_pid()"
                    + " AND relation='public.heritage_sites'::regclass AND mode='RowShareLock' AND granted)", Boolean.class));
            tx.setRollbackOnly();
        });
    }
    private long source() {
        return jdbc.queryForObject("INSERT INTO public.sources(title,source_type) VALUES ('test','BOOK') RETURNING id", Long.class);
    }
    @Configuration(proxyBeanMethods = false) @EnableAutoConfiguration
    @EntityScan(basePackageClasses = Source.class)
    @org.springframework.data.jpa.repository.config.EnableJpaRepositories(basePackageClasses = SourceRepository.class)
    @Import({HeritageSiteSourceLinksService.class, AuditWriterService.class, AuditLogService.class})
    static class Config {
        @Bean com.qaryati.security.api.UserLookup users() {
            return username -> Optional.empty();
        }
        @Bean HeritageSiteSourceLinkRepository repository(EntityManager em) { return new HeritageSiteSourceLinkRepository(em); }
        @Bean SourceLookup sources(SourceRepository repository) {
            return new com.qaryati.dataquality.service.SourceLookupService(repository);
        }
    }
    static class Guard implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        static final String APPROVAL_VARIABLE = "QARYATI_SITE_LINK_TESTS";
        private final boolean approved;

        public Guard() { this(System.getenv(APPROVAL_VARIABLE)); }

        // Offline test seam; the Spring initializer still reads only the original environment variable.
        Guard(String approval) { approved = "verified-test-db".equals(approval); }

        @Override
        public void initialize(ConfigurableApplicationContext context) {
            var env = context.getEnvironment();
            // Ordinary integration tests must not configure a separate Flyway datasource or migration setup.
            for (var source : env.getPropertySources()) {
                if (!(source instanceof EnumerablePropertySource<?> enumerable)) continue;
                for (String name : enumerable.getPropertyNames()) {
                    String key = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
                    if (key.startsWith("springflyway") && !key.equals("springflywayenabled")
                            && !key.equals("springflywaycleandisabled")) {
                        throw new IllegalStateException("Unsupported Flyway override for heritage site source link tests");
                    }
                }
            }
            DedicatedPostgresSafety.initialize(context, approved, true);
        }
    }
}
