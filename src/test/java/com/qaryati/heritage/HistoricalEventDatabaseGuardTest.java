package com.qaryati.heritage;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import static org.junit.jupiter.api.Assertions.*;

class HistoricalEventDatabaseGuardTest {
    @Test void rejectsMissingIndependentVerification() {
        check("test", false, "jdbc:postgresql://localhost:5432/qaryati_test_db", null);
    }
    @Test void rejectsDevelopmentDatabase() {
        check("test", true, "jdbc:postgresql://localhost:5432/qaryati_db", null);
    }
    @Test void rejectsMissingTestProfile() {
        check("dev", true, "jdbc:postgresql://localhost:5432/qaryati_test_db", null);
    }
    @Test void rejectsSeparateFlywayDatabaseBeforeAnyConnection() {
        check("test", true, "jdbc:postgresql://localhost:5432/qaryati_test_db",
                "jdbc:postgresql://localhost:5432/qaryati_db");
    }
    private void check(String profile, boolean verified, String url, String flywayUrl) {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles(profile);
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                    "qaryati.heritage-tests.database-verified", verified, "spring.datasource.url", url)));
            if (flywayUrl != null) context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("flyway", Map.of("spring.flyway.url", flywayUrl)));
            assertThrows(IllegalStateException.class,
                    () -> new HistoricalEventRepositoryIntegrationTest.DatabaseGuard().initialize(context));
        }
    }
}
