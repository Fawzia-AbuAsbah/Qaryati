package com.qaryati.heritage;

import com.qaryati.dataquality.api.VillageHistorySourceLinks;
import com.qaryati.heritage.dto.VillageHistoricalOverviewRequest;
import com.qaryati.heritage.service.VillageHistoricalOverviewService;
import java.util.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.context.*;

@EnabledIfEnvironmentVariable(named = "QARYATI_REAL_AUDIT_TESTS", matches = "verified-test-db")
@SpringBootTest(classes = RealAuditPostgresConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@ContextConfiguration(initializers = VillageHistoryRealAuditPostgresIntegrationTest.Guard.class)
class VillageHistoryRealAuditPostgresIntegrationTest extends RealAuditPostgresContract {
    @Autowired VillageHistoricalOverviewService service;
    @Autowired VillageHistorySourceLinks links;
    String table() { return "village_histories"; }
    String linkTable() { return "history_sources"; }
    String parentColumn() { return "village_history_id"; }
    String entityType() { return "VILLAGE_HISTORY"; }
    String textColumn() { return "overview"; }
    long create(String text, List<Long> sources) {
        return service.create(new VillageHistoricalOverviewRequest(village, text, sources)).id();
    }
    void update(long id, String text, List<Long> sources) {
        service.update(id, new VillageHistoricalOverviewRequest(village, text, sources));
    }
    void delete(long id) { service.delete(id); }
    Map<String, Object> snapshot(String text, List<Long> sources) {
        return Map.of("villageId", village, "overview", text, "sourceIds", sources, "verificationStatus", "DRAFT");
    }
    Map<Long, List<Long>> batch(List<Long> ids) { return links.sourceIdsByHistoryIds(ids); }
    void replaceSources(long id, List<Long> sources) { links.replaceSources(id, sources); }

    /** Checks configuration and identity before refresh; committed fixtures still require a disposable database. */
    static class Guard implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        static final String APPROVAL_VARIABLE = RealAuditPostgresGuard.OPT_IN;
        private final boolean approved;

        public Guard() { this(System.getenv(APPROVAL_VARIABLE)); }

        // Offline test seam; runtime approval remains the original environment variable.
        Guard(String approval) { approved = "verified-test-db".equals(approval); }

        @Override public void initialize(ConfigurableApplicationContext context) {
            var env = context.getEnvironment();
            for (var source : env.getPropertySources()) {
                if (!(source instanceof EnumerablePropertySource<?> enumerable)) continue;
                for (String name : enumerable.getPropertyNames()) {
                    String key = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
                    if (key.startsWith("springflyway") && !key.equals("springflywayenabled")
                            && !key.equals("springflywaycleandisabled")) {
                        throw new IllegalStateException("Unsupported Flyway override for village history real audit tests");
                    }
                }
            }
            DedicatedPostgresSafety.initialize(context, approved, true);
            // Preserve bounded waits and the second connection used by lock tests, without copying legacy NONE actions.
            var legacy = RealAuditPostgresGuard.safetyProperties();
            var limits = new HashMap<String, Object>();
            for (String key : List.of("spring.datasource.hikari.maximum-pool-size",
                    "spring.datasource.hikari.minimum-idle", "spring.datasource.hikari.connection-timeout",
                    "spring.datasource.hikari.validation-timeout", "spring.datasource.hikari.auto-commit",
                    "spring.jdbc.template.query-timeout", "spring.transaction.default-timeout")) {
                limits.put(key, legacy.get(key));
            }
            env.getPropertySources().addFirst(new MapPropertySource(
                    "village-history-real-audit-limits", Map.copyOf(limits)));
        }
    }
}
