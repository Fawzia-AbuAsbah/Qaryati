
package com.qaryati.village.service;

import com.qaryati.common.exception.DuplicateResourceException;
import com.qaryati.village.dto.GovernorateRequest;
import com.qaryati.village.mapper.GovernorateMapper;
import com.qaryati.village.repository.GovernorateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.qaryati.village.entity.Governorate;
import com.qaryati.village.dto.GovernorateResponse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.Optional;
import java.util.List;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import com.qaryati.common.exception.ResourceNotFoundException;
import static org.mockito.Mockito.*;

class GovernorateServiceTest {

    private GovernorateRepository repository;
    private GovernorateService service;

    @BeforeEach
    void setUp() {
        repository = mock(GovernorateRepository.class);
        GovernorateMapper mapper = new GovernorateMapper();
        service = new GovernorateService(repository, mapper);
    }

    @Test
    void createRejectsDuplicateArabicName() {
        GovernorateRequest request = new GovernorateRequest(
                "نابلس", "Nablus", "NABLUS"
        );

        when(repository.existsByNameAr("نابلس"))
                .thenReturn(true);

        assertThrows(
                DuplicateResourceException.class,
                () -> service.create(request)
        );

        verify(repository, never()).saveAndFlush(any());
    }


    @Test
    void createSuccessfullySavesGovernorate() {
        GovernorateRequest request = new GovernorateRequest(
                "نابلس", "Nablus", "NABLUS"
        );

        when(repository.existsByNameAr("نابلس"))
                .thenReturn(false);

        when(repository.existsByCode("NABLUS"))
                .thenReturn(false);

        when(repository.saveAndFlush(any(Governorate.class)))
                .thenAnswer(invocation -> {
                    Governorate saved = invocation.getArgument(0);
                    saved.setId(1L);
                    return saved;
                });

        GovernorateResponse response = service.create(request);

        assertEquals(1L, response.id());
        assertEquals("نابلس", response.nameAr());
        assertEquals("Nablus", response.nameEn());
        assertEquals("NABLUS", response.code());

        verify(repository).saveAndFlush(any(Governorate.class));
    }

    @Test
    void getByIdReturnsExistingGovernorate() {
        Governorate governorate = new Governorate();
        governorate.setId(1L);
        governorate.setNameAr("نابلس");
        governorate.setNameEn("Nablus");
        governorate.setCode("NABLUS");

        when(repository.findById(1L))
                .thenReturn(Optional.of(governorate));

        GovernorateResponse response = service.getById(1L);

        assertEquals(1L, response.id());
        assertEquals("نابلس", response.nameAr());
        assertEquals("Nablus", response.nameEn());

        verify(repository).findById(1L);
    }

    @Test
    void getByIdThrowsExceptionWhenNotFound() {
        when(repository.findById(99L))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> service.getById(99L)
        );

        verify(repository).findById(99L);
    }



    @Test
    void createRejectsDuplicateCodeWithoutSaving() {
        GovernorateRequest request = new GovernorateRequest("نابلس", "Nablus", "NABLUS");
        when(repository.existsByCode(request.code())).thenReturn(true);

        DuplicateResourceException exception = assertThrows(
                DuplicateResourceException.class, () -> service.create(request));

        assertEquals("Governorate code already exists", exception.getMessage());
        verify(repository).existsByNameAr(request.nameAr());
        verify(repository, never()).saveAndFlush(any());
        verify(repository, never()).save(any());
    }

    @Test
    void updateSuccessfullyChangesAllFields() {
        Governorate governorate = existingGovernorate();
        GovernorateRequest request = new GovernorateRequest("جنين", "Jenin", "JENIN");
        when(repository.findById(1L)).thenReturn(Optional.of(governorate));
        when(repository.saveAndFlush(same(governorate))).thenReturn(governorate);

        GovernorateResponse response = service.update(1L, request);

        assertEquals(new GovernorateResponse(1L, "جنين", "Jenin", "JENIN"), response);
        assertEquals(request.nameAr(), governorate.getNameAr());
        assertEquals(request.nameEn(), governorate.getNameEn());
        assertEquals(request.code(), governorate.getCode());
        verify(repository).existsByNameArAndIdNot(request.nameAr(), 1L);
        verify(repository).existsByCodeAndIdNot(request.code(), 1L);
        verify(repository).saveAndFlush(same(governorate));
    }

    @Test
    void updateAllowsKeepingCurrentArabicNameAndCode() {
        Governorate governorate = existingGovernorate();
        GovernorateRequest request = new GovernorateRequest("نابلس", "Updated Nablus", "NABLUS");
        when(repository.findById(1L)).thenReturn(Optional.of(governorate));
        when(repository.saveAndFlush(same(governorate))).thenReturn(governorate);

        GovernorateResponse response = service.update(1L, request);

        assertEquals(new GovernorateResponse(1L, "نابلس", "Updated Nablus", "NABLUS"), response);
        verify(repository).existsByNameArAndIdNot("نابلس", 1L);
        verify(repository).existsByCodeAndIdNot("NABLUS", 1L);
        verify(repository, never()).existsByNameAr(anyString());
        verify(repository, never()).existsByCode(anyString());
        verify(repository).saveAndFlush(same(governorate));
    }

    @Test
    void updateRejectsAnotherGovernoratesArabicNameWithoutChangingEntity() {
        Governorate governorate = existingGovernorate();
        GovernorateRequest request = new GovernorateRequest("جنين", "Jenin", "JENIN");
        when(repository.findById(1L)).thenReturn(Optional.of(governorate));
        when(repository.existsByNameArAndIdNot(request.nameAr(), 1L)).thenReturn(true);

        DuplicateResourceException exception = assertThrows(
                DuplicateResourceException.class, () -> service.update(1L, request));

        assertEquals("Governorate Arabic name already exists", exception.getMessage());
        assertUnchanged(governorate);
        verify(repository, never()).saveAndFlush(any());
        verify(repository, never()).save(any());
    }

    @Test
    void updateRejectsAnotherGovernoratesCodeWithoutChangingEntity() {
        Governorate governorate = existingGovernorate();
        GovernorateRequest request = new GovernorateRequest("جنين", "Jenin", "JENIN");
        when(repository.findById(1L)).thenReturn(Optional.of(governorate));
        when(repository.existsByCodeAndIdNot(request.code(), 1L)).thenReturn(true);

        DuplicateResourceException exception = assertThrows(
                DuplicateResourceException.class, () -> service.update(1L, request));

        assertEquals("Governorate code already exists", exception.getMessage());
        verify(repository).existsByNameArAndIdNot(request.nameAr(), 1L);
        assertUnchanged(governorate);
        verify(repository, never()).saveAndFlush(any());
        verify(repository, never()).save(any());
    }

    @Test
    void updateThrowsResourceNotFoundForNonexistentGovernorate() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        GovernorateRequest request = new GovernorateRequest("نابلس", "Nablus", "NABLUS");

        assertThrows(ResourceNotFoundException.class, () -> service.update(99L, request));

        verify(repository).findById(99L);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void getAllReturnsCorrectlyMappedResponsesInRepositoryOrder() {
        Governorate first = existingGovernorate();
        Governorate second = new Governorate();
        second.setId(2L);
        second.setNameAr("جنين");
        when(repository.findAll()).thenReturn(List.of(first, second));

        List<GovernorateResponse> responses = service.getAll();

        assertEquals(List.of(
                new GovernorateResponse(1L, "نابلس", "Nablus", "NABLUS"),
                new GovernorateResponse(2L, "جنين", null, null)
        ), responses);
        verify(repository).findAll();
        verifyNoMoreInteractions(repository);
    }

    @Test
    void getAllReturnsEmptyListWhenNoGovernoratesExist() {
        when(repository.findAll()).thenReturn(List.of());

        assertEquals(List.of(), service.getAll());

        verify(repository).findAll();
    }

    @Test
    void deleteExistingGovernorateDeletesThenFlushes() {
        Governorate governorate = existingGovernorate();
        when(repository.findById(1L)).thenReturn(Optional.of(governorate));

        service.delete(1L);

        InOrder inOrder = inOrder(repository);
        inOrder.verify(repository).findById(1L);
        inOrder.verify(repository).delete(same(governorate));
        inOrder.verify(repository).flush();
        verifyNoMoreInteractions(repository);
    }

    @Test
    void deleteThrowsResourceNotFoundForNonexistentGovernorate() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.delete(99L));

        verify(repository).findById(99L);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void createTranslatesDatabaseConstraintViolationToDuplicateResource() {
        GovernorateRequest request = new GovernorateRequest("نابلس", "Nablus", "NABLUS");
        when(repository.saveAndFlush(any(Governorate.class)))
                .thenThrow(new DataIntegrityViolationException("unique constraint"));

        DuplicateResourceException exception = assertThrows(
                DuplicateResourceException.class, () -> service.create(request));

        assertEquals("Governorate violates a database constraint", exception.getMessage());
        verify(repository).existsByNameAr(request.nameAr());
        verify(repository).existsByCode(request.code());
        verify(repository).saveAndFlush(any(Governorate.class));
    }

    @Test
    void updateTranslatesDatabaseConstraintViolationToDuplicateResource() {
        Governorate governorate = existingGovernorate();
        GovernorateRequest request = new GovernorateRequest("جنين", "Jenin", "JENIN");
        when(repository.findById(1L)).thenReturn(Optional.of(governorate));
        when(repository.saveAndFlush(same(governorate)))
                .thenThrow(new DataIntegrityViolationException("unique constraint"));

        DuplicateResourceException exception = assertThrows(
                DuplicateResourceException.class, () -> service.update(1L, request));

        assertEquals("Governorate violates a database constraint", exception.getMessage());
        verify(repository).existsByNameArAndIdNot(request.nameAr(), 1L);
        verify(repository).existsByCodeAndIdNot(request.code(), 1L);
        verify(repository).saveAndFlush(same(governorate));
    }

    @Test
    void deleteTranslatesReferenceConstraintViolationFromFlush() {
        Governorate governorate = existingGovernorate();
        when(repository.findById(1L)).thenReturn(Optional.of(governorate));
        doThrow(new DataIntegrityViolationException("foreign key constraint"))
                .when(repository).flush();

        DuplicateResourceException exception = assertThrows(
                DuplicateResourceException.class, () -> service.delete(1L));

        assertEquals("Cannot delete a governorate referenced by other records", exception.getMessage());
        InOrder inOrder = inOrder(repository);
        inOrder.verify(repository).delete(same(governorate));
        inOrder.verify(repository).flush();
    }

    @Test
    void deleteTranslatesReferenceConstraintViolationFromDelete() {
        Governorate governorate = existingGovernorate();
        when(repository.findById(1L)).thenReturn(Optional.of(governorate));
        doThrow(new DataIntegrityViolationException("foreign key constraint"))
                .when(repository).delete(same(governorate));

        DuplicateResourceException exception = assertThrows(
                DuplicateResourceException.class, () -> service.delete(1L));

        assertEquals("Cannot delete a governorate referenced by other records", exception.getMessage());
        verify(repository).delete(same(governorate));
        verify(repository, never()).flush();
    }

    private Governorate existingGovernorate() {
        Governorate governorate = new Governorate();
        governorate.setId(1L);
        governorate.setNameAr("نابلس");
        governorate.setNameEn("Nablus");
        governorate.setCode("NABLUS");
        return governorate;
    }

    private void assertUnchanged(Governorate governorate) {
        assertEquals(1L, governorate.getId());
        assertEquals("نابلس", governorate.getNameAr());
        assertEquals("Nablus", governorate.getNameEn());
        assertEquals("NABLUS", governorate.getCode());
    }
}
