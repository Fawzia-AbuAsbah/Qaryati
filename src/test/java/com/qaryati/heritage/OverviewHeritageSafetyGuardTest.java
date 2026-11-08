package com.qaryati.heritage;

import java.sql.DriverManager;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;
import org.springframework.boot.test.context.*;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import com.qaryati.village.api.VillageLookupApi;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Metadata and mocked failure paths only; never refreshes a persistence context. */
class OverviewHeritageSafetyGuardTest {
    @Test void allSixSuitesHaveIndependentGatesIsolatedConfigurationsAndFailBeforeJdbc() throws Exception {
        var suites = Map.of(
                "com.qaryati.heritage.VillageHistoricalOverviewPostgresIntegrationTest", "QARYATI_HISTORY_CRUD_TESTS",
                "com.qaryati.heritage.CulturalHeritageCrudPostgresIntegrationTest", "QARYATI_CULTURAL_CRUD_TESTS",
                "com.qaryati.dataquality.repository.VillageHistorySourceLinksPostgresIntegrationTest", "QARYATI_HISTORY_LINK_TESTS",
                "com.qaryati.dataquality.repository.CulturalHeritageSourceLinksPostgresIntegrationTest", "QARYATI_CULTURAL_LINK_TESTS",
                "com.qaryati.heritage.VillageHistoryRepositoryIntegrationTest", "repository",
                "com.qaryati.heritage.CulturalHeritageRepositoryIntegrationTest", "repository");
        try (var driver = mockStatic(DriverManager.class)) {
            for (var entry : suites.entrySet()) {
                var suite = Class.forName(entry.getKey());
                if (entry.getValue().equals("repository")) {
                    var gate = suite.getAnnotation(EnabledIfSystemProperty.class);
                    assertEquals("qaryati.heritage-tests.database-verified", gate.named());
                    assertEquals("true", gate.matches());
                } else {
                    var gate = suite.getAnnotation(EnabledIfEnvironmentVariable.class);
                    assertEquals(entry.getValue(), gate.named());
                    assertEquals("verified-test-db", gate.matches());
                }
                var config = suite.getAnnotation(SpringBootTest.class).classes()[0];
                assertNotNull(config.getAnnotation(TestComponent.class));
                assertNull(config.getAnnotation(org.springframework.context.annotation.ComponentScan.class));
                assertTrue(config.getAnnotation(EnableJpaRepositories.class).includeFilters().length > 0);
                var constructor = suite.getAnnotation(ContextConfiguration.class).initializers()[0].getDeclaredConstructor();
                constructor.setAccessible(true);
                var guard = (ApplicationContextInitializer<org.springframework.context.ConfigurableApplicationContext>) constructor.newInstance();
                try (var context = new GenericApplicationContext()) {
                    context.getEnvironment().setActiveProfiles("unsafe");
                    assertThrows(IllegalStateException.class, () -> guard.initialize(context));
                }
            }
            driver.verifyNoInteractions();
        }
    }

    @Test void replacementInsertFailureStillDeletesOnlyCommittedFixtureIds() {
        var suite = new CulturalHeritageCrudPostgresIntegrationTest();
        suite.jdbc = mock(JdbcTemplate.class);
        suite.villages = mock(VillageLookupApi.class);
        suite.transactions = mock(PlatformTransactionManager.class);
        when(suite.transactions.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        when(suite.jdbc.queryForObject(startsWith("INSERT INTO public.governorates"), eq(Long.class), anyString())).thenReturn(101L);
        when(suite.jdbc.queryForObject(startsWith("INSERT INTO public.villages"), eq(Long.class), eq(101L))).thenReturn(102L);
        when(suite.jdbc.queryForObject("INSERT INTO public.sources(title,source_type) VALUES ('test','BOOK') RETURNING id", Long.class)).thenReturn(103L);
        when(suite.jdbc.queryForObject("INSERT INTO public.sources(title,source_type) VALUES ('replacement','BOOK') RETURNING id", Long.class))
                .thenThrow(new IllegalStateException("simulated replacement insert failure"));
        try {
            assertThrows(IllegalStateException.class, suite::auditFailureRestoresUpdatesAndDeletedHistoryWithItsCascadedLinks);
            verify(suite.jdbc).update("DELETE FROM public.cultural_heritage WHERE village_id=?", 102L);
            verify(suite.jdbc).update("DELETE FROM public.sources WHERE id=?", 103L);
            verify(suite.jdbc).update("DELETE FROM public.villages WHERE id=?", 102L);
            verify(suite.jdbc).update("DELETE FROM public.governorates WHERE id=?", 101L);
            verify(suite.transactions).rollback(any());
            verify(suite.transactions, times(2)).commit(any());
        } finally { SecurityContextHolder.clearContext(); }
    }
}
