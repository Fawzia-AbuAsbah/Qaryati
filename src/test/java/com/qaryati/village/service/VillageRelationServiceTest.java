package com.qaryati.village.service;
import com.qaryati.common.exception.*;
import com.qaryati.village.dto.*;
import com.qaryati.village.entity.*;
import com.qaryati.village.mapper.VillageRelationMapper;
import com.qaryati.village.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.dao.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VillageRelationServiceTest {
    VillageRepository villages; VillageRelationRepository relations; VillageRelationService service;
    Village parent; VillageRelation child;
    final VillageRelationRequest request = new VillageRelationRequest(null, " Name ", VillageRelationType.OTHER, null);
    @BeforeEach void setup() {
        villages = mock(VillageRepository.class); relations = mock(VillageRelationRepository.class);
        service = new VillageRelationService(villages, relations, new VillageRelationMapper());
        parent = new Village(); parent.setId(1L); parent.setStatus(VillageStatus.DRAFT); parent.setUpdatedAt(Instant.EPOCH);
        child = new VillageRelationMapper().toEntity(request); child.setId(2L); child.setVillage(parent);
        when(villages.findById(1L)).thenReturn(Optional.of(parent));
        when(villages.findByIdForUpdate(1L)).thenReturn(Optional.of(parent));
        when(relations.findByIdAndVillageId(2L, 1L)).thenReturn(Optional.of(child));
        when(relations.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }
    @Test void createLocksBeforeDuplicateCheckAndPreservesParentTimestamp() {
        var result = service.create(1L, request);
        assertEquals(" Name ", result.relatedPlaceName()); assertNull(result.relatedVillageId());
        assertEquals(Instant.EPOCH, parent.getUpdatedAt());
        var order = inOrder(villages, relations);
        order.verify(villages).findByIdForUpdate(1L);
        order.verify(relations).existsByVillageIdAndRelatedPlaceNameAndRelationType(1L, " Name ", VillageRelationType.OTHER);
        order.verify(relations).saveAndFlush(any());
        verify(villages, never()).save(any());
    }
    @Test void updateExcludesSelfClearsDistanceAndScopesLookup() {
        child.setDistanceKm(java.math.BigDecimal.ONE);
        assertNull(service.update(1L, 2L, request).distanceKm());
        var order = inOrder(villages, relations);
        order.verify(villages).findByIdForUpdate(1L);
        order.verify(relations).findByIdAndVillageId(2L, 1L);
        order.verify(relations).existsByVillageIdAndRelatedPlaceNameAndRelationTypeAndIdNot(1L, " Name ", VillageRelationType.OTHER, 2L);
        order.verify(relations).saveAndFlush(child);
        assertEquals(Instant.EPOCH, parent.getUpdatedAt());
    }
    @Test void deleteLocksScopesAndFlushesAndRepeatIs404() {
        service.delete(1L, 2L);
        var order = inOrder(villages, relations);
        order.verify(villages).findByIdForUpdate(1L); order.verify(relations).findByIdAndVillageId(2L, 1L);
        order.verify(relations).delete(child); order.verify(relations).flush();
        assertEquals(Instant.EPOCH, parent.getUpdatedAt());
        when(relations.findByIdAndVillageId(2L, 1L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.delete(1L, 2L));
    }
    @Test void archivedReadsAndEmptyCollection() {
        parent.setArchivedAt(Instant.EPOCH); parent.setStatus(VillageStatus.VERIFIED);
        when(relations.findByVillageIdOrderByIdAsc(1L)).thenReturn(List.of(child));
        assertEquals(2L, service.getAll(1L).getFirst().id());
        assertEquals(2L, service.getById(1L, 2L).id());
        when(relations.findByVillageIdOrderByIdAsc(1L)).thenReturn(List.of()); assertTrue(service.getAll(1L).isEmpty());
        verify(villages, never()).findByIdForUpdate(any());
    }
    @ParameterizedTest @ValueSource(strings = {"create", "update", "delete"})
    void archivedMutationsRejected(String op) { parent.setArchivedAt(Instant.EPOCH); conflict(op); }
    @ParameterizedTest @EnumSource(value = VillageStatus.class, names = {"UNDER_REVIEW", "VERIFIED", "REJECTED"})
    void nonDraftMutationsRejected(VillageStatus status) { parent.setStatus(status); for (String op : List.of("create","update","delete")) conflict(op); }
    private void conflict(String op) {
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> mutate(op)).getStatusCode().value());
        verify(relations, never()).saveAndFlush(any()); verify(relations, never()).delete(any());
    }
    private void mutate(String op) { switch(op) { case "create" -> service.create(1L, request); case "update" -> service.update(1L,2L,request); default -> service.delete(1L,2L); } }
    @Test void duplicateCreateAndUpdateRejected() {
        when(relations.existsByVillageIdAndRelatedPlaceNameAndRelationType(1L," Name ",VillageRelationType.OTHER)).thenReturn(true);
        when(relations.existsByVillageIdAndRelatedPlaceNameAndRelationTypeAndIdNot(1L," Name ",VillageRelationType.OTHER,2L)).thenReturn(true);
        assertThrows(DuplicateResourceException.class, () -> service.create(1L,request));
        assertThrows(DuplicateResourceException.class, () -> service.update(1L,2L,request));
        verify(relations, never()).saveAndFlush(any());
    }
    @ParameterizedTest @NullSource @ValueSource(longs = {0,-1})
    void invalidIdsRejected(Long id) {
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.getAll(id)).getStatusCode().value());
        assertThrows(ResponseStatusException.class, () -> service.create(id, request));
        assertThrows(ResponseStatusException.class, () -> service.getById(1L,id));
        assertThrows(ResponseStatusException.class, () -> service.update(1L,id,request));
        assertThrows(ResponseStatusException.class, () -> service.delete(1L,id));
        verifyNoInteractions(villages, relations);
    }
    @Test void missingParentAndWrongVillageChildAre404() {
        assertThrows(ResourceNotFoundException.class, () -> service.getAll(9L));
        assertThrows(ResourceNotFoundException.class, () -> service.create(9L,request));
        assertThrows(ResourceNotFoundException.class, () -> service.getById(1L,9L));
        assertThrows(ResourceNotFoundException.class, () -> service.update(1L,9L,request));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(1L,9L));
        verify(relations, never()).findById(any());
    }
    @Test void integrityConflictsSanitizedWithoutRetry() {
        when(relations.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("secret SQL"));
        var ex = assertThrows(ResponseStatusException.class, () -> service.create(1L,request));
        assertEquals(409,ex.getStatusCode().value()); assertFalse(ex.getReason().contains("secret"));
        verify(relations).saveAndFlush(any());
        doThrow(new DataIntegrityViolationException("secret")).when(relations).flush();
        assertEquals(409,assertThrows(ResponseStatusException.class, () -> service.delete(1L,2L)).getStatusCode().value());
    }
    @Test void unrelatedPersistenceFailuresPropagate() {
        var failure = new DataAccessResourceFailureException("connection");
        when(relations.saveAndFlush(any())).thenThrow(failure);
        assertSame(failure,assertThrows(DataAccessResourceFailureException.class, () -> service.create(1L,request)));
        var lock = new CannotAcquireLockException("lock");
        when(villages.findByIdForUpdate(1L)).thenThrow(lock);
        assertSame(lock,assertThrows(CannotAcquireLockException.class, () -> service.delete(1L,2L)));
    }
    @Test void transactionAnnotations() throws Exception {
        for (var method : VillageRelationService.class.getDeclaredMethods()) {
            if (!java.lang.reflect.Modifier.isPublic(method.getModifiers())) continue;
            var annotation = method.getAnnotation(org.springframework.transaction.annotation.Transactional.class);
            assertNotNull(annotation); assertEquals(method.getName().startsWith("get"), annotation.readOnly());
        }
    }

    @Test void allOperationsValidateVillageIdsBeforeRepositories() {
        assertThrows(ResponseStatusException.class, () -> service.getById(0L,2L));
        assertThrows(ResponseStatusException.class, () -> service.update(0L,2L,request));
        assertThrows(ResponseStatusException.class, () -> service.delete(0L,2L));
        verifyNoInteractions(villages,relations);
    }
    @Test void missingParentPreventsItemAccess() {
        assertThrows(ResourceNotFoundException.class, () -> service.getById(9L,2L));
        assertThrows(ResourceNotFoundException.class, () -> service.update(9L,2L,request));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(9L,2L));
        verifyNoInteractions(relations);
    }
    @Test void updateIntegrityFailureAndUnrelatedDeleteFailure() {
        when(relations.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("secret"));
        assertEquals(409,assertThrows(ResponseStatusException.class, () -> service.update(1L,2L,request)).getStatusCode().value());
        var failure = new DataAccessResourceFailureException("connection");
        doThrow(failure).when(relations).flush();
        assertSame(failure,assertThrows(DataAccessResourceFailureException.class, () -> service.delete(1L,2L)));
    }
    @Test void villageTargetArchivedCrossGovernorateAndTimestampPreserved() {
        var target = new Village(); target.setId(7L); target.setStatus(VillageStatus.VERIFIED);
        target.setArchivedAt(Instant.EPOCH); target.setUpdatedAt(Instant.EPOCH);
        when(villages.findById(7L)).thenReturn(Optional.of(target));
        var request = new VillageRelationRequest(7L,null,VillageRelationType.NEARBY,java.math.BigDecimal.ONE);
        assertEquals(7L,service.create(1L,request).relatedVillageId());
        assertEquals(Instant.EPOCH,target.getUpdatedAt());
        assertEquals(Instant.EPOCH,parent.getUpdatedAt());
        verify(villages,never()).findByIdForUpdate(7L);
        assertEquals(7L,service.update(1L,2L,request).relatedVillageId());
        var text = service.update(1L,2L,this.request);
        assertNull(text.relatedVillageId()); assertEquals(" Name ",text.relatedPlaceName()); assertNull(text.distanceKm());
        verify(relations).existsByVillageIdAndRelatedVillageIdAndRelationType(1L,7L,VillageRelationType.NEARBY);
        verify(relations).existsByVillageIdAndRelatedVillageIdAndRelationTypeAndIdNot(1L,7L,VillageRelationType.NEARBY,2L);
    }
    @Test void selfLinksAndMissingTargets() {
        var self = new VillageRelationRequest(1L,null,VillageRelationType.OTHER,null);
        assertEquals(400,assertThrows(ResponseStatusException.class,()->service.create(1L,self)).getStatusCode().value());
        assertEquals(400,assertThrows(ResponseStatusException.class,()->service.update(1L,2L,self)).getStatusCode().value());
        var missing = new VillageRelationRequest(7L,null,VillageRelationType.OTHER,null);
        assertThrows(ResourceNotFoundException.class,()->service.create(1L,missing));
        assertThrows(ResourceNotFoundException.class,()->service.update(1L,2L,missing));
        verify(relations,never()).saveAndFlush(any());
    }
    @Test void villageDuplicatesIgnoreDistanceAndExcludeCurrentId() {
        var target = new Village(); target.setId(7L);
        when(villages.findById(7L)).thenReturn(Optional.of(target));
        when(relations.existsByVillageIdAndRelatedVillageIdAndRelationType(1L,7L,VillageRelationType.OTHER)).thenReturn(true);
        when(relations.existsByVillageIdAndRelatedVillageIdAndRelationTypeAndIdNot(1L,7L,VillageRelationType.OTHER,2L)).thenReturn(true);
        var r = new VillageRelationRequest(7L,null,VillageRelationType.OTHER,java.math.BigDecimal.TEN);
        assertThrows(DuplicateResourceException.class,()->service.create(1L,r));
        assertThrows(DuplicateResourceException.class,()->service.update(1L,2L,r));
        verify(relations,never()).saveAndFlush(any());
    }
    @Test void missingChildPrecedesArchivedGuard() {
        parent.setArchivedAt(Instant.EPOCH);
        assertThrows(ResourceNotFoundException.class,()->service.update(1L,9L,request));
        assertThrows(ResourceNotFoundException.class,()->service.delete(1L,9L));
    }
}
