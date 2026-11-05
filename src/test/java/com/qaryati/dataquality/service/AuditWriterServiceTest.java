package com.qaryati.dataquality.service;

import com.qaryati.dataquality.api.*;
import com.qaryati.dataquality.entity.AuditLog;
import com.qaryati.dataquality.repository.AuditLogRepository;
import com.qaryati.dataquality.repository.SourceRepository;
import com.qaryati.security.api.UserLookup;
import com.qaryati.security.api.UserSummary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.*;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real Spring transaction interceptors; no database or Boot auto-configuration. */
class AuditWriterServiceTest {
    @AfterEach
    void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {
        @Bean AuditLogRepository repository() { return mock(AuditLogRepository.class); }
        @Bean SourceRepository sources() { return mock(SourceRepository.class); }
        @Bean UserLookup users() { return mock(UserLookup.class); }
        @Bean SourceLookupService lookup(SourceRepository sources) { return new SourceLookupService(sources); }
        @Bean AuditLogService audit(AuditLogRepository repository, UserLookup users) {
            return new AuditLogService(repository, users);
        }
        @Bean AuditWriterService writer(AuditLogService audit) { return new AuditWriterService(audit); }
        @Bean TestTransactions transactionManager() { return new TestTransactions(); }
    }

    static class TestTransactions extends AbstractPlatformTransactionManager {
        static class State implements SmartTransactionObject {
            boolean rollbackOnly;
            @Override public boolean isRollbackOnly() { return rollbackOnly; }
            @Override public void flush() { }
        }
        private final ThreadLocal<State> active = new ThreadLocal<>();
        int begins;
        int commits;
        int rollbacks;
        @Override protected Object doGetTransaction() {
            return active.get() == null ? new State() : active.get();
        }
        @Override protected boolean isExistingTransaction(Object transaction) { return active.get() != null; }
        @Override protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) {
            active.set((State) transaction);
            begins++;
        }
        @Override protected void doCommit(DefaultTransactionStatus status) { commits++; }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
        @Override protected void doSetRollbackOnly(DefaultTransactionStatus status) {
            ((State) status.getTransaction()).rollbackOnly = true;
        }
        @Override protected void doCleanupAfterCompletion(Object transaction) { active.remove(); }
    }

    @Test
    void createMapsNullBeforeAndPopulatedAfter() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            assertNotNull(context.getBean(SourceLookup.class));
            AuditWriter writer = context.getBean(AuditWriter.class);
            authenticate(context);
            var repository = context.getBean(AuditLogRepository.class);
            writer.record("POPULATION_RECORD", 7L, AuditAction.CREATE, null,
                    new AuditSnapshot(1L, 2L, 2020, 500));
            var logs = ArgumentCaptor.forClass(AuditLog.class);
            verify(repository).save(logs.capture());
            assertEquals(List.of("CREATE"),
                    logs.getAllValues().stream().map(AuditLog::getAction).toList());
            for (var log : logs.getAllValues()) {
                assertEquals(42L, log.getActorUserId());
                assertEquals("reviewer", log.getActorLabel());
                assertNull(log.getBeforeData());
                assertEquals(Map.of("villageId", 1L, "sourceId", 2L, "referenceYear", 2020,
                        "populationCount", 500), log.getAfterData());
            }
            assertEquals(1, context.getBean(TestTransactions.class).commits);
        }
    }

    @Test
    void updateMapsDistinctBeforeAndAfter() {
        assertSnapshotMapping(AuditAction.UPDATE,
                new AuditSnapshot(1L, 2L, 2020, 500),
                new AuditSnapshot(3L, 4L, 2021, 600),
                Map.of("villageId", 1L, "sourceId", 2L, "referenceYear", 2020, "populationCount", 500),
                Map.of("villageId", 3L, "sourceId", 4L, "referenceYear", 2021, "populationCount", 600));
    }

    @Test
    void deleteMapsPopulatedBeforeAndNullAfter() {
        assertSnapshotMapping(AuditAction.DELETE, new AuditSnapshot(1L, 2L, 2020, 500), null,
                Map.of("villageId", 1L, "sourceId", 2L, "referenceYear", 2020, "populationCount", 500), null);
    }

    @Test
    void omitsEachNullFieldAndPreservesZeroCounts() {
        assertSnapshotMapping(AuditAction.UPDATE, new AuditSnapshot(null, 2L, null, 0),
                new AuditSnapshot(1L, null, 2020, null),
                Map.of("sourceId", 2L, "populationCount", 0),
                Map.of("villageId", 1L, "referenceYear", 2020));
        assertSnapshotMapping(AuditAction.CREATE, null, new AuditSnapshot(null, null, null, null),
                null, Map.of());
    }

    @Test
    void snapshotContractAllowsOnlyTheReviewedImmutableNumericFields() throws Exception {
        assertTrue(AuditSnapshot.class.isRecord());
        assertEquals(List.of("villageId", "sourceId", "referenceYear", "populationCount"),
                Arrays.stream(AuditSnapshot.class.getRecordComponents()).map(c -> c.getName()).toList());
        assertEquals(List.of(Long.class, Long.class, Integer.class, Integer.class),
                Arrays.stream(AuditSnapshot.class.getRecordComponents()).map(c -> c.getType()).toList());
        var method = AuditWriter.class.getMethod("record", String.class, Long.class,
                AuditAction.class, AuditSnapshot.class, AuditSnapshot.class);
        assertEquals(void.class, method.getReturnType());
        assertEquals(List.of("CREATE", "UPDATE", "DELETE"),
                Arrays.stream(AuditAction.values()).map(Enum::name).toList());
    }

    private void assertSnapshotMapping(AuditAction action, AuditSnapshot before, AuditSnapshot after,
                                       Map<String, Object> expectedBefore, Map<String, Object> expectedAfter) {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            authenticate(context);
            context.getBean(AuditWriter.class).record("POPULATION_RECORD", 7L, action, before, after);
            var log = ArgumentCaptor.forClass(AuditLog.class);
            verify(context.getBean(AuditLogRepository.class)).save(log.capture());
            assertEquals(action.name(), log.getValue().getAction());
            assertEquals(expectedBefore, log.getValue().getBeforeData());
            assertEquals(expectedAfter, log.getValue().getAfterData());
            assertEquals("Domain record " + action.name(), log.getValue().getReason());
            assertEquals("POPULATION_RECORD", log.getValue().getEntityType());
            assertEquals(7L, log.getValue().getEntityId());
        }
    }

    @Test
    void successfulAuditSharesCallerTransactionWithoutAnIndependentCommit() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            authenticate(context);
            var transactions = context.getBean(TestTransactions.class);
            new TransactionTemplate(transactions).execute(status -> {
                var callerTransaction = transactions.active.get();
                when(context.getBean(AuditLogRepository.class).save(any())).thenAnswer(invocation -> {
                    assertSame(callerTransaction, transactions.active.get());
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                    assertFalse(TransactionAspectSupport.currentTransactionStatus().isNewTransaction());
                    return invocation.getArgument(0);
                });
                context.getBean(AuditWriter.class).record("POPULATION_RECORD", 7L, AuditAction.CREATE, null, null);
                assertEquals(1, transactions.begins);
                assertEquals(0, transactions.commits);
                assertFalse(status.isRollbackOnly());
                return null;
            });
            assertEquals(1, transactions.commits);
            assertEquals(0, transactions.rollbacks);
        }
    }

    @Test
    void caughtWriterValidationFailureMarksCallerRollbackOnlyBeforeDelegation() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var transactions = context.getBean(TestTransactions.class);
            assertThrows(UnexpectedRollbackException.class, () -> new TransactionTemplate(transactions).execute(status -> {
                assertThrows(IllegalArgumentException.class, () -> context.getBean(AuditWriter.class)
                        .record("POPULATION_RECORD", 7L, null, null, null));
                assertTrue(status.isRollbackOnly());
                assertEquals(1, transactions.begins);
                return null;
            }));
            assertEquals(1, transactions.rollbacks);
            assertEquals(0, transactions.commits);
            verifyNoInteractions(context.getBean(UserLookup.class), context.getBean(AuditLogRepository.class));
        }
    }

    @Test
    void caughtAuditPersistenceFailureMarksCallerRollbackOnlyAndReportsRolledBackCompletion() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            authenticate(context);
            var transactions = context.getBean(TestTransactions.class);
            var repository = context.getBean(AuditLogRepository.class);
            when(repository.save(any())).thenThrow(new IllegalStateException("Audit persistence failed"));
            int[] completionStatus = {-1};
            assertThrows(UnexpectedRollbackException.class, () -> new TransactionTemplate(transactions).execute(status -> {
                var callerTransaction = transactions.active.get();
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int completion) {
                        completionStatus[0] = completion;
                    }
                });
                assertThrows(IllegalStateException.class, () -> context.getBean(AuditWriter.class)
                        .record("POPULATION_RECORD", 7L, AuditAction.CREATE, null, null));
                assertSame(callerTransaction, transactions.active.get());
                assertTrue(status.isRollbackOnly());
                assertEquals(1, transactions.begins);
                return null;
            }));
            assertEquals(TransactionSynchronization.STATUS_ROLLED_BACK, completionStatus[0]);
            assertEquals(1, transactions.rollbacks);
            assertEquals(0, transactions.commits);
        }
    }

    @Test
    void rejectsUnauthenticatedCallsAndInvalidPublicInput() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var writer = context.getBean(AuditWriter.class);
            assertThrows(IllegalStateException.class,
                    () -> writer.record("POPULATION_RECORD", 7L, AuditAction.DELETE, null, null));
            assertThrows(IllegalArgumentException.class,
                    () -> writer.record("free text", 7L, AuditAction.CREATE, null, null));
            assertThrows(IllegalArgumentException.class,
                    () -> writer.record("POPULATION_RECORD", 7L, null, null, null));
            verifyNoInteractions(context.getBean(AuditLogRepository.class));
        }
    }

    private void authenticate(AnnotationConfigApplicationContext context) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("reviewer", null, List.of()));
        when(context.getBean(UserLookup.class).findByUsername("reviewer"))
                .thenReturn(Optional.of(new UserSummary(42L, true)));
    }
}
