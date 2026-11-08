package com.qaryati.heritage;

import com.qaryati.dataquality.api.VillageHistorySourceLinks;
import com.qaryati.heritage.dto.VillageHistoricalOverviewRequest;
import com.qaryati.heritage.service.VillageHistoricalOverviewService;
import java.util.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;

@EnabledIfEnvironmentVariable(named = "QARYATI_REAL_AUDIT_TESTS", matches = "verified-test-db")
@SpringBootTest(classes = RealAuditPostgresConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@ContextConfiguration(initializers = RealAuditPostgresGuard.class)
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
}
