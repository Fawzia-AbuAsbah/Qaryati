package com.qaryati.community.service;

import com.qaryati.community.dto.EconomicActivityRequest;
import com.qaryati.community.entity.EconomicActivity;
import com.qaryati.community.entity.EconomicActivityType;
import com.qaryati.community.mapper.EconomicActivityMapper;
import com.qaryati.community.repository.EconomicActivityRepository;
import com.qaryati.village.api.VillageLookupApi;
import com.qaryati.village.api.VillageLookupResult;
import com.qaryati.village.api.VillageLookupStatus;
import com.qaryati.dataquality.api.AuditAction;
import com.qaryati.dataquality.api.AuditWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EconomicActivityServiceTest {

    private EconomicActivityRepository repository;
    private VillageLookupApi villages;
    private AuditWriter audits;
    private EconomicActivityService service;

    @BeforeEach
    void setUp() {
        repository = mock(EconomicActivityRepository.class);
        villages = mock(VillageLookupApi.class);
        audits = mock(AuditWriter.class);
        service = new EconomicActivityService(repository, new EconomicActivityMapper(), villages, audits);
    }

    @Test
    void createsActivityForExistingVillage() {
        var request = request(1L, "Olive farming", EconomicActivityType.AGRICULTURE);
        existingVillage(1L);
        when(repository.existsByVillageIdAndNameAndActivityType(1L, "Olive farming",
                EconomicActivityType.AGRICULTURE)).thenReturn(false);
        when(repository.saveAndFlush(any(EconomicActivity.class)))
                .thenAnswer(invocation -> {
                    var activity = invocation.getArgument(0, EconomicActivity.class);
                    id(activity, 5L);
                    return activity;
                });

        var response = service.create(request);

        assertEquals(1L, response.villageId());
        assertEquals("Olive farming", response.name());
        verify(repository).saveAndFlush(any(EconomicActivity.class));
        verify(audits).recordEconomicActivity(eq(5L), eq(AuditAction.CREATE), isNull(),
                argThat(snapshot -> snapshot.villageId().equals(1L)
                        && "Olive farming".equals(snapshot.name())
                        && "AGRICULTURE".equals(snapshot.activityType())));
    }

    @Test
    void rejectsMissingVillageBeforeDatabaseWrite() {
        when(villages.findById(1L)).thenReturn(Optional.empty());

        var exception = assertThrows(ResponseStatusException.class,
                () -> service.create(request(1L, "Olive farming", EconomicActivityType.AGRICULTURE)));

        assertEquals(404, exception.getStatusCode().value());
        verifyNoInteractions(repository);
        verifyNoInteractions(audits);
    }

    @Test
    void rejectsDuplicateTuple() {
        existingVillage(1L);
        when(repository.existsByVillageIdAndNameAndActivityType(1L, "Olive farming",
                EconomicActivityType.AGRICULTURE)).thenReturn(true);

        var exception = assertThrows(ResponseStatusException.class,
                () -> service.create(request(1L, "Olive farming", EconomicActivityType.AGRICULTURE)));

        assertEquals(409, exception.getStatusCode().value());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void getsAllOrFiltersByVillage() {
        var activity = new EconomicActivity(1L, "Olive farming", EconomicActivityType.AGRICULTURE, null);
        when(repository.findAll()).thenReturn(List.of(activity));
        when(repository.findByVillageId(1L)).thenReturn(List.of(activity));

        assertEquals(1, service.getAll(null).size());
        assertEquals(1, service.getAll(1L).size());
        verify(repository).findAll();
        verify(repository).findByVillageId(1L);
    }

    @Test
    void rejectsInvalidIdentifiers() {
        assertStatus(400, () -> service.getById(0L));
        assertStatus(400, () -> service.getAll(-1L));
        assertStatus(400, () -> service.delete(0L));
        verifyNoInteractions(repository, villages);
    }

    @Test
    void returnsNotFoundForMissingActivity() {
        when(repository.findById(9L)).thenReturn(Optional.empty());
        assertStatus(404, () -> service.getById(9L));
    }

    @Test
    void updatesAllSchemaBackedFieldsAndChecksNewVillage() {
        var current = new EconomicActivity(1L, "Olive farming", EconomicActivityType.AGRICULTURE, null);
        id(current, 5L);
        when(repository.findById(5L)).thenReturn(Optional.of(current));
        existingVillage(2L);
        when(repository.existsByVillageIdAndNameAndActivityTypeAndIdNot(2L, "Craft workshop",
                EconomicActivityType.CRAFT, 5L)).thenReturn(false);
        when(repository.saveAndFlush(current)).thenReturn(current);

        var response = service.update(5L, request(2L, "Craft workshop", EconomicActivityType.CRAFT));

        assertEquals(2L, response.villageId());
        assertEquals(EconomicActivityType.CRAFT, response.activityType());
        verify(repository).saveAndFlush(current);
        verify(audits).recordEconomicActivity(eq(5L), eq(AuditAction.UPDATE),
                argThat(snapshot -> snapshot.villageId().equals(1L) && "Olive farming".equals(snapshot.name())),
                argThat(snapshot -> snapshot.villageId().equals(2L) && "Craft workshop".equals(snapshot.name())));
    }

    @Test
    void deletesExistingActivity() {
        var activity = new EconomicActivity(1L, "Olive farming", EconomicActivityType.AGRICULTURE, null);
        id(activity, 5L);
        when(repository.findById(5L)).thenReturn(Optional.of(activity));

        service.delete(5L);

        verify(repository).delete(activity);
        verify(repository).flush();
        verify(audits).recordEconomicActivity(eq(5L), eq(AuditAction.DELETE),
                argThat(snapshot -> "Olive farming".equals(snapshot.name())), isNull());
    }

    @Test
    void translatesDatabaseConstraintFailures() {
        existingVillage(1L);
        when(repository.existsByVillageIdAndNameAndActivityType(1L, "Olive farming",
                EconomicActivityType.AGRICULTURE)).thenReturn(false);
        when(repository.saveAndFlush(any(EconomicActivity.class)))
                .thenThrow(new DataIntegrityViolationException("constraint"));

        assertStatus(409, () -> service.create(request(1L, "Olive farming", EconomicActivityType.AGRICULTURE)));
        verifyNoInteractions(audits);
    }

    @Test
    void propagatesAuditFailureAfterSuccessfulCreate() {
        var request = request(1L, "Olive farming", EconomicActivityType.AGRICULTURE);
        existingVillage(1L);
        when(repository.existsByVillageIdAndNameAndActivityType(1L, "Olive farming", EconomicActivityType.AGRICULTURE))
                .thenReturn(false);
        when(repository.saveAndFlush(any(EconomicActivity.class))).thenAnswer(invocation -> {
            var activity = invocation.getArgument(0, EconomicActivity.class);
            id(activity, 5L);
            return activity;
        });
        doThrow(new IllegalStateException("audit failure")).when(audits).recordEconomicActivity(
                eq(5L), eq(AuditAction.CREATE), isNull(), any());

        assertEquals("audit failure", assertThrows(IllegalStateException.class, () -> service.create(request)).getMessage());
        InOrder order = inOrder(repository, audits);
        order.verify(repository).saveAndFlush(any(EconomicActivity.class));
        order.verify(audits).recordEconomicActivity(eq(5L), eq(AuditAction.CREATE), isNull(), any());
    }

    private EconomicActivityRequest request(Long villageId, String name, EconomicActivityType type) {
        return new EconomicActivityRequest(villageId, name, type, "Description");
    }

    private void existingVillage(Long id) {
        when(villages.findById(id)).thenReturn(Optional.of(
                new VillageLookupResult(id, VillageLookupStatus.DRAFT, false)));
    }

    private void assertStatus(int expectedStatus, Runnable operation) {
        var exception = assertThrows(ResponseStatusException.class, operation::run);
        assertEquals(expectedStatus, exception.getStatusCode().value());
    }

    private void id(EconomicActivity activity, Long id) {
        ReflectionTestUtils.setField(activity, "id", id);
    }
}
