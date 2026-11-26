package com.qaryati.dataquality.repository;

import com.qaryati.dataquality.api.AuditAction;
import com.qaryati.dataquality.api.AuditWriter;
import com.qaryati.dataquality.api.HeritageSiteSourceLinks;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opentest4j.AssertionFailedError;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises the actual failure callback with mocks and in-memory transactions; no context or database. */
class HeritageSiteSourceLinksRollbackTest {
    private HeritageSiteSourceLinksPostgresIntegrationTest scenario(RecordingTransactions transactions) {
        var scenario = new HeritageSiteSourceLinksPostgresIntegrationTest();
        scenario.transactions = transactions;
        scenario.jdbc = mock(JdbcTemplate.class);
        scenario.links = mock(HeritageSiteSourceLinks.class);
        scenario.audit = mock(AuditWriter.class);
        when(scenario.jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(1L);
        when(scenario.jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(scenario.jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);
        return scenario;
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unexpectedSuccessInEitherIterationRollsBackAndStillFailsTheOriginalAssertion(boolean firstFailureOccurs) {
        var transactions = new RecordingTransactions();
        var scenario = scenario(transactions);
        if (firstFailureOccurs) {
            doThrow(new IllegalStateException("Expected linking failure"))
                    .when(scenario.links).replaceSources(anyLong(), any());
        }
        try {
            var failure = assertThrows(AssertionFailedError.class,
                    scenario::linkingAndRealAuditFailuresRollBackTheEnclosingMutation);
            assertNull(failure.getCause());
            assertEquals(0, transactions.commits);
            assertEquals(firstFailureOccurs ? List.of(true, true) : List.of(true), transactions.rollbackOnlyRequests);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void expectedLinkingAndAuditFailuresPreserveBothIterationsAndPostconditions() {
        var transactions = new RecordingTransactions();
        var scenario = scenario(transactions);
        doThrow(new IllegalStateException("Expected linking failure"))
                .when(scenario.links).replaceSources(anyLong(), any());
        doThrow(new IllegalStateException("Expected audit failure"))
                .when(scenario.audit).recordHeritageSite(anyLong(), any(), isNull(), any());
        try {
            assertDoesNotThrow(scenario::linkingAndRealAuditFailuresRollBackTheEnclosingMutation);
            assertEquals(0, transactions.commits);
            assertEquals(List.of(true, true), transactions.rollbackOnlyRequests);
            verify(scenario.links, times(2)).linkNewSite(1L, List.of(1L));
            verify(scenario.links).replaceSources(1L, List.of(Long.MAX_VALUE));
            verify(scenario.audit).recordHeritageSite(eq(1L), eq(AuditAction.CREATE), isNull(), any());
            verify(scenario.jdbc, times(4)).queryForObject(anyString(), eq(Integer.class), any(Object[].class));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void assertionFailureDuringFixtureOrLinkWorkStillRequestsRollbackOnly(boolean duringFixtureSetup) {
        var transactions = new RecordingTransactions();
        var scenario = scenario(transactions);
        var assertionFailure = new AssertionError("Controlled assertion failure");
        if (duringFixtureSetup) {
            doThrow(assertionFailure).when(scenario.jdbc)
                    .queryForObject(anyString(), eq(Long.class), any(Object[].class));
        } else {
            doThrow(assertionFailure).when(scenario.links).linkNewSite(anyLong(), any());
        }
        try {
            var failure = assertThrows(AssertionFailedError.class,
                    scenario::linkingAndRealAuditFailuresRollBackTheEnclosingMutation);
            assertSame(assertionFailure, failure.getCause());
            assertEquals(0, transactions.commits);
            assertEquals(List.of(true), transactions.rollbackOnlyRequests);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static class RecordingTransactions extends AbstractPlatformTransactionManager {
        private int commits;
        private final List<Boolean> rollbackOnlyRequests = new ArrayList<>();

        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { commits++; }
        @Override protected void doRollback(DefaultTransactionStatus status) {
            rollbackOnlyRequests.add(status.isLocalRollbackOnly());
        }
    }
}
