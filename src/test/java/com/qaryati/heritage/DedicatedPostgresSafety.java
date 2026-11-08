package com.qaryati.heritage;

import java.sql.*;
import java.util.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.*;

/** Test-only, fail-closed configuration check performed before any connection or context refresh. */
public final class DedicatedPostgresSafety {
    public static final String URL = "jdbc:postgresql://localhost:5432/qaryati_test_db";
    private DedicatedPostgresSafety() {}

    private static String normalized(String key) {
        return key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public static void validate(ConfigurableEnvironment env, boolean optedIn) {
        if (!optedIn || !List.of("test").equals(Arrays.asList(env.getActiveProfiles()))
                || !URL.equals(env.getProperty("spring.datasource.url")))
            throw new IllegalStateException("Explicit dedicated test database opt-in and test-only profile required");
        String username = env.getRequiredProperty("spring.datasource.username");
        String password = env.getRequiredProperty("spring.datasource.password");
        if (username.isBlank() || password.isBlank()) throw new IllegalStateException("Database credentials required");
        for (PropertySource<?> source : env.getPropertySources()) {
            // Boot's aggregate delegates to the underlying sources inspected below.
            if (source.getClass().getName().equals("org.springframework.boot.context.properties.source.ConfigurationPropertySourcesPropertySource")) continue;
            if (source instanceof PropertySource.StubPropertySource
                    || source.getClass().getName().equals("org.springframework.boot.env.RandomValuePropertySource")) continue;
            if (!(source instanceof EnumerablePropertySource<?> enumerable))
                throw new IllegalStateException("Cannot inspect non-enumerable property source");
            for (String name : enumerable.getPropertyNames()) {
                String key = normalized(name);
                if (!(key.startsWith("springdatasource") || key.startsWith("springjpa")
                        || key.startsWith("springflyway") || key.startsWith("springsqlinit")
                        || key.startsWith("springliquibase") || key.startsWith("hibernate")
                        || key.startsWith("jakartapersistence") || key.startsWith("javaxpersistence")
                        || key.startsWith("jdbc") || key.equals("springmainallowbeandefinitionoverriding"))) continue;
                String value = env.resolveRequiredPlaceholders(String.valueOf(source.getProperty(name)));
                // Only immutable packaged defaults may be shadowed. External files, aliases and
                // system/environment values (including shadowed ones) must all pass the allowlist.
                boolean packaged = source.getName().startsWith("Config resource 'class path resource [application")
                        && (source.getName().contains("[application.properties]")
                        || source.getName().contains("[application-test.properties]"));
                if (packaged && name.equals("spring.datasource.url")
                        && (value.equals(URL) || value.equals("jdbc:postgresql://localhost:5432/qaryati_db"))) continue;
                if (packaged && name.equals("spring.flyway.enabled") && value.equals("true")) continue;
                boolean safe = switch (key) {
                    case "springdatasourceurl", "springdatasourcehikarijdbcurl", "springflywayurl",
                         "springjpapropertieshibernateconnectionurl", "springjpapropertiesjakartapersistencejdbcurl",
                         "springjpapropertiesjavaxpersistencejdbcurl", "hibernateconnectionurl",
                         "jakartapersistencejdbcurl", "javaxpersistencejdbcurl", "jdbcurl" -> URL.equals(value);
                    case "springdatasourceusername", "springdatasourcehikariusername" -> username.equals(value);
                    case "springdatasourcepassword", "springdatasourcehikaripassword" -> password.equals(value);
                    case "springdatasourcedriverclassname" -> value.equals("org.postgresql.Driver");
                    case "springdatasourcetype" -> value.equals("com.zaxxer.hikari.HikariDataSource");
                    case "springjpahibernateddlauto", "springjpapropertieshibernatehbm2ddlauto",
                         "hibernatehbm2ddlauto" -> Set.of("none", "validate").contains(value);
                    case "springjpagenerateddl", "springflywayenabled", "springliquibaseenabled",
                         "springmainallowbeandefinitionoverriding" -> value.equals("false");
                    case "springflywaycleandisabled" -> value.equals("true");
                    case "springsqlinitmode" -> value.equals("never");
                    case "springjpapropertiesjakartapersistenceschemagenerationdatabaseaction",
                         "springjpapropertiesjakartapersistenceschemagenerationscriptsaction" -> value.equals("none");
                    case "springjpashowsql", "springjpaopeninview" -> Set.of("true", "false").contains(value);
                    default -> false;
                };
                if (!safe) throw new IllegalStateException("Disallowed database configuration key: " + name);
            }
        }
    }

    public static Map<String, Object> safetyProperties(boolean validateSchema) {
        String ddl = validateSchema ? "validate" : "none";
        return Map.ofEntries(
                Map.entry("spring.datasource.url", URL), Map.entry("spring.datasource.hikari.jdbc-url", URL),
                Map.entry("spring.datasource.type", "com.zaxxer.hikari.HikariDataSource"),
                Map.entry("spring.flyway.enabled", "false"), Map.entry("spring.flyway.clean-disabled", "true"),
                Map.entry("spring.liquibase.enabled", "false"), Map.entry("spring.sql.init.mode", "never"),
                Map.entry("spring.jpa.generate-ddl", "false"), Map.entry("spring.jpa.hibernate.ddl-auto", ddl),
                Map.entry("spring.jpa.properties.hibernate.hbm2ddl.auto", ddl),
                Map.entry("spring.jpa.properties.jakarta.persistence.schema-generation.database.action", "none"),
                Map.entry("spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action", "none"),
                Map.entry("spring.main.allow-bean-definition-overriding", "false"));
    }

    public static void initialize(ConfigurableApplicationContext context, boolean optedIn, boolean validateSchema) {
        var env = context.getEnvironment();
        validate(env, optedIn);
        Properties credentials = new Properties();
        credentials.setProperty("user", env.getRequiredProperty("spring.datasource.username"));
        credentials.setProperty("password", env.getRequiredProperty("spring.datasource.password"));
        credentials.setProperty("connectTimeout", "5");
        credentials.setProperty("socketTimeout", "10");
        try (var connection = DriverManager.getConnection(URL, credentials);
             var statement = connection.createStatement()) {
            statement.setQueryTimeout(5);
            try (var result = statement.executeQuery("SELECT current_database(), current_user")) {
                if (!"PostgreSQL".equals(connection.getMetaData().getDatabaseProductName()) || !result.next()
                        || !"qaryati_test_db".equals(result.getString(1))
                        || !credentials.getProperty("user").equals(result.getString(2)))
                    throw new IllegalStateException("Database identity mismatch");
            }
        } catch (SQLException failure) { throw new IllegalStateException("Database identity verification failed"); }
        env.getPropertySources().addFirst(new MapPropertySource("dedicated-postgres-safety", safetyProperties(validateSchema)));
    }
}
