package com.qaryati.dataquality.repository;

import com.qaryati.dataquality.api.*;
import com.qaryati.dataquality.service.CulturalHeritageSourceLinksService;
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
@EnabledIfEnvironmentVariable(named = "QARYATI_CULTURAL_LINK_TESTS", matches = "verified-test-db")
@SpringBootTest(classes = CulturalHeritageSourceLinksPostgresIntegrationTest.Config.class)
@ActiveProfiles("test")
@ContextConfiguration(initializers = CulturalHeritageSourceLinksPostgresIntegrationTest.Guard.class)
class CulturalHeritageSourceLinksPostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired CulturalHeritageSourceLinks links;
    @Autowired PlatformTransactionManager transactions;
    @Test void persistsReplacesAndCascadesWithinRolledBackFixture() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            long heritage = heritage();
            long first = source();
            long second = source();
            links.linkNewCulturalHeritage(heritage, List.of(second, first, first));
            assertEquals(List.of(first, second), links.sourceIds(heritage));
            links.replaceSources(heritage, List.of(second));
            assertEquals(List.of(second), links.sourceIds(heritage));
            jdbc.update("DELETE FROM public.cultural_heritage WHERE id=?", heritage);
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM public.cultural_heritage_sources WHERE cultural_heritage_id=?", Integer.class, heritage));
            tx.setRollbackOnly();
        });
    }
    @Test void constraintsAndFailureRollbackPreserveOriginalSet() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            long heritage = heritage();
            long source = source();
            links.linkNewCulturalHeritage(heritage, List.of(source));
            constraint("23505", "INSERT INTO public.cultural_heritage_sources VALUES (?,?)", heritage, source);
            constraint("23503", "DELETE FROM public.sources WHERE id=?", source);
            constraint("23503", "INSERT INTO public.cultural_heritage_sources VALUES (?,?)", heritage, -1L);
            constraint("23503", "INSERT INTO public.cultural_heritage_sources VALUES (?,?)", -1L, source);
            assertEquals(List.of(source), links.sourceIds(heritage));
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
    @Test void enclosingFailureRollsBackNewCulturalHeritageAndLinks() {
        long[] id = {0};
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            id[0] = heritage();
            links.linkNewCulturalHeritage(id[0], List.of(source()));
            throw new IllegalStateException("enclosing audit/mutation failure");
        }));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM public.cultural_heritage WHERE id=?", Integer.class, id[0]));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM public.cultural_heritage_sources WHERE cultural_heritage_id=?", Integer.class, id[0]));
    }
    @Test void batchReadsAndParentLockContention() {
        long[] fixture = new TransactionTemplate(transactions).execute(tx -> {
            long heritage = heritage();
            long source = source();
            links.linkNewCulturalHeritage(heritage, List.of(source));
            long village = jdbc.queryForObject("SELECT village_id FROM public.cultural_heritage WHERE id=?", Long.class, heritage);
            long governorate = jdbc.queryForObject("SELECT governorate_id FROM public.villages WHERE id=?", Long.class, village);
            return new long[]{heritage, source, village, governorate};
        });
        try {
            assertEquals(Map.of(fixture[0], List.of(fixture[1])), links.sourceIdsByCulturalHeritageIds(List.of(fixture[0])));
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                jdbc.queryForObject("SELECT id FROM public.cultural_heritage WHERE id=? FOR UPDATE", Long.class, fixture[0]);
                var env = context.getEnvironment();
                try (var connection = DriverManager.getConnection(env.getRequiredProperty("spring.datasource.url"),
                        env.getRequiredProperty("spring.datasource.username"), env.getRequiredProperty("spring.datasource.password"))) {
                    connection.setAutoCommit(false);
                    try (var timeout = connection.createStatement()) { timeout.execute("SET LOCAL lock_timeout = '200ms'"); }
                    try (var statement = connection.prepareStatement("SELECT id FROM public.cultural_heritage WHERE id=? FOR UPDATE")) {
                        statement.setLong(1, fixture[0]);
                        assertEquals("55P03", assertThrows(SQLException.class, statement::executeQuery).getSQLState());
                    } finally { connection.rollback(); }
                } catch (SQLException failure) { throw new AssertionError(failure); }
            });
        } finally {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                jdbc.update("DELETE FROM public.cultural_heritage WHERE id=?", fixture[0]);
                jdbc.update("DELETE FROM public.sources WHERE id=?", fixture[1]);
                jdbc.update("DELETE FROM public.villages WHERE id=?", fixture[2]);
                jdbc.update("DELETE FROM public.governorates WHERE id=?", fixture[3]);
            });
        }
    }
    @Autowired ConfigurableApplicationContext context;
    private long heritage() {
        long governorate = jdbc.queryForObject("INSERT INTO public.governorates(name_ar) VALUES (?) RETURNING id", Long.class, UUID.randomUUID().toString());
        long village = jdbc.queryForObject("INSERT INTO public.villages(governorate_id,name_ar,location_description) VALUES (?,'test','test') RETURNING id", Long.class, governorate);
        return jdbc.queryForObject("INSERT INTO public.cultural_heritage(village_id,name,heritage_type,description) VALUES (?,'test','CRAFT','description') RETURNING id", Long.class, village);
    }
    private long source() {
        return jdbc.queryForObject("INSERT INTO public.sources(title,source_type) VALUES ('test','BOOK') RETURNING id", Long.class);
    }
    @Configuration(proxyBeanMethods = false) @EnableAutoConfiguration
    @EntityScan(basePackageClasses = Source.class)
    @org.springframework.data.jpa.repository.config.EnableJpaRepositories(basePackageClasses = SourceRepository.class)
    @Import(CulturalHeritageSourceLinksService.class)
    static class Config {
        @Bean CulturalHeritageSourceLinkRepository repository(EntityManager em) { return new CulturalHeritageSourceLinkRepository(em); }
        @Bean SourceLookup sources(SourceRepository repository) {
            return new com.qaryati.dataquality.service.SourceLookupService(repository);
        }
    }
    static class Guard implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        public void initialize(ConfigurableApplicationContext context) {
            var env = context.getEnvironment();
            String url = "jdbc:postgresql://localhost:5432/qaryati_test_db";
            if (!"verified-test-db".equals(System.getenv("QARYATI_CULTURAL_LINK_TESTS"))
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
            env.getPropertySources().addFirst(new MapPropertySource("cultural-link-test-safety", Map.of(
                    "spring.datasource.url", url, "spring.datasource.hikari.jdbc-url", url,
                    "spring.flyway.enabled", "false", "spring.jpa.hibernate.ddl-auto", "none",
                    "spring.jpa.properties.hibernate.hbm2ddl.auto", "none",
                    "spring.jpa.properties.jakarta.persistence.schema-generation.database.action", "none")));
        }
    }
}
