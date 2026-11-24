
package com.qaryati.agriculture.service;

import com.qaryati.agriculture.entity.AgricultureImportance;
import com.qaryati.agriculture.entity.VillageAgriculture;
import com.qaryati.agriculture.mapper.VillageAgricultureMapper;
import com.qaryati.agriculture.repository.AgriculturalProductRepository;
import com.qaryati.agriculture.repository.VillageAgricultureRepository;
import com.qaryati.agriculture.dto.VillageAgricultureRequest;
import com.qaryati.village.api.VillageLookupApi;
import com.qaryati.village.api.VillageLookupResult;
import com.qaryati.village.api.VillageLookupStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VillageAgricultureServiceTest {

    private VillageAgricultureRepository repository;
    private VillageAgricultureService service;
    private AgriculturalProductRepository productRepository;
    private VillageLookupApi villages;

    @BeforeEach
    void setUp() {

        repository = mock(VillageAgricultureRepository.class);

        productRepository = mock(AgriculturalProductRepository.class);
        villages = mock(VillageLookupApi.class);
        service = new VillageAgricultureService(
                repository,
                productRepository,
                new VillageAgricultureMapper(),
                villages
        );
    }

    @Test
    void shouldGetVillageAgricultureById() {

        VillageAgriculture relation = new VillageAgriculture(
                10L,
                5L,
                "Olive cultivation",
                AgricultureImportance.HIGH
        );

        when(repository.findById(1L))
                .thenReturn(Optional.of(relation));

        var result = service.getById(1L);

        assertEquals(10L, result.villageId());
        assertEquals(5L, result.productId());
        assertEquals(AgricultureImportance.HIGH,
                result.importance());
    }

    @Test
    void shouldReturnNotFoundWhenRelationDoesNotExist() {

        when(repository.findById(999L))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getById(999L)
        );

        assertEquals(404,
                exception.getStatusCode().value());
    }

    @Test
    void shouldGetRelationsByVillageId() {

        VillageAgriculture relation = new VillageAgriculture(
                10L,
                5L,
                "Olives",
                AgricultureImportance.MEDIUM
        );

        when(repository.findByVillageId(10L))
                .thenReturn(List.of(relation));

        var result = service.getByVillageId(10L);

        assertEquals(1, result.size());
        assertEquals(5L, result.get(0).productId());
    }

    @Test
    void shouldGetRelationsByProductId() {

        VillageAgriculture relation = new VillageAgriculture(
                10L,
                5L,
                "Olives",
                AgricultureImportance.HIGH
        );

        when(repository.findByProductId(5L))
                .thenReturn(List.of(relation));

        var result = service.getByProductId(5L);

        assertEquals(1, result.size());
        assertEquals(10L, result.get(0).villageId());
    }

    @Test
    void shouldUpdateDescriptionAndImportance() {

        VillageAgriculture relation = new VillageAgriculture(
                10L, 5L, "Old description",
                AgricultureImportance.LOW
        );

        when(repository.findById(1L))
                .thenReturn(Optional.of(relation));

        when(repository.saveAndFlush(any(VillageAgriculture.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var request = new com.qaryati.agriculture.dto.VillageAgricultureRequest(
                10L, 5L, "Updated description",
                AgricultureImportance.HIGH
        );

        var result = service.update(1L, request);

        assertEquals("Updated description", result.description());
        assertEquals(AgricultureImportance.HIGH, result.importance());

        verify(repository).saveAndFlush(relation);
    }

    @Test
    void shouldRejectChangingVillageOrProduct() {

        VillageAgriculture relation = new VillageAgriculture(
                10L, 5L, "Olives",
                AgricultureImportance.MEDIUM
        );

        when(repository.findById(1L))
                .thenReturn(Optional.of(relation));

        var request = new com.qaryati.agriculture.dto.VillageAgricultureRequest(
                99L, 5L, "Changed village",
                AgricultureImportance.HIGH
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.update(1L, request)
        );

        assertEquals(400, exception.getStatusCode().value());

        verify(repository, never())
                .save(any(VillageAgriculture.class));
    }

    @Test
    void shouldDeleteVillageAgriculture() {

        VillageAgriculture relation = new VillageAgriculture(
                10L,
                5L,
                "Olive cultivation",
                AgricultureImportance.HIGH
        );

        when(repository.findById(1L))
                .thenReturn(Optional.of(relation));

        service.delete(1L);

        verify(repository).delete(relation);
    }

    @Test
    void shouldReturnNotFoundWhenDeletingMissingRelation() {

        when(repository.findById(999L))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.delete(999L)
        );

        assertEquals(404, exception.getStatusCode().value());

        verify(repository, never())
                .delete(any(VillageAgriculture.class));
    }

    @Test
    void shouldCreateWhenVillageAndProductExist() {
        var request = new VillageAgricultureRequest(
                10L, 5L, "Olives", AgricultureImportance.HIGH
        );

        when(villages.findById(10L)).thenReturn(Optional.of(village(10L)));
        when(productRepository.existsById(5L)).thenReturn(true);
        when(repository.existsByVillageIdAndProductId(10L, 5L)).thenReturn(false);
        when(repository.saveAndFlush(any(VillageAgriculture.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(request);

        assertEquals(10L, response.villageId());
        assertEquals(5L, response.productId());
        assertEquals(AgricultureImportance.HIGH, response.importance());
        verify(repository).saveAndFlush(any(VillageAgriculture.class));
    }

    @Test
    void shouldRejectCreateWhenVillageDoesNotExist() {
        var request = new VillageAgricultureRequest(
                10L, 5L, "Olives", AgricultureImportance.HIGH
        );

        when(villages.findById(10L)).thenReturn(Optional.empty());

        var ex = assertThrows(ResponseStatusException.class, () -> service.create(request));

        assertEquals(404, ex.getStatusCode().value());
        verifyNoInteractions(productRepository, repository);
    }

    @Test
    void shouldRejectCreateWhenProductDoesNotExist() {
        var request = new VillageAgricultureRequest(
                10L, 5L, "Olives", AgricultureImportance.HIGH
        );

        when(villages.findById(10L)).thenReturn(Optional.of(village(10L)));
        when(productRepository.existsById(5L)).thenReturn(false);

        var ex = assertThrows(
                ResponseStatusException.class,
                () -> service.create(request)
        );

        assertEquals(404, ex.getStatusCode().value());
        verify(repository, never()).save(any());
    }

    @Test
    void shouldRejectDuplicateRelationship() {
        var request = new VillageAgricultureRequest(
                10L, 5L, "Olives", AgricultureImportance.HIGH
        );

        when(villages.findById(10L)).thenReturn(Optional.of(village(10L)));
        when(productRepository.existsById(5L)).thenReturn(true);
        when(repository.existsByVillageIdAndProductId(10L, 5L))
                .thenReturn(true);

        var ex = assertThrows(
                ResponseStatusException.class,
                () -> service.create(request)
        );

        assertEquals(409, ex.getStatusCode().value());
        verify(repository, never()).save(any());
    }

    @Test
    void shouldRejectInvalidVillageIdBeforeLookup() {
        var request = new VillageAgricultureRequest(
                -1L, 5L, "Olives", AgricultureImportance.HIGH
        );

        var ex = assertThrows(
                ResponseStatusException.class,
                () -> service.create(request)
        );

        assertEquals(400, ex.getStatusCode().value());
        verifyNoInteractions(villages, productRepository, repository);
    }

    @Test
    void shouldTranslateWriteConstraintFailureToConflict() {
        var request = new VillageAgricultureRequest(
                10L, 5L, "Olives", AgricultureImportance.HIGH
        );

        when(villages.findById(10L)).thenReturn(Optional.of(village(10L)));
        when(productRepository.existsById(5L)).thenReturn(true);
        when(repository.existsByVillageIdAndProductId(10L, 5L)).thenReturn(false);
        when(repository.saveAndFlush(any(VillageAgriculture.class)))
                .thenThrow(new DataIntegrityViolationException("constraint"));

        var ex = assertThrows(ResponseStatusException.class, () -> service.create(request));

        assertEquals(409, ex.getStatusCode().value());
    }

    private VillageLookupResult village(Long id) {
        return new VillageLookupResult(id, VillageLookupStatus.DRAFT, false);
    }

}
