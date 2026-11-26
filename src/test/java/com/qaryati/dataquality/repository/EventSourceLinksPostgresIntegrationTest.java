package com.qaryati.dataquality.repository;

import com.qaryati.dataquality.api.*;
import com.qaryati.dataquality.service.EventSourceLinksService;
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
@EnabledIfEnvironmentVariable(named = "QARYATI_EVENT_LINK_TESTS", matches = "verified-test-db")
@SpringBootTest(classes = EventSourceLinksPostgresIntegrationTest.Config.class)
@ActiveProfiles("test")
@ContextConfiguration(initializers = EventSourceLinksPostgresIntegrationTest.Guard.class)
class EventSourceLinksPostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired EventSourceLinks links;
    @Autowired PlatformTransactionManager transactions;
    @Test void persistsReplacesAndCascadesWithinRolledBackFixture() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            long event = event();
            long first = source();
            long second = source();
            links.linkNewEvent(event, List.of(second, first, first));
            assertEquals(List.of(first, second), links.sourceIds(event));
            links.replaceSources(event, List.of(second));
            assertEquals(List.of(second), links.sourceIds(event));
            jdbc.update("DELETE FROM public.historical_events WHERE id=?", event);
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM public.event_sources WHERE event_id=?", Integer.class, event));
            tx.setRollbackOnly();
        });
    }
    @Test void constraintsAndFailureRollbackPreserveOriginalSet() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            long event = event();
            long source = source();
            links.linkNewEvent(event, List.of(source));
            constraint("23505", "INSERT INTO public.event_sources VALUES (?,?)", event, source);
            constraint("23503", "DELETE FROM public.sources WHERE id=?", source);
            constraint("23503", "INSERT INTO public.event_sources VALUES (?,?)", event, -1L);
            constraint("23503", "INSERT INTO public.event_sources VALUES (?,?)", -1L, source);
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
            links.linkNewEvent(id[0], List.of(source()));
            throw new IllegalStateException("enclosing audit/mutation failure");
        }));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM public.historical_events WHERE id=?", Integer.class, id[0]));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM public.event_sources WHERE event_id=?", Integer.class, id[0]));
    }
    private long event() {
        long governorate = jdbc.queryForObject("INSERT INTO public.governorates(name_ar) VALUES (?) RETURNING id", Long.class, UUID.randomUUID().toString());
        long village = jdbc.queryForObject("INSERT INTO public.villages(governorate_id,name_ar,location_description) VALUES (?,'test','test') RETURNING id", Long.class, governorate);
        return jdbc.queryForObject("INSERT INTO public.historical_events(village_id,title,description,date_precision) VALUES (?,'test','test','UNKNOWN') RETURNING id", Long.class, village);
    }
    private long source() {
        return jdbc.queryForObject("INSERT INTO public.sources(title,source_type) VALUES ('test','BOOK') RETURNING id", Long.class);
    }
    @Configuration(proxyBeanMethods = false) @EnableAutoConfiguration
    @EntityScan(basePackageClasses = Source.class)
    @org.springframework.data.jpa.repository.config.EnableJpaRepositories(basePackageClasses = SourceRepository.class)
    @Import(EventSourceLinksService.class)
    static class Config {
        @Bean EventSourceLinkRepository repository(EntityManager em) { return new EventSourceLinkRepository(em); }
        @Bean SourceLookup sources(SourceRepository repository) {
            return new com.qaryati.dataquality.service.SourceLookupService(repository);
        }
    }
    static class Guard implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        static final String APPROVAL_VARIABLE = "QARYATI_EVENT_LINK_TESTS";
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
                        throw new IllegalStateException("Unsupported Flyway override for event source link tests");
                    }
                }
            }
            DedicatedPostgresSafety.initialize(context, approved, true);
        }
    }
}
