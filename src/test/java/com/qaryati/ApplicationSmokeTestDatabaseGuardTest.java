package com.qaryati;

import com.qaryati.heritage.DedicatedPostgresSafety;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Offline only: mocked JDBC, no context refresh, application startup, or database. */
class ApplicationSmokeTestDatabaseGuardTest {

    private MockEnvironment environment() {
        var environment = new MockEnvironment()
                .withProperty("spring.datasource.url", DedicatedPostgresSafety.URL)
                .withProperty("spring.datasource.username", "smoke-fixture-user")
                .withProperty("spring.datasource.password", "unused-offline-value")
                .withProperty("spring.jpa.hibernate.ddl-auto", "validate");
        environment.setActiveProfiles("test");
        // Model inherited packaged defaults without loading real credentials.
        environment.getPropertySources().addLast(new MapPropertySource(
                "Config resource 'class path resource [application.properties]' via location 'optional:classpath:/'",
                Map.of("spring.datasource.url", "jdbc:postgresql://localhost:5432/qaryati_db")));
        environment.getPropertySources().addLast(new MapPropertySource(
                "Config resource 'class path resource [application-test.properties]' via location 'optional:classpath:/'",
                Map.of("spring.flyway.enabled", "true")));
        return environment;
    }

    private GenericApplicationContext context(MockEnvironment environment) {
        var context = new GenericApplicationContext();
        context.setEnvironment(environment);
        context.registerBean("mustNotInitialize", Object.class, () -> {
            throw new AssertionError("Guard must run before bean initialization");
        });
        return context;
    }

    private void assertRejectedBeforeJdbc(MockEnvironment environment, String approval) {
        try (var context = context(environment); var driver = mockStatic(DriverManager.class)) {
            assertThrows(IllegalStateException.class,
                    () -> new ApplicationSmokeTestDatabaseGuard(approval).initialize(context));
            assertFalse(context.isActive());
            assertFalse(environment.getPropertySources().contains("dedicated-postgres-safety"));
            driver.verifyNoInteractions();
        }
    }

    @Test
    void smokeTestRetainsFullContextAndRegistersEarlyGuard() throws Exception {
        var smokeTest = QaryatiApplicationTests.class;
        assertNotNull(smokeTest.getAnnotation(SpringBootTest.class));
        assertEquals(0, smokeTest.getAnnotation(SpringBootTest.class).classes().length);
        assertArrayEquals(new String[]{"test"}, smokeTest.getAnnotation(ActiveProfiles.class).value());
        assertArrayEquals(new Class<?>[]{ApplicationSmokeTestDatabaseGuard.class},
                smokeTest.getAnnotation(ContextConfiguration.class).initializers());
        assertNotNull(ApplicationSmokeTestDatabaseGuard.class.getConstructor());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"true", "false", "verified"})
    void requiresItsOwnExactApprovalBeforeJdbc(String approval) {
        var environment = environment()
                .withProperty("qaryati.heritage-tests.database-verified", "true")
                .withProperty(ApplicationSmokeTestDatabaseGuard.APPROVAL_VARIABLE, "verified-test-db");
        assertRejectedBeforeJdbc(environment, approval);
    }

    @ParameterizedTest
    @ValueSource(strings = {"none", "dev", "test,dev"})
    void requiresExclusiveTestProfileBeforeJdbc(String profiles) {
        var environment = environment();
        environment.setActiveProfiles(profiles.equals("none") ? new String[0] : profiles.split(","));
        assertRejectedBeforeJdbc(environment, "verified-test-db");
    }

    @ParameterizedTest
    @ValueSource(strings = {"qaryati_db", "qaryati_governorate_dev"})
    void rejectsForbiddenDatabasesAndShadowedRoutingBeforeJdbc(String database) {
        String forbiddenUrl = "jdbc:postgresql://localhost:5432/" + database;
        for (String key : List.of("spring.datasource.url", "spring.datasource.hikari.jdbc-url",
                "spring.flyway.url", "spring.jpa.properties.jakarta.persistence.jdbc.url",
                "spring.jpa.properties.hibernate.connection.url")) {
            assertRejectedBeforeJdbc(environment().withProperty(key, forbiddenUrl), "verified-test-db");
            var shadowed = environment().withProperty(key, DedicatedPostgresSafety.URL);
            String alias = key.toUpperCase(java.util.Locale.ROOT).replace('.', '_').replace('-', '_');
            shadowed.getPropertySources().addLast(new SystemEnvironmentPropertySource(
                    "shadowed-unsafe-routing", Map.of(alias, forbiddenUrl)));
            assertRejectedBeforeJdbc(shadowed, "verified-test-db");
        }
    }

    @ParameterizedTest
    @CsvSource({
            "spring.datasource.url,jdbc:postgresql://other:5432/qaryati_test_db",
            "spring.datasource.url,jdbc:postgresql://localhost:5432/qaryati_test_db?user=other",
            "spring.datasource.jndi-name,unsupported",
            "spring.datasource.hikari.data-source-class-name,unsupported",
            "spring.datasource.hikari.data-source-properties.url,unsupported",
            "spring.datasource.hikari.connection-init-sql,unsupported",
            "spring.jpa.properties.hibernate.connection.provider_class,unsupported",
            "spring.flyway.enabled,true",
            "spring.flyway.clean-disabled,false",
            "spring.sql.init.mode,always",
            "spring.sql.init.schema-locations,unsupported",
            "spring.jpa.hibernate.ddl-auto,update",
            "spring.jpa.hibernate.ddl-auto,create-drop",
            "spring.jpa.generate-ddl,true",
            "spring.jpa.properties.jakarta.persistence.schema-generation.database.action,create"
    })
    void rejectsExternalRoutingAndInitializationOverridesBeforeJdbc(String key, String value) {
        assertRejectedBeforeJdbc(environment().withProperty(key, value), "verified-test-db");
    }

    @ParameterizedTest
    @CsvSource({
            "PostgreSQL,qaryati_test_db,smoke-fixture-user,true,true",
            "PostgreSQL,qaryati_db,smoke-fixture-user,true,false",
            "PostgreSQL,qaryati_governorate_dev,smoke-fixture-user,true,false",
            "PostgreSQL,qaryati_test_db,other-user,true,false",
            "OtherDatabase,qaryati_test_db,smoke-fixture-user,true,false",
            "PostgreSQL,qaryati_test_db,smoke-fixture-user,false,false"
    })
    void verifiesIdentityBeforeAllowingRefreshAndLocksInitializationSettings(
            String product, String database, String user, boolean hasRow, boolean allowed) throws Exception {
        var environment = environment();
        try (var context = context(environment); var driver = mockStatic(DriverManager.class)) {
            var connection = mock(Connection.class);
            var statement = mock(Statement.class);
            var result = mock(ResultSet.class);
            var metadata = mock(DatabaseMetaData.class);
            driver.when(() -> DriverManager.getConnection(eq(DedicatedPostgresSafety.URL), any(Properties.class)))
                    .thenReturn(connection);
            when(connection.createStatement()).thenReturn(statement);
            when(connection.getMetaData()).thenReturn(metadata);
            when(metadata.getDatabaseProductName()).thenReturn(product);
            when(statement.executeQuery("SELECT current_database(), current_user")).thenReturn(result);
            when(result.next()).thenReturn(hasRow);
            when(result.getString(1)).thenReturn(database);
            when(result.getString(2)).thenReturn(user);
            var guard = new ApplicationSmokeTestDatabaseGuard("verified-test-db");

            if (allowed) {
                assertDoesNotThrow(() -> guard.initialize(context));
                assertEquals(DedicatedPostgresSafety.URL, environment.getProperty("spring.datasource.url"));
                assertEquals(DedicatedPostgresSafety.URL, environment.getProperty("spring.datasource.hikari.jdbc-url"));
                assertEquals("false", environment.getProperty("spring.flyway.enabled"));
                assertEquals("true", environment.getProperty("spring.flyway.clean-disabled"));
                assertEquals("false", environment.getProperty("spring.liquibase.enabled"));
                assertEquals("never", environment.getProperty("spring.sql.init.mode"));
                assertEquals("validate", environment.getProperty("spring.jpa.hibernate.ddl-auto"));
                assertEquals("validate", environment.getProperty("spring.jpa.properties.hibernate.hbm2ddl.auto"));
                assertEquals("false", environment.getProperty("spring.jpa.generate-ddl"));
                assertEquals("validate", environment.getProperty(
                        "spring.jpa.properties.jakarta.persistence.schema-generation.database.action"));
                assertEquals("none", environment.getProperty(
                        "spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action"));
            } else {
                assertThrows(IllegalStateException.class, () -> guard.initialize(context));
                assertFalse(environment.getPropertySources().contains("dedicated-postgres-safety"));
            }

            assertFalse(context.isActive());
            driver.verify(() -> DriverManager.getConnection(eq(DedicatedPostgresSafety.URL), any(Properties.class)));
            driver.verifyNoMoreInteractions();
            verify(result).close();
            verify(statement).close();
            verify(connection).close();
        }
    }

    @Test
    void connectionFailureIsSanitizedAndCannotInitializeBeans() {
        var environment = environment();
        var connectionFailure = new SQLException("private driver details");
        try (var context = context(environment); var driver = mockStatic(DriverManager.class)) {
            driver.when(() -> DriverManager.getConnection(eq(DedicatedPostgresSafety.URL), any(Properties.class)))
                    .thenThrow(connectionFailure);
            var error = assertThrows(IllegalStateException.class,
                    () -> new ApplicationSmokeTestDatabaseGuard("verified-test-db").initialize(context));
            assertEquals("Database identity verification failed", error.getMessage());
            assertNull(error.getCause());
            assertFalse(context.isActive());
            assertFalse(environment.getPropertySources().contains("dedicated-postgres-safety"));
        }
    }
}
