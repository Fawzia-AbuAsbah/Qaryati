package com.qaryati.heritage;

import java.lang.reflect.Modifier;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Binding checks only; the shared initializer's behavior is covered by its existing offline suite. */
class CulturalHeritageRealAuditGuardBindingTest {
    @Test
    void culturalHeritageReusesGuardWithUnchangedApprovalAndRealAuditConfiguration() {
        var type = CulturalHeritageRealAuditPostgresIntegrationTest.class;
        var configuration = type.getDeclaredAnnotation(ContextConfiguration.class);
        assertNotNull(configuration);
        assertArrayEquals(new Class<?>[]{VillageHistoryRealAuditPostgresIntegrationTest.Guard.class},
                configuration.initializers());

        var approval = type.getDeclaredAnnotation(EnabledIfEnvironmentVariable.class);
        assertNotNull(approval);
        assertEquals("QARYATI_REAL_AUDIT_TESTS", approval.named());
        assertEquals(VillageHistoryRealAuditPostgresIntegrationTest.Guard.APPROVAL_VARIABLE, approval.named());
        assertEquals("verified-test-db", approval.matches());
        var profiles = type.getDeclaredAnnotation(ActiveProfiles.class);
        assertNotNull(profiles);
        assertArrayEquals(new String[]{"test"}, profiles.value());

        var boot = type.getDeclaredAnnotation(SpringBootTest.class);
        assertNotNull(boot);
        assertArrayEquals(new Class<?>[]{RealAuditPostgresConfig.class}, boot.classes());
        assertEquals(SpringBootTest.WebEnvironment.NONE, boot.webEnvironment());
        assertEquals(0, boot.properties().length);
        assertEquals(RealAuditPostgresContract.class, type.getSuperclass());
        assertNull(type.getDeclaredAnnotation(Transactional.class));
    }

    @Test
    void springCanInstantiateReusedStaticInitializerWithoutConnecting() throws Exception {
        var type = VillageHistoryRealAuditPostgresIntegrationTest.Guard.class;
        assertTrue(Modifier.isStatic(type.getModifiers()));
        assertTrue(Modifier.isPublic(type.getDeclaredConstructor().getModifiers()));
        try (var driver = mockStatic(DriverManager.class)) {
            // Construction only: no initialize(), context refresh, or database approval changes.
            var initializer = BeanUtils.instantiateClass(type);
            assertInstanceOf(ApplicationContextInitializer.class, initializer);
            driver.verifyNoInteractions();
        }
    }
}
