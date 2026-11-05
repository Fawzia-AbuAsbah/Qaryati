package com.qaryati.population.service;

import com.qaryati.population.dto.*;
import com.qaryati.population.entity.*;
import com.qaryati.population.mapper.PopulationRecordMapper;
import com.qaryati.population.repository.PopulationRecordRepository;
import com.qaryati.dataquality.api.*;
import com.qaryati.village.api.*;
import com.qaryati.common.exception.DuplicateResourceException;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.*;
import org.springframework.security.authentication.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PopulationRecordServiceTest {
    private final PopulationRecordRepository repository = mock(PopulationRecordRepository.class);
    private final VillageLookupApi villages = mock(VillageLookupApi.class);
    private final SourceLookup sources = mock(SourceLookup.class);
    private final AuditWriter audits = mock(AuditWriter.class);
    private final jakarta.validation.ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    private final PopulationRecordService service = new PopulationRecordService(repository,
            new PopulationRecordMapper(), villages, sources, audits, factory.getValidator());
    private final PopulationRecordRequest request = new PopulationRecordRequest(1L, 2020, null, 100, Methodology.CENSUS, 2L, null);

    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); factory.close(); }
    private void role(String role) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "member", null, List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role))));
    }
    private PopulationRecord existing() {
        var record = new PopulationRecord(1L, 2019, null, 90, Methodology.CENSUS, 2L, null);
        ReflectionTestUtils.setField(record, "id", 3L);
        when(repository.findByIdForUpdate(3L)).thenReturn(Optional.of(record));
        return record;
    }
    private void eligible() {
        when(villages.findById(1L)).thenReturn(Optional.of(new VillageLookupResult(1L, VillageLookupStatus.VERIFIED, false)));
        when(sources.existsById(2L)).thenReturn(true);
        when(repository.saveAndFlush(any())).thenAnswer(call -> {
            PopulationRecord r = call.getArgument(0); ReflectionTestUtils.setField(r, "id", 3L); return r;
        });
    }
    @ParameterizedTest @ValueSource(strings = {"ADMIN", "EDITOR"})
    void privilegedCrudAndImmutableAuditSnapshots(String role) {
        role(role); eligible();
        assertEquals(VerificationStatus.DRAFT, service.create(request).verificationStatus());
        verify(audits).record("POPULATION_RECORD", 3L, AuditAction.CREATE, null, new AuditSnapshot(1L, 2L, 2020, 100));
        existing();
        assertEquals(2020, service.update(3L, request).referenceYear());
        verify(audits).record("POPULATION_RECORD", 3L, AuditAction.UPDATE,
                new AuditSnapshot(1L, 2L, 2019, 90), new AuditSnapshot(1L, 2L, 2020, 100));
        service.delete(3L);
        verify(audits).record("POPULATION_RECORD", 3L, AuditAction.DELETE, new AuditSnapshot(1L, 2L, 2020, 100), null);
    }
    @Test void allOperationsRequireAuthentication() {
        for (org.junit.jupiter.api.function.Executable op : operations())
            assertThrows(AuthenticationCredentialsNotFoundException.class, op);
        verifyNoInteractions(repository, villages, sources, audits);
    }
    private List<org.junit.jupiter.api.function.Executable> operations() {
        return List.of(() -> service.create(request), () -> service.update(3L, request),
                () -> service.delete(3L), () -> service.getById(3L), () -> service.getAll(0, 20));
    }
    @Test void viewerAndUnknownRolesCannotMutate() {
        role("VIEWER");
        for (var op : operations().subList(0, 3)) assertThrows(AccessDeniedException.class, op);
        role("OTHER"); for (var op : operations()) assertThrows(AccessDeniedException.class, op);
        verifyNoInteractions(repository);
    }
    @Test void viewerQueriesFilterBeforeCountingAndDetailIsHidden() {
        role("VIEWER");
        var pageable = PageRequest.of(1, 20, Sort.by(Sort.Order.desc("referenceYear"), Sort.Order.asc("id")));
        when(repository.findByVerificationStatus(VerificationStatus.VERIFIED, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 20));
        assertEquals(20, service.getAll(1, 20).totalElements());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.getById(3L)).getStatusCode().value());
        verify(repository).findByIdAndVerificationStatus(3L, VerificationStatus.VERIFIED);
        verify(repository, never()).findById(any()); verify(repository, never()).findAll(any(Pageable.class));
    }
    @ParameterizedTest @ValueSource(strings = {"ADMIN", "EDITOR"})
    void privilegedReadsIncludeDraftsWithoutVillageLookup(String role) {
        role(role); var record = existing(); when(repository.findById(3L)).thenReturn(Optional.of(record));
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(record)));
        assertEquals(VerificationStatus.DRAFT, service.getById(3L).verificationStatus());
        assertEquals(1, service.getAll(0, 20).totalElements()); verifyNoInteractions(villages, sources);
    }
    @Test void villageEligibilityAndMissingReferences() {
        role("ADMIN");
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.create(request)).getStatusCode().value());
        for (var status : VillageLookupStatus.values()) for (boolean archived : List.of(false, true)) {
            when(villages.findById(1L)).thenReturn(Optional.of(new VillageLookupResult(1L, status, archived)));
            if (status != VillageLookupStatus.VERIFIED || archived)
                assertThrows(DuplicateResourceException.class, () -> service.create(request));
        }
        when(villages.findById(1L)).thenReturn(Optional.of(new VillageLookupResult(1L, VillageLookupStatus.VERIFIED, false)));
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.create(request)).getStatusCode().value());
        verifyNoInteractions(audits);
    }
    @Test void immutableVillageAndNonDraftGuards() {
        role("EDITOR"); var record = existing();
        var moved = new PopulationRecordRequest(9L, 2020, null, 100, Methodology.CENSUS, 2L, null);
        assertThrows(DuplicateResourceException.class, () -> service.update(3L, moved));
        for (var status : VerificationStatus.values()) if (status != VerificationStatus.DRAFT) {
            ReflectionTestUtils.setField(record, "verificationStatus", status);
            assertThrows(DuplicateResourceException.class, () -> service.update(3L, request));
            assertThrows(DuplicateResourceException.class, () -> service.delete(3L));
        }
        verifyNoInteractions(villages, sources, audits);
    }
    @Test void deletionDoesNotConsultParents() {
        role("ADMIN"); existing(); service.delete(3L); verifyNoInteractions(villages, sources);
    }
    @Test void validatesIdsPagesAndServiceRequests() {
        role("EDITOR");
        for (Long id : Arrays.asList(null, 0L, -1L)) {
            assertThrows(ResponseStatusException.class, () -> service.getById(id));
            assertThrows(ResponseStatusException.class, () -> service.delete(id));
            assertThrows(ResponseStatusException.class, () -> service.update(id, request));
        }
        for (int[] page : List.of(new int[]{-1,20}, new int[]{0,0}, new int[]{0,101}))
            assertThrows(ResponseStatusException.class, () -> service.getAll(page[0], page[1]));
        assertThrows(ResponseStatusException.class, () -> service.create(null));
        assertThrows(ResponseStatusException.class, () -> service.create(new PopulationRecordRequest(0L, 2020, null, 1, Methodology.CENSUS, 2L, null)));
        verifyNoInteractions(repository);
    }
    @Test void duplicatePrechecksDoNotWrite() {
        role("ADMIN"); eligible(); existing();
        when(repository.existsByVillageIdAndReferenceYearAndMethodologyAndSourceId(1L,2020,Methodology.CENSUS,2L)).thenReturn(true);
        when(repository.existsByVillageIdAndReferenceYearAndMethodologyAndSourceIdAndIdNot(1L,2020,Methodology.CENSUS,2L,3L)).thenReturn(true);
        assertThrows(DuplicateResourceException.class, () -> service.create(request));
        assertThrows(DuplicateResourceException.class, () -> service.update(3L,request));
        verify(repository, never()).saveAndFlush(any()); verifyNoInteractions(audits);
    }
    @Test void auditFailuresEscapeEveryMutation() {
        role("ADMIN"); eligible(); existing();
        var failure = new IllegalStateException("audit infrastructure");
        doThrow(failure).when(audits).record(anyString(), anyLong(), any(), nullable(AuditSnapshot.class), nullable(AuditSnapshot.class));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> service.create(request)));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> service.update(3L, request)));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> service.delete(3L)));
    }
    @Test void onlyExactUniqueConstraintIsTranslated() {
        role("ADMIN"); eligible();
        for (String constraint : List.of("uq_population_observation", "other_constraint")) {
            var cause = new org.hibernate.exception.ConstraintViolationException("private detail",
                    new java.sql.SQLException("secret", "23505"), constraint);
            var failure = new org.springframework.dao.DataIntegrityViolationException("private", cause);
            doThrow(failure).when(repository).saveAndFlush(any());
            if (constraint.equals("uq_population_observation")) assertThrows(DuplicateResourceException.class, () -> service.create(request));
            else assertSame(failure, assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> service.create(request)));
        }
    }
}
