package com.qaryati.dataquality.service;

import com.qaryati.dataquality.api.*;
import com.qaryati.dataquality.entity.AuditLog;
import com.qaryati.dataquality.repository.AuditLogRepository;
import com.qaryati.security.api.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VillageHistoryAuditTest {
    final VillageHistoryAuditSnapshot before = new VillageHistoryAuditSnapshot(1L, "  Overview\n\u0646\u0635  ", List.of(3L, 2L, 3L), "DRAFT");
    final VillageHistoryAuditSnapshot after = new VillageHistoryAuditSnapshot(1L, "Revised", List.of(4L), "DRAFT");
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void serializesEveryFieldAndPreservesBeforeAfterForAllActions() {
        try (var context = context()) {
            var writer = context.getBean(AuditWriter.class);
            writer.recordVillageHistory(7L, AuditAction.CREATE, null, before);
            writer.recordVillageHistory(7L, AuditAction.UPDATE, before, after);
            writer.recordVillageHistory(7L, AuditAction.DELETE, after, null);
            var captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(context.getBean(AuditLogRepository.class), times(3)).save(captor.capture());
            var logs = captor.getAllValues();
            var exact = Map.of("villageId", 1L, "overview", before.overview(), "sourceIds", List.of(2L, 3L), "verificationStatus", "DRAFT");
            var period = Map.of("villageId", 1L, "overview", "Revised", "sourceIds", List.of(4L), "verificationStatus", "DRAFT");
            assertNull(logs.get(0).getBeforeData());
            assertEquals(exact, logs.get(0).getAfterData());
            assertEquals(exact, logs.get(1).getBeforeData());
            assertEquals(period, logs.get(1).getAfterData());
            assertEquals(period, logs.get(2).getBeforeData());
            assertNull(logs.get(2).getAfterData());
            for (var log : logs) {
                assertEquals("VILLAGE_HISTORY", log.getEntityType());
                assertEquals(42L, log.getActorUserId());
            }
            // Map values contain only JSON-safe primitives and arrays; no temporal/entity objects.
            assertDoesNotThrow(() -> tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(exact));
        }
    }
    @Test void rejectsInvalidStateAndMetadata() {
        var writer = new AuditWriterService(mock(AuditLogService.class));
        assertThrows(IllegalArgumentException.class, () -> writer.recordVillageHistory(1L, AuditAction.CREATE, before, after));
        assertThrows(IllegalArgumentException.class, () -> writer.recordVillageHistory(1L, AuditAction.UPDATE, null, after));
        assertThrows(IllegalArgumentException.class, () -> writer.recordVillageHistory(1L, AuditAction.DELETE, before, after));
        assertThrows(IllegalArgumentException.class, () -> writer.recordVillageHistory(1L, null, before, after));
        assertThrows(IllegalArgumentException.class, () -> new VillageHistoryAuditSnapshot(1L, "x", List.of(), "INVALID"));
        assertThrows(IllegalArgumentException.class, () -> new VillageHistoryAuditSnapshot(1L, "x", List.of(-1L), "DRAFT"));
        assertThrows(UnsupportedOperationException.class, () -> before.sourceIds().add(5L));
    }
    @Test void joinsCallerAndAuditFailureRollsBack() {
        try (var context = context()) {
            var tx = context.getBean(AuditWriterServiceTest.TestTransactions.class);
            when(context.getBean(AuditLogRepository.class).save(any())).thenAnswer(call -> {
                assertEquals(1, tx.begins);
                assertEquals(0, tx.commits);
                throw new IllegalStateException("audit failed");
            });
            assertThrows(IllegalStateException.class, () -> new TransactionTemplate(tx).executeWithoutResult(status ->
                    context.getBean(AuditWriter.class).recordVillageHistory(1L, AuditAction.CREATE, null, before)));
            assertEquals(1, tx.rollbacks);
            assertEquals(0, tx.commits);
        }
    }
    private AnnotationConfigApplicationContext context() {
        var context = new AnnotationConfigApplicationContext(AuditWriterServiceTest.Config.class);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("reviewer", null, List.of()));
        when(context.getBean(UserLookup.class).findByUsername("reviewer")).thenReturn(Optional.of(new UserSummary(42L, true)));
        return context;
    }
}
