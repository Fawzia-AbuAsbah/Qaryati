package com.qaryati.village.repository;

import com.qaryati.village.entity.Governorate;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PostgreSQL tests against the Flyway schema, rolled back after each test.
 * Before running, independently verify that qaryati_test_db exists and is
 * dedicated to tests, then explicitly set:
 * -Dqaryati.governorate-tests.database-verified=true
 * Do not run these tests against either development database.
 */
@SpringBootTest(properties = "spring.flyway.clean-disabled=true")
@ActiveProfiles("test")
@ContextConfiguration(initializers =
        GovernorateRepositoryIntegrationTest.VerifiedTestDatabaseInitializer.class)
@Transactional
class GovernorateRepositoryIntegrationTest {

    @Autowired
    private GovernorateRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void savesAndRetrievesGovernorateFromDatabase() {
        Governorate governorate = governorate("نابلس");
        Governorate saved = repository.saveAndFlush(governorate);
        Long id = saved.getId();
        assertNotNull(id);
        entityManager.clear();

        Governorate found = repository.findById(id).orElseThrow();

        assertEquals(id, found.getId());
        assertEquals(governorate.getNameAr(), found.getNameAr());
        assertEquals(governorate.getNameEn(), found.getNameEn());
        assertEquals(governorate.getCode(), found.getCode());
    }

    @Test
    void databaseRejectsDuplicateArabicNames() {
        Governorate first = repository.saveAndFlush(governorate("نابلس"));
        Governorate duplicate = governorate("جنين");
        duplicate.setNameAr(first.getNameAr());
        entityManager.clear();

        DataIntegrityViolationException exception = assertThrows(
                DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(duplicate));

        assertSqlState("23505", exception);
        // No queries after a constraint violation: PostgreSQL aborts the transaction.
    }

    @Test
    void databaseRejectsDuplicateNonNullCodes() {
        Governorate first = repository.saveAndFlush(governorate("نابلس"));
        Governorate duplicate = governorate("جنين");
        duplicate.setCode(first.getCode());
        entityManager.clear();

        DataIntegrityViolationException exception = assertThrows(
                DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(duplicate));

        assertSqlState("23505", exception);
    }

    @Test
    void allowsNullOptionalFieldsAndMultipleNullCodes() {
        Governorate first = governorate("نابلس");
        first.setNameEn(null);
        first.setCode(null);
        Governorate second = governorate("جنين");
        second.setNameEn(null);
        second.setCode(null);
        Long firstId = repository.saveAndFlush(first).getId();
        Long secondId = repository.saveAndFlush(second).getId();
        entityManager.clear();

        assertNotEquals(firstId, secondId);
        for (Long id : new Long[]{firstId, secondId}) {
            Governorate found = repository.findById(id).orElseThrow();
            assertNull(found.getNameEn());
            assertNull(found.getCode());
        }
    }

    @Test
    void uniquenessChecksDetectExistingAndMissingValues() {
        Governorate saved = repository.saveAndFlush(governorate("نابلس"));
        entityManager.clear();

        assertTrue(repository.existsByNameAr(saved.getNameAr()));
        assertTrue(repository.existsByCode(saved.getCode()));
        assertFalse(repository.existsByNameAr("missing_" + UUID.randomUUID()));
        assertFalse(repository.existsByCode(uniqueCode()));
    }

    @Test
    void uniquenessChecksExcludeCurrentIdButDetectAnotherGovernorate() {
        Governorate first = repository.saveAndFlush(governorate("نابلس"));
        Governorate second = repository.saveAndFlush(governorate("جنين"));
        entityManager.clear();

        assertFalse(repository.existsByNameArAndIdNot(first.getNameAr(), first.getId()));
        assertFalse(repository.existsByCodeAndIdNot(first.getCode(), first.getId()));
        assertTrue(repository.existsByNameArAndIdNot(first.getNameAr(), second.getId()));
        assertTrue(repository.existsByCodeAndIdNot(first.getCode(), second.getId()));
        assertFalse(repository.existsByNameArAndIdNot(
                "missing_" + UUID.randomUUID(), first.getId()));
        assertFalse(repository.existsByCodeAndIdNot(uniqueCode(), first.getId()));
    }

    @Test
    void databaseRejectsNullArabicNameEvenWhenBypassingHibernate() {
        DataIntegrityViolationException exception = assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbcTemplate.update(
                        "INSERT INTO public.governorates (name_ar, name_en, code) VALUES (?, ?, ?)",
                        null, "Nablus", uniqueCode()));

        assertSqlState("23502", exception);
    }

    @ParameterizedTest
    @ValueSource(strings = {"name_ar", "name_en", "code"})
    void databaseEnforcesColumnLengthsEvenWhenBypassingHibernate(String column) {
        int maxLength = column.equals("code") ? 32 : 120;
        String oversized = "x".repeat(maxLength + 1);
        Governorate valid = governorate("نابلس");

        DataIntegrityViolationException exception = assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbcTemplate.update(
                        "INSERT INTO public.governorates (name_ar, name_en, code) VALUES (?, ?, ?)",
                        column.equals("name_ar") ? oversized : valid.getNameAr(),
                        column.equals("name_en") ? oversized : valid.getNameEn(),
                        column.equals("code") ? oversized : valid.getCode()));

        assertSqlState("22001", exception);
    }

    @Test
    void databaseAcceptsValuesAtMaximumColumnLengths() {
        Governorate governorate = new Governorate();
        governorate.setNameAr("ن".repeat(84) + UUID.randomUUID());
        governorate.setNameEn("x".repeat(120));
        governorate.setCode(uniqueCode());
        Long id = repository.saveAndFlush(governorate).getId();
        entityManager.clear();

        Governorate found = repository.findById(id).orElseThrow();

        assertEquals(governorate.getNameAr(), found.getNameAr());
        assertEquals(governorate.getNameEn(), found.getNameEn());
        assertEquals(governorate.getCode(), found.getCode());
        assertEquals(120, found.getNameAr().length());
        assertEquals(120, found.getNameEn().length());
        assertEquals(32, found.getCode().length());
    }

    @Test
    void databaseRejectsDeletingReferencedGovernorate() {
        Governorate saved = repository.saveAndFlush(governorate("نابلس"));
        jdbcTemplate.update("""
                INSERT INTO public.local_authorities (governorate_id, name_ar, authority_type)
                VALUES (?, ?, ?)
                """, saved.getId(), "مجلس_" + UUID.randomUUID(), "VILLAGE_COUNCIL");

        DataIntegrityViolationException exception = assertThrows(
                DataIntegrityViolationException.class, () -> {
                    repository.delete(saved);
                    repository.flush();
                });

        assertSqlState("23503", exception);
    }

    private Governorate governorate(String nameAr) {
        Governorate governorate = new Governorate();
        governorate.setNameAr(nameAr + "_" + UUID.randomUUID());
        governorate.setNameEn("Test governorate");
        governorate.setCode(uniqueCode());
        return governorate;
    }

    private String uniqueCode() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private void assertSqlState(String expected, Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof SQLException sqlException) {
                assertEquals(expected, sqlException.getSQLState());
                return;
            }
            cause = cause.getCause();
        }
        fail("Expected a PostgreSQL SQLException with SQLSTATE " + expected, exception);
    }

    /**
     * Runs before context refresh, before Flyway or a DataSource can connect.
     * The explicit flag records external verification; it does not perform it.
     */
    static class VerifiedTestDatabaseInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        private static final String TEST_URL =
                "jdbc:postgresql://localhost:5432/qaryati_test_db";

        @Override
        public void initialize(ConfigurableApplicationContext context) {
            Environment environment = context.getEnvironment();
            String url = environment.getProperty("spring.datasource.url");
            String flywayUrl = environment.getProperty("spring.flyway.url");
            String jpaUrl = environment.getProperty("spring.jpa.properties.jakarta.persistence.jdbc.url");
            String hikariUrl = environment.getProperty("spring.datasource.hikari.jdbc-url");
            if (!TEST_URL.equals(url)
                    || (flywayUrl != null && !TEST_URL.equals(flywayUrl))
                    || (jpaUrl != null && !TEST_URL.equals(jpaUrl))
                    || (hikariUrl != null && !TEST_URL.equals(hikariUrl))) {
                throw new IllegalStateException(
                        "Governorate repository tests require the isolated JDBC URL " + TEST_URL);
            }
            if (!environment.getProperty(
                    "qaryati.governorate-tests.database-verified", Boolean.class, false)) {
                throw new IllegalStateException(
                        "Verify qaryati_test_db exists and is dedicated to tests before setting "
                                + "qaryati.governorate-tests.database-verified=true");
            }
            if (!"validate".equals(environment.getProperty("spring.jpa.hibernate.ddl-auto"))) {
                throw new IllegalStateException("Repository tests require Hibernate ddl-auto=validate");
            }
        }
    }
}