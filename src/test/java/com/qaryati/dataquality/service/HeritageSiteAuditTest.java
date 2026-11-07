package com.qaryati.dataquality.service;

import com.qaryati.dataquality.api.*;
import com.qaryati.dataquality.entity.AuditLog;
import com.qaryati.dataquality.repository.AuditLogRepository;
import com.qaryati.security.api.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HeritageSiteAuditTest {
    final HeritageSiteAuditSnapshot before = snapshot("Site");
    final HeritageSiteAuditSnapshot after = snapshot("Revised");
    private HeritageSiteAuditSnapshot snapshot(String name) {
        return new HeritageSiteAuditSnapshot(1L, name, "SPRING", "Description",
                new BigDecimal("31.123456"), new BigDecimal("35.123456"), "Condition",
                List.of(3L, 2L, 3L), "DRAFT");
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private AnnotationConfigApplicationContext context() {
        var context = new AnnotationConfigApplicationContext(AuditWriterServiceTest.Config.class);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("reviewer", null, List.of()));
        when(context.getBean(UserLookup.class).findByUsername("reviewer"))
                .thenReturn(Optional.of(new UserSummary(42L, true)));
        return context;
    }
    @Test void recordsAllActionsAndEveryFieldThroughExistingInfrastructure() {
        try (var context = context()) {
            var writer = context.getBean(AuditWriter.class);
            writer.recordHeritageSite(7L, AuditAction.CREATE, null, before);
            writer.recordHeritageSite(7L, AuditAction.UPDATE, before, after);
            writer.recordHeritageSite(7L, AuditAction.DELETE, after, null);
            var captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(context.getBean(AuditLogRepository.class), times(3)).save(captor.capture());
            var logs = captor.getAllValues();
            var expected = Map.of("villageId", 1L, "name", "Site", "siteType", "SPRING",
                    "description", "Description", "latitude", before.latitude(), "longitude", before.longitude(),
                    "conditionNotes", "Condition", "sourceIds", List.of(2L, 3L), "verificationStatus", "DRAFT");
            assertNull(logs.get(0).getBeforeData());
            assertEquals(expected, logs.get(0).getAfterData());
            assertEquals(expected, logs.get(1).getBeforeData());
            assertEquals("Revised", logs.get(1).getAfterData().get("name"));
            assertEquals(logs.get(1).getAfterData(), logs.get(2).getBeforeData());
            assertNull(logs.get(2).getAfterData());
            for (int i = 0; i < 3; i++) {
                assertEquals(AuditAction.values()[i].name(), logs.get(i).getAction());
                assertEquals("HERITAGE_SITE", logs.get(i).getEntityType());
                assertEquals(42L, logs.get(i).getActorUserId());
                assertEquals(7L, logs.get(i).getEntityId());
            }
            assertDoesNotThrow(() -> tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(expected));
        }
    }
    @Test void validatesStatesAndCopiesSources() {
        var writer = new AuditWriterService(mock(AuditLogService.class));
        for (var action : AuditAction.values()) {
            assertThrows(IllegalArgumentException.class, () -> writer.recordHeritageSite(1L, action, null, null));
        }
        assertThrows(IllegalArgumentException.class, () -> writer.recordHeritageSite(1L, AuditAction.CREATE, before, after));
        assertThrows(IllegalArgumentException.class, () -> writer.recordHeritageSite(1L, AuditAction.DELETE, before, after));
        assertThrows(IllegalArgumentException.class, () -> writer.recordHeritageSite(1L, null, before, after));
        assertEquals(List.of(2L, 3L), before.sourceIds());
        assertThrows(UnsupportedOperationException.class, () -> before.sourceIds().add(4L));
        assertThrows(IllegalArgumentException.class, () -> new HeritageSiteAuditSnapshot(1L, "x", "INVALID", null, null, null, null, List.of(1L), "DRAFT"));
    }
    @Test void joinsCallerAndPropagatesAuditFailureForRollback() {
        try (var context = context()) {
            var tx = context.getBean(AuditWriterServiceTest.TestTransactions.class);
            when(context.getBean(AuditLogRepository.class).save(any())).thenAnswer(call -> {
                assertEquals(1, tx.begins);
                assertEquals(0, tx.commits);
                throw new IllegalStateException("audit failed");
            });
            assertThrows(IllegalStateException.class, () -> new TransactionTemplate(tx).executeWithoutResult(status ->
                    context.getBean(AuditWriter.class).recordHeritageSite(1L, AuditAction.CREATE, null, before)));
            assertEquals(1, tx.rollbacks);
            assertEquals(0, tx.commits);
        }
    }

    @Test void validatesSnapshotMetadataAndAllowsAbsentOptionalFields() {
        var mutable = new ArrayList<>(List.of(2L, 1L, 2L));
        var minimal = new HeritageSiteAuditSnapshot(1L, "Site", "OTHER", null, null, null, null, mutable, "DRAFT");
        mutable.clear();
        assertEquals(List.of(1L, 2L), minimal.sourceIds());
        assertDoesNotThrow(() -> new HeritageSiteAuditSnapshot(1L, "Site", "OTHER", null, null, null, null, List.of(), "DRAFT"));
        for (Long id : Arrays.asList(null, 0L, -1L)) {
            assertThrows(IllegalArgumentException.class, () -> new HeritageSiteAuditSnapshot(id, "Site", "OTHER", null, null, null, null, List.of(1L), "DRAFT"));
            assertThrows(IllegalArgumentException.class, () -> new HeritageSiteAuditSnapshot(1L, "Site", "OTHER", null, null, null, null, Arrays.asList(id), "DRAFT"));
        }
        assertThrows(IllegalArgumentException.class, () -> new HeritageSiteAuditSnapshot(1L, "Site", "OTHER", null, BigDecimal.ONE, null, null, List.of(1L), "DRAFT"));
        assertThrows(IllegalArgumentException.class, () -> new HeritageSiteAuditSnapshot(1L, "Site", "OTHER", null, new BigDecimal("91"), BigDecimal.ZERO, null, List.of(1L), "DRAFT"));
        assertThrows(IllegalArgumentException.class, () -> new HeritageSiteAuditSnapshot(1L, "Site", "OTHER", null, null, null, null, List.of(1L), "INVALID"));
        var service = mock(AuditLogService.class);
        new AuditWriterService(service).recordHeritageSite(1L, AuditAction.CREATE, null, minimal);
        verify(service).record("HERITAGE_SITE", 1L, "CREATE",
                null, Map.of("villageId", 1L, "name", "Site", "siteType", "OTHER", "sourceIds", List.of(1L, 2L), "verificationStatus", "DRAFT"), "Domain record CREATE");
    }
}
