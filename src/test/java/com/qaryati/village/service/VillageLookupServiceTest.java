package com.qaryati.village.service;

import com.qaryati.village.api.VillageLookupApi;
import com.qaryati.village.api.VillageLookupResult;
import com.qaryati.village.api.VillageLookupStatus;
import com.qaryati.village.entity.Village;
import com.qaryati.village.entity.VillageStatus;
import com.qaryati.village.repository.VillageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VillageLookupServiceTest {
    private final VillageRepository repository = mock(VillageRepository.class);
    private final VillageLookupService service = new VillageLookupService(repository);

    static Stream<Arguments> villageStates() {
        return Stream.of(false, true).flatMap(archived -> Stream.of(
                Arguments.of(VillageStatus.DRAFT, VillageLookupStatus.DRAFT, archived),
                Arguments.of(VillageStatus.UNDER_REVIEW, VillageLookupStatus.UNDER_REVIEW, archived),
                Arguments.of(VillageStatus.VERIFIED, VillageLookupStatus.VERIFIED, archived),
                Arguments.of(VillageStatus.REJECTED, VillageLookupStatus.REJECTED, archived)));
    }

    @ParameterizedTest
    @MethodSource("villageStates")
    void returnsStateUsingOnlyAnOrdinaryLookupAndScalarReads(
            VillageStatus status, VillageLookupStatus expectedStatus, boolean archived) {
        Village village = spy(village(status, archived));
        when(repository.findById(42L)).thenReturn(Optional.of(village));

        assertEquals(Optional.of(new VillageLookupResult(42L, expectedStatus, archived)),
                service.findById(42L));

        verify(repository).findById(42L);
        // Any save, flush, lock, or additional repository read fails this assertion.
        verifyNoMoreInteractions(repository);
        verify(village).getId();
        verify(village).getStatus();
        verify(village).getArchivedAt();
        // No entity mutation or lazy parent traversal is needed for the snapshot.
        verifyNoMoreInteractions(village);
    }

    @Test
    void returnsEmptyForAMissingPositiveId() {
        when(repository.findById(42L)).thenReturn(Optional.empty());

        assertTrue(service.findById(42L).isEmpty());

        verify(repository).findById(42L);
        verifyNoMoreInteractions(repository);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void rejectsInvalidIdsBeforeRepositoryAccess(Long villageId) {
        assertThrows(IllegalArgumentException.class, () -> service.findById(villageId));

        verifyNoInteractions(repository);
    }

    @Test
    void propagatesInfrastructureFailuresWithoutTranslationOrRetry() {
        var failure = new DataAccessResourceFailureException("Repository unavailable");
        when(repository.findById(42L)).thenThrow(failure);

        assertSame(failure, assertThrows(DataAccessResourceFailureException.class,
                () -> service.findById(42L)));

        verify(repository).findById(42L);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void scannedServiceIsInjectableByApiAndRunsInAReadOnlyTransaction() {
        var transactionManager = new RecordingTransactionManager();
        Village village = village(VillageStatus.UNDER_REVIEW, true);
        when(repository.findById(42L)).thenAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            return Optional.of(village);
        });

        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(TransactionConfiguration.class);
            context.registerBean(VillageRepository.class, () -> repository);
            context.registerBean(RecordingTransactionManager.class, () -> transactionManager);
            var scanner = new ClassPathBeanDefinitionScanner(context);
            scanner.addExcludeFilter((metadata, factory) ->
                    !metadata.getClassMetadata().getClassName().equals(VillageLookupService.class.getName()));
            scanner.scan(VillageLookupService.class.getPackageName());
            context.refresh();

            VillageLookupApi lookup = context.getBean(VillageLookupApi.class);
            assertEquals(Optional.of(new VillageLookupResult(42L, VillageLookupStatus.UNDER_REVIEW, true)),
                    lookup.findById(42L));
        }

        assertEquals(1, transactionManager.begins);
        assertEquals(1, transactionManager.commits);
        assertEquals(0, transactionManager.rollbacks);
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
        verify(repository).findById(42L);
        verifyNoMoreInteractions(repository);
    }

    private static Village village(VillageStatus status, boolean archived) {
        Village village = new Village();
        village.setId(42L);
        village.setStatus(status);
        village.setArchivedAt(archived ? Instant.EPOCH : null);
        return village;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TransactionConfiguration {
    }

    /** In-memory transaction lifecycle only: no datasource or persistence context. */
    private static class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        int begins;
        int commits;
        int rollbacks;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            assertTrue(definition.isReadOnly());
            begins++;
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commits++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbacks++;
        }
    }
}
