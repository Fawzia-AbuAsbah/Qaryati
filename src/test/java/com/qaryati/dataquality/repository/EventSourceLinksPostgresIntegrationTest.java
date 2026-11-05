package com.qaryati.dataquality.repository;

import com.qaryati.dataquality.api.*;
import com.qaryati.dataquality.service.EventSourceLinksService;
import com.qaryati.dataquality.entity.Source;
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
import org.springframework.core.env.MapPropertySource;
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
        public void initialize(ConfigurableApplicationContext context) {
            var env = context.getEnvironment();
            String url = "jdbc:postgresql://localhost:5432/qaryati_test_db";
            if (!"verified-test-db".equals(System.getenv("QARYATI_EVENT_LINK_TESTS"))
                    || !Arrays.asList(env.getActiveProfiles()).contains("test")
                    || !url.equals(env.getProperty("spring.datasource.url"))) throw new IllegalStateException("Dedicated test database opt-in required");
            String username = env.getRequiredProperty("spring.datasource.username");
            String password = env.getRequiredProperty("spring.datasource.password");
            try (var connection = DriverManager.getConnection(url, username, password);
                    var statement = connection.createStatement();
                    var result = statement.executeQuery("SELECT current_database(), current_user")) {
                if (!"PostgreSQL".equals(connection.getMetaData().getDatabaseProductName()) || !result.next()
                        || !"qaryati_test_db".equals(result.getString(1)) || !username.equals(result.getString(2)))
                    throw new IllegalStateException("Database identity mismatch");
            } catch (SQLException error) { throw new IllegalStateException("Database identity verification failed", error); }
            env.getPropertySources().addFirst(new MapPropertySource("event-link-test-safety", Map.of(
                    "spring.datasource.url", url, "spring.datasource.hikari.jdbc-url", url,
                    "spring.flyway.enabled", "false", "spring.jpa.hibernate.ddl-auto", "none",
                    "spring.jpa.properties.hibernate.hbm2ddl.auto", "none",
                    "spring.jpa.properties.jakarta.persistence.schema-generation.database.action", "none")));
        }
    }
}
