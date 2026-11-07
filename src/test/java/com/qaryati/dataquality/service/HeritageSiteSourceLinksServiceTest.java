package com.qaryati.dataquality.service;

import com.qaryati.dataquality.api.*;
import com.qaryati.dataquality.repository.HeritageSiteSourceLinkRepository;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HeritageSiteSourceLinksServiceTest {
    final HeritageSiteSourceLinkRepository repository = mock(HeritageSiteSourceLinkRepository.class);
    final SourceLookup sources = mock(SourceLookup.class);
    final HeritageSiteSourceLinksService service = new HeritageSiteSourceLinksService(repository, sources);

    @Test void batchesSortedDistinctSitesAndIncludesEmptySets() {
        assertEquals(Map.of(), service.sourceIdsBySiteIds(List.of()));
        verifyNoInteractions(repository);
        when(repository.sourceIdsBySiteIds(new TreeSet<>(List.of(1L, 2L))))
                .thenReturn(Map.of(1L, List.of(4L, 3L, 4L), 2L, List.of()));
        var result = service.sourceIdsBySiteIds(List.of(2L, 1L, 2L));
        assertEquals(List.of(1L, 2L), new ArrayList<>(result.keySet()));
        assertEquals(List.of(3L, 4L), result.get(1L));
        assertEquals(List.of(), result.get(2L));
        assertThrows(UnsupportedOperationException.class, () -> result.put(3L, List.of()));
        assertThrows(UnsupportedOperationException.class, () -> result.get(1L).add(5L));
        verify(repository).sourceIdsBySiteIds(new TreeSet<>(List.of(1L, 2L)));
        verifyNoMoreInteractions(repository);
    }

    @Test void batchRejectsInvalidAndMissingSitesAndPropagatesFailures() {
        assertThrows(IllegalArgumentException.class, () -> service.sourceIdsBySiteIds(null));
        for (Long id : Arrays.asList(null, 0L, -1L))
            assertThrows(IllegalArgumentException.class, () -> service.sourceIdsBySiteIds(Arrays.asList(id)));
        verifyNoInteractions(repository);
        when(repository.sourceIdsBySiteIds(any())).thenReturn(Map.of(1L, List.of()));
        assertThrows(NoSuchElementException.class, () -> service.sourceIdsBySiteIds(List.of(1L, 2L)));
        when(repository.sourceIdsBySiteIds(any())).thenThrow(new IllegalStateException("provider failed"));
        assertThrows(IllegalStateException.class, () -> service.sourceIdsBySiteIds(List.of(1L)));
    }

    @Test void validatesAllIdsAndEmptyCollections() {
        for (Long id : Arrays.asList(null, 0L, -1L)) {
            assertThrows(IllegalArgumentException.class, () -> service.sourceIds(id));
            assertThrows(IllegalArgumentException.class, () -> service.replaceSources(1L, Arrays.asList(id)));
        }
        assertThrows(IllegalArgumentException.class, () -> service.linkNewSite(1L, List.of()));
        assertThrows(IllegalArgumentException.class, () -> service.replaceSources(1L, null));
        verifyNoInteractions(repository, sources);
    }
    @Test void missingEventsFailForEveryOperation() {
        assertThrows(NoSuchElementException.class, () -> service.sourceIds(1L));
        assertThrows(NoSuchElementException.class, () -> service.linkNewSite(1L, List.of(2L)));
        assertThrows(NoSuchElementException.class, () -> service.replaceSources(1L, List.of(2L)));
        verify(repository, never()).deleteLinks(any());
        verifyNoInteractions(sources);
    }
    @Test void missingSourceFailsBeforeDeletingExistingLinks() {
        when(repository.siteExists(1L, true)).thenReturn(true);
        doThrow(new NoSuchElementException()).when(sources).requireExists(2L);
        assertThrows(NoSuchElementException.class, () -> service.replaceSources(1L, List.of(2L)));
        verify(repository, never()).deleteLinks(any());
        verify(repository, never()).insert(any(), any());
    }
    @Test void replacesSortedDistinctSourcesUnderEventLock() {
        when(repository.siteExists(1L, true)).thenReturn(true);
        service.replaceSources(1L, List.of(3L, 2L, 3L));
        var order = inOrder(repository, sources);
        order.verify(repository).siteExists(1L, true);
        order.verify(sources).requireExists(2L);
        order.verify(sources).requireExists(3L);
        order.verify(repository).deleteLinks(1L);
        order.verify(repository).insert(1L, 2L);
        order.verify(repository).insert(1L, 3L);
        order.verifyNoMoreInteractions();
    }
    @Test void readsImmutableOrderedIdsAndRejectsRelinking() {
        when(repository.siteExists(1L, false)).thenReturn(true);
        when(repository.siteExists(1L, true)).thenReturn(true);
        when(repository.sourceIds(1L)).thenReturn(List.of(2L, 3L));
        assertEquals(List.of(2L, 3L), service.sourceIds(1L));
        assertThrows(UnsupportedOperationException.class, () -> service.sourceIds(1L).add(4L));
        assertThrows(IllegalStateException.class, () -> service.linkNewSite(1L, List.of(4L)));
        verifyNoInteractions(sources);
    }
    @Test void newLinksCollapseDuplicates() {
        when(repository.siteExists(1L, true)).thenReturn(true);
        when(repository.sourceIds(1L)).thenReturn(List.of());
        service.linkNewSite(1L, List.of(2L, 2L));
        verify(repository).insert(1L, 2L);
        verify(repository, never()).deleteLinks(any());
    }
    @Configuration @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {
        @Bean HeritageSiteSourceLinkRepository repository() { return mock(HeritageSiteSourceLinkRepository.class); }
        @Bean SourceLookup sources() { return mock(SourceLookup.class); }
        @Bean HeritageSiteSourceLinksService service(HeritageSiteSourceLinkRepository repository, SourceLookup sources) {
            return new HeritageSiteSourceLinksService(repository, sources);
        }
        @Bean AuditWriterServiceTest.TestTransactions transactionManager() {
            return new AuditWriterServiceTest.TestTransactions();
        }
    }
    @Test void joinsCallerAndPersistenceFailureRollsBack() {
        for (boolean fail : List.of(false, true)) {
            try (var context = new AnnotationConfigApplicationContext(Config.class)) {
                var repo = context.getBean(HeritageSiteSourceLinkRepository.class);
                when(repo.siteExists(1L, true)).thenReturn(true);
                var tx = context.getBean(AuditWriterServiceTest.TestTransactions.class);
                doAnswer(call -> {
                    assertTrue(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
                    assertEquals(1, tx.begins);
                    assertEquals(0, tx.commits);
                    if (fail) throw new IllegalStateException("insert failed");
                    return null;
                }).when(repo).insert(1L, 2L);
                Runnable mutation = () -> new TransactionTemplate(tx).executeWithoutResult(status ->
                        context.getBean(HeritageSiteSourceLinks.class).replaceSources(1L, List.of(2L)));
                if (fail) assertThrows(IllegalStateException.class, mutation::run);
                else mutation.run();
                assertEquals(fail ? 1 : 0, tx.rollbacks);
                assertEquals(fail ? 0 : 1, tx.commits);
            }
        }
    }
}
