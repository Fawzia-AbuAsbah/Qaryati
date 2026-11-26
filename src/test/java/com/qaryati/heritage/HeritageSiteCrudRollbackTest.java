package com.qaryati.heritage;

import com.qaryati.dataquality.api.AuditWriter;
import com.qaryati.dataquality.api.HeritageSiteSourceLinks;
import com.qaryati.heritage.dto.HeritageSitePage;
import com.qaryati.heritage.dto.HeritageSiteRequest;
import com.qaryati.heritage.dto.HeritageSiteResponse;
import com.qaryati.heritage.repository.HeritageSiteRepository;
import com.qaryati.heritage.service.HeritageSiteService;
import com.qaryati.village.api.VillageLookupApi;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.opentest4j.AssertionFailedError;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionExecutionListener;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises actual CRUD failure scenarios with mocks and in-memory transactions; no Spring context or database. */
class HeritageSiteCrudRollbackTest {
    private HeritageSiteCrudPostgresIntegrationTest scenario(RecordingTransactions transactions) {
        var scenario = new HeritageSiteCrudPostgresIntegrationTest();
        scenario.transactions = transactions;
        scenario.jdbc = mock(JdbcTemplate.class);
        scenario.service = mock(HeritageSiteService.class);
        scenario.repository = mock(HeritageSiteRepository.class);
        scenario.links = mock(HeritageSiteSourceLinks.class);
        scenario.villages = mock(VillageLookupApi.class);
        scenario.audits = mock(AuditWriter.class);
        when(scenario.jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(1L);
        when(scenario.jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        var response = mock(HeritageSiteResponse.class);
        when(response.id()).thenReturn(1L);
        when(scenario.service.create(any())).thenReturn(response);
        when(scenario.links.sourceIds(anyLong())).thenThrow(new NoSuchElementException("No persisted fixture"));
        return scenario;
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unexpectedSuccessInEitherCallbackRollsBackAndStillFailsTheOriginalAssertion(boolean firstCallbackFails) {
        var transactions = new RecordingTransactions();
        var scenario = scenario(transactions);
        if (firstCallbackFails) {
            when(scenario.service.update(anyLong(), any())).thenThrow(new IllegalStateException("Expected failure"));
        }
        try {
            var failure = assertThrows(AssertionFailedError.class, scenario::sourceAndAuditFailuresRollBackSiteAndAssociations);
            assertNull(failure.getCause());
            assertEquals(0, transactions.commits);
            assertEquals(firstCallbackFails ? List.of(true, true) : List.of(true), transactions.rollbackOnlyRequests);
        } finally {
            scenario.clearAuthentication();
        }
    }

    @Test
    void assertionFailureInsideCallbackStillRequestsRollbackOnly() {
        var transactions = new RecordingTransactions();
        var scenario = scenario(transactions);
        var assertionFailure = new AssertionError("Controlled assertion failure");
        when(scenario.service.create(any())).thenThrow(assertionFailure);
        try {
            var failure = assertThrows(AssertionFailedError.class, scenario::sourceAndAuditFailuresRollBackSiteAndAssociations);
            assertSame(assertionFailure, failure.getCause());
            assertEquals(0, transactions.commits);
            assertEquals(List.of(true), transactions.rollbackOnlyRequests);
        } finally {
            scenario.clearAuthentication();
        }
    }

    private enum FailureCall { AUDIT_UPDATE, AUDIT_DELETE, INVALID_SOURCE_CREATE, INVALID_SOURCE_UPDATE }

    private HeritageSiteCrudPostgresIntegrationTest committedFailureScenario(
            RecordingTransactions transactions, FailureCall unexpectedSuccess) {
        var scenario = scenario(transactions);
        var response = mock(HeritageSiteResponse.class);
        when(response.id()).thenReturn(1L);
        when(response.name()).thenReturn("Before");
        when(scenario.service.getById(anyLong())).thenReturn(response);
        when(scenario.service.getAll(0, 20, 1L))
                .thenReturn(new HeritageSitePage(List.of(response), 0, 20, 1, 1));
        when(scenario.repository.existsById(anyLong())).thenReturn(true);
        doReturn(List.of(1L)).when(scenario.links).sourceIds(anyLong());
        doAnswer(call -> {
            HeritageSiteRequest request = call.getArgument(0);
            // Baseline setup already owns a transaction; the real service would join it.
            if (!request.sourceIds().contains(Long.MAX_VALUE)) return response;
            return failureTransaction(transactions, unexpectedSuccess, FailureCall.INVALID_SOURCE_CREATE, response);
        }).when(scenario.service).create(any());
        doAnswer(call -> {
            HeritageSiteRequest request = call.getArgument(1);
            var operation = request.sourceIds().contains(Long.MAX_VALUE)
                    ? FailureCall.INVALID_SOURCE_UPDATE : FailureCall.AUDIT_UPDATE;
            return failureTransaction(transactions, unexpectedSuccess, operation, response);
        }).when(scenario.service).update(anyLong(), any());
        doAnswer(call -> {
            failureTransaction(transactions, unexpectedSuccess, FailureCall.AUDIT_DELETE, response);
            return null;
        }).when(scenario.service).delete(anyLong());
        return scenario;
    }

    private HeritageSiteResponse failureTransaction(RecordingTransactions transactions,
            FailureCall unexpectedSuccess, FailureCall operation, HeritageSiteResponse response) {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),
                "The service must start its own transaction");
        return new TransactionTemplate(transactions).execute(status -> {
            if (operation != unexpectedSuccess) {
                if (operation == FailureCall.AUDIT_UPDATE || operation == FailureCall.AUDIT_DELETE)
                    throw new IllegalStateException("Controlled audit failure");
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Controlled missing source");
            }
            return response;
        });
    }

    @ParameterizedTest
    @EnumSource(FailureCall.class)
    void unexpectedSuccessInEachStandaloneCallCannotCommit(FailureCall operation) {
        var transactions = new RecordingTransactions();
        var observer = mock(TransactionExecutionListener.class);
        var original = new ArrayList<>(List.of(observer));
        transactions.setTransactionExecutionListeners(original);
        var scenario = committedFailureScenario(transactions, operation);
        try {
            var failure = assertThrows(AssertionFailedError.class,
                    scenario::failedUpdateAndDeleteRestoreCommittedMetadataAndSources);
            assertInstanceOf(AssertionError.class, failure.getCause());
            assertEquals("Expected-failure service call attempted to commit", failure.getCause().getMessage());
            // Only baseline creation and cleanup may reach the physical commit implementation.
            assertEquals(2, transactions.commits);
            assertEquals(java.util.Collections.nCopies(operation.ordinal() + 1, false), transactions.rollbackOnlyRequests);
            assertSame(original, transactions.getTransactionExecutionListeners());
            verify(observer, times(3)).beforeCommit(any());
        } finally {
            scenario.clearAuthentication();
        }
    }

    @Test
    void expectedStandaloneFailuresPreservePostconditionsAndIntentionalCommits() {
        var transactions = new RecordingTransactions();
        var observer = mock(TransactionExecutionListener.class);
        var original = new ArrayList<>(List.of(observer));
        transactions.setTransactionExecutionListeners(original);
        var scenario = committedFailureScenario(transactions, null);
        try {
            assertDoesNotThrow(scenario::failedUpdateAndDeleteRestoreCommittedMetadataAndSources);
            assertEquals(2, transactions.commits);
            assertEquals(List.of(false, false, false, false), transactions.rollbackOnlyRequests);
            assertSame(original, transactions.getTransactionExecutionListeners());
            verify(observer, times(2)).beforeCommit(any());
        } finally {
            scenario.clearAuthentication();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void wrongExceptionOrAssertionErrorRestoresListenersAndAllowsCleanup(boolean assertionFailure) {
        var transactions = new RecordingTransactions();
        var original = transactions.getTransactionExecutionListeners();
        var scenario = committedFailureScenario(transactions, null);
        var wrongException = new IllegalArgumentException("Wrong failure type");
        var assertionError = new AssertionError("Controlled assertion failure");
        doAnswer(call -> new TransactionTemplate(transactions).execute(status -> {
            if (assertionFailure) throw assertionError;
            throw wrongException;
        })).when(scenario.service).update(anyLong(), any());
        try {
            var failure = assertThrows(AssertionFailedError.class,
                    scenario::failedUpdateAndDeleteRestoreCommittedMetadataAndSources);
            assertSame(assertionFailure ? assertionError : wrongException, failure.getCause());
            assertEquals(2, transactions.commits);
            assertEquals(List.of(false), transactions.rollbackOnlyRequests);
            assertSame(original, transactions.getTransactionExecutionListeners());
        } finally {
            scenario.clearAuthentication();
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
