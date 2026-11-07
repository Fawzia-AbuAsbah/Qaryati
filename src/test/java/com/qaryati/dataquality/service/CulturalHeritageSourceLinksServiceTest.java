package com.qaryati.dataquality.service;

import com.qaryati.dataquality.api.*;
import com.qaryati.dataquality.repository.CulturalHeritageSourceLinkRepository;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CulturalHeritageSourceLinksServiceTest {
    final CulturalHeritageSourceLinkRepository repository = mock(CulturalHeritageSourceLinkRepository.class);
    final SourceLookup sources = mock(SourceLookup.class);
    final CulturalHeritageSourceLinksService service = new CulturalHeritageSourceLinksService(repository, sources);

    @Test void batchReadDetectsMissingAndIncludesEmptyLegacyAssociations() {
        when(repository.sourceIdsByCulturalHeritageIds(any())).thenReturn(Map.of(1L, List.of(3L, 2L, 3L), 2L, List.of()));
        assertEquals(Map.of(1L, List.of(2L, 3L), 2L, List.of()), service.sourceIdsByCulturalHeritageIds(List.of(2L, 1L, 1L)));
        var immutable = service.sourceIdsByCulturalHeritageIds(List.of(1L,2L));
        assertThrows(UnsupportedOperationException.class,()->immutable.put(3L,List.of()));
        assertThrows(UnsupportedOperationException.class,()->immutable.get(1L).add(4L));
        verify(repository,times(2)).sourceIdsByCulturalHeritageIds(new TreeSet<>(List.of(1L, 2L)));
        verify(repository, never()).culturalHeritageExists(any(), anyBoolean());
        assertThrows(NoSuchElementException.class, () -> service.sourceIdsByCulturalHeritageIds(List.of(3L)));
        assertEquals(Map.of(), service.sourceIdsByCulturalHeritageIds(List.of()));
        assertThrows(IllegalArgumentException.class, () -> service.sourceIdsByCulturalHeritageIds(null));
        assertThrows(IllegalArgumentException.class, () -> service.sourceIdsByCulturalHeritageIds(List.of(0L)));
    }
    @Test void validatesAllIdsAndEmptyCollections() {
        for (Long id : Arrays.asList(null, 0L, -1L)) {
            assertThrows(IllegalArgumentException.class, () -> service.sourceIds(id));
            assertThrows(IllegalArgumentException.class, () -> service.replaceSources(1L, Arrays.asList(id)));
        }
        assertThrows(IllegalArgumentException.class, () -> service.linkNewCulturalHeritage(1L, List.of()));
        assertThrows(IllegalArgumentException.class, () -> service.replaceSources(1L, null));
        verifyNoInteractions(repository, sources);
    }
    @Test void missingParentsFailForEveryOperation() {
        assertThrows(NoSuchElementException.class, () -> service.sourceIds(1L));
        assertThrows(NoSuchElementException.class, () -> service.linkNewCulturalHeritage(1L, List.of(2L)));
        assertThrows(NoSuchElementException.class, () -> service.replaceSources(1L, List.of(2L)));
        verify(repository, never()).deleteLinks(any());
        verifyNoInteractions(sources);
    }
    @Test void missingSourceFailsBeforeDeletingExistingLinks() {
        when(repository.culturalHeritageExists(1L, true)).thenReturn(true);
        doThrow(new NoSuchElementException()).when(sources).requireExists(2L);
        assertThrows(NoSuchElementException.class, () -> service.replaceSources(1L, List.of(2L)));
        verify(repository, never()).deleteLinks(any());
        verify(repository, never()).insert(any(), any());
    }
    @Test void replacesSortedDistinctSourcesUnderParentLock() {
        when(repository.culturalHeritageExists(1L, true)).thenReturn(true);
        service.replaceSources(1L, List.of(3L, 2L, 3L));
        var order = inOrder(repository, sources);
        order.verify(repository).culturalHeritageExists(1L, true);
        order.verify(sources).requireExists(2L);
        order.verify(sources).requireExists(3L);
        order.verify(repository).deleteLinks(1L);
        order.verify(repository).insert(1L, 2L);
        order.verify(repository).insert(1L, 3L);
        order.verifyNoMoreInteractions();
    }
    @Test void readsImmutableOrderedIdsAndRejectsRelinking() {
        when(repository.culturalHeritageExists(1L, false)).thenReturn(true);
        when(repository.culturalHeritageExists(1L, true)).thenReturn(true);
        when(repository.sourceIds(1L)).thenReturn(List.of(3L, 2L, 3L));
        assertEquals(List.of(2L, 3L), service.sourceIds(1L));
        assertThrows(UnsupportedOperationException.class, () -> service.sourceIds(1L).add(4L));
        assertThrows(IllegalStateException.class, () -> service.linkNewCulturalHeritage(1L, List.of(4L)));
        verifyNoInteractions(sources);
    }
    @Test void newLinksCollapseDuplicates() {
        when(repository.culturalHeritageExists(1L, true)).thenReturn(true);
        when(repository.sourceIds(1L)).thenReturn(List.of());
        service.linkNewCulturalHeritage(1L, List.of(2L, 2L));
        verify(repository).insert(1L, 2L);
        verify(repository, never()).deleteLinks(any());
    }
    @Configuration @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {
        @Bean CulturalHeritageSourceLinkRepository repository() { return mock(CulturalHeritageSourceLinkRepository.class); }
        @Bean SourceLookup sources() { return mock(SourceLookup.class); }
        @Bean CulturalHeritageSourceLinksService service(CulturalHeritageSourceLinkRepository repository, SourceLookup sources) {
            return new CulturalHeritageSourceLinksService(repository, sources);
        }
        @Bean AuditWriterServiceTest.TestTransactions transactionManager() {
            return new AuditWriterServiceTest.TestTransactions();
        }
    }
    @Test void joinsCallerAndPersistenceFailureRollsBack() {
        for (boolean fail : List.of(false, true)) {
            try (var context = new AnnotationConfigApplicationContext(Config.class)) {
                var repo = context.getBean(CulturalHeritageSourceLinkRepository.class);
                when(repo.culturalHeritageExists(1L, true)).thenReturn(true);
                var tx = context.getBean(AuditWriterServiceTest.TestTransactions.class);
                doAnswer(call -> {
                    assertTrue(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
                    assertEquals(1, tx.begins);
                    assertEquals(0, tx.commits);
                    if (fail) throw new IllegalStateException("insert failed");
                    return null;
                }).when(repo).insert(1L, 2L);
                Runnable mutation = () -> new TransactionTemplate(tx).executeWithoutResult(status ->
                        context.getBean(CulturalHeritageSourceLinks.class).replaceSources(1L, List.of(2L)));
                if (fail) assertThrows(IllegalStateException.class, mutation::run);
                else mutation.run();
                assertEquals(fail ? 1 : 0, tx.rollbacks);
                assertEquals(fail ? 0 : 1, tx.commits);
            }
        }
    }
}
