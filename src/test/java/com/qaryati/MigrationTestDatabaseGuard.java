package com.qaryati;

import com.qaryati.heritage.DedicatedPostgresSafety;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.datasource.AbstractDataSource;

import javax.sql.DataSource;
import javax.sql.XADataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Migration-only gate. Approval must follow independent provisioning of an empty,
 * exclusively reserved disposable database on a trusted PostgreSQL server.
 * Catalog checks cannot prove exclusive ownership or prevent concurrent access.
 */
public final class MigrationTestDatabaseGuard
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    public static final String APPROVAL_VARIABLE = "QARYATI_MIGRATION_TESTS";
    static final String URL = DedicatedPostgresSafety.URL;
    static final String SAFETY_SOURCE = "migration-test-safety";
    static final String IDENTITY_SQL = "SELECT pg_catalog.current_database(), current_user";
    // Inspect metadata/existence only; never read subscription or user-mapping options.
    static final String FRESHNESS_SQL = """
            WITH target_public AS (
                SELECT oid FROM pg_catalog.pg_namespace WHERE nspname = 'public'
            )
            SELECT
                EXISTS (SELECT 1 FROM target_public),
                EXISTS (SELECT 1 FROM pg_catalog.pg_class
                        WHERE relnamespace IN (SELECT oid FROM target_public)
                          AND relname = 'flyway_schema_history'),
                (EXISTS (SELECT 1 FROM pg_catalog.pg_namespace
                         WHERE nspname NOT IN ('public', 'pg_catalog', 'information_schema', 'pg_toast'))
                 OR EXISTS (SELECT 1 FROM pg_catalog.pg_class
                            WHERE relnamespace IN (SELECT oid FROM target_public))
                 OR EXISTS (SELECT 1 FROM pg_catalog.pg_proc
                            WHERE pronamespace IN (SELECT oid FROM target_public))
                 OR EXISTS (SELECT 1 FROM pg_catalog.pg_type
                            WHERE typnamespace IN (SELECT oid FROM target_public))
                 OR EXISTS (SELECT 1 FROM pg_catalog.pg_depend
                            WHERE refclassid = 'pg_catalog.pg_namespace'::pg_catalog.regclass
                              AND refobjid IN (SELECT oid FROM target_public))
                 OR EXISTS (SELECT 1 FROM pg_catalog.pg_extension WHERE extname <> 'plpgsql')
                 OR EXISTS (SELECT 1 FROM pg_catalog.pg_event_trigger)
                 OR EXISTS (SELECT 1 FROM pg_catalog.pg_foreign_data_wrapper)
                 OR EXISTS (SELECT 1 FROM pg_catalog.pg_foreign_server)
                 OR EXISTS (SELECT 1 FROM pg_catalog.pg_user_mapping)
                 OR EXISTS (SELECT 1 FROM pg_catalog.pg_publication)
                 OR EXISTS (SELECT 1 FROM pg_catalog.pg_subscription
                            WHERE subdbid = (SELECT oid FROM pg_catalog.pg_database
                                             WHERE datname = pg_catalog.current_database()))
                 OR EXISTS (SELECT 1 FROM pg_catalog.pg_default_acl)
                 OR EXISTS (SELECT 1 FROM pg_catalog.pg_largeobject_metadata)
                 OR EXISTS (SELECT 1 FROM pg_catalog.pg_transform)
                 OR EXISTS (SELECT 1 FROM pg_catalog.pg_language
                            WHERE lanname NOT IN ('internal', 'c', 'sql', 'plpgsql')))
            """;

    private final boolean approved;

    public MigrationTestDatabaseGuard() {
        this(System.getenv(APPROVAL_VARIABLE));
    }

    // Offline regressions never consult or change the process's real approval.
    MigrationTestDatabaseGuard(String approval) {
        approved = "verified-disposable-empty-test-db".equals(approval);
    }

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        try {
            if (context.isActive() || context.getParent() != null) throw rejected();
            var environment = context.getEnvironment();
            // Reuse the pure preflight validator, never the no-migrations initializer.
            DedicatedPostgresSafety.validate(environment, approved);
            rejectUnsupportedOverrides(environment);
            if (!(context.getBeanFactory() instanceof DefaultListableBeanFactory factory)) throw rejected();
            if (factory.getParentBeanFactory() != null || factory.isConfigurationFrozen()
                    || factory.isAlias("dataSource") || factory.containsBeanDefinition("dataSource")
                    || factory.containsSingleton("dataSource")
                    || factory.getBeanNamesForType(DataSource.class, false, false).length != 0
                    || factory.getBeanNamesForType(XADataSource.class, false, false).length != 0) throw rejected();

            var source = new MigrationDataSource(environment.getRequiredProperty("spring.datasource.username"),
                    environment.getRequiredProperty("spring.datasource.password"));
            verifyFreshness(source);

            // A typed definition makes Boot back off; its supplier returns the exact
            // datasource used above, with identity checks on every later connection.
            var definition = new RootBeanDefinition(MigrationDataSource.class);
            definition.setInstanceSupplier(() -> source);
            definition.setPrimary(true);
            factory.setAllowBeanDefinitionOverriding(false);
            factory.registerBeanDefinition("dataSource", definition);
            context.addBeanFactoryPostProcessor(candidate -> {
                var names = candidate.getBeanNamesForType(DataSource.class, false, false);
                if (candidate != factory || candidate.getBeanDefinition("dataSource") != definition
                        || names.length != 1 || !names[0].equals("dataSource")
                        || candidate.getBeanNamesForType(XADataSource.class, false, false).length != 0) throw rejected();
                candidate.addBeanPostProcessor(new SameDataSourceEnforcer(source));
            });
            environment.getPropertySources().addFirst(new MapPropertySource(SAFETY_SOURCE, migrationProperties()));
        } catch (SQLException | RuntimeException failure) {
            // Property conversion and driver errors can contain credentials or URLs.
            throw rejected();
        }
    }

    private static void rejectUnsupportedOverrides(ConfigurableEnvironment environment) {
        Set<String> datasourceKeys = Set.of("springdatasourceurl", "springdatasourceusername",
                "springdatasourcepassword", "springdatasourcedriverclassname");
        Set<String> jpaKeys = Set.of("springjpahibernateddlauto", "springjpagenerateddl",
                "springjpashowsql", "springjpaopeninview", "springjpapropertieshibernatehbm2ddlauto",
                "springjpapropertiesjakartapersistenceschemagenerationdatabaseaction",
                "springjpapropertiesjakartapersistenceschemagenerationscriptsaction");
        for (var source : environment.getPropertySources()) {
            if (!(source instanceof EnumerablePropertySource<?> enumerable)) continue;
            boolean packaged = source.getName().startsWith("Config resource 'class path resource [application")
                    && (source.getName().contains("[application.properties]")
                    || source.getName().contains("[application-test.properties]"));
            for (String name : enumerable.getPropertyNames()) {
                String key = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
                if (key.startsWith("springdatasource") && !datasourceKeys.contains(key)) throw rejected();
                if ((key.startsWith("springjpa") && !jpaKeys.contains(key)) || key.startsWith("hibernate")
                        || key.startsWith("jakartapersistence") || key.startsWith("javaxpersistence")
                        || key.startsWith("jdbc")) throw rejected();
                if (key.startsWith("springflyway")) {
                    String value = environment.resolveRequiredPlaceholders(String.valueOf(source.getProperty(name)));
                    if (packaged && name.equals("spring.flyway.enabled") && value.equals("true")) continue;
                    if (packaged && name.equals("spring.flyway.clean-disabled") && value.equals("true")) continue;
                    // Even a matching Flyway URL/user creates an independent datasource.
                    throw rejected();
                }
                if (key.equals("springprofilesactive")
                        && !"test".equals(source.getProperty(name))) throw rejected();
                if (key.startsWith("springconfig") || key.equals("springapplicationjson")
                        || key.equals("springprofilesinclude") || key.startsWith("springprofilesgroup")
                        || key.startsWith("springr2dbc") || key.startsWith("springjta")
                        || key.equals("springmainsources") || key.equals("springmainlazyinitialization")
                        || key.equals("springmainwebapplicationtype") || key.equals("springautoconfigureexclude")
                        || key.equals("contextinitializerclasses") || key.equals("contextlistenerclasses")) throw rejected();
            }
        }
    }

    private static void verifyFreshness(MigrationDataSource source) throws SQLException {
        try (Connection connection = source.openReadOnlyConnection()) {
            try {
                try (var statement = connection.createStatement()) {
                    statement.setQueryTimeout(5);
                    try (var result = statement.executeQuery(FRESHNESS_SQL)) {
                        if (!result.next() || !result.getBoolean(1)
                                || result.getBoolean(2) || result.getBoolean(3)) throw rejected();
                    }
                }
            } finally {
                connection.rollback();
            }
        }
    }

    private static Map<String, Object> migrationProperties() {
        var properties = new HashMap<>(DedicatedPostgresSafety.safetyProperties(true));
        // Our fixed datasource is not a configurable Hikari datasource.
        properties.remove("spring.datasource.type");
        properties.remove("spring.datasource.hikari.jdbc-url");
        properties.put("spring.flyway.enabled", "true");
        properties.put("spring.flyway.clean-disabled", "true");
        properties.put("spring.flyway.baseline-on-migrate", "false");
        properties.put("spring.flyway.create-schemas", "false");
        properties.put("spring.flyway.locations", "classpath:db/migration");
        properties.put("spring.flyway.schemas", "public");
        properties.put("spring.flyway.default-schema", "public");
        properties.put("spring.flyway.table", "flyway_schema_history");
        properties.put("spring.flyway.target", "2");
        properties.put("spring.flyway.out-of-order", "false");
        properties.put("spring.flyway.validate-on-migrate", "true");
        return properties;
    }

    private static IllegalStateException rejected() {
        return new IllegalStateException("Migration tests require an independently approved, disposable empty "
                + "test database and supported configuration; details withheld");
    }

    static final class SameDataSourceEnforcer implements BeanPostProcessor {
        private final DataSource expected;

        SameDataSourceEnforcer(DataSource expected) {
            this.expected = expected;
        }

        @Override
        public Object postProcessBeforeInitialization(Object bean, String name) {
            if ((bean instanceof DataSource && bean != expected) || bean instanceof XADataSource) throw rejected();
            if (bean instanceof Flyway flyway && (flyway.getConfiguration() == null
                    || flyway.getConfiguration().getDataSource() != expected)) throw rejected();
            return bean;
        }
    }

    static final class MigrationDataSource extends AbstractDataSource {
        private final String username;
        private final Properties credentials = new Properties();

        MigrationDataSource(String username, String password) {
            this.username = username;
            credentials.setProperty("user", username);
            credentials.setProperty("password", password);
            credentials.setProperty("connectTimeout", "5");
            credentials.setProperty("socketTimeout", "10");
        }

        @Override
        public Connection getConnection() throws SQLException {
            return open(false);
        }

        Connection openReadOnlyConnection() throws SQLException {
            return open(true);
        }

        private Connection open(boolean readOnly) throws SQLException {
            Connection connection = null;
            boolean transactionStarted = false;
            try {
                var requestedCredentials = new Properties();
                requestedCredentials.putAll(credentials);
                connection = DriverManager.getConnection(URL, requestedCredentials);
                if (readOnly) {
                    connection.setReadOnly(true);
                    connection.setAutoCommit(false);
                    transactionStarted = true;
                }
                if (!"PostgreSQL".equals(connection.getMetaData().getDatabaseProductName())) throw rejected();
                try (var statement = connection.createStatement()) {
                    statement.setQueryTimeout(5);
                    try (var result = statement.executeQuery(IDENTITY_SQL)) {
                        if (!result.next() || !"qaryati_test_db".equals(result.getString(1))
                                || !username.equals(result.getString(2))) throw rejected();
                    }
                }
                return connection;
            } catch (SQLException | RuntimeException failure) {
                if (connection != null) {
                    if (transactionStarted) try { connection.rollback(); } catch (SQLException ignored) { }
                    try { connection.close(); } catch (SQLException ignored) { }
                }
                throw new SQLException("Migration datasource connection or identity rejected; details withheld");
            }
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw new SQLException("Per-call migration datasource credentials are prohibited");
        }
    }
}
