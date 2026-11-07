package com.qaryati.dataquality;

import com.qaryati.QaryatiApplication;
import com.qaryati.dataquality.api.*;
import com.qaryati.dataquality.service.AuditWriterService;
import com.qaryati.dataquality.service.SourceLookupService;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class DataQualityIntegrationArchitectureTest {
    @Test
    void verifiesBoundariesAndExposesOnlyIntegrationContracts() {
        var modules = ApplicationModules.of(QaryatiApplication.class);
        modules.verify();
        var module = modules.getModuleByName("dataquality").orElseThrow();
        assertFalse(module.isOpen());
        assertEquals(Set.of(SourceLookup.class.getName(), AuditWriter.class.getName(),
                        AuditSnapshot.class.getName(), AuditAction.class.getName(),
                        EventSourceLinks.class.getName(), HistoricalEventAuditSnapshot.class.getName(),
                        VillageHistorySourceLinks.class.getName(), VillageHistoryAuditSnapshot.class.getName()),
                module.getNamedInterfaces().getByName("integration").orElseThrow()
                        .asJavaClasses().map(type -> type.getName()).collect(Collectors.toSet()));
        assertTrue(module.isExposed(SourceLookup.class));
        assertTrue(module.isExposed(AuditWriter.class));
        assertTrue(module.isExposed(EventSourceLinks.class));
        assertTrue(module.isExposed(VillageHistorySourceLinks.class));
        assertTrue(module.isExposed(VillageHistoryAuditSnapshot.class));
        assertFalse(module.isExposed(com.qaryati.dataquality.service.VillageHistorySourceLinksService.class));
        assertFalse(module.isExposed(com.qaryati.dataquality.repository.VillageHistorySourceLinkRepository.class));
        assertFalse(module.isExposed(com.qaryati.dataquality.service.EventSourceLinksService.class));
        assertFalse(module.isExposed(com.qaryati.dataquality.repository.EventSourceLinkRepository.class));
        assertFalse(module.isExposed(SourceLookupService.class));
        assertFalse(module.isExposed(AuditWriterService.class));
    }
}
