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

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "EDITOR"})
    void villageFilteredPrivilegedReadsPreserveAllStatusesAndPageMetadata(String role) {
        role(role);
        var pageable = PageRequest.of(2, 2, Sort.by(Sort.Order.desc("referenceYear"), Sort.Order.asc("id")));
        var draft = readRecord(31L, 1L, 2021, VerificationStatus.DRAFT);
        var disputed = readRecord(32L, 1L, 2019, VerificationStatus.DISPUTED);
        when(repository.findByVillageId(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(draft, disputed), pageable, 9));

        var result = service.getAll(2, 2, 1L);

        assertEquals(2, result.page());
        assertEquals(2, result.size());
        assertEquals(9, result.totalElements());
        assertEquals(5, result.totalPages());
        assertEquals(2, result.content().size());
        assertReadRecord(result.content().get(0), 31L, 1L, 2021, VerificationStatus.DRAFT);
        assertReadRecord(result.content().get(1), 32L, 1L, 2019, VerificationStatus.DISPUTED);
        verify(repository).findByVillageId(1L, pageable);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(villages, sources, audits);
    }

    @Test void villageFilteredViewerReadsConstrainVillageAndVerifiedBeforeCounting() {
        role("VIEWER");
        var pageable = PageRequest.of(1, 2, Sort.by(Sort.Order.desc("referenceYear"), Sort.Order.asc("id")));
        var latest = readRecord(41L, 7L, 2022, VerificationStatus.VERIFIED);
        var earlier = readRecord(42L, 7L, 2020, VerificationStatus.VERIFIED);
        when(repository.findByVillageIdAndVerificationStatus(7L, VerificationStatus.VERIFIED, pageable))
                .thenReturn(new PageImpl<>(List.of(latest, earlier), pageable, 7));

        var result = service.getAll(1, 2, 7L);

        assertEquals(1, result.page());
        assertEquals(2, result.size());
        assertEquals(7, result.totalElements());
        assertEquals(4, result.totalPages());
        assertEquals(2, result.content().size());
        assertReadRecord(result.content().get(0), 41L, 7L, 2022, VerificationStatus.VERIFIED);
        assertReadRecord(result.content().get(1), 42L, 7L, 2020, VerificationStatus.VERIFIED);
        verify(repository).findByVillageIdAndVerificationStatus(7L, VerificationStatus.VERIFIED, pageable);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(villages, sources, audits);
    }

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "EDITOR", "VIEWER"})
    void absentVillageFilterPreservesTheTwoArgumentReadContract(String role) {
        role(role);
        var pageable = PageRequest.of(0, 3, Sort.by(Sort.Order.desc("referenceYear"), Sort.Order.asc("id")));
        var status = role.equals("VIEWER") ? VerificationStatus.VERIFIED : VerificationStatus.DRAFT;
        var first = readRecord(51L, 7L, 2021, status);
        var second = readRecord(52L, 8L, 2020, status);
        var page = new PageImpl<>(List.of(first, second), pageable, 8);
        if (role.equals("VIEWER")) {
            when(repository.findByVerificationStatus(VerificationStatus.VERIFIED, pageable)).thenReturn(page);
        } else {
            when(repository.findAll(pageable)).thenReturn(page);
        }

        var explicitNull = service.getAll(0, 3, null);
        var existingOverload = service.getAll(0, 3);

        assertEquals(explicitNull, existingOverload);
        assertEquals(0, explicitNull.page());
        assertEquals(3, explicitNull.size());
        assertEquals(8, explicitNull.totalElements());
        assertEquals(3, explicitNull.totalPages());
        assertEquals(2, explicitNull.content().size());
        assertReadRecord(explicitNull.content().get(0), 51L, 7L, 2021, status);
        assertReadRecord(explicitNull.content().get(1), 52L, 8L, 2020, status);
        if (role.equals("VIEWER")) {
            verify(repository, times(2)).findByVerificationStatus(VerificationStatus.VERIFIED, pageable);
        } else {
            verify(repository, times(2)).findAll(pageable);
        }
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(villages, sources, audits);
    }

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "EDITOR", "VIEWER"})
    void unknownPositiveVillageReturnsAnEmptyPageWithoutReferenceLookup(String role) {
        role(role);
        for (int size : List.of(1, 100)) {
            var pageable = PageRequest.of(3, size, Sort.by(Sort.Order.desc("referenceYear"), Sort.Order.asc("id")));
            var page = new PageImpl<PopulationRecord>(List.of(), pageable, 0);
            if (role.equals("VIEWER")) {
                when(repository.findByVillageIdAndVerificationStatus(Long.MAX_VALUE, VerificationStatus.VERIFIED, pageable))
                        .thenReturn(page);
            } else {
                when(repository.findByVillageId(Long.MAX_VALUE, pageable)).thenReturn(page);
            }

            var result = service.getAll(3, size, Long.MAX_VALUE);

            assertTrue(result.content().isEmpty());
            assertEquals(3, result.page());
            assertEquals(size, result.size());
            assertEquals(0, result.totalElements());
            assertEquals(0, result.totalPages());
            if (role.equals("VIEWER")) {
                verify(repository).findByVillageIdAndVerificationStatus(Long.MAX_VALUE, VerificationStatus.VERIFIED, pageable);
            } else {
                verify(repository).findByVillageId(Long.MAX_VALUE, pageable);
            }
        }
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(villages, sources, audits);
    }

    @ParameterizedTest @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
    void invalidVillageFiltersAreRejectedBeforeAnyRepositoryAccess(long villageId) {
        for (String role : List.of("ADMIN", "EDITOR", "VIEWER")) {
            role(role);
            var failure = assertThrows(ResponseStatusException.class, () -> service.getAll(0, 20, villageId));
            assertEquals(400, failure.getStatusCode().value());
        }
        verifyNoInteractions(repository, villages, sources, audits);
    }

    @Test void villageFilteredReadsRetainPaginationValidationBeforeQuerying() {
        role("VIEWER");
        for (int[] page : List.of(new int[]{-1, 20}, new int[]{Integer.MIN_VALUE, 20},
                new int[]{0, 0}, new int[]{0, -1}, new int[]{0, 101}, new int[]{0, Integer.MAX_VALUE})) {
            for (Long villageId : Arrays.asList(null, 7L)) {
                var failure = assertThrows(ResponseStatusException.class,
                        () -> service.getAll(page[0], page[1], villageId));
                assertEquals(400, failure.getStatusCode().value());
            }
        }
        verifyNoInteractions(repository, villages, sources, audits);
    }

    @Test void villageFilteredReadsAuthenticateBeforeValidatingOrQuerying() {
        for (var authentication : Arrays.asList(null,
                UsernamePasswordAuthenticationToken.unauthenticated("member", null),
                new AnonymousAuthenticationToken("test-key", "anonymous", List.of(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ANONYMOUS"))))) {
            SecurityContextHolder.getContext().setAuthentication(authentication);
            assertThrows(AuthenticationCredentialsNotFoundException.class, () -> service.getAll(0, 20, 7L));
            assertThrows(AuthenticationCredentialsNotFoundException.class, () -> service.getAll(-1, 0, 0L));
        }
        verifyNoInteractions(repository, villages, sources, audits);
    }

    @ParameterizedTest @ValueSource(strings = {"OTHER", "viewer", ""})
    void villageFilteredReadsRejectUnsupportedRolesBeforeValidatingOrQuerying(String role) {
        role(role);
        assertThrows(AccessDeniedException.class, () -> service.getAll(0, 20, 7L));
        assertThrows(AccessDeniedException.class, () -> service.getAll(-1, 0, 0L));
        verifyNoInteractions(repository, villages, sources, audits);
    }

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "EDITOR", "VIEWER"})
    void villageFilteredRepositoryFailuresEscapeWithoutUnfilteredFallback(String role) {
        role(role);
        var pageable = PageRequest.of(0, 20, Sort.by(Sort.Order.desc("referenceYear"), Sort.Order.asc("id")));
        var failure = new IllegalStateException("synthetic repository failure");
        if (role.equals("VIEWER")) {
            when(repository.findByVillageIdAndVerificationStatus(7L, VerificationStatus.VERIFIED, pageable))
                    .thenThrow(failure);
        } else {
            when(repository.findByVillageId(7L, pageable)).thenThrow(failure);
        }

        assertSame(failure, assertThrows(IllegalStateException.class, () -> service.getAll(0, 20, 7L)));

        if (role.equals("VIEWER")) {
            verify(repository).findByVillageIdAndVerificationStatus(7L, VerificationStatus.VERIFIED, pageable);
        } else {
            verify(repository).findByVillageId(7L, pageable);
        }
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(villages, sources, audits);
    }

    @Test void villageFilteredDerivedQueriesUseTheRequiredAndPredicates() throws Exception {
        var villageMethod = PopulationRecordRepository.class.getMethod("findByVillageId", Long.class, Pageable.class);
        var verifiedMethod = PopulationRecordRepository.class.getMethod("findByVillageIdAndVerificationStatus",
                Long.class, VerificationStatus.class, Pageable.class);
        for (var method : List.of(villageMethod, verifiedMethod)) {
            assertNull(method.getAnnotation(org.springframework.data.jpa.repository.Query.class));
            var tree = new org.springframework.data.repository.query.parser.PartTree(method.getName(), PopulationRecord.class);
            var alternatives = tree.stream().toList();
            assertEquals(1, alternatives.size());
            var parts = alternatives.getFirst().stream().toList();
            assertEquals(method.equals(villageMethod) ? Set.of("villageId") : Set.of("villageId", "verificationStatus"),
                    parts.stream().map(part -> part.getProperty().toDotPath()).collect(java.util.stream.Collectors.toSet()));
            assertEquals(method.equals(villageMethod) ? 1 : 2, parts.size());
            assertTrue(parts.stream().allMatch(part -> part.getType()
                    == org.springframework.data.repository.query.parser.Part.Type.SIMPLE_PROPERTY));
        }
        verifyNoInteractions(repository, villages, sources, audits);
    }

    private PopulationRecord readRecord(Long id, Long villageId, int year, VerificationStatus status) {
        var record = new PopulationRecord(villageId, year, java.time.LocalDate.of(year, 6, 15),
                345, Methodology.ESTIMATE, 42L, "synthetic observation");
        ReflectionTestUtils.setField(record, "id", id);
        ReflectionTestUtils.setField(record, "verificationStatus", status);
        ReflectionTestUtils.setField(record, "createdAt", java.time.Instant.parse("2025-01-01T00:00:00Z"));
        ReflectionTestUtils.setField(record, "updatedAt", java.time.Instant.parse("2025-02-01T00:00:00Z"));
        return record;
    }

    private void assertReadRecord(PopulationRecordResponse response, Long id, Long villageId,
            int year, VerificationStatus status) {
        assertEquals(id, response.id());
        assertEquals(villageId, response.villageId());
        assertEquals(year, response.referenceYear());
        assertEquals(java.time.LocalDate.of(year, 6, 15), response.referenceDate());
        assertEquals(345, response.populationCount());
        assertEquals(Methodology.ESTIMATE, response.methodology());
        assertEquals(42L, response.sourceId());
        assertEquals("synthetic observation", response.notes());
        assertEquals(status, response.verificationStatus());
        assertEquals(java.time.Instant.parse("2025-01-01T00:00:00Z"), response.createdAt());
        assertEquals(java.time.Instant.parse("2025-02-01T00:00:00Z"), response.updatedAt());
    }
}
