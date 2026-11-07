package com.qaryati.dataquality.repository;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

class HeritageSiteSourceLinksDatabaseGuardTest {
    @Test void refusesUnconfiguredDatabaseBeforeAnyConnection() {
        try (var context = new AnnotationConfigApplicationContext()) {
            assertThrows(IllegalStateException.class,
                    () -> new HeritageSiteSourceLinksPostgresIntegrationTest.Guard().initialize(context));
        }
    }
}
