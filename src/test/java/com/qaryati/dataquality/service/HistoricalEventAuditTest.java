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

class HistoricalEventAuditTest {
    final HistoricalEventAuditSnapshot before = new HistoricalEventAuditSnapshot(1L, "Event", LocalDate.of(1948, 5, 1),
            null, null, "EXACT", List.of(3L, 2L, 3L), "DRAFT");
    final HistoricalEventAuditSnapshot after = new HistoricalEventAuditSnapshot(1L, "Revised", null,
            1948, 1950, "PERIOD", List.of(4L), null);
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void serializesEveryFieldAndPreservesBeforeAfterForAllActions() {
        try (var context = context()) {
            var writer = context.getBean(AuditWriter.class);
            writer.recordHistoricalEvent(7L, AuditAction.CREATE, null, before);
            writer.recordHistoricalEvent(7L, AuditAction.UPDATE, before, after);
            writer.recordHistoricalEvent(7L, AuditAction.DELETE, after, null);
            var captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(context.getBean(AuditLogRepository.class), times(3)).save(captor.capture());
            var logs = captor.getAllValues();
            var exact = Map.of("villageId", 1L, "title", "Event", "eventDate", "1948-05-01",
                    "datePrecision", "EXACT", "sourceIds", List.of(2L, 3L), "verificationStatus", "DRAFT");
            var period = Map.of("villageId", 1L, "title", "Revised", "yearFrom", 1948, "yearTo", 1950,
                    "datePrecision", "PERIOD", "sourceIds", List.of(4L));
            assertNull(logs.get(0).getBeforeData());
            assertEquals(exact, logs.get(0).getAfterData());
            assertEquals(exact, logs.get(1).getBeforeData());
            assertEquals(period, logs.get(1).getAfterData());
            assertEquals(period, logs.get(2).getBeforeData());
            assertNull(logs.get(2).getAfterData());
            for (var log : logs) {
                assertEquals("HISTORICAL_EVENT", log.getEntityType());
                assertEquals(42L, log.getActorUserId());
            }
            // Map values contain only JSON-safe primitives and arrays; no temporal/entity objects.
            assertDoesNotThrow(() -> tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(exact));
        }
    }
    @Test void rejectsInvalidStateAndMetadata() {
        var writer = new AuditWriterService(mock(AuditLogService.class));
        assertThrows(IllegalArgumentException.class, () -> writer.recordHistoricalEvent(1L, AuditAction.CREATE, before, after));
        assertThrows(IllegalArgumentException.class, () -> writer.recordHistoricalEvent(1L, AuditAction.UPDATE, null, after));
        assertThrows(IllegalArgumentException.class, () -> writer.recordHistoricalEvent(1L, AuditAction.DELETE, before, after));
        assertThrows(IllegalArgumentException.class, () -> writer.recordHistoricalEvent(1L, null, before, after));
        assertThrows(IllegalArgumentException.class, () -> new HistoricalEventAuditSnapshot(1L, "x", null, null, null,
                "INVALID", List.of(), null));
        assertThrows(IllegalArgumentException.class, () -> new HistoricalEventAuditSnapshot(1L, "x", null, null, null,
                "UNKNOWN", List.of(-1L), null));
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
                    context.getBean(AuditWriter.class).recordHistoricalEvent(1L, AuditAction.CREATE, null, before)));
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
