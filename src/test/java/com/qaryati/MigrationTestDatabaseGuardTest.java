package com.qaryati;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import javax.sql.XADataSource;
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

/** Offline only: mocked JDBC and an unrefreshed context; never runs migrations. */
class MigrationTestDatabaseGuardTest {
    private static final String APPROVAL = "verified-disposable-empty-test-db";
    private static final String USER = "migration-fixture-user";

    private MockEnvironment environment() {
        var environment = new MockEnvironment()
                .withProperty("spring.datasource.url", MigrationTestDatabaseGuard.URL)
                .withProperty("spring.datasource.username", USER)
                .withProperty("spring.datasource.password", "unused-offline-value")
                .withProperty("spring.jpa.hibernate.ddl-auto", "validate");
        environment.setActiveProfiles("test");
        environment.getPropertySources().addLast(new MapPropertySource(
                "Config resource 'class path resource [application.properties]' via location 'optional:classpath:/'",
                Map.of("spring.datasource.url", "jdbc:postgresql://localhost:5432/qaryati_db")));
        environment.getPropertySources().addLast(new MapPropertySource(
                "Config resource 'class path resource [application-test.properties]' via location 'optional:classpath:/'",
                Map.of("spring.flyway.enabled", "true", "spring.flyway.clean-disabled", "true")));
        return environment;
    }

    private GenericApplicationContext context(MockEnvironment environment) {
        var context = new GenericApplicationContext();
        context.setEnvironment(environment);
        context.registerBean("mustNotInitialize", Object.class, () -> {
            throw new AssertionError("Preflight must not initialize application beans");
        });
        return context;
    }

    private void assertNotInstalled(GenericApplicationContext context) {
        assertFalse(context.isActive());
        assertFalse(context.containsBeanDefinition("dataSource"));
        assertFalse(context.getEnvironment().getPropertySources().contains(MigrationTestDatabaseGuard.SAFETY_SOURCE));
    }

    private void assertRejectedBeforeJdbc(MockEnvironment environment, String approval) {
        try (var context = context(environment); var driver = mockStatic(DriverManager.class)) {
            assertThrows(IllegalStateException.class,
                    () -> new MigrationTestDatabaseGuard(approval).initialize(context));
            assertNotInstalled(context);
            driver.verifyNoInteractions();
        }
    }

    private record JdbcFixture(Connection connection, DatabaseMetaData metadata,
                               Statement identityStatement, ResultSet identityResult,
                               Statement freshnessStatement, ResultSet freshnessResult) {
    }

    private JdbcFixture fixture() throws SQLException {
        var connection = mock(Connection.class);
        var metadata = mock(DatabaseMetaData.class);
        var identityStatement = mock(Statement.class);
        var identityResult = mock(ResultSet.class);
        var freshnessStatement = mock(Statement.class);
        var freshnessResult = mock(ResultSet.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(connection.createStatement()).thenReturn(identityStatement, freshnessStatement);
        when(identityStatement.executeQuery(MigrationTestDatabaseGuard.IDENTITY_SQL)).thenReturn(identityResult);
        when(identityResult.next()).thenReturn(true);
        when(identityResult.getString(1)).thenReturn("qaryati_test_db");
        when(identityResult.getString(2)).thenReturn(USER);
        when(freshnessStatement.executeQuery(MigrationTestDatabaseGuard.FRESHNESS_SQL)).thenReturn(freshnessResult);
        when(freshnessResult.next()).thenReturn(true);
        when(freshnessResult.getBoolean(1)).thenReturn(true);
        when(freshnessResult.getBoolean(2)).thenReturn(false);
        when(freshnessResult.getBoolean(3)).thenReturn(false);
        return new JdbcFixture(connection, metadata, identityStatement, identityResult,
                freshnessStatement, freshnessResult);
    }

    private void connectWith(MockedStatic<DriverManager> driver, JdbcFixture fixture) {
        driver.when(() -> DriverManager.getConnection(eq(MigrationTestDatabaseGuard.URL), any(Properties.class)))
                .thenReturn(fixture.connection());
    }

    private MigrationTestDatabaseGuard.MigrationDataSource registeredDataSource(GenericApplicationContext context) {
        var definition = assertInstanceOf(RootBeanDefinition.class,
                context.getBeanFactory().getBeanDefinition("dataSource"));
        assertTrue(definition.isPrimary());
        assertTrue(DataSource.class.isAssignableFrom(definition.getBeanClass()));
        assertNotNull(definition.getInstanceSupplier());
        var source = assertInstanceOf(MigrationTestDatabaseGuard.MigrationDataSource.class,
                definition.getInstanceSupplier().get());
        assertSame(source, definition.getInstanceSupplier().get());
        return source;
    }

    @Test
    void migrationTestRetainsFullContextAndRegistersItsOwnGuard() throws Exception {
        var testClass = Class.forName("com.qaryati.DatabaseMigrationIntegrationTest", false,
                getClass().getClassLoader());
        var springBootTest = testClass.getAnnotation(SpringBootTest.class);
        var profiles = testClass.getAnnotation(ActiveProfiles.class);
        var configuration = testClass.getAnnotation(ContextConfiguration.class);
        assertNotNull(springBootTest);
        assertEquals(0, springBootTest.classes().length);
        assertNotNull(profiles);
        assertArrayEquals(new String[]{"test"}, profiles.value());
        assertNotNull(configuration);
        assertArrayEquals(new Class<?>[]{MigrationTestDatabaseGuard.class},
                configuration.initializers());
        assertNull(testClass.getDeclaredAnnotation(Transactional.class));
        assertEquals("QARYATI_MIGRATION_TESTS", MigrationTestDatabaseGuard.APPROVAL_VARIABLE);
        assertNotNull(MigrationTestDatabaseGuard.class.getConstructor());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"true", "verified-test-db", "verified-disposable-empty-test-db "})
    void requiresIndependentExactApprovalBeforeJdbc(String approval) {
        var environment = environment()
                .withProperty("QARYATI_APPLICATION_SMOKE_TESTS", "verified-test-db")
                .withProperty("QARYATI_SECURITY_TESTS", "verified-test-db")
                .withProperty("QARYATI_AGRICULTURE_TESTS", "verified-test-db")
                .withProperty("QARYATI_COMMUNITY_TESTS", "verified-test-db")
                .withProperty("QARYATI_DATAQUALITY_TESTS", "verified-test-db")
                .withProperty("QARYATI_GEOCODING_TESTS", "verified-test-db")
                .withProperty(MigrationTestDatabaseGuard.APPROVAL_VARIABLE, APPROVAL);
        assertRejectedBeforeJdbc(environment, approval);
    }

    @ParameterizedTest
    @ValueSource(strings = {"none", "dev", "test,dev"})
    void requiresOnlyTheTestProfileBeforeJdbc(String profiles) {
        var environment = environment();
        environment.setActiveProfiles(profiles.equals("none") ? new String[0] : profiles.split(","));
        assertRejectedBeforeJdbc(environment, APPROVAL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"qaryati_db", "qaryati_governorate_dev"})
    void rejectsForbiddenAndShadowedAlternateRoutingBeforeJdbc(String database) {
        String forbidden = "jdbc:postgresql://localhost:5432/" + database;
        for (String key : List.of("spring.datasource.url", "spring.datasource.hikari.jdbc-url",
                "spring.flyway.url", "spring.jpa.properties.hibernate.connection.url")) {
            assertRejectedBeforeJdbc(environment().withProperty(key, forbidden), APPROVAL);
            var environment = environment();
            String alias = key.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_');
            environment.getPropertySources().addLast(new SystemEnvironmentPropertySource(
                    "shadowed-external-routing", Map.of(alias, forbidden)));
            assertRejectedBeforeJdbc(environment, APPROVAL);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "spring.datasource.url,jdbc:postgresql://other:5432/qaryati_test_db",
            "spring.datasource.url,jdbc:postgresql://localhost:5432/qaryati_test_db?currentSchema=other",
            "spring.datasource.hikari.jdbc-url,jdbc:postgresql://localhost:5432/qaryati_test_db",
            "spring.datasource.hikari.username,migration-fixture-user",
            "spring.datasource.jndi-name,unsupported",
            "spring.datasource.type,com.zaxxer.hikari.HikariDataSource",
            "spring.datasource.driver-class-name,unsupported",
            "spring.jpa.properties.hibernate.connection.url,jdbc:postgresql://localhost:5432/qaryati_test_db",
            "spring.jpa.properties.jakarta.persistence.jdbc.url,jdbc:postgresql://localhost:5432/qaryati_test_db",
            "hibernate.connection.url,jdbc:postgresql://localhost:5432/qaryati_test_db",
            "jdbc.url,jdbc:postgresql://localhost:5432/qaryati_test_db",
            "spring.flyway.url,jdbc:postgresql://localhost:5432/qaryati_test_db",
            "spring.flyway.user,migration-fixture-user",
            "spring.flyway.password,unused-offline-value",
            "spring.flyway.enabled,false",
            "spring.flyway.clean-disabled,true",
            "spring.flyway.locations,unsupported",
            "spring.flyway.schemas,other",
            "spring.flyway.baseline-on-migrate,true",
            "spring.flyway.create-schemas,true",
            "spring.flyway.target,1",
            "spring.liquibase.enabled,true",
            "spring.sql.init.mode,always",
            "spring.jpa.hibernate.ddl-auto,update",
            "spring.jpa.generate-ddl,true",
            "spring.config.import,unsupported",
            "spring.profiles.group.test,dev",
            "spring.main.sources,unsupported"
    })
    void rejectsExternalMigrationAndUnsupportedConfigurationBeforeJdbc(String key, String value) {
        assertRejectedBeforeJdbc(environment().withProperty(key, value), APPROVAL);
    }

    @Test
    void rejectsOpaqueSourcesBeforeJdbc() {
        var environment = environment();
        environment.getPropertySources().addLast(new PropertySource<Object>("opaque", new Object()) {
            @Override public Object getProperty(String key) { return null; }
        });
        assertRejectedBeforeJdbc(environment, APPROVAL);
    }

    @Test
    void rejectsUnsupportedRegistryBeforeJdbc() {
        var unsupported = mock(ConfigurableApplicationContext.class);
        when(unsupported.getEnvironment()).thenReturn(environment());
        try (var driver = mockStatic(DriverManager.class)) {
            assertThrows(IllegalStateException.class,
                    () -> new MigrationTestDatabaseGuard(APPROVAL).initialize(unsupported));
            driver.verifyNoInteractions();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"definition", "singleton", "alias"})
    void rejectsExistingDatasourceNamesBeforeJdbc(String existing) {
        try (var context = context(environment()); var driver = mockStatic(DriverManager.class)) {
            switch (existing) {
                case "definition" -> context.registerBean("dataSource", Object.class, () -> {
                    throw new AssertionError("An existing datasource must not be initialized or replaced");
                });
                case "singleton" -> context.getBeanFactory().registerSingleton("dataSource", new Object());
                case "alias" -> context.registerAlias("mustNotInitialize", "dataSource");
                default -> throw new AssertionError(existing);
            }
            assertThrows(IllegalStateException.class,
                    () -> new MigrationTestDatabaseGuard(APPROVAL).initialize(context));
            assertFalse(context.isActive());
            assertFalse(context.getEnvironment().getPropertySources().contains(MigrationTestDatabaseGuard.SAFETY_SOURCE));
            driver.verifyNoInteractions();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"parent-context", "parent-factory", "frozen-factory"})
    void rejectsParentAndFrozenFactoriesBeforeJdbc(String conflict) {
        try (var parent = context(environment()); var context = context(environment());
             var driver = mockStatic(DriverManager.class)) {
            switch (conflict) {
                case "parent-context" -> context.setParent(parent);
                case "parent-factory" -> context.getBeanFactory().setParentBeanFactory(parent.getBeanFactory());
                case "frozen-factory" -> context.getBeanFactory().freezeConfiguration();
                default -> throw new AssertionError(conflict);
            }
            assertThrows(IllegalStateException.class,
                    () -> new MigrationTestDatabaseGuard(APPROVAL).initialize(context));
            assertNotInstalled(context);
            driver.verifyNoInteractions();
        }
    }

    @Test
    void verifiedEmptyDatabaseInstallsOneFixedDatasourceAndMigrationPolicy() throws Exception {
        var fixture = fixture();
        try (var context = context(environment()); var driver = mockStatic(DriverManager.class)) {
            connectWith(driver, fixture);
            new MigrationTestDatabaseGuard(APPROVAL).initialize(context);
            registeredDataSource(context);
            assertFalse(context.isActive());
            assertFalse(context.getBeanFactoryPostProcessors().isEmpty());
            context.getBeanFactoryPostProcessors().forEach(
                    processor -> processor.postProcessBeanFactory(context.getBeanFactory()));
            var environment = context.getEnvironment();
            assertTrue(environment.getPropertySources().contains(MigrationTestDatabaseGuard.SAFETY_SOURCE));
            for (var setting : Map.ofEntries(
                    Map.entry("spring.flyway.enabled", "true"),
                    Map.entry("spring.flyway.clean-disabled", "true"),
                    Map.entry("spring.flyway.baseline-on-migrate", "false"),
                    Map.entry("spring.flyway.create-schemas", "false"),
                    Map.entry("spring.flyway.locations", "classpath:db/migration"),
                    Map.entry("spring.flyway.schemas", "public"),
                    Map.entry("spring.flyway.default-schema", "public"),
                    Map.entry("spring.flyway.table", "flyway_schema_history"),
                    Map.entry("spring.flyway.target", "2"),
                    Map.entry("spring.flyway.out-of-order", "false"),
                    Map.entry("spring.flyway.validate-on-migrate", "true"),
                    Map.entry("spring.liquibase.enabled", "false"),
                    Map.entry("spring.sql.init.mode", "never"),
                    Map.entry("spring.jpa.hibernate.ddl-auto", "validate"),
                    Map.entry("spring.jpa.properties.hibernate.hbm2ddl.auto", "validate"),
                    Map.entry("spring.jpa.generate-ddl", "false"),
                    Map.entry("spring.jpa.properties.jakarta.persistence.schema-generation.database.action", "validate"),
                    Map.entry("spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action", "none"),
                    Map.entry("spring.main.allow-bean-definition-overriding", "false")).entrySet()) {
                assertEquals(setting.getValue(), environment.getProperty(setting.getKey()), setting.getKey());
            }
            for (String routing : List.of("spring.flyway.url", "spring.flyway.user", "spring.flyway.password")) {
                assertNull(environment.getProperty(routing), routing);
            }
            var order = inOrder(fixture.connection(), fixture.identityStatement(), fixture.identityResult(),
                    fixture.freshnessStatement(), fixture.freshnessResult());
            order.verify(fixture.connection()).setReadOnly(true);
            order.verify(fixture.connection()).setAutoCommit(false);
            order.verify(fixture.connection()).getMetaData();
            order.verify(fixture.identityStatement()).executeQuery(MigrationTestDatabaseGuard.IDENTITY_SQL);
            order.verify(fixture.identityResult()).close();
            order.verify(fixture.identityStatement()).close();
            order.verify(fixture.freshnessStatement()).executeQuery(MigrationTestDatabaseGuard.FRESHNESS_SQL);
            order.verify(fixture.freshnessResult()).close();
            order.verify(fixture.freshnessStatement()).close();
            order.verify(fixture.connection()).rollback();
            order.verify(fixture.connection()).close();
            var requested = org.mockito.ArgumentCaptor.forClass(Properties.class);
            driver.verify(() -> DriverManager.getConnection(eq(MigrationTestDatabaseGuard.URL), requested.capture()));
            assertEquals(4, requested.getValue().size());
            assertEquals(USER, requested.getValue().getProperty("user"));
            assertEquals("unused-offline-value", requested.getValue().getProperty("password"));
            assertEquals("5", requested.getValue().getProperty("connectTimeout"));
            assertEquals("10", requested.getValue().getProperty("socketTimeout"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"product", "database", "user", "missing-row"})
    void rejectsIdentityMismatchBeforeFreshnessAndClosesPreflight(String mismatch) throws Exception {
        var fixture = fixture();
        switch (mismatch) {
            case "product" -> when(fixture.metadata().getDatabaseProductName()).thenReturn("OtherDatabase");
            case "database" -> when(fixture.identityResult().getString(1)).thenReturn("qaryati_db");
            case "user" -> when(fixture.identityResult().getString(2)).thenReturn("other-user");
            case "missing-row" -> when(fixture.identityResult().next()).thenReturn(false);
            default -> throw new AssertionError(mismatch);
        }
        try (var context = context(environment()); var driver = mockStatic(DriverManager.class)) {
            connectWith(driver, fixture);
            assertThrows(IllegalStateException.class,
                    () -> new MigrationTestDatabaseGuard(APPROVAL).initialize(context));
            assertNotInstalled(context);
            verify(fixture.freshnessStatement(), never()).executeQuery(anyString());
            verify(fixture.connection()).rollback();
            verify(fixture.connection()).close();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-public", "history", "objects", "missing-row"})
    void rejectsNonemptyOrUnverifiablePublicSchemaAndRollsBack(String mismatch) throws Exception {
        var fixture = fixture();
        switch (mismatch) {
            case "missing-public" -> when(fixture.freshnessResult().getBoolean(1)).thenReturn(false);
            case "history" -> when(fixture.freshnessResult().getBoolean(2)).thenReturn(true);
            case "objects" -> when(fixture.freshnessResult().getBoolean(3)).thenReturn(true);
            case "missing-row" -> when(fixture.freshnessResult().next()).thenReturn(false);
            default -> throw new AssertionError(mismatch);
        }
        try (var context = context(environment()); var driver = mockStatic(DriverManager.class)) {
            connectWith(driver, fixture);
            assertThrows(IllegalStateException.class,
                    () -> new MigrationTestDatabaseGuard(APPROVAL).initialize(context));
            assertNotInstalled(context);
            verify(fixture.identityResult()).close();
            verify(fixture.identityStatement()).close();
            verify(fixture.freshnessResult()).close();
            verify(fixture.freshnessStatement()).close();
            verify(fixture.connection()).rollback();
            verify(fixture.connection()).close();
        }
    }

    @Test
    void connectionFailureIsSanitizedWithoutInstallingMigrationBeans() {
        var connectionFailure = new SQLException("private driver details");
        try (var context = context(environment()); var driver = mockStatic(DriverManager.class)) {
            driver.when(() -> DriverManager.getConnection(eq(MigrationTestDatabaseGuard.URL), any(Properties.class)))
                    .thenThrow(connectionFailure);
            var failure = assertThrows(IllegalStateException.class,
                    () -> new MigrationTestDatabaseGuard(APPROVAL).initialize(context));
            assertFalse(failure.getMessage().contains("private driver details"));
            assertNull(failure.getCause());
            assertNotInstalled(context);
        }
    }

    @Test
    void freshnessQueryFailureClosesResourcesAndRollsBackBeforeRejection() throws Exception {
        var queryFailure = new SQLException("private freshness details");
        var fixture = fixture();
        when(fixture.freshnessStatement().executeQuery(MigrationTestDatabaseGuard.FRESHNESS_SQL))
                .thenThrow(queryFailure);
        try (var context = context(environment()); var driver = mockStatic(DriverManager.class)) {
            connectWith(driver, fixture);
            var failure = assertThrows(IllegalStateException.class,
                    () -> new MigrationTestDatabaseGuard(APPROVAL).initialize(context));
            assertFalse(failure.getMessage().contains("private freshness details"));
            assertNull(failure.getCause());
            assertNotInstalled(context);
            verify(fixture.identityResult()).close();
            verify(fixture.identityStatement()).close();
            verify(fixture.freshnessStatement()).close();
            verify(fixture.connection()).rollback();
            verify(fixture.connection()).close();
        }
    }

    @Test
    void registeredDatasourceChecksEveryLaterIdentityWithoutRepeatingFreshness() throws Exception {
        var preflight = fixture();
        var later = fixture();
        var mismatch = fixture();
        when(later.freshnessResult().getBoolean(3)).thenReturn(true);
        when(mismatch.identityResult().getString(2)).thenReturn("other-user");
        try (var context = context(environment()); var driver = mockStatic(DriverManager.class)) {
            driver.when(() -> DriverManager.getConnection(eq(MigrationTestDatabaseGuard.URL), any(Properties.class)))
                    .thenReturn(preflight.connection(), later.connection(), mismatch.connection());
            new MigrationTestDatabaseGuard(APPROVAL).initialize(context);
            var source = registeredDataSource(context);
            try (var connection = source.getConnection()) {
                assertSame(later.connection(), connection);
            }
            assertThrows(SQLException.class, source::getConnection);
            verify(later.identityStatement()).executeQuery(MigrationTestDatabaseGuard.IDENTITY_SQL);
            verify(mismatch.identityStatement()).executeQuery(MigrationTestDatabaseGuard.IDENTITY_SQL);
            verify(later.freshnessStatement(), never()).executeQuery(anyString());
            verify(mismatch.freshnessStatement(), never()).executeQuery(anyString());
            verify(later.connection()).close();
            verify(mismatch.connection()).close();
            assertFalse(context.isActive());
        }
    }

    @Test
    void perCallCredentialsAndDelegateUnwrappingCannotOpenConnections() throws Exception {
        var source = new MigrationTestDatabaseGuard.MigrationDataSource(USER, "unused-offline-value");
        try (var driver = mockStatic(DriverManager.class)) {
            assertThrows(SQLException.class, () -> source.getConnection("other-user", "other-offline-value"));
            assertSame(source, source.unwrap(DataSource.class));
            assertTrue(source.isWrapperFor(DataSource.class));
            assertFalse(source.isWrapperFor(Connection.class));
            assertThrows(SQLException.class, () -> source.unwrap(Connection.class));
            driver.verify(() -> DriverManager.getConnection(anyString(), any(Properties.class)), never());
            driver.verify(() -> DriverManager.getConnection(anyString(), anyString(), anyString()), never());
            driver.verify(() -> DriverManager.getConnection(anyString()), never());
        }
    }

    @ParameterizedTest
    @ValueSource(classes = {DataSource.class, XADataSource.class})
    void factoryCheckRejectsAdditionalDatasourceDefinitionsWithoutInitializingBeans(Class<?> type) throws Exception {
        var fixture = fixture();
        try (var context = context(environment()); var driver = mockStatic(DriverManager.class)) {
            connectWith(driver, fixture);
            new MigrationTestDatabaseGuard(APPROVAL).initialize(context);
            var alternative = new RootBeanDefinition(type);
            alternative.setInstanceSupplier(() -> {
                throw new AssertionError("An alternative datasource must not be created");
            });
            context.registerBeanDefinition("alternativeDataSource", alternative);
            assertFalse(context.getBeanFactoryPostProcessors().isEmpty());
            assertThrows(IllegalStateException.class, () -> context.getBeanFactoryPostProcessors().forEach(
                    processor -> processor.postProcessBeanFactory(context.getBeanFactory())));
            assertFalse(context.isActive());
        }
    }

    @Test
    void enforcerRejectsAlternativeDatasourceAndFlywayDatasourceBeforeInitialization() {
        var source = new MigrationTestDatabaseGuard.MigrationDataSource(USER, "unused-offline-value");
        var enforcer = new MigrationTestDatabaseGuard.SameDataSourceEnforcer(source);
        var alternative = mock(DataSource.class);
        var flyway = mock(Flyway.class);
        var configuration = mock(Configuration.class);
        when(flyway.getConfiguration()).thenReturn(configuration);
        when(configuration.getDataSource()).thenReturn(source);
        assertSame(source, enforcer.postProcessBeforeInitialization(source, "dataSource"));
        assertSame(flyway, enforcer.postProcessBeforeInitialization(flyway, "flyway"));
        assertThrows(IllegalStateException.class,
                () -> enforcer.postProcessBeforeInitialization(alternative, "alternativeDataSource"));
        assertThrows(IllegalStateException.class,
                () -> enforcer.postProcessBeforeInitialization(mock(XADataSource.class), "alternativeXaDataSource"));
        when(configuration.getDataSource()).thenReturn(alternative);
        assertThrows(IllegalStateException.class,
                () -> enforcer.postProcessBeforeInitialization(flyway, "flyway"));
    }
}
