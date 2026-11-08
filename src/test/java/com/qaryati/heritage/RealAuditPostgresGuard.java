package com.qaryati.heritage;

import java.sql.*;
import java.util.*;
import org.springframework.context.*;
import org.springframework.core.env.*;

/** Validates configuration before context refresh or the preliminary identity connection. */
public final class RealAuditPostgresGuard implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    static final String OPT_IN = "QARYATI_REAL_AUDIT_TESTS";
    static final String URL = "jdbc:postgresql://localhost:5432/qaryati_test_db";

    static void validate(ConfigurableEnvironment env, String optIn) {
        if (!"verified-test-db".equals(optIn) || !Set.of("test").equals(Set.of(env.getActiveProfiles()))
                || !URL.equals(env.getProperty("spring.datasource.url")))
            throw new IllegalStateException("Explicit dedicated test database configuration required");
        String username = env.getRequiredProperty("spring.datasource.username");
        if (username.isBlank()) throw new IllegalStateException("Database username required");
        // Include shadowed system/environment values. Never include configuration values in errors.
        for (PropertySource<?> source : env.getPropertySources()) {
            if (!(source instanceof EnumerablePropertySource<?> enumerable)) continue;
            for (String name : enumerable.getPropertyNames()) {
                String key = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
                if (!(key.startsWith("springdatasource") || key.startsWith("springflyway")
                        || key.startsWith("springjpa") || key.startsWith("jakartapersistence")
                        || key.startsWith("hibernate"))) continue;
                String value = env.resolveRequiredPlaceholders(String.valueOf(source.getProperty(name)));
                // The project's packaged default points at development; the active test profile
                // overrides that one baseline property. External/system overrides are never exempt.
                if (name.equals("spring.datasource.url")
                        && source.getName().contains("class path resource [application.properties]")) continue;
                if ((key.contains("url") && !URL.equals(value)) || key.contains("jndi")
                        || key.endsWith("connectionproviderclass") || key.endsWith("datasourceclassname")
                        || key.endsWith("connectioninitsql") || key.endsWith("connectiontestquery")
                        || key.endsWith("datasource")
                        || key.contains("datasourceproperties") || key.contains("hibernateconnectionproperties")
                        || key.endsWith("connectionfactoryclass")
                        || (key.equals("springdatasourcetype") && !value.equals("com.zaxxer.hikari.HikariDataSource"))
                        || (key.endsWith("username") && !username.equals(value)))
                    throw new IllegalStateException("Conflicting database override: " + name);
            }
        }
    }

    @Override public void initialize(ConfigurableApplicationContext context) {
        var env = context.getEnvironment();
        validate(env, System.getenv(OPT_IN));
        String username = env.getRequiredProperty("spring.datasource.username");
        Properties connection = new Properties();
        connection.setProperty("user", username);
        connection.setProperty("password", env.getRequiredProperty("spring.datasource.password"));
        connection.setProperty("connectTimeout", "5");
        connection.setProperty("socketTimeout", "10");
        try (var c = DriverManager.getConnection(URL, connection);
             var s = c.createStatement()) {
            s.setQueryTimeout(5);
            try (var r = s.executeQuery("SELECT current_database(), current_user")) {
            if (!"PostgreSQL".equals(c.getMetaData().getDatabaseProductName()) || !r.next()
                    || !"qaryati_test_db".equals(r.getString(1)) || !username.equals(r.getString(2)))
                throw new IllegalStateException("Database identity mismatch");
            }
        } catch (SQLException e) { throw new IllegalStateException("Database identity verification failed"); }
        env.getPropertySources().addFirst(new MapPropertySource("real-audit-safety", safetyProperties()));
    }

    static Map<String, Object> safetyProperties() {
        return Map.ofEntries(
                Map.entry("spring.datasource.url", URL), Map.entry("spring.datasource.hikari.jdbc-url", URL),
                Map.entry("spring.datasource.hikari.maximum-pool-size", "2"),
                Map.entry("spring.datasource.hikari.minimum-idle", "0"),
                Map.entry("spring.datasource.hikari.connection-timeout", "5000"),
                Map.entry("spring.datasource.hikari.validation-timeout", "3000"),
                Map.entry("spring.datasource.hikari.auto-commit", "true"),
                Map.entry("spring.jdbc.template.query-timeout", "5s"),
                Map.entry("spring.transaction.default-timeout", "10s"),
                Map.entry("spring.flyway.enabled", "false"), Map.entry("spring.sql.init.mode", "never"),
                Map.entry("spring.jpa.hibernate.ddl-auto", "none"), Map.entry("spring.jpa.generate-ddl", "false"),
                Map.entry("spring.jpa.properties.hibernate.hbm2ddl.auto", "none"),
                Map.entry("spring.jpa.properties.jakarta.persistence.schema-generation.database.action", "none"));
    }
}
