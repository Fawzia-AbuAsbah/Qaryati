
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

        when(villageSourceRepository.saveAndFlush(
                any(VillageSource.class)
        )).thenAnswer(invocation -> invocation.getArgument(0));

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
                .saveAndFlush(any(VillageSource.class));

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
                .saveAndFlush(any(VillageSource.class));

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
                .saveAndFlush(any(VillageSource.class));

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
                .saveAndFlush(any(VillageSource.class));

        verifyNoInteractions(auditLogService);
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
