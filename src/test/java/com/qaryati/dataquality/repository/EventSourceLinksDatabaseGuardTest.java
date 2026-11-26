package com.qaryati.dataquality.repository;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

class EventSourceLinksDatabaseGuardTest {
    @Test void refusesUnconfiguredDatabaseBeforeAnyConnection() {
        try (var driver = mockStatic(DriverManager.class);
             var context = new GenericApplicationContext()) {
            context.setEnvironment(new MockEnvironment());
            context.registerBean("mustNotInitialize", Object.class, () -> {
                throw new AssertionError("Guard must reject before bean initialization");
            });
            assertThrows(IllegalStateException.class,
                    () -> new EventSourceLinksPostgresIntegrationTest.Guard("unapproved").initialize(context));
            assertFalse(context.isActive());
            assertFalse(context.getBeanFactory().containsSingleton("mustNotInitialize"));
            driver.verifyNoInteractions();
        }
    }
}
