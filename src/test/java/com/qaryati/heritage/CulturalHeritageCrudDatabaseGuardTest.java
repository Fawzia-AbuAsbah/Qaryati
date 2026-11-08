package com.qaryati.heritage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.support.GenericApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

/** Safety checks only: no context refresh, connection, migration or integration execution. */
class CulturalHeritageCrudDatabaseGuardTest {
    @Test void crudGuardRejectsMissingDedicatedProfileBeforeConnecting() {
        try (var context = new GenericApplicationContext()) {
            context.getEnvironment().setActiveProfiles("unsafe-profile");
            assertThrows(IllegalStateException.class,
                    () -> new CulturalHeritageCrudPostgresIntegrationTest.Guard().initialize(context));
        }
    }
    @Test void postgresCrudRequiresItsOwnExplicitOptIn() {
        var guard = CulturalHeritageCrudPostgresIntegrationTest.class.getAnnotation(EnabledIfEnvironmentVariable.class);
        assertEquals("QARYATI_CULTURAL_CRUD_TESTS", guard.named());
        assertEquals("verified-test-db", guard.matches());
    }
}
