package com.qaryati.heritage;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.env.*;
import static org.junit.jupiter.api.Assertions.*;

/** Pure configuration validation: no Spring context, JDBC driver, or connection is created. */
class RealAuditPostgresGuardTest {
    StandardEnvironment environment(Map<String, Object> overrides) {
        var env = new StandardEnvironment();
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        env.setActiveProfiles("test");
        env.getPropertySources().addLast(new MapPropertySource("base", Map.of(
                "spring.datasource.url", RealAuditPostgresGuard.URL, "spring.datasource.username", "test-user")));
        env.getPropertySources().addFirst(new MapPropertySource("overrides", overrides));
        return env;
    }
    @Test void rejectsOptInProfileUrlAndUsernameBeforeAnyConnection() {
        assertThrows(IllegalStateException.class, () -> RealAuditPostgresGuard.validate(environment(Map.of()), null));
        var wrongProfile = environment(Map.of());
        wrongProfile.setActiveProfiles("test", "dev");
        assertThrows(IllegalStateException.class, () -> RealAuditPostgresGuard.validate(wrongProfile, "verified-test-db"));
        for (var overrides : List.of(Map.<String, Object>of("spring.datasource.url", "jdbc:postgresql://localhost:5432/qaryati"),
                Map.<String, Object>of("spring.datasource.username", "")))
            assertThrows(IllegalStateException.class, () -> RealAuditPostgresGuard.validate(environment(overrides), "verified-test-db"));
    }
    @Test void rejectsSecondaryAndShadowedUnsafeOverrides() {
        for (String key : List.of("spring.datasource.hikari.jdbc-url", "spring.flyway.url",
                "spring.jpa.properties.hibernate.connection.url", "spring.jpa.properties.jakarta.persistence.jdbc.url",
                "SPRING_DATASOURCE_HIKARI_JDBC_URL", "SPRING_FLYWAY_URL")) {
            var env = environment(Map.of(key, "jdbc:postgresql://localhost:5432/qaryati"));
            env.getPropertySources().addFirst(new MapPropertySource("safe-shadow", Map.of(key, RealAuditPostgresGuard.URL)));
            assertThrows(IllegalStateException.class, () -> RealAuditPostgresGuard.validate(env, "verified-test-db"), key);
        }
        assertThrows(IllegalStateException.class, () -> RealAuditPostgresGuard.validate(
                environment(Map.of("spring.datasource.hikari.username", "other-user")), "verified-test-db"));
        assertThrows(IllegalStateException.class, () -> RealAuditPostgresGuard.validate(
                environment(Map.of("spring.datasource.jndi-name", "unsafe")), "verified-test-db"));
        for (String key : List.of("spring.datasource.hikari.connection-init-sql",
                "spring.datasource.hikari.data-source-properties.serverName", "spring.datasource.type"))
            assertThrows(IllegalStateException.class, () -> RealAuditPostgresGuard.validate(
                    environment(Map.of(key, "unsafe")), "verified-test-db"));
    }
    @Test void acceptsOnlyExplicitSafeConfigurationWithoutConnecting() {
        assertDoesNotThrow(() -> RealAuditPostgresGuard.validate(environment(Map.of()), "verified-test-db"));
    }
    @Test void rejectsEverySqlHookAliasEvenWhenShadowedBeforeConnecting() {
        for (String hook : List.of("connection-test-query", "connection-init-sql")) {
            String camel = hook.equals("connection-test-query") ? "connectionTestQuery" : "connectionInitSql";
            for (String key : List.of("spring.datasource.hikari." + hook,
                    "spring.datasource.hikari." + camel,
                    "spring.datasource.hikari." + hook.replace("-", ""),
                    "SPRING_DATASOURCE_HIKARI_" + hook.replace('-', '_').toUpperCase(Locale.ROOT),
                    "SPRING_DATASOURCE_HIKARI_" + hook.replace("-", "").toUpperCase(Locale.ROOT))) {
                var env = environment(Map.of());
                env.getPropertySources().addLast(new SystemEnvironmentPropertySource("unsafe-environment",
                        Map.of(key, "SELECT unsafe_sql_hook()")));
                env.getPropertySources().addFirst(new MapPropertySource("shadow", Map.of(key, "")));
                assertThrows(IllegalStateException.class,
                        () -> RealAuditPostgresGuard.validate(env, "verified-test-db"), key);
            }
        }
    }

    @Test void safetyPropertiesOverridePoolStarvationAndBoundWaitsWithoutConnecting() {
        var env = environment(Map.of("spring.datasource.hikari.maximum-pool-size", "1",
                "spring.datasource.hikari.connection-timeout", "600000"));
        env.getPropertySources().addFirst(new MapPropertySource("safety", RealAuditPostgresGuard.safetyProperties()));
        assertEquals(2, env.getProperty("spring.datasource.hikari.maximum-pool-size", Integer.class));
        assertEquals(5000, env.getProperty("spring.datasource.hikari.connection-timeout", Integer.class));
        assertEquals("5s", env.getProperty("spring.jdbc.template.query-timeout"));
        assertDoesNotThrow(() -> RealAuditPostgresGuard.validate(env, "verified-test-db"));
    }
    @Test void bothConcreteSuitesRequireExplicitOptIn() {
        for (var type : List.of(VillageHistoryRealAuditPostgresIntegrationTest.class, CulturalHeritageRealAuditPostgresIntegrationTest.class)) {
            var annotation = type.getAnnotation(EnabledIfEnvironmentVariable.class);
            assertNotNull(annotation);
            assertEquals(RealAuditPostgresGuard.OPT_IN, annotation.named());
            assertEquals("verified-test-db", annotation.matches());
        }
    }
}
