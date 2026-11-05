package com.qaryati.dataquality.repository;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

class EventSourceLinksDatabaseGuardTest {
    @Test void refusesUnconfiguredDatabaseBeforeAnyConnection() {
        try (var context = new AnnotationConfigApplicationContext()) {
            assertThrows(IllegalStateException.class,
                    () -> new EventSourceLinksPostgresIntegrationTest.Guard().initialize(context));
        }
    }
}
