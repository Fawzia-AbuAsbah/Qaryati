package com.qaryati.heritage;

import com.qaryati.heritage.dto.HistoricalEventRequest;
import com.qaryati.heritage.entity.*;
import com.qaryati.heritage.repository.HistoricalEventRepository;
import jakarta.persistence.EntityManager;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.*;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = HistoricalEventRepositoryIntegrationTest.PersistenceConfiguration.class,
        properties = {"spring.flyway.clean-disabled=true", "spring.jpa.hibernate.ddl-auto=validate"})
@ActiveProfiles("test")
@ContextConfiguration(initializers = HistoricalEventRepositoryIntegrationTest.DatabaseGuard.class)
@Transactional
class HistoricalEventRepositoryIntegrationTest {
    private static final String URL = "jdbc:postgresql://localhost:5432/qaryati_test_db";
    @Autowired HistoricalEventRepository repository;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    private Long village;
    private Long otherVillage;

    @BeforeEach void fixtures() {
        Long governorate = jdbc.queryForObject("INSERT INTO public.governorates(name_ar) VALUES (?) RETURNING id",
                Long.class, "heritage-test-" + UUID.randomUUID());
        village = village(governorate);
        otherVillage = village(governorate);
    }

    private Long village(Long governorate) {
        return jdbc.queryForObject("""
                INSERT INTO public.villages(governorate_id,name_ar,location_description)
                VALUES (?,?,'Isolated historical event test fixture') RETURNING id
                """, Long.class, governorate, "heritage-test-" + UUID.randomUUID());
    }

    @ParameterizedTest @EnumSource(DatePrecision.class)
    void persistsAndRetrievesEveryPrecision(DatePrecision precision) {
        LocalDate date = precision == DatePrecision.EXACT ? LocalDate.of(2500, 1, 1) : null;
        Integer from = precision == DatePrecision.YEAR || precision == DatePrecision.PERIOD ? 1 : null;
        Integer to = precision == DatePrecision.PERIOD ? 2200 : null;
        var saved = repository.saveAndFlush(HistoricalEvent.draft(new HistoricalEventRequest(
                village, "x".repeat(250), "Description", date, from, to, precision)));
        Long id = saved.getId();
        assertNotNull(saved.getCreatedAt());
        entityManager.clear();
        var found = repository.findByIdAndVillageId(id, village).orElseThrow();
        assertEquals(precision, found.getDatePrecision());
        assertEquals(date, found.getEventDate());
        assertEquals(from, found.getYearFrom());
        assertEquals(to, found.getYearTo());
        assertEquals("x".repeat(250), found.getTitle());
        assertEquals("Description", found.getDescription());
        assertEquals(VerificationStatus.DRAFT, found.getVerificationStatus());
        assertTrue(repository.findByIdAndVillageId(id, otherVillage).isEmpty());
        found.reviseDraft(new HistoricalEventRequest(village, "Revised", "Changed", null, null, null, DatePrecision.UNKNOWN));
        repository.flush();
        entityManager.clear();
        assertEquals("Revised", repository.findByIdAndVillageId(id, village).orElseThrow().getTitle());
    }

    @Test void deterministicPagesStayWithinVillage() {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ids.add(repository.saveAndFlush(draft(village)).getId());
            repository.saveAndFlush(draft(otherVillage));
        }
        entityManager.clear();
        List<Long> actual = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            var result = repository.findVillagePage(village, page, 2);
            assertEquals(5, result.getTotalElements());
            actual.addAll(result.getContent().stream().map(HistoricalEvent::getId).toList());
            assertEquals(result.getContent().stream().map(HistoricalEvent::getId).toList(),
                    repository.findVillagePage(village, page, 2).stream().map(HistoricalEvent::getId).toList());
        }
        assertEquals(ids, actual);
    }

    static Stream<Arguments> dateCombinations() { return HistoricalEventTest.dateCombinations(); }

    @ParameterizedTest @MethodSource("dateCombinations")
    void databaseEnforcesAllPrecisionCombinations(DatePrecision precision, LocalDate date,
            Integer from, Integer to, boolean valid) {
        if (valid) assertDoesNotThrow(() -> insert(village, "Title", "Description", date, from, to, precision.name(), "DRAFT"));
        else assertState("23514", () -> insert(village, "Title", "Description", date, from, to, precision.name(), "DRAFT"));
    }

    @ParameterizedTest @CsvSource({"0,1", "1,2201", "1900,1899", "2201,2201", "1,0"})
    void databaseRejectsInvalidYears(int from, int to) {
        assertState("23514", () -> insert(village, "Title", "Description", null, from, to, "PERIOD", "DRAFT"));
    }

    @ParameterizedTest @ValueSource(strings = {"title", "description", "village_id", "date_precision", "verification_status", "created_at"})
    void databaseRejectsRequiredNulls(String column) {
        Long id = repository.saveAndFlush(draft(village)).getId();
        assertState("23502", () -> jdbc.update("UPDATE public.historical_events SET " + column + "=NULL WHERE id=?", id));
    }

    @Test void databaseRejectsOversizedTitle() {
        assertState("22001", () -> insert(village, "x".repeat(251), "Description", null, null, null, "UNKNOWN", "DRAFT"));
    }
    @Test void databaseRejectsUnknownStatus() {
        assertState("23514", () -> insert(village, "Title", "Description", null, null, null, "UNKNOWN", "INVALID"));
    }
    @Test void databaseRejectsUnknownPrecision() {
        assertState("23514", () -> insert(village, "Title", "Description", null, null, null, "INVALID", "DRAFT"));
    }
    @Test void databaseRejectsMissingVillage() {
        Long absent = jdbc.queryForObject("SELECT COALESCE(MAX(id),0)+1 FROM public.villages", Long.class);
        assertState("23503", () -> insert(absent, "Title", "Description", null, null, null, "UNKNOWN", "DRAFT"));
    }
    @Test void databaseRestrictsDeletingReferencedVillage() {
        repository.saveAndFlush(draft(village));
        assertState("23503", () -> jdbc.update("DELETE FROM public.villages WHERE id=?", village));
    }
    @Test void databaseDefaultsStatusAndTimestamp() {
        Long id = jdbc.queryForObject("""
                INSERT INTO public.historical_events(village_id,title,description,date_precision)
                VALUES (?,'Title','Description','UNKNOWN') RETURNING id
                """, Long.class, village);
        var event = repository.findByIdAndVillageId(id, village).orElseThrow();
        assertEquals(VerificationStatus.DRAFT, event.getVerificationStatus());
        assertNotNull(event.getCreatedAt());
    }
    @ParameterizedTest @EnumSource(VerificationStatus.class)
    void loadsEveryDatabaseStatusAndGuardsMutation(VerificationStatus status) {
        Long id = insert(village, "Title", "Description", null, null, null, "UNKNOWN", status.name());
        var event = repository.findByIdAndVillageId(id, village).orElseThrow();
        assertEquals(status, event.getVerificationStatus());
        if (status == VerificationStatus.DRAFT) assertDoesNotThrow(event::requireDraftMutation);
        else assertThrows(IllegalStateException.class, () -> event.reviseDraft(new HistoricalEventRequest(
                village, "Changed", "Description", null, null, null, DatePrecision.UNKNOWN)));
    }

    private HistoricalEvent draft(Long villageId) {
        return HistoricalEvent.draft(new HistoricalEventRequest(villageId, "Same title", "Description",
                null, null, null, DatePrecision.UNKNOWN));
    }
    private Long insert(Long villageId, String title, String description, LocalDate date,
            Integer from, Integer to, String precision, String status) {
        return jdbc.queryForObject("""
                INSERT INTO public.historical_events(village_id,title,description,event_date,year_from,year_to,date_precision,verification_status)
                VALUES (?,?,?,?,?,?,?,?) RETURNING id
                """, Long.class, villageId, title, description, date, from, to, precision, status);
    }
    private void assertState(String state, Runnable operation) {
        RuntimeException error = assertThrows(RuntimeException.class, operation::run);
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) { assertEquals(state, sql.getSQLState()); return; }
        }
        fail("Missing SQLException", error);
        // No further SQL after a PostgreSQL error; the transaction is rolled back.
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = HistoricalEvent.class)
    @EnableJpaRepositories(basePackageClasses = HistoricalEventRepository.class)
    static class PersistenceConfiguration {
        @Bean DataSource dataSource(org.springframework.core.env.Environment env) {
            return new DriverManagerDataSource(URL, env.getRequiredProperty("spring.datasource.username"),
                    env.getRequiredProperty("spring.datasource.password"));
        }
    }

    /** Checks live identity before context refresh, so Flyway cannot run first. */
    static class DatabaseGuard implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override public void initialize(ConfigurableApplicationContext context) {
            var env = context.getEnvironment();
            if (!Arrays.asList(env.getActiveProfiles()).contains("test")
                    || !env.getProperty("qaryati.heritage-tests.database-verified", Boolean.class, false)
                    || !URL.equals(env.getProperty("spring.datasource.url")))
                throw new IllegalStateException("Independently verify dedicated qaryati_test_db and explicitly enable heritage tests");
            for (String key : List.of("spring.flyway.url", "spring.datasource.hikari.jdbc-url",
                    "spring.jpa.properties.jakarta.persistence.jdbc.url")) {
                String value = env.getProperty(key);
                if (value != null && !URL.equals(value)) throw new IllegalStateException("Unsafe database override: " + key);
            }
            String username = env.getRequiredProperty("spring.datasource.username");
            String password = env.getRequiredProperty("spring.datasource.password");
            try (Connection connection = DriverManager.getConnection(URL, username, password);
                    Statement statement = connection.createStatement();
                    ResultSet rs = statement.executeQuery("SELECT current_database(), current_user")) {
                if (!"PostgreSQL".equals(connection.getMetaData().getDatabaseProductName()) || !rs.next()
                        || !"qaryati_test_db".equals(rs.getString(1)) || !username.equals(rs.getString(2)))
                    throw new IllegalStateException("Test database identity mismatch");
            } catch (SQLException exception) {
                throw new IllegalStateException("Cannot independently verify test database identity", exception);
            }
            // Lock migration and ORM safety properties after verifying the same connection credentials.
            env.getPropertySources().addFirst(new MapPropertySource("heritage-test-safety", Map.of(
                    "spring.flyway.url", URL, "spring.flyway.user", username, "spring.flyway.password", password,
                    "spring.flyway.clean-disabled", "true", "spring.flyway.enabled", "true",
                    "spring.jpa.hibernate.ddl-auto", "validate",
                    "spring.jpa.properties.hibernate.hbm2ddl.auto", "validate",
                    "spring.jpa.properties.jakarta.persistence.schema-generation.database.action", "none")));
        }
    }
}
