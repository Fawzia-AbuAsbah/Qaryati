package com.qaryati.heritage;

import com.qaryati.dataquality.api.CulturalHeritageSourceLinks;
import com.qaryati.heritage.dto.CulturalHeritageCrudRequest;
import com.qaryati.heritage.entity.CulturalHeritageType;
import com.qaryati.heritage.service.CulturalHeritageCrudService;
import java.util.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;

@EnabledIfEnvironmentVariable(named = "QARYATI_REAL_AUDIT_TESTS", matches = "verified-test-db")
@SpringBootTest(classes = RealAuditPostgresConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@ContextConfiguration(initializers = RealAuditPostgresGuard.class)
class CulturalHeritageRealAuditPostgresIntegrationTest extends RealAuditPostgresContract {
    @Autowired CulturalHeritageCrudService service;
    @Autowired CulturalHeritageSourceLinks links;
    String table() { return "cultural_heritage"; }
    String linkTable() { return "cultural_heritage_sources"; }
    String parentColumn() { return "cultural_heritage_id"; }
    String entityType() { return "CULTURAL_HERITAGE"; }
    String textColumn() { return "name"; }
    long create(String text, List<Long> sources) {
        return service.create(new CulturalHeritageCrudRequest(village, text, CulturalHeritageType.CRAFT, text, sources)).id();
    }
    void update(long id, String text, List<Long> sources) {
        service.update(id, new CulturalHeritageCrudRequest(village, text, CulturalHeritageType.CRAFT, text, sources));
    }
    void delete(long id) { service.delete(id); }
    Map<String, Object> snapshot(String text, List<Long> sources) {
        return Map.of("villageId", village, "name", text, "heritageType", "CRAFT", "description", text,
                "sourceIds", sources, "verificationStatus", "DRAFT");
    }
    Map<Long, List<Long>> batch(List<Long> ids) { return links.sourceIdsByCulturalHeritageIds(ids); }
    void replaceSources(long id, List<Long> sources) { links.replaceSources(id, sources); }
}
