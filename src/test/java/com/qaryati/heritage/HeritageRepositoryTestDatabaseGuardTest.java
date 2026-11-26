package com.qaryati.heritage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Offline only: mocked JDBC and unrefreshed contexts; no application or database startup. */
class HeritageRepositoryTestDatabaseGuardTest {
    private static final String APPROVAL = "qaryati.heritage-tests.database-verified";
    private static final String USER = "heritage-fixture-user";

    private MockEnvironment environment(String approval) {
        var environment = new MockEnvironment()
                .withProperty("spring.datasource.url", DedicatedPostgresSafety.URL)
                .withProperty("spring.datasource.username", USER)
                .withProperty("spring.datasource.password", "unused-offline-value")
                .withProperty("spring.jpa.hibernate.ddl-auto", "validate");
        if (approval != null) environment.withProperty(APPROVAL, approval);
        environment.setActiveProfiles("test");
        // Model packaged defaults without loading credentials from configuration files.
        environment.getPropertySources().addLast(new MapPropertySource(
                "Config resource 'class path resource [application.properties]' via location 'optional:classpath:/'",
                Map.of("spring.datasource.url", "jdbc:postgresql://localhost:5432/qaryati_db")));
        environment.getPropertySources().addLast(new MapPropertySource(
                "Config resource 'class path resource [application-test.properties]' via location 'optional:classpath:/'",
                Map.of("spring.flyway.enabled", "true")));
        return environment;
    }

    private MockEnvironment environment() {
        return environment("true");
    }

    private GenericApplicationContext context(MockEnvironment environment) {
        var context = new GenericApplicationContext();
        context.setEnvironment(environment);
        context.registerBean("mustNotInitialize", Object.class, () -> {
            throw new AssertionError("Guard must run before bean initialization");
        });
        return context;
    }

    private void assertRejectedBeforeJdbc(MockEnvironment environment) {
        try (var context = context(environment); var driver = mockStatic(DriverManager.class)) {
            assertThrows(IllegalStateException.class,
                    () -> new HistoricalEventRepositoryIntegrationTest.DatabaseGuard().initialize(context));
            assertFalse(context.isActive());
            assertFalse(context.getBeanFactory().containsSingleton("mustNotInitialize"));
            assertFalse(environment.getPropertySources().contains("dedicated-postgres-safety"));
            driver.verifyNoInteractions();
        }
    }

    @Test
    void bothRepositoryTestsRetainSharedContextEarlyGuardAndTransactions() throws Exception {
        for (String name : List.of("HistoricalEventRepositoryIntegrationTest", "HeritageSiteRepositoryIntegrationTest")) {
            var testClass = Class.forName("com.qaryati.heritage." + name, false, getClass().getClassLoader());
            var boot = testClass.getDeclaredAnnotation(SpringBootTest.class);
            var profiles = testClass.getDeclaredAnnotation(ActiveProfiles.class);
            var configuration = testClass.getDeclaredAnnotation(ContextConfiguration.class);
            assertNotNull(boot, name);
            assertArrayEquals(new Class<?>[]{HistoricalEventRepositoryIntegrationTest.PersistenceConfiguration.class},
                    boot.classes(), name);
            assertArrayEquals(new String[]{"spring.flyway.clean-disabled=true", "spring.jpa.hibernate.ddl-auto=validate"},
                    boot.properties(), name);
            assertNotNull(profiles, name);
            assertArrayEquals(new String[]{"test"}, profiles.value(), name);
            assertNotNull(configuration, name);
            assertArrayEquals(new Class<?>[]{HistoricalEventRepositoryIntegrationTest.DatabaseGuard.class},
                    configuration.initializers(), name);
            assertNotNull(testClass.getDeclaredAnnotation(Transactional.class), name);
        }
    }

    @Test
    void manualDatasourceUsesTheVerifiedTargetWithoutConnecting() {
        try (var driver = mockStatic(DriverManager.class)) {
            var source = assertInstanceOf(org.springframework.jdbc.datasource.DriverManagerDataSource.class,
                    new HistoricalEventRepositoryIntegrationTest.PersistenceConfiguration().dataSource(environment()));
            assertEquals(DedicatedPostgresSafety.URL, source.getUrl());
            assertEquals(USER, source.getUsername());
            driver.verifyNoInteractions();
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "false")
    void requiresExistingHeritageApprovalBeforeJdbc(String approval) {
        assertRejectedBeforeJdbc(environment(approval)
                .withProperty("qaryati.governorate-tests.database-verified", "true")
                .withProperty("qaryati.population-tests.database-verified", "true"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"none", "dev", "test,dev", "dev,test"})
    void requiresExclusiveTestProfileBeforeJdbc(String profiles) {
        var environment = environment();
        environment.setActiveProfiles(profiles.equals("none") ? new String[0] : profiles.split(","));
        assertRejectedBeforeJdbc(environment);
    }

    @ParameterizedTest
    @ValueSource(strings = {"qaryati_db", "qaryati_governorate_dev"})
    void rejectsForbiddenTargetsAndShadowedRoutingBeforeJdbc(String database) {
        String forbidden = "jdbc:postgresql://localhost:5432/" + database;
        for (String key : List.of("spring.datasource.url", "spring.datasource.hikari.jdbc-url",
                "spring.jpa.properties.jakarta.persistence.jdbc.url",
                "spring.jpa.properties.javax.persistence.jdbc.url",
                "spring.jpa.properties.hibernate.connection.url", "spring.flyway.url")) {
            assertRejectedBeforeJdbc(environment().withProperty(key, forbidden));
            var shadowed = environment().withProperty(key, DedicatedPostgresSafety.URL);
            String alias = key.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_');
            shadowed.getPropertySources().addLast(new SystemEnvironmentPropertySource(
                    "shadowed-unsafe-routing", Map.of(alias, forbidden)));
            assertRejectedBeforeJdbc(shadowed);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "spring.datasource.url,jdbc:postgresql://other:5432/qaryati_test_db",
            "spring.datasource.url,jdbc:postgresql://localhost:5432/qaryati_test_db?user=other",
            "spring.datasource.jndi-name,unsupported",
            "spring.datasource.type,unsupported",
            "spring.datasource.hikari.data-source-class-name,unsupported",
            "spring.datasource.hikari.data-source-properties.url,unsupported",
            "spring.datasource.hikari.connection-init-sql,unsupported",
            "spring.datasource.hikari.connection-test-query,unsupported",
            "spring.datasource.dbcp2.url,unsupported",
            "spring.jpa.properties.hibernate.connection.provider_class,unsupported",
            "spring.flyway.url,jdbc:postgresql://localhost:5432/qaryati_test_db",
            "spring.flyway.user,unsupported",
            "spring.flyway.locations,unsupported",
            "SPRING_FLYWAY_LOCATIONS,unsupported",
            "spring.flyway.enabled,true",
            "spring.flyway.clean-disabled,false",
            "spring.flyway.baseline-on-migrate,true",
            "spring.liquibase.enabled,true",
            "spring.sql.init.mode,always",
            "spring.sql.init.schema-locations,unsupported",
            "spring.jpa.hibernate.ddl-auto,update",
            "spring.jpa.hibernate.ddl-auto,create-drop",
            "spring.jpa.generate-ddl,true",
            "spring.jpa.properties.hibernate.hbm2ddl.auto,update",
            "spring.jpa.properties.hibernate.hbm2ddl.import_files,unsupported",
            "spring.jpa.properties.jakarta.persistence.schema-generation.database.action,create",
            "spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action,validate"
    })
    void rejectsUnsupportedRoutingAndInitializationBeforeJdbc(String key, String value) {
        assertRejectedBeforeJdbc(environment().withProperty(key, value));
    }

    @Test
    void rejectsOpaqueConfigurationBeforeJdbc() {
        var environment = environment();
        environment.getPropertySources().addLast(new PropertySource<Object>("opaque", new Object()) {
            @Override
            public Object getProperty(String name) { return null; }
        });
        assertRejectedBeforeJdbc(environment);
    }

    @ParameterizedTest
    @CsvSource({
            "PostgreSQL,qaryati_test_db,heritage-fixture-user,true,true",
            "PostgreSQL,qaryati_db,heritage-fixture-user,true,false",
            "PostgreSQL,qaryati_governorate_dev,heritage-fixture-user,true,false",
            "PostgreSQL,qaryati_test_db,other-user,true,false",
            "OtherDatabase,qaryati_test_db,heritage-fixture-user,true,false",
            "PostgreSQL,qaryati_test_db,heritage-fixture-user,false,false"
    })
    void verifiesIdentityAndPinsNonWritingSchemaPolicyBeforeRefresh(
            String product, String database, String user, boolean hasRow, boolean allowed) throws Exception {
        // Even permitted non-writing inputs must not suppress requested schema validation.
        var environment = environment()
                .withProperty("spring.jpa.hibernate.ddl-auto", "none")
                .withProperty("spring.jpa.properties.hibernate.hbm2ddl.auto", "none")
                .withProperty("spring.jpa.properties.jakarta.persistence.schema-generation.database.action", "none");
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
            var guard = new HistoricalEventRepositoryIntegrationTest.DatabaseGuard();

            if (allowed) {
                assertDoesNotThrow(() -> guard.initialize(context));
                for (var setting : Map.ofEntries(
                        Map.entry("spring.datasource.url", DedicatedPostgresSafety.URL),
                        Map.entry("spring.datasource.hikari.jdbc-url", DedicatedPostgresSafety.URL),
                        Map.entry("spring.datasource.type", "com.zaxxer.hikari.HikariDataSource"),
                        Map.entry("spring.flyway.enabled", "false"),
                        Map.entry("spring.flyway.clean-disabled", "true"),
                        Map.entry("spring.liquibase.enabled", "false"),
                        Map.entry("spring.sql.init.mode", "never"),
                        Map.entry("spring.jpa.generate-ddl", "false"),
                        Map.entry("spring.jpa.hibernate.ddl-auto", "validate"),
                        Map.entry("spring.jpa.properties.hibernate.hbm2ddl.auto", "validate"),
                        Map.entry("spring.jpa.properties.jakarta.persistence.schema-generation.database.action", "validate"),
                        Map.entry("spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action", "none"),
                        Map.entry("spring.main.allow-bean-definition-overriding", "false")).entrySet()) {
                    assertEquals(setting.getValue(), environment.getProperty(setting.getKey()), setting.getKey());
                }
            } else {
                assertThrows(IllegalStateException.class, () -> guard.initialize(context));
                assertFalse(environment.getPropertySources().contains("dedicated-postgres-safety"));
            }

            assertFalse(context.isActive());
            assertFalse(context.getBeanFactory().containsSingleton("mustNotInitialize"));
            driver.verify(() -> DriverManager.getConnection(eq(DedicatedPostgresSafety.URL), any(Properties.class)));
            driver.verifyNoMoreInteractions();
            verify(statement).setQueryTimeout(5);
            verify(result).close();
            verify(statement).close();
            verify(connection).close();
        }
    }

    @Test
    void connectionFailureIsSanitizedBeforeAnyBeanInitialization() {
        var connectionFailure = new SQLException("private driver details");
        var environment = environment();
        try (var context = context(environment); var driver = mockStatic(DriverManager.class)) {
            driver.when(() -> DriverManager.getConnection(eq(DedicatedPostgresSafety.URL), any(Properties.class)))
                    .thenThrow(connectionFailure);
            var error = assertThrows(IllegalStateException.class,
                    () -> new HistoricalEventRepositoryIntegrationTest.DatabaseGuard().initialize(context));
            assertEquals("Database identity verification failed", error.getMessage());
            assertNull(error.getCause());
            assertFalse(context.isActive());
            assertFalse(context.getBeanFactory().containsSingleton("mustNotInitialize"));
            assertFalse(environment.getPropertySources().contains("dedicated-postgres-safety"));
        }
    }
}
