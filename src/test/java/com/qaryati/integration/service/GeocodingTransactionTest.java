package com.qaryati.integration.service;

import com.qaryati.integration.adapter.GeocodingProvider;
import com.qaryati.integration.config.GeoapifyProperties;
import com.qaryati.integration.dto.GeocodingProviderAttribution;
import com.qaryati.integration.dto.GeocodingResponse;
import com.qaryati.integration.dto.GeocodingResult;
import com.qaryati.integration.entity.GeocodingCache;
import com.qaryati.integration.exception.GeocodingProviderException;
import com.qaryati.integration.repository.GeocodingCacheRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.NameMatchTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.SmartTransactionObject;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real transaction advice and in-memory completion only; no context, datasource or HTTP client. */
class GeocodingTransactionTest {
    private static final List<GeocodingResult> RESULTS = List.of(new GeocodingResult(
            new BigDecimal("31.9"), new BigDecimal("35.2"), "Fixture location",
            null, null, null, null, null, null));
    private static final GeocodingProviderAttribution ATTRIBUTION = new GeocodingProviderAttribution(
            "fixture-provider", "Fixture attribution", "https://example.test/attribution");

    private final List<String> events = new ArrayList<>();
    private final List<Boolean> providerTransactions = new ArrayList<>();
    private final List<Boolean> writeTransactions = new ArrayList<>();
    private RecordingTransactions transactions;
    private GeocodingCacheRepository repository;
    private GeocodingProvider provider;
    private GeocodingService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
        transactions = new RecordingTransactions(events);
        repository = mock(GeocodingCacheRepository.class);
        provider = mock(GeocodingProvider.class);
        objectMapper = new ObjectMapper();
        when(repository.findByProviderAndQueryHash(eq("fixture-provider"), anyString()))
                .thenReturn(Optional.empty());
        when(provider.providerName()).thenReturn("fixture-provider");
        when(provider.attribution()).thenReturn(ATTRIBUTION);
        doAnswer(call -> providerResult()).when(provider).forward("Ramallah");
        doAnswer(call -> providerResult()).when(provider)
                .reverse(new BigDecimal("31.9"), new BigDecimal("35.2"));
        doAnswer(call -> {
            recordWrite();
            return call.getArgument(0);
        }).when(repository).saveAndFlush(any(GeocodingCache.class));

        // Use Spring Data's actual write metadata rather than assuming its propagation.
        var annotations = new AnnotationTransactionAttributeSource();
        var write = annotations.getTransactionAttribute(
                SimpleJpaRepository.class.getMethod("saveAndFlush", Object.class), SimpleJpaRepository.class);
        assertNotNull(write);
        assertEquals(TransactionDefinition.PROPAGATION_REQUIRED, write.getPropagationBehavior());
        assertFalse(write.isReadOnly());
        var repositoryAttributes = new NameMatchTransactionAttributeSource();
        repositoryAttributes.addTransactionalMethod("saveAndFlush", write);
        var repositoryProxy = new ProxyFactory();
        repositoryProxy.setTarget(repository);
        repositoryProxy.setInterfaces(GeocodingCacheRepository.class);
        repositoryProxy.addAdvice(advice(repositoryAttributes));

        var target = new GeocodingService((GeocodingCacheRepository) repositoryProxy.getProxy(),
                provider, objectMapper, new GeoapifyProperties());
        var serviceProxy = new ProxyFactory(target);
        serviceProxy.setProxyTargetClass(true);
        serviceProxy.addAdvice(advice(annotations));
        service = (GeocodingService) serviceProxy.getProxy();
    }

    private TransactionInterceptor advice(TransactionAttributeSource attributes) {
        var interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactions);
        interceptor.setTransactionAttributeSource(attributes);
        return interceptor;
    }

    private List<GeocodingResult> providerResult() {
        events.add("provider");
        providerTransactions.add(TransactionSynchronizationManager.isActualTransactionActive());
        return RESULTS;
    }

    private void recordWrite() {
        events.add("write");
        writeTransactions.add(TransactionSynchronizationManager.isActualTransactionActive());
    }

    private void failCacheWrite(RuntimeException failure) {
        doAnswer(call -> {
            recordWrite();
            throw failure;
        }).when(repository).saveAndFlush(any(GeocodingCache.class));
    }

    private enum Operation {
        FORWARD, REVERSE;

        GeocodingResponse invoke(GeocodingService service) {
            return this == FORWARD ? service.search("  Ramallah  ")
                    : service.reverse(new BigDecimal("31.90"), new BigDecimal("35.20"));
        }
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void cacheConflictRollsBackBeforeReturningProviderResult(Operation operation) {
        failCacheWrite(new DataIntegrityViolationException("Controlled cache conflict"));

        var response = assertDoesNotThrow(() -> operation.invoke(service));
        events.add("returned");

        assertEquals(RESULTS, response.results());
        assertEquals(ATTRIBUTION, response.providerAttribution());
        assertFalse(response.cached());
        assertEquals(List.of(false), providerTransactions);
        assertEquals(List.of(true), writeTransactions);
        assertEquals(List.of("provider", "begin", "write", "rollback", "cleanup", "returned"), events);
        assertEquals(1, transactions.begins);
        assertEquals(1, transactions.rollbacks);
        assertEquals(0, transactions.commits);
        assertEquals(0, transactions.rollbackOnlyMarks);
        verify(repository).saveAndFlush(any(GeocodingCache.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void successfulInsertOrRefreshCommitsOnlyTheRepositoryTransaction(boolean refresh) {
        var existing = new GeocodingCache("fixture-provider", "hash", "expired",
                Instant.EPOCH, Instant.EPOCH.plusSeconds(1));
        if (refresh) {
            when(repository.findByProviderAndQueryHash(eq("fixture-provider"), anyString()))
                    .thenReturn(Optional.of(existing));
        }

        var response = service.search("Ramallah");
        events.add("returned");

        assertEquals(RESULTS, response.results());
        assertFalse(response.cached());
        assertEquals(List.of(false), providerTransactions);
        assertEquals(List.of(true), writeTransactions);
        assertEquals(List.of("provider", "begin", "write", "commit", "cleanup", "returned"), events);
        assertEquals(1, transactions.begins);
        assertEquals(1, transactions.commits);
        assertEquals(0, transactions.rollbacks);
        if (refresh) verify(repository).saveAndFlush(same(existing));
        else verify(repository).saveAndFlush(any(GeocodingCache.class));
    }

    @Test
    void cacheHitUsesNoProviderOrWriteTransaction() {
        var cached = new GeocodingCache("fixture-provider", "hash", objectMapper.writeValueAsString(RESULTS),
                Instant.now(), Instant.now().plusSeconds(3600));
        when(repository.findByProviderAndQueryHash(eq("fixture-provider"), anyString()))
                .thenReturn(Optional.of(cached));

        var response = service.search("Ramallah");

        assertTrue(response.cached());
        assertEquals(RESULTS, response.results());
        assertEquals(ATTRIBUTION, response.providerAttribution());
        assertTrue(events.isEmpty());
        assertEquals(0, transactions.begins);
        verify(provider, never()).forward(anyString());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void invalidInputStillFailsBeforeRepositoryOrProviderAccess() {
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.search(" "))
                .getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.reverse(new BigDecimal("91"), BigDecimal.ZERO)).getStatusCode().value());
        assertTrue(events.isEmpty());
        assertEquals(0, transactions.begins);
        verifyNoInteractions(repository, provider);
    }

    @Test
    void providerFailureRetainsItsHttpStatusWithoutStartingWriteTransaction() {
        doThrow(new GeocodingProviderException(GeocodingProviderException.Reason.TIMEOUT))
                .when(provider).forward("Ramallah");

        var failure = assertThrows(ResponseStatusException.class, () -> service.search("Ramallah"));

        assertEquals(504, failure.getStatusCode().value());
        assertEquals(0, transactions.begins);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void unrelatedCacheInfrastructureFailureStillPropagatesAfterRollback() {
        var failure = new DataAccessResourceFailureException("Controlled unavailable cache");
        failCacheWrite(failure);

        assertSame(failure, assertThrows(DataAccessResourceFailureException.class,
                () -> service.search("Ramallah")));
        assertEquals(List.of(false), providerTransactions);
        assertEquals(List.of(true), writeTransactions);
        assertEquals(1, transactions.rollbacks);
        assertEquals(0, transactions.commits);
    }

    @Test
    void callerOwnedTransactionReproducesTheOriginalCommitTimeFailure() {
        failCacheWrite(new DataIntegrityViolationException("Controlled joined cache conflict"));
        var returnedInsideTransaction = new AtomicBoolean();

        assertThrows(UnexpectedRollbackException.class, () -> new TransactionTemplate(transactions).execute(tx -> {
            assertEquals(RESULTS, service.search("Ramallah").results());
            returnedInsideTransaction.set(true);
            events.add("returnedToCaller");
            return null;
        }));

        assertTrue(returnedInsideTransaction.get(), "The service caught the failure before outer completion");
        assertEquals(List.of(true), providerTransactions);
        assertEquals(List.of(true), writeTransactions);
        assertEquals(List.of("begin", "provider", "write", "markRollbackOnly", "returnedToCaller",
                "rollback", "cleanup"), events);
        assertEquals(1, transactions.begins);
        assertEquals(1, transactions.rollbackOnlyMarks);
        assertEquals(1, transactions.rollbacks);
        assertEquals(0, transactions.commits);
    }

    @AfterEach
    void transactionStateIsReleased() {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
        assertFalse(TransactionSynchronizationManager.hasResource(transactions));
    }

    private static class RecordingTransactions extends AbstractPlatformTransactionManager {
        private final List<String> events;
        private int begins;
        private int commits;
        private int rollbacks;
        private int rollbackOnlyMarks;

        RecordingTransactions(List<String> events) { this.events = events; }

        @Override protected Object doGetTransaction() {
            var state = (State) TransactionSynchronizationManager.getResource(this);
            return new TransactionObject(state == null ? new State() : state);
        }

        @Override protected boolean isExistingTransaction(Object transaction) {
            return ((TransactionObject) transaction).state.active;
        }

        @Override protected void doBegin(Object transaction, TransactionDefinition definition) {
            var state = ((TransactionObject) transaction).state;
            state.active = true;
            TransactionSynchronizationManager.bindResource(this, state);
            begins++;
            events.add("begin");
        }

        @Override protected void doCommit(DefaultTransactionStatus status) {
            commits++;
            events.add("commit");
        }

        @Override protected void doRollback(DefaultTransactionStatus status) {
            rollbacks++;
            events.add("rollback");
        }

        @Override protected void doSetRollbackOnly(DefaultTransactionStatus status) {
            ((TransactionObject) status.getTransaction()).state.rollbackOnly = true;
            rollbackOnlyMarks++;
            events.add("markRollbackOnly");
        }

        @Override protected void doCleanupAfterCompletion(Object transaction) {
            ((TransactionObject) transaction).state.active = false;
            TransactionSynchronizationManager.unbindResource(this);
            events.add("cleanup");
        }

        private static class State {
            private boolean active;
            private boolean rollbackOnly;
        }

        private record TransactionObject(State state) implements SmartTransactionObject {
            @Override public boolean isRollbackOnly() { return state.rollbackOnly; }
            @Override public void flush() { }
        }
    }
}
