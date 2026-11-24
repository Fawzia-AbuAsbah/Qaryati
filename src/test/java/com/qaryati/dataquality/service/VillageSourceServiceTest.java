
package com.qaryati.dataquality.service;

import com.qaryati.dataquality.entity.VillageSource;
import com.qaryati.dataquality.entity.VillageSourceId;
import com.qaryati.dataquality.repository.SourceRepository;
import com.qaryati.dataquality.repository.VillageExistsRepository;
import com.qaryati.dataquality.repository.VillageSourceRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VillageSourceServiceTest {

    private VillageSourceRepository villageSourceRepository;
    private SourceRepository sourceRepository;
    private VillageExistsRepository villageExistsRepository;
    private AuditLogService auditLogService;
    private VillageSourceService service;

    @BeforeEach
    void setUp() {
        villageSourceRepository = mock(VillageSourceRepository.class);
        sourceRepository = mock(SourceRepository.class);
        villageExistsRepository = mock(VillageExistsRepository.class);
        auditLogService = mock(AuditLogService.class);

        service = new VillageSourceService(
                villageSourceRepository,
                sourceRepository,
                villageExistsRepository,
                auditLogService
        );
    }

    @Test
    void shouldGetSourcesForVillage() {
        VillageSource link =
                new VillageSource(1L, 5L, "Population data");

        when(villageSourceRepository.findByIdVillageId(1L))
                .thenReturn(List.of(link));

        List<VillageSource> result = service.getSourcesForVillage(1L);

        assertEquals(1, result.size());
        assertEquals(5L, result.get(0).getSourceId());

        verifyNoInteractions(auditLogService);
    }

    @Test
    void shouldGetVillagesForSource() {
        VillageSource link =
                new VillageSource(1L, 5L, "Population data");

        when(villageSourceRepository.findByIdSourceId(5L))
                .thenReturn(List.of(link));

        List<VillageSource> result = service.getVillagesForSource(5L);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getVillageId());

        verifyNoInteractions(auditLogService);
    }

    @Test
    void shouldGetExistingVillageSource() {
        VillageSourceId id = new VillageSourceId(1L, 5L);

        VillageSource link =
                new VillageSource(1L, 5L, "Population data");

        when(villageSourceRepository.findById(id))
                .thenReturn(Optional.of(link));

        VillageSource result = service.getVillageSource(1L, 5L);

        assertEquals(1L, result.getVillageId());
        assertEquals(5L, result.getSourceId());

        verifyNoInteractions(auditLogService);
    }

    @Test
    void shouldThrowWhenVillageSourceNotFound() {
        VillageSourceId id = new VillageSourceId(1L, 999L);

        when(villageSourceRepository.findById(id))
                .thenReturn(Optional.empty());

        assertThrows(
                NoSuchElementException.class,
                () -> service.getVillageSource(1L, 999L)
        );

        verifyNoInteractions(auditLogService);
    }

    @Test
    void shouldLinkSourceToVillageSuccessfully() {

        when(villageExistsRepository.existsById(1L))
                .thenReturn(true);

        when(sourceRepository.existsById(5L))
                .thenReturn(true);

        when(villageSourceRepository.insertIfAbsent(1L, 5L, "Population data"))
                .thenReturn(1);

        VillageSource result =
                service.linkSourceToVillage(
                        1L,
                        5L,
                        "Population data"
                );

        assertEquals(1L, result.getVillageId());
        assertEquals(5L, result.getSourceId());
        assertEquals("Population data", result.getClaimNote());

        verify(villageSourceRepository)
                .insertIfAbsent(1L, 5L, "Population data");
        verify(villageSourceRepository, never()).saveAndFlush(any());

        verify(auditLogService).record(
                eq("VILLAGE_SOURCE"),
                eq(1L),
                eq("CREATE"),
                isNull(),
                argThat(data ->
                        Long.valueOf(1L).equals(data.get("villageId"))
                                && Long.valueOf(5L).equals(data.get("sourceId"))
                ),
                eq("Source linked to village")
        );
    }

    @Test
    void shouldRejectMissingVillage() {

        when(villageExistsRepository.existsById(999L))
                .thenReturn(false);

        assertThrows(
                NoSuchElementException.class,
                () -> service.linkSourceToVillage(999L, 5L, "Test")
        );

        verify(villageSourceRepository, never())
                .insertIfAbsent(any(), any(), any());

        verifyNoInteractions(auditLogService);
    }

    @Test
    void shouldRejectMissingSource() {

        when(villageExistsRepository.existsById(1L))
                .thenReturn(true);

        when(sourceRepository.existsById(999L))
                .thenReturn(false);

        assertThrows(
                NoSuchElementException.class,
                () -> service.linkSourceToVillage(1L, 999L, "Test")
        );

        verify(villageSourceRepository, never())
                .insertIfAbsent(any(), any(), any());

        verifyNoInteractions(auditLogService);
    }

    @Test
    void shouldRejectDuplicateLink() {

        when(villageExistsRepository.existsById(1L))
                .thenReturn(true);

        when(sourceRepository.existsById(5L))
                .thenReturn(true);

        when(villageSourceRepository.existsById(
                new VillageSourceId(1L, 5L)
        )).thenReturn(true);

        assertThrows(
                IllegalStateException.class,
                () -> service.linkSourceToVillage(1L, 5L, "Test")
        );

        verify(villageSourceRepository, never())
                .insertIfAbsent(any(), any(), any());

        verifyNoInteractions(auditLogService);
    }

    @Test
    void rejectsDuplicateWhenAdvisoryPrecheckMissesCompetingInsert() {
        when(villageExistsRepository.existsById(1L)).thenReturn(true);
        when(sourceRepository.existsById(5L)).thenReturn(true);
        when(villageSourceRepository.insertIfAbsent(1L, 5L, "Competing note")).thenReturn(0);

        var failure = assertThrows(IllegalStateException.class,
                () -> service.linkSourceToVillage(1L, 5L, "Competing note"));

        assertEquals("Source is already linked to this village", failure.getMessage());
        verify(villageSourceRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void unrelatedIntegrityFailurePropagatesWithoutAuditOrDuplicateTranslation() {
        when(villageExistsRepository.existsById(1L)).thenReturn(true);
        when(sourceRepository.existsById(5L)).thenReturn(true);
        var failure = new org.springframework.dao.DataIntegrityViolationException("simulated FK failure");
        when(villageSourceRepository.insertIfAbsent(1L, 5L, null)).thenThrow(failure);

        assertSame(failure, assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> service.linkSourceToVillage(1L, 5L, null)));
        verifyNoInteractions(auditLogService);
    }

    @Test
    void concurrentCallsWithStalePrechecksKeepOnlyWinningNoteAndAudit() throws Exception {
        when(villageExistsRepository.existsById(1L)).thenReturn(true);
        when(sourceRepository.existsById(5L)).thenReturn(true);
        var bothPrechecks = new CyclicBarrier(2);
        when(villageSourceRepository.existsById(new VillageSourceId(1L, 5L))).thenAnswer(call -> {
            bothPrechecks.await(5, TimeUnit.SECONDS);
            return false;
        });
        // Models atomic insert-if-absent, not PostgreSQL execution or transaction behavior.
        var stored = new ConcurrentHashMap<VillageSourceId, VillageSource>();
        when(villageSourceRepository.insertIfAbsent(eq(1L), eq(5L), anyString())).thenAnswer(call -> {
            var candidate = new VillageSource(1L, 5L, call.getArgument(2));
            return stored.putIfAbsent(candidate.getId(), candidate) == null ? 1 : 0;
        });
        java.util.function.Function<String, Object> attempt = note -> {
            try { return service.linkSourceToVillage(1L, 5L, note); }
            catch (IllegalStateException duplicate) { return duplicate; }
        };
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> attempt.apply("  First citation\noriginal note  "));
            var second = pool.submit(() -> attempt.apply("Second citation note"));
            var results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            var winners = results.stream().filter(VillageSource.class::isInstance).map(VillageSource.class::cast).toList();
            var duplicates = results.stream().filter(IllegalStateException.class::isInstance)
                    .map(IllegalStateException.class::cast).toList();
            assertEquals(1, winners.size());
            assertEquals(1, duplicates.size());
            assertEquals("Source is already linked to this village", duplicates.getFirst().getMessage());
            assertEquals(1, stored.size());
            assertEquals(winners.getFirst().getClaimNote(), stored.get(new VillageSourceId(1L, 5L)).getClaimNote());
            verify(villageSourceRepository, times(2)).insertIfAbsent(eq(1L), eq(5L), anyString());
            verify(villageSourceRepository, never()).saveAndFlush(any());
            verify(auditLogService, times(1)).record(eq("VILLAGE_SOURCE"), eq(1L), eq("CREATE"),
                    isNull(), eq(java.util.Map.of("villageId", 1L, "sourceId", 5L)), eq("Source linked to village"));
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void shouldRejectInvalidIds() {

        assertThrows(
                IllegalArgumentException.class,
                () -> service.linkSourceToVillage(-1L, 5L, "Test")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.linkSourceToVillage(null, 5L, "Test")
        );

        verifyNoInteractions(
                villageExistsRepository,
                sourceRepository,
                villageSourceRepository,
                auditLogService
        );
    }
}
