package com.qaryati.village.service;
import com.qaryati.common.exception.*;
import com.qaryati.village.dto.*;
import com.qaryati.village.entity.*;
import com.qaryati.village.mapper.VillageNameMapper;
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

class VillageNameServiceTest {
    VillageRepository villages; VillageNameRepository names; VillageNameService service;
    Village parent; VillageName child;
    final VillageNameRequest request = new VillageNameRequest(" Name ", " EN ", VillageNameType.FORMER, null);
    @BeforeEach void setup() {
        villages = mock(VillageRepository.class); names = mock(VillageNameRepository.class);
        service = new VillageNameService(villages, names, new VillageNameMapper());
        parent = new Village(); parent.setId(1L); parent.setStatus(VillageStatus.DRAFT); parent.setUpdatedAt(Instant.EPOCH);
        child = new VillageNameMapper().toEntity(request); child.setId(2L); child.setVillage(parent);
        when(villages.findById(1L)).thenReturn(Optional.of(parent));
        when(villages.findByIdForUpdate(1L)).thenReturn(Optional.of(parent));
        when(names.findByIdAndVillageId(2L, 1L)).thenReturn(Optional.of(child));
        when(names.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }
    @Test void createLocksBeforeDuplicateCheckAndPreservesParentTimestamp() {
        var result = service.create(1L, request);
        assertEquals(" Name ", result.name()); assertEquals(" EN ", result.languageCode());
        assertEquals(Instant.EPOCH, parent.getUpdatedAt());
        var order = inOrder(villages, names);
        order.verify(villages).findByIdForUpdate(1L);
        order.verify(names).existsByVillageIdAndNameAndLanguageCodeAndNameType(1L, " Name ", " EN ", VillageNameType.FORMER);
        order.verify(names).saveAndFlush(any());
        verify(villages, never()).save(any());
    }
    @Test void updateExcludesSelfClearsNotesAndScopesLookup() {
        child.setNotes("old");
        assertNull(service.update(1L, 2L, request).notes());
        var order = inOrder(villages, names);
        order.verify(villages).findByIdForUpdate(1L);
        order.verify(names).findByIdAndVillageId(2L, 1L);
        order.verify(names).existsByVillageIdAndNameAndLanguageCodeAndNameTypeAndIdNot(1L, " Name ", " EN ", VillageNameType.FORMER, 2L);
        order.verify(names).saveAndFlush(child);
        assertEquals(Instant.EPOCH, parent.getUpdatedAt());
    }
    @Test void deleteLocksScopesAndFlushesAndRepeatIs404() {
        service.delete(1L, 2L);
        var order = inOrder(villages, names);
        order.verify(villages).findByIdForUpdate(1L); order.verify(names).findByIdAndVillageId(2L, 1L);
        order.verify(names).delete(child); order.verify(names).flush();
        assertEquals(Instant.EPOCH, parent.getUpdatedAt());
        when(names.findByIdAndVillageId(2L, 1L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.delete(1L, 2L));
    }
    @Test void archivedReadsAndEmptyCollection() {
        parent.setArchivedAt(Instant.EPOCH); parent.setStatus(VillageStatus.VERIFIED);
        when(names.findByVillageIdOrderByIdAsc(1L)).thenReturn(List.of(child));
        assertEquals(2L, service.getAll(1L).getFirst().id());
        assertEquals(2L, service.getById(1L, 2L).id());
        when(names.findByVillageIdOrderByIdAsc(1L)).thenReturn(List.of()); assertTrue(service.getAll(1L).isEmpty());
        verify(villages, never()).findByIdForUpdate(any());
    }
    @ParameterizedTest @ValueSource(strings = {"create", "update", "delete"})
    void archivedMutationsRejected(String op) { parent.setArchivedAt(Instant.EPOCH); conflict(op); }
    @ParameterizedTest @EnumSource(value = VillageStatus.class, names = {"UNDER_REVIEW", "VERIFIED", "REJECTED"})
    void nonDraftMutationsRejected(VillageStatus status) { parent.setStatus(status); for (String op : List.of("create","update","delete")) conflict(op); }
    private void conflict(String op) {
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> mutate(op)).getStatusCode().value());
        verify(names, never()).saveAndFlush(any()); verify(names, never()).delete(any());
    }
    private void mutate(String op) { switch(op) { case "create" -> service.create(1L, request); case "update" -> service.update(1L,2L,request); default -> service.delete(1L,2L); } }
    @Test void duplicateCreateAndUpdateRejected() {
        when(names.existsByVillageIdAndNameAndLanguageCodeAndNameType(1L," Name "," EN ",VillageNameType.FORMER)).thenReturn(true);
        when(names.existsByVillageIdAndNameAndLanguageCodeAndNameTypeAndIdNot(1L," Name "," EN ",VillageNameType.FORMER,2L)).thenReturn(true);
        assertThrows(DuplicateResourceException.class, () -> service.create(1L,request));
        assertThrows(DuplicateResourceException.class, () -> service.update(1L,2L,request));
        verify(names, never()).saveAndFlush(any());
    }
    @ParameterizedTest @NullSource @ValueSource(longs = {0,-1})
    void invalidIdsRejected(Long id) {
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.getAll(id)).getStatusCode().value());
        assertThrows(ResponseStatusException.class, () -> service.create(id, request));
        assertThrows(ResponseStatusException.class, () -> service.getById(1L,id));
        assertThrows(ResponseStatusException.class, () -> service.update(1L,id,request));
        assertThrows(ResponseStatusException.class, () -> service.delete(1L,id));
        verifyNoInteractions(villages, names);
    }
    @Test void missingParentAndWrongVillageChildAre404() {
        assertThrows(ResourceNotFoundException.class, () -> service.getAll(9L));
        assertThrows(ResourceNotFoundException.class, () -> service.create(9L,request));
        assertThrows(ResourceNotFoundException.class, () -> service.getById(1L,9L));
        assertThrows(ResourceNotFoundException.class, () -> service.update(1L,9L,request));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(1L,9L));
        verify(names, never()).findById(any());
    }
    @Test void integrityConflictsSanitizedWithoutRetry() {
        when(names.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("secret SQL"));
        var ex = assertThrows(ResponseStatusException.class, () -> service.create(1L,request));
        assertEquals(409,ex.getStatusCode().value()); assertFalse(ex.getReason().contains("secret"));
        verify(names).saveAndFlush(any());
        doThrow(new DataIntegrityViolationException("secret")).when(names).flush();
        assertEquals(409,assertThrows(ResponseStatusException.class, () -> service.delete(1L,2L)).getStatusCode().value());
    }
    @Test void unrelatedPersistenceFailuresPropagate() {
        var failure = new DataAccessResourceFailureException("connection");
        when(names.saveAndFlush(any())).thenThrow(failure);
        assertSame(failure,assertThrows(DataAccessResourceFailureException.class, () -> service.create(1L,request)));
        var lock = new CannotAcquireLockException("lock");
        when(villages.findByIdForUpdate(1L)).thenThrow(lock);
        assertSame(lock,assertThrows(CannotAcquireLockException.class, () -> service.delete(1L,2L)));
    }
    @Test void transactionAnnotations() throws Exception {
        for (var method : VillageNameService.class.getDeclaredMethods()) {
            if (!java.lang.reflect.Modifier.isPublic(method.getModifiers())) continue;
            var annotation = method.getAnnotation(org.springframework.transaction.annotation.Transactional.class);
            assertNotNull(annotation); assertEquals(method.getName().startsWith("get"), annotation.readOnly());
        }
    }

    @Test void allOperationsValidateVillageIdsBeforeRepositories() {
        assertThrows(ResponseStatusException.class, () -> service.getById(0L,2L));
        assertThrows(ResponseStatusException.class, () -> service.update(0L,2L,request));
        assertThrows(ResponseStatusException.class, () -> service.delete(0L,2L));
        verifyNoInteractions(villages,names);
    }
    @Test void missingParentPreventsItemAccess() {
        assertThrows(ResourceNotFoundException.class, () -> service.getById(9L,2L));
        assertThrows(ResourceNotFoundException.class, () -> service.update(9L,2L,request));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(9L,2L));
        verifyNoInteractions(names);
    }
    @Test void updateIntegrityFailureAndUnrelatedDeleteFailure() {
        when(names.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("secret"));
        assertEquals(409,assertThrows(ResponseStatusException.class, () -> service.update(1L,2L,request)).getStatusCode().value());
        var failure = new DataAccessResourceFailureException("connection");
        doThrow(failure).when(names).flush();
        assertSame(failure,assertThrows(DataAccessResourceFailureException.class, () -> service.delete(1L,2L)));
    }
}