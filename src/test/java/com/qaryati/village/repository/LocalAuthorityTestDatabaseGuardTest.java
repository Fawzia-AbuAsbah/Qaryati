package com.qaryati.village.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocalAuthorityTestDatabaseGuardTest {
    private MockEnvironment safe() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        return environment.withProperty(LocalAuthorityTestDatabaseSupport.APPROVED, "true")
                .withProperty("spring.datasource.url", LocalAuthorityTestDatabaseSupport.URL)
                .withProperty("spring.datasource.username", "test-user")
                .withProperty("spring.datasource.password", "dummy-not-a-secret")
                .withProperty("spring.jpa.hibernate.ddl-auto", "validate")
                .withProperty("spring.flyway.enabled", "false")
                .withProperty("spring.flyway.clean-disabled", "true")
                .withProperty("spring.sql.init.mode", "never");
    }

    @Test
    void acceptsOnlyExplicitlySafeConfigurationWithoutOpeningConnection() {
        assertDoesNotThrow(() -> LocalAuthorityTestDatabaseSupport.validate(safe()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"none", "default", "test,dev"})
    void rejectsWrongProfiles(String profiles) {
        MockEnvironment environment = safe();
        environment.setActiveProfiles(profiles.equals("none") ? new String[0] : profiles.split(","));
        assertThrows(IllegalStateException.class, () -> LocalAuthorityTestDatabaseSupport.validate(environment));
    }

    @ParameterizedTest
    @CsvSource({
            "spring.datasource.url,jdbc:postgresql://localhost:5432/qaryati_db",
            "spring.datasource.url,jdbc:postgresql://localhost:5432/qaryati_governorate_dev",
            "spring.datasource.url,jdbc:postgresql://other:5432/qaryati_test_db",
            "spring.datasource.url,jdbc:postgresql://localhost:5432/qaryati_test_db?user=other",
            "spring.jpa.hibernate.ddl-auto,create",
            "spring.jpa.hibernate.ddl-auto,create-drop",
            "spring.jpa.hibernate.ddl-auto,update",
            "spring.jpa.hibernate.ddl-auto,none",
            "spring.flyway.enabled,true",
            "spring.flyway.clean-disabled,false",
            "spring.sql.init.mode,always",
            "qaryati.local-authority-tests.database-verified,false"
    })
    void rejectsUnsafeEffectiveSettings(String key, String value) {
        MockEnvironment environment = safe().withProperty(key, value);
        assertThrows(IllegalStateException.class, () -> LocalAuthorityTestDatabaseSupport.validate(environment));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "spring.datasource.hikari.jdbc-url",
            "spring.datasource.hikari.data-source-properties.url",
            "spring.datasource.hikari.connection-init-sql",
            "spring.datasource.hikari.data-source-class-name",
            "spring.datasource.jndi-name",
            "spring.datasource.type",
            "spring.datasource.dbcp2.url",
            "spring.flyway.url",
            "spring.flyway.user",
            "spring.flyway.locations",
            "spring.jpa.properties.jakarta.persistence.jdbc.url",
            "spring.jpa.properties.hibernate.hbm2ddl.auto",
            "spring.jpa.properties.jakarta.persistence.schema-generation.database.action",
            "hibernate.hbm2ddl.auto",
            "jakarta.persistence.jdbc.url",
            "spring.sql.init.schema-locations",
            "spring.application.json",
            "SPRING_APPLICATION_JSON",
            "spring.config.import",
            "spring.config.location",
            "spring.profiles.active",
            "SPRING_PROFILES_ACTIVE",
            "spring.profiles.include",
            "spring.profiles.group.test"
    })
    void rejectsAlternateRoutingOrInitializationMechanisms(String key) {
        MockEnvironment environment = safe().withProperty(key, "unsupported-value-withheld");
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> LocalAuthorityTestDatabaseSupport.validate(environment));
        assertFalse(error.getMessage().contains("unsupported-value-withheld"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SPRING_DATASOURCE_URL", "spring.datasource.url"})
    void rejectsShadowedEnvironmentAndJvmRouting(String key) {
        MockEnvironment environment = safe();
        environment.getPropertySources().addLast(new MapPropertySource("shadowed",
                Map.of(key, "jdbc:postgresql://localhost:5432/qaryati_db")));
        assertThrows(IllegalStateException.class, () -> LocalAuthorityTestDatabaseSupport.validate(environment));
    }

    @ParameterizedTest
    @ValueSource(strings = {"spring.datasource.url", "spring.datasource.username", "spring.datasource.password",
            "spring.flyway.enabled", "spring.flyway.clean-disabled", "spring.sql.init.mode",
            "spring.jpa.hibernate.ddl-auto", "qaryati.local-authority-tests.database-verified"})
    void rejectsMissingRequiredSettings(String key) {
        MockEnvironment environment = safe();
        environment.getPropertySources().forEach(source -> {
            if (source.getSource() instanceof Map<?, ?> map) map.remove(key);
        });
        assertThrows(IllegalStateException.class, () -> LocalAuthorityTestDatabaseSupport.validate(environment));
    }

    @Test
    void initializerRejectsUnsafeContextBeforeAnyBeanInstantiation() {
        AtomicBoolean instantiated = new AtomicBoolean();
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.setEnvironment(safe().withProperty("spring.flyway.enabled", "true"));
            context.registerBean("connectionFactory", Object.class, () -> {
                instantiated.set(true);
                throw new AssertionError("Must not instantiate");
            });
            assertThrows(IllegalStateException.class,
                    () -> new LocalAuthorityTestDatabaseSupport().initialize(context));
            assertFalse(context.isActive());
            assertFalse(instantiated.get());
        }
    }

    @ParameterizedTest
    @CsvSource({"PostgreSQL,qaryati_test_db,test-user,true",
            "PostgreSQL,qaryati_db,test-user,false",
            "PostgreSQL,qaryati_governorate_dev,test-user,false",
            "PostgreSQL,qaryati_test_db,other-user,false",
            "OtherDatabase,qaryati_test_db,test-user,false"})
    void checksLiveIdentityBeforeReturningConnectionUsingMocksOnly(
            String product, String database, String user, boolean allowed) throws Exception {
        DataSource delegate = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        Statement statement = mock(Statement.class);
        ResultSet result = mock(ResultSet.class);
        when(delegate.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn(product);
        if (product.equals("PostgreSQL")) {
            when(connection.createStatement()).thenReturn(statement);
            when(statement.executeQuery("SELECT current_database(), current_user")).thenReturn(result);
            when(result.next()).thenReturn(true);
            when(result.getString(1)).thenReturn(database);
            when(result.getString(2)).thenReturn(user);
        }
        var checked = new LocalAuthorityTestDatabaseSupport.IdentityCheckedDataSource(delegate, "test-user");
        if (allowed) {
            assertSame(connection, checked.getConnection());
            verify(connection, never()).close();
            verify(statement).close();
            verify(result).close();
        } else {
            assertThrows(SQLException.class, checked::getConnection);
            verify(connection).close();
        }
    }

    @Test
    void connectionFailureIsSanitized() throws Exception {
        DataSource delegate = mock(DataSource.class);
        when(delegate.getConnection()).thenThrow(new SQLException("private connection details"));
        var checked = new LocalAuthorityTestDatabaseSupport.IdentityCheckedDataSource(delegate, "test-user");
        SQLException error = assertThrows(SQLException.class, checked::getConnection);
        assertFalse(error.getMessage().contains("private connection details"));
        assertNull(error.getCause());
    }

    @Test
    void refusesAlternativeConnectionCredentialsWithoutCallingDelegate() {
        DataSource delegate = mock(DataSource.class);
        var checked = new LocalAuthorityTestDatabaseSupport.IdentityCheckedDataSource(delegate, "test-user");
        assertThrows(SQLException.class, () -> checked.getConnection("other-user", "dummy"));
        verifyNoInteractions(delegate);
    }
}