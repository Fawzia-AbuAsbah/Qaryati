package com.qaryati.dataquality.repository;

import com.qaryati.dataquality.api.*;
import com.qaryati.dataquality.service.VillageHistorySourceLinksService;
import com.qaryati.dataquality.entity.Source;
import jakarta.persistence.EntityManager;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in only. Requires an independently verified, already migrated dedicated database. */
@EnabledIfEnvironmentVariable(named = "QARYATI_HISTORY_LINK_TESTS", matches = "verified-test-db")
@SpringBootTest(classes = VillageHistorySourceLinksPostgresIntegrationTest.Config.class)
@ActiveProfiles("test")
@ContextConfiguration(initializers = VillageHistorySourceLinksPostgresIntegrationTest.Guard.class)
class VillageHistorySourceLinksPostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired VillageHistorySourceLinks links;
    @Autowired PlatformTransactionManager transactions;
    @Test void persistsReplacesAndCascadesWithinRolledBackFixture() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            long event = event();
            long first = source();
            long second = source();
            links.linkNewHistory(event, List.of(second, first, first));
            assertEquals(List.of(first, second), links.sourceIds(event));
            links.replaceSources(event, List.of(second));
            assertEquals(List.of(second), links.sourceIds(event));
            jdbc.update("DELETE FROM public.village_histories WHERE id=?", event);
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM public.history_sources WHERE village_history_id=?", Integer.class, event));
            tx.setRollbackOnly();
        });
    }
    @Test void constraintsAndFailureRollbackPreserveOriginalSet() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            long event = event();
            long source = source();
            links.linkNewHistory(event, List.of(source));
            constraint("23505", "INSERT INTO public.history_sources VALUES (?,?)", event, source);
            constraint("23503", "DELETE FROM public.sources WHERE id=?", source);
            constraint("23503", "INSERT INTO public.history_sources VALUES (?,?)", event, com.qaryati.heritage.MissingFixtureId.in(jdbc, "sources"));
            constraint("23503", "INSERT INTO public.history_sources VALUES (?,?)", com.qaryati.heritage.MissingFixtureId.in(jdbc, "village_histories"), source);
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
            links.linkNewHistory(id[0], List.of(source()));
            throw new IllegalStateException("enclosing audit/mutation failure");
        }));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM public.village_histories WHERE id=?", Integer.class, id[0]));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM public.history_sources WHERE village_history_id=?", Integer.class, id[0]));
    }
    @Test void batchReadsAndHistoryLockContention() {
        long[] fixture = new TransactionTemplate(transactions).execute(tx -> {
            long history = event();
            long source = source();
            links.linkNewHistory(history, List.of(source));
            long village = jdbc.queryForObject("SELECT village_id FROM public.village_histories WHERE id=?", Long.class, history);
            long governorate = jdbc.queryForObject("SELECT governorate_id FROM public.villages WHERE id=?", Long.class, village);
            return new long[]{history, source, village, governorate};
        });
        try {
            assertEquals(Map.of(fixture[0], List.of(fixture[1])), links.sourceIdsByHistoryIds(List.of(fixture[0])));
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                jdbc.queryForObject("SELECT id FROM public.village_histories WHERE id=? FOR UPDATE", Long.class, fixture[0]);
                var env = context.getEnvironment();
                try (var connection = DriverManager.getConnection(env.getRequiredProperty("spring.datasource.url"),
                        env.getRequiredProperty("spring.datasource.username"), env.getRequiredProperty("spring.datasource.password"))) {
                    connection.setAutoCommit(false);
                    try (var timeout = connection.createStatement()) { timeout.execute("SET LOCAL lock_timeout = '200ms'"); }
                    try (var statement = connection.prepareStatement("SELECT id FROM public.village_histories WHERE id=? FOR UPDATE")) {
                        statement.setLong(1, fixture[0]);
                        assertEquals("55P03", assertThrows(SQLException.class, statement::executeQuery).getSQLState());
                    } finally { connection.rollback(); }
                } catch (SQLException failure) { throw new AssertionError(failure); }
            });
        } finally {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                jdbc.update("DELETE FROM public.village_histories WHERE id=?", fixture[0]);
                jdbc.update("DELETE FROM public.sources WHERE id=?", fixture[1]);
                jdbc.update("DELETE FROM public.villages WHERE id=?", fixture[2]);
                jdbc.update("DELETE FROM public.governorates WHERE id=?", fixture[3]);
            });
        }
    }
    @Autowired ConfigurableApplicationContext context;
    private long event() {
        long governorate = jdbc.queryForObject("INSERT INTO public.governorates(name_ar) VALUES (?) RETURNING id", Long.class, UUID.randomUUID().toString());
        long village = jdbc.queryForObject("INSERT INTO public.villages(governorate_id,name_ar,location_description) VALUES (?,'test','test') RETURNING id", Long.class, governorate);
        return jdbc.queryForObject("INSERT INTO public.village_histories(village_id,overview) VALUES (?,'test') RETURNING id", Long.class, village);
    }
    private long source() {
        return jdbc.queryForObject("INSERT INTO public.sources(title,source_type) VALUES ('test','BOOK') RETURNING id", Long.class);
    }
    @org.springframework.boot.test.context.TestComponent
    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            JdbcTemplateAutoConfiguration.class, TransactionAutoConfiguration.class})
    @org.springframework.transaction.annotation.EnableTransactionManagement
    @EntityScan(basePackageClasses = Source.class)
    @org.springframework.data.jpa.repository.config.EnableJpaRepositories(basePackageClasses = SourceRepository.class,
            includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SourceRepository.class))
    @Import(VillageHistorySourceLinksService.class)
    static class Config {
        @Bean VillageHistorySourceLinkRepository repository(EntityManager em) { return new VillageHistorySourceLinkRepository(em); }
        @Bean SourceLookup sources(SourceRepository repository) {
            return new com.qaryati.dataquality.service.SourceLookupService(repository);
        }
    }
    static class Guard implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        public void initialize(ConfigurableApplicationContext context) {
            com.qaryati.heritage.DedicatedPostgresSafety.initialize(context,
                    "verified-test-db".equals(System.getenv("QARYATI_HISTORY_LINK_TESTS")), false);
        }
    }
}
