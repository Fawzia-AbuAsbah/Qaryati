package com.qaryati.heritage;

import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** No context refresh, real driver, database, or integration opt-in. */
class DedicatedPostgresSafetyTest {
    StandardEnvironment environment() {
        var env = new StandardEnvironment();
        env.getPropertySources().remove("systemProperties");
        env.getPropertySources().remove("systemEnvironment");
        env.setActiveProfiles("test");
        env.getPropertySources().addLast(new MapPropertySource("base", Map.of(
                "spring.datasource.url", DedicatedPostgresSafety.URL,
                "spring.datasource.username", "fixture-user", "spring.datasource.password", "fixture-password")));
        return env;
    }

    @Test void requiresIndependentOptInAndExclusiveProfileBeforeConnecting() {
        try (var driver = mockStatic(DriverManager.class)) {
            var env = environment();
            assertThrows(IllegalStateException.class, () -> DedicatedPostgresSafety.validate(env, false));
            env.setActiveProfiles("test", "dev");
            assertThrows(IllegalStateException.class, () -> DedicatedPostgresSafety.validate(env, true));
            driver.verifyNoInteractions();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"spring.datasource.url", "spring.datasource.hikari.jdbc-url",
            "spring.jpa.properties.hibernate.connection.url", "spring.jpa.properties.jakarta.persistence.jdbc.url",
            "spring.jpa.properties.javax.persistence.jdbc.url", "hibernate.connection.url", "jdbc.url",
            "spring.datasource.hikari.data-source-properties.serverName", "spring.datasource.jndi-name",
            "spring.datasource.type", "spring.datasource.hikari.connection-init-sql",
            "spring.datasource.hikari.connection-test-query", "spring.datasource.hikari.data-source-class-name",
            "spring.jpa.properties.hibernate.connection.provider_class", "spring.flyway.url",
            "spring.sql.init.schema-locations", "spring.sql.init.data-locations", "spring.sql.init.mode",
            "spring.flyway.enabled", "spring.jpa.hibernate.ddl-auto", "spring.jpa.generate-ddl",
            "spring.jpa.properties.jakarta.persistence.schema-generation.database.action",
            "spring.jpa.properties.jakarta.persistence.schema-generation.scripts.create-target",
            "spring.main.allow-bean-definition-overriding", "spring.liquibase.url"})
    void rejectsAliasesAndShadowedPropertiesBeforeConnecting(String key) {
        for (String alias : List.of(key, key.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_'),
                key.replace("-", ""), key.replace(".", "").replace("-", ""))) {
            var env = environment();
            env.getPropertySources().addLast(new SystemEnvironmentPropertySource("unsafe-shadowed", Map.of(alias, "unsafe")));
            env.getPropertySources().addFirst(new MapPropertySource("safe-shadow", Map.of(alias, DedicatedPostgresSafety.URL)));
            try (var driver = mockStatic(DriverManager.class)) {
                assertThrows(IllegalStateException.class, () -> DedicatedPostgresSafety.validate(env, true), alias);
                driver.verifyNoInteractions();
            }
        }
    }

    @Test void rejectsOpaqueSourcesAndCredentialOverrides() {
        var env = environment();
        env.getPropertySources().addLast(new PropertySource<Object>("opaque", new Object()) {
            public Object getProperty(String key) { return null; }
        });
        assertThrows(IllegalStateException.class, () -> DedicatedPostgresSafety.validate(env, true));
        for (String key : List.of("spring.datasource.hikari.username", "spring.datasource.hikari.password")) {
            var credentials = environment();
            credentials.getPropertySources().addLast(new MapPropertySource("conflict", Map.of(key, "other")));
            assertThrows(IllegalStateException.class, () -> DedicatedPostgresSafety.validate(credentials, true));
        }
    }

    @Test void packagedDefaultsAreOverriddenButExternalDevelopmentUrlIsRejected() {
        var env = environment();
        env.getPropertySources().addLast(new MapPropertySource(
                "Config resource 'class path resource [application.properties]' via location 'optional:classpath:/'",
                Map.of("spring.datasource.url", "jdbc:postgresql://localhost:5432/qaryati_db")));
        env.getPropertySources().addLast(new MapPropertySource(
                "Config resource 'class path resource [application-test.properties]' via location 'optional:classpath:/'",
                Map.of("spring.flyway.enabled", "true")));
        assertDoesNotThrow(() -> DedicatedPostgresSafety.validate(env, true));
        env.getPropertySources().addLast(new MapPropertySource("external", Map.of(
                "spring.datasource.url", "jdbc:postgresql://localhost:5432/qaryati_db")));
        assertThrows(IllegalStateException.class, () -> DedicatedPostgresSafety.validate(env, true));
    }

    @Test void actualPackagedTestPropertiesPassWithoutRefreshingOrConnecting() {
        try (var context = new GenericApplicationContext(); var driver = mockStatic(DriverManager.class)) {
            var env = environment();
            env.getPropertySources().remove("base");
            env.getPropertySources().addFirst(new MapPropertySource("placeholder", Map.of("DB_PASSWORD", "fixture-password")));
            context.setEnvironment(env);
            new org.springframework.boot.test.context.ConfigDataApplicationContextInitializer().initialize(context);
            assertEquals(DedicatedPostgresSafety.URL, env.getProperty("spring.datasource.url"));
            assertDoesNotThrow(() -> DedicatedPostgresSafety.validate(env, true));
            assertFalse(context.isActive());
            driver.verifyNoInteractions();
        }
    }

    @Test void onlyVerifiedIdentityInstallsSafetyProperties() throws Exception {
        for (boolean validate : List.of(false, true)) {
            try (var context = new GenericApplicationContext(); var driver = mockStatic(DriverManager.class)) {
                context.setEnvironment(environment());
                var connection = mock(Connection.class);
                var statement = mock(Statement.class);
                var result = mock(ResultSet.class);
                var metadata = mock(DatabaseMetaData.class);
                driver.when(() -> DriverManager.getConnection(eq(DedicatedPostgresSafety.URL), any(Properties.class))).thenReturn(connection);
                when(connection.createStatement()).thenReturn(statement);
                when(connection.getMetaData()).thenReturn(metadata);
                when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
                when(statement.executeQuery("SELECT current_database(), current_user")).thenReturn(result);
                when(result.next()).thenReturn(true);
                when(result.getString(1)).thenReturn("qaryati_test_db");
                when(result.getString(2)).thenReturn("fixture-user");
                DedicatedPostgresSafety.initialize(context, true, validate);
                var env = context.getEnvironment();
                assertEquals("false", env.getProperty("spring.flyway.enabled"));
                assertEquals("never", env.getProperty("spring.sql.init.mode"));
                assertEquals(validate ? "validate" : "none", env.getProperty("spring.jpa.hibernate.ddl-auto"));
                assertEquals("none", env.getProperty("spring.jpa.properties.jakarta.persistence.schema-generation.database.action"));
                assertDoesNotThrow(() -> DedicatedPostgresSafety.validate(env, true));
                driver.verify(() -> DriverManager.getConnection(eq(DedicatedPostgresSafety.URL), any(Properties.class)));
                driver.verifyNoMoreInteractions();
                verify(connection).close();
            }
        }
    }

    @Test void wrongLiveIdentityCannotRefreshOrInstallSafety() throws Exception {
        try (var context = new GenericApplicationContext(); var driver = mockStatic(DriverManager.class)) {
            context.setEnvironment(environment());
            var connection = mock(Connection.class);
            var statement = mock(Statement.class);
            var metadata = mock(DatabaseMetaData.class);
            var result = mock(ResultSet.class);
            driver.when(() -> DriverManager.getConnection(eq(DedicatedPostgresSafety.URL), any(Properties.class))).thenReturn(connection);
            when(connection.createStatement()).thenReturn(statement);
            when(connection.getMetaData()).thenReturn(metadata);
            when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
            when(statement.executeQuery(anyString())).thenReturn(result);
            when(result.next()).thenReturn(true);
            when(result.getString(1)).thenReturn("qaryati_db");
            assertThrows(IllegalStateException.class, () -> DedicatedPostgresSafety.initialize(context, true, false));
            assertFalse(context.isActive());
            assertFalse(context.getEnvironment().getPropertySources().contains("dedicated-postgres-safety"));
        }
    }
}
