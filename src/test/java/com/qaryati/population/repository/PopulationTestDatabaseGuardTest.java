package com.qaryati.population.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class PopulationTestDatabaseGuardTest {
    @Test
    void acceptsVerifiedIsolatedConfiguration() {
        assertDoesNotThrow(() -> PopulationTestDatabaseGuard.validate(safeEnvironment()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"spring.datasource.url", "spring.flyway.url",
            "spring.jpa.properties.jakarta.persistence.jdbc.url", "spring.datasource.hikari.jdbc-url",
            "spring.datasource.hikari.data-source-properties.url"})
    void rejectsEveryAlternativeDatabaseUrlBeforeConnecting(String property) {
        MockEnvironment environment = safeEnvironment().withProperty(property,
                "jdbc:postgresql://localhost:5432/qaryati_db");
        assertThrows(IllegalStateException.class, () -> PopulationTestDatabaseGuard.validate(environment));
    }

    @ParameterizedTest
    @ValueSource(strings = {"spring.datasource.jndi-name", "spring.datasource.hikari.data-source-class-name"})
    void rejectsAlternateDatasourceMechanisms(String property) {
        MockEnvironment environment = safeEnvironment().withProperty(property, "override");
        assertThrows(IllegalStateException.class, () -> PopulationTestDatabaseGuard.validate(environment));
    }

    @ParameterizedTest
    @ValueSource(strings = {"spring.flyway.enabled", "spring.flyway.clean-disabled",
            "qaryati.population-tests.database-verified"})
    void rejectsMissingSafetyFlags(String property) {
        MockEnvironment environment = safeEnvironment().withProperty(property, "false");
        assertThrows(IllegalStateException.class, () -> PopulationTestDatabaseGuard.validate(environment));
    }

    @Test
    void rejectsSchemaGenerationAndWrongProfiles() {
        MockEnvironment environment = safeEnvironment().withProperty("spring.jpa.hibernate.ddl-auto", "create-drop");
        assertThrows(IllegalStateException.class, () -> PopulationTestDatabaseGuard.validate(environment));
        for (String[] profiles : new String[][]{ {}, {"dev"}, {"test", "dev"} }) {
            MockEnvironment wrongProfile = safeEnvironment();
            wrongProfile.setActiveProfiles(profiles);
            assertThrows(IllegalStateException.class, () -> PopulationTestDatabaseGuard.validate(wrongProfile));
        }
    }

    private MockEnvironment safeEnvironment() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", PopulationTestDatabaseGuard.TEST_URL)
                .withProperty(PopulationTestDatabaseGuard.VERIFIED, "true")
                .withProperty("spring.jpa.hibernate.ddl-auto", "validate")
                .withProperty("spring.flyway.enabled", "true")
                .withProperty("spring.flyway.clean-disabled", "true");
        environment.setActiveProfiles("test");
        return environment;
    }
}
