package com.qaryati.village.service;

import com.qaryati.common.exception.DuplicateResourceException;
import com.qaryati.common.exception.ResourceNotFoundException;
import com.qaryati.village.dto.LocalAuthorityRequest;
import com.qaryati.village.dto.LocalAuthorityResponse;
import com.qaryati.village.entity.Governorate;
import com.qaryati.village.entity.LocalAuthority;
import com.qaryati.village.entity.LocalAuthorityType;
import com.qaryati.village.mapper.LocalAuthorityMapper;
import com.qaryati.village.repository.GovernorateRepository;
import com.qaryati.village.repository.LocalAuthorityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocalAuthorityServiceTest {

    private LocalAuthorityRepository repository;
    private GovernorateRepository governorateRepository;
    private LocalAuthorityService service;
    private Governorate governorate;
    private LocalAuthority authority;

    @BeforeEach
    void setUp() {
        repository = mock(LocalAuthorityRepository.class);
        governorateRepository = mock(GovernorateRepository.class);
        service = new LocalAuthorityService(repository, governorateRepository, new LocalAuthorityMapper());
        governorate = new Governorate();
        governorate.setId(10L);
        authority = new LocalAuthority();
        authority.setId(1L);
        authority.setGovernorate(governorate);
        authority.setNameAr("Original");
        authority.setNameEn("Original English");
        authority.setAuthorityType(LocalAuthorityType.VILLAGE_COUNCIL);
    }

    @Test
    void createSavesWithResolvedGovernorateAndUnmodifiedNames() {
        LocalAuthorityRequest request = request(10L, "  Name  ", "  English  ");
        when(governorateRepository.findById(10L)).thenReturn(Optional.of(governorate));
        when(repository.saveAndFlush(any(LocalAuthority.class))).thenAnswer(invocation -> {
            LocalAuthority saved = invocation.getArgument(0);
            assertNull(saved.getId());
            assertSame(governorate, saved.getGovernorate());
            saved.setId(2L);
            return saved;
        });

        assertEquals(new LocalAuthorityResponse(2L, 10L, "  Name  ", "  English  ",
                LocalAuthorityType.MUNICIPALITY), service.create(request));

        verify(repository).existsByGovernorateAndNameAr(same(governorate), eq(request.nameAr()));
        verify(repository).saveAndFlush(any(LocalAuthority.class));
    }

    @Test
    void createRejectsMissingGovernorateWithoutSaving() {
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class,
                () -> service.create(request(99L, "New", null)));
        assertEquals("Governorate with id 99 not found", exception.getMessage());
        verify(governorateRepository).findById(99L);
        verifyNoInteractions(repository);
    }

    @Test
    void createRejectsDuplicateNameWithoutSaving() {
        when(governorateRepository.findById(10L)).thenReturn(Optional.of(governorate));
        when(repository.existsByGovernorateAndNameAr(governorate, "New")).thenReturn(true);

        DuplicateResourceException exception = assertThrows(DuplicateResourceException.class,
                () -> service.create(request(10L, "New", null)));

        assertEquals("Local authority Arabic name already exists in this governorate", exception.getMessage());
        assertNoSave();
        assertUnchanged();
    }

    @Test
    void createTranslatesSaveAndFlushIntegrityFailure() {
        when(governorateRepository.findById(10L)).thenReturn(Optional.of(governorate));
        when(repository.saveAndFlush(any(LocalAuthority.class)))
                .thenThrow(new DataIntegrityViolationException("private SQL details"));

        DuplicateResourceException exception = assertThrows(DuplicateResourceException.class,
                () -> service.create(request(10L, "New", null)));

        assertEquals("Local authority violates a database constraint", exception.getMessage());
        verify(repository).saveAndFlush(any(LocalAuthority.class));
        verifyNoMoreInteractionsAfterCreate();
    }

    @Test
    void getByIdReturnsMappedResponse() {
        when(repository.findById(1L)).thenReturn(Optional.of(authority));
        assertEquals(originalResponse(), service.getById(1L));
        verifyNoInteractions(governorateRepository);
    }

    @Test
    void getByIdRejectsMissingAuthority() {
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class,
                () -> service.getById(99L));
        assertEquals("LocalAuthority with id 99 not found", exception.getMessage());
        verify(repository).findById(99L);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void getAllMapsPopulatedResults() {
        LocalAuthority second = new LocalAuthority();
        second.setId(2L);
        second.setGovernorate(governorate);
        second.setNameAr("Second");
        second.setAuthorityType(LocalAuthorityType.OTHER);
        when(repository.findAll()).thenReturn(List.of(authority, second));

        assertEquals(List.of(originalResponse(),
                new LocalAuthorityResponse(2L, 10L, "Second", null, LocalAuthorityType.OTHER)),
                service.getAll());
        verifyNoInteractions(governorateRepository);
    }

    @Test
    void getAllReturnsEmptyList() {
        when(repository.findAll()).thenReturn(List.of());
        assertEquals(List.of(), service.getAll());
    }

    @Test
    void getByGovernorateResolvesBeforeQueryingAndMapsResults() {
        when(governorateRepository.findById(10L)).thenReturn(Optional.of(governorate));
        when(repository.findByGovernorate(governorate)).thenReturn(List.of(authority));

        assertEquals(List.of(originalResponse()), service.getByGovernorate(10L));

        InOrder order = inOrder(governorateRepository, repository);
        order.verify(governorateRepository).findById(10L);
        order.verify(repository).findByGovernorate(same(governorate));
    }

    @Test
    void getByGovernorateReturnsEmptyListForExistingGovernorate() {
        when(governorateRepository.findById(10L)).thenReturn(Optional.of(governorate));
        when(repository.findByGovernorate(governorate)).thenReturn(List.of());
        assertEquals(List.of(), service.getByGovernorate(10L));
    }

    @Test
    void getByGovernorateRejectsMissingGovernorateWithoutQueryingAuthorities() {
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class,
                () -> service.getByGovernorate(99L));
        assertEquals("Governorate with id 99 not found", exception.getMessage());
        verifyNoInteractions(repository);
    }

    @Test
    void updateReplacesScalarsAndClearsOptionalNamePreservingIdentityAndRelationship() {
        stubUpdate();
        // A separately resolved object with the same ID must not replace the relationship.
        Governorate resolved = new Governorate();
        resolved.setId(10L);
        when(governorateRepository.findById(10L)).thenReturn(Optional.of(resolved));
        when(repository.saveAndFlush(same(authority))).thenReturn(authority);

        assertEquals(new LocalAuthorityResponse(1L, 10L, "  Updated  ", null,
                LocalAuthorityType.MUNICIPALITY), service.update(1L, request(10L, "  Updated  ", null)));

        assertEquals(1L, authority.getId());
        assertSame(governorate, authority.getGovernorate());
        assertNull(authority.getNameEn());
        verify(repository).existsByGovernorateAndNameArAndIdNot(same(governorate), eq("  Updated  "), eq(1L));
        verify(repository).saveAndFlush(same(authority));
    }

    @Test
    void updateAllowsCurrentNameUsingIdExclusion() {
        stubUpdate();
        when(repository.saveAndFlush(same(authority))).thenReturn(authority);

        assertEquals("Original", service.update(1L, request(10L, "Original", "Updated English")).nameAr());

        verify(repository).existsByGovernorateAndNameArAndIdNot(governorate, "Original", 1L);
        verify(repository, never()).existsByGovernorateAndNameAr(any(), anyString());
        assertSame(governorate, authority.getGovernorate());
        assertEquals(1L, authority.getId());
    }

    @Test
    void updateRejectsDuplicateBeforeMutationOrSave() {
        stubUpdate();
        when(repository.existsByGovernorateAndNameArAndIdNot(governorate, "New", 1L)).thenReturn(true);

        DuplicateResourceException exception = assertThrows(DuplicateResourceException.class,
                () -> service.update(1L, request(10L, "New", null)));

        assertEquals("Local authority Arabic name already exists in this governorate", exception.getMessage());
        assertUnchanged();
        assertNoSave();
    }

    @Test
    void updateRejectsMissingAuthorityBeforeGovernorateLookup() {
        assertThrows(ResourceNotFoundException.class,
                () -> service.update(99L, request(10L, "New", null)));
        verify(repository).findById(99L);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(governorateRepository);
        assertUnchanged();
    }

    @Test
    void updateRejectsMissingGovernorateBeforeMutationOrSave() {
        when(repository.findById(1L)).thenReturn(Optional.of(authority));

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class,
                () -> service.update(1L, request(99L, "New", null)));

        assertEquals("Governorate with id 99 not found", exception.getMessage());
        assertUnchanged();
        verify(repository).findById(1L);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void updateRejectsGovernorateReassignmentBeforeUniquenessCheckOrMutation() {
        when(repository.findById(1L)).thenReturn(Optional.of(authority));
        Governorate other = new Governorate();
        other.setId(20L);
        when(governorateRepository.findById(20L)).thenReturn(Optional.of(other));

        DuplicateResourceException exception = assertThrows(DuplicateResourceException.class,
                () -> service.update(1L, request(20L, "New", null)));

        assertEquals("Changing a local authority's governorate is not supported", exception.getMessage());
        assertUnchanged();
        verify(repository).findById(1L);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void updateTranslatesSaveAndFlushIntegrityFailureWithoutRetry() {
        stubUpdate();
        when(repository.saveAndFlush(same(authority)))
                .thenThrow(new DataIntegrityViolationException("private constraint details"));

        DuplicateResourceException exception = assertThrows(DuplicateResourceException.class,
                () -> service.update(1L, request(10L, "New", null)));

        assertEquals("Local authority violates a database constraint", exception.getMessage());
        verify(repository).findById(1L);
        verify(repository).existsByGovernorateAndNameArAndIdNot(governorate, "New", 1L);
        verify(repository).saveAndFlush(same(authority));
        verifyNoMoreInteractions(repository);
    }

    @Test
    void deleteDeletesThenFlushes() {
        when(repository.findById(1L)).thenReturn(Optional.of(authority));

        service.delete(1L);

        InOrder order = inOrder(repository);
        order.verify(repository).findById(1L);
        order.verify(repository).delete(same(authority));
        order.verify(repository).flush();
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(governorateRepository);
        assertUnchanged();
    }

    @Test
    void deleteRejectsMissingAuthorityWithoutWriting() {
        assertThrows(ResourceNotFoundException.class, () -> service.delete(99L));
        verify(repository).findById(99L);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void deleteTranslatesIntegrityFailureFromDeleteWithoutFlushing() {
        when(repository.findById(1L)).thenReturn(Optional.of(authority));
        doThrow(new DataIntegrityViolationException("private FK details"))
                .when(repository).delete(same(authority));

        assertDeleteConflict();

        verify(repository).findById(1L);
        verify(repository).delete(same(authority));
        verifyNoMoreInteractions(repository);
        assertUnchanged();
    }

    @Test
    void deleteTranslatesIntegrityFailureFromFlushWithoutRetry() {
        when(repository.findById(1L)).thenReturn(Optional.of(authority));
        doThrow(new DataIntegrityViolationException("private FK details")).when(repository).flush();

        assertDeleteConflict();

        InOrder order = inOrder(repository);
        order.verify(repository).findById(1L);
        order.verify(repository).delete(same(authority));
        order.verify(repository).flush();
        verifyNoMoreInteractions(repository);
        assertUnchanged();
    }

    @Test
    void unexpectedWriteExceptionPropagatesUnchanged() {
        when(governorateRepository.findById(10L)).thenReturn(Optional.of(governorate));
        IllegalStateException failure = new IllegalStateException("unexpected");
        when(repository.saveAndFlush(any(LocalAuthority.class))).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> service.create(request(10L, "New", null))));
    }

    private LocalAuthorityRequest request(Long governorateId, String nameAr, String nameEn) {
        return new LocalAuthorityRequest(governorateId, nameAr, nameEn, LocalAuthorityType.MUNICIPALITY);
    }

    private LocalAuthorityResponse originalResponse() {
        return new LocalAuthorityResponse(1L, 10L, "Original", "Original English",
                LocalAuthorityType.VILLAGE_COUNCIL);
    }

    private void stubUpdate() {
        when(repository.findById(1L)).thenReturn(Optional.of(authority));
        when(governorateRepository.findById(10L)).thenReturn(Optional.of(governorate));
    }

    private void assertUnchanged() {
        assertEquals(originalResponse(), new LocalAuthorityMapper().toResponse(authority));
        assertSame(governorate, authority.getGovernorate());
    }

    private void assertNoSave() {
        verify(repository, never()).save(any());
        verify(repository, never()).saveAndFlush(any());
    }

    private void assertDeleteConflict() {
        DuplicateResourceException exception = assertThrows(DuplicateResourceException.class,
                () -> service.delete(1L));
        assertEquals("Cannot delete local authority because of existing references or a database constraint",
                exception.getMessage());
    }

    private void verifyNoMoreInteractionsAfterCreate() {
        verify(repository).existsByGovernorateAndNameAr(governorate, "New");
        verifyNoMoreInteractions(repository);
    }
}
