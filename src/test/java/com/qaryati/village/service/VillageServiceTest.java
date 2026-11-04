package com.qaryati.village.service;

import com.qaryati.common.exception.DuplicateResourceException;
import com.qaryati.common.exception.ResourceNotFoundException;
import com.qaryati.village.dto.*;
import com.qaryati.village.entity.*;
import com.qaryati.village.mapper.VillageMapper;
import com.qaryati.village.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.data.domain.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VillageServiceTest {
    private VillageRepository repository;
    private GovernorateRepository governorates;
    private LocalAuthorityRepository authorities;
    private Clock clock;
    private VillageService service;
    private final VillageMapper mapper = new VillageMapper();
    private Governorate governorate;
    private LocalAuthority authority;
    private Village village;
    private static final Instant NOW = Instant.parse("2026-11-04T12:00:00.123456789Z");
    private static final Instant MICROS = NOW.truncatedTo(ChronoUnit.MICROS);
    private static final Instant OLD = Instant.parse("2026-01-01T00:00:00Z");

    @BeforeEach
    void setUp() {
        repository = mock(VillageRepository.class);
        governorates = mock(GovernorateRepository.class);
        authorities = mock(LocalAuthorityRepository.class);
        clock = mock(Clock.class);
        when(clock.instant()).thenReturn(NOW);
        service = new VillageService(repository, governorates, authorities, mapper, clock);
        governorate = governorate(10L);
        authority = authority(20L, governorate);
        village = new Village();
        village.setId(1L);
        village.setGovernorate(governorate);
        village.setLocalAuthority(authority);
        village.setStatus(VillageStatus.DRAFT);
        village.setCreatedAt(OLD);
        village.setUpdatedAt(OLD);
        mapper.updateEntity(request(10L, 20L), village);
    }

    private Governorate governorate(Long id) {
        var value = new Governorate();
        value.setId(id);
        return value;
    }

    private LocalAuthority authority(Long id, Governorate parent) {
        var value = new LocalAuthority();
        value.setId(id);
        value.setGovernorate(parent);
        return value;
    }

    private VillageRequest request(Long governorateId, Long authorityId) {
        return new VillageRequest(governorateId, authorityId, "  Arabic  ", "  English  ",
                "Origin", "Location", new BigDecimal("32.123456"), new BigDecimal("35.654321"),
                new BigDecimal("-430.25"), new BigDecimal("12.345"));
    }

    private void parents() {
        when(governorates.findById(10L)).thenReturn(Optional.of(governorate));
        when(authorities.findById(20L)).thenReturn(Optional.of(authority));
    }

    private void locked() {
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(village));
    }

    private void saving() {
        when(repository.saveAndFlush(any(Village.class))).thenAnswer(call -> call.getArgument(0));
    }

    private ResponseStatusException status(int expected, org.junit.jupiter.api.function.Executable action) {
        var failure = assertThrows(ResponseStatusException.class, action);
        assertEquals(expected, failure.getStatusCode().value());
        return failure;
    }

    private void unchanged(VillageResponse before) {
        assertEquals(before, mapper.toResponse(village));
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(clock);
    }

    // Applies to every test, including rejection paths and archive operations.
    @AfterEach
    void neverUsesPhysicalDeleteOrUnboundedListing() {
        var forbidden = mockingDetails(repository).getInvocations().stream()
                .map(call -> call.getMethod().getName())
                .filter(name -> name.startsWith("delete") || name.equals("findAll")).toList();
        assertTrue(forbidden.isEmpty(), forbidden.toString());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void createsWithResolvedParentsAndExactEditableValues(boolean withAuthority) {
        parents();
        var request = request(10L, withAuthority ? 20L : null);
        when(repository.saveAndFlush(any(Village.class))).thenAnswer(call -> {
            Village created = call.getArgument(0);
            assertNull(created.getId());
            assertSame(governorate, created.getGovernorate());
            assertSame(withAuthority ? authority : null, created.getLocalAuthority());
            assertEquals(VillageStatus.DRAFT, created.getStatus());
            assertNull(created.getArchivedAt());
            assertEquals(MICROS, created.getCreatedAt());
            assertEquals(MICROS, created.getUpdatedAt());
            assertEditable(request, created);
            created.setId(77L);
            return created;
        });
        var response = service.create(request);
        assertEquals(77L, response.id());
        assertEquals(10L, response.governorateId());
        assertEquals(withAuthority ? 20L : null, response.localAuthorityId());
        assertEquals("  Arabic  ", response.nameAr());
        assertEquals("  English  ", response.nameEn());
        var order = inOrder(governorates, authorities, repository, clock);
        order.verify(governorates).findById(10L);
        if (withAuthority) order.verify(authorities).findById(20L);
        order.verify(repository).existsByGovernorateAndNameAr(governorate, request.nameAr());
        order.verify(clock).instant();
        order.verify(repository).saveAndFlush(any());
        verifyNoMoreInteractions(repository, clock);
        if (!withAuthority) verifyNoInteractions(authorities);
    }

    @ParameterizedTest
    @ValueSource(strings = {"governorate", "authority", "mismatch", "duplicate"})
    void createBusinessFailuresDoNotPersist(String condition) {
        var request = request(10L, 20L);
        if (!condition.equals("governorate")) parents();
        if (condition.equals("authority")) when(authorities.findById(20L)).thenReturn(Optional.empty());
        if (condition.equals("mismatch")) authority.setGovernorate(governorate(99L));
        if (condition.equals("duplicate"))
            when(repository.existsByGovernorateAndNameAr(governorate, request.nameAr())).thenReturn(true);
        switch (condition) {
            case "governorate" -> {
                assertEquals("Governorate with id 10 not found",
                        assertThrows(ResourceNotFoundException.class, () -> service.create(request)).getMessage());
                verifyNoInteractions(authorities, repository);
            }
            case "authority" -> {
                assertEquals("LocalAuthority with id 20 not found",
                        assertThrows(ResourceNotFoundException.class, () -> service.create(request)).getMessage());
                verifyNoInteractions(repository);
            }
            case "mismatch" -> {
                status(400, () -> service.create(request));
                verifyNoInteractions(repository);
            }
            default -> {
                assertThrows(DuplicateResourceException.class, () -> service.create(request));
                verify(repository).existsByGovernorateAndNameAr(governorate, request.nameAr());
                verifyNoMoreInteractions(repository);
            }
        }
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(clock);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void readsActiveOrArchivedWithoutLockOrWrite(boolean archived) {
        if (archived) village.setArchivedAt(OLD);
        when(repository.findById(1L)).thenReturn(Optional.of(village));
        assertEquals(mapper.toResponse(village), service.getById(1L));
        verify(repository).findById(1L);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(governorates, authorities, clock);
    }

    @Test
    void missingReadIsNotFound() {
        assertEquals("Village with id 1 not found",
                assertThrows(ResourceNotFoundException.class, () -> service.getById(1L)).getMessage());
        verify(repository).findById(1L);
        verifyNoMoreInteractions(repository);
    }

    static Stream<Object[]> invalidIds() {
        return Stream.of("read", "update", "archive").flatMap(operation ->
                Stream.of(null, 0L, -1L).map(id -> new Object[]{operation, id}));
    }

    @ParameterizedTest
    @MethodSource("invalidIds")
    void invalidIdsAreRejectedBeforeRepositoryAccess(String operation, Long id) {
        status(400, () -> {
            switch (operation) {
                case "read" -> service.getById(id);
                case "update" -> service.update(id, request(10L, 20L));
                default -> service.archive(id);
            }
        });
        verifyNoInteractions(repository, governorates, authorities, clock);
    }

    @ParameterizedTest
    @CsvSource({"0,1", "0,100", "2,20", "2147483647,1"})
    void activePageIsBoundedUnsortedAndPreservesMetadata(int page, int size) {
        var pageable = PageRequest.of(page, size);
        var source = new PageImpl<>(List.of(village), pageable, pageable.getOffset() + size + 7);
        when(repository.findByArchivedAtIsNullOrderByIdAsc(any())).thenReturn(source);
        var result = service.getActive(page, size);
        assertEquals(List.of(mapper.toResponse(village)), result.getContent());
        assertEquals(source.getNumber(), result.getNumber());
        assertEquals(source.getSize(), result.getSize());
        assertEquals(source.getTotalElements(), result.getTotalElements());
        assertEquals(source.getTotalPages(), result.getTotalPages());
        verify(repository).findByArchivedAtIsNullOrderByIdAsc(argThat(value ->
                value.isPaged() && value.getPageNumber() == page && value.getPageSize() == size
                        && value.getSort().isUnsorted()));
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(governorates, authorities, clock);
    }

    @Test
    void emptyPageRemainsEmptyWithOriginalTotal() {
        var source = new PageImpl<Village>(List.of(), PageRequest.of(3, 20), 2);
        when(repository.findByArchivedAtIsNullOrderByIdAsc(any())).thenReturn(source);
        var result = service.getActive(3, 20);
        assertTrue(result.isEmpty());
        assertEquals(2, result.getTotalElements());
        assertEquals(3, result.getNumber());
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,-1", "0,101", "2147483647,2", "2147483647,100"})
    void invalidPaginationDoesNotAccessRepository(int page, int size) {
        status(400, () -> service.getActive(page, size));
        verifyNoInteractions(repository, governorates, authorities, clock);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "archived", "UNDER_REVIEW", "VERIFIED", "REJECTED"})
    void updateLifecycleGuardsRunBeforeParentResolution(String condition) {
        var before = mapper.toResponse(village);
        if (!condition.equals("missing")) locked();
        if (condition.equals("archived")) village.setArchivedAt(OLD);
        else if (!condition.equals("missing")) village.setStatus(VillageStatus.valueOf(condition));
        before = mapper.toResponse(village);
        if (condition.equals("missing"))
            assertThrows(ResourceNotFoundException.class, () -> service.update(1L, request(10L, 20L)));
        else status(409, () -> service.update(1L, request(10L, 20L)));
        unchanged(before);
        verify(repository).findByIdForUpdate(1L);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(governorates, authorities);
    }

    @ParameterizedTest
    @ValueSource(strings = {"governorate", "reassignment", "authority", "mismatch", "duplicate"})
    void updateBusinessFailuresDoNotMutateOrSave(String condition) {
        locked();
        parents();
        var before = mapper.toResponse(village);
        Long requestedGovernorate = 10L;
        if (condition.equals("governorate")) when(governorates.findById(10L)).thenReturn(Optional.empty());
        if (condition.equals("reassignment")) {
            requestedGovernorate = 99L;
            when(governorates.findById(99L)).thenReturn(Optional.of(governorate(99L)));
        }
        if (condition.equals("authority")) when(authorities.findById(20L)).thenReturn(Optional.empty());
        if (condition.equals("mismatch")) {
            // Return a different authority; do not mutate the Village's existing authority.
            when(authorities.findById(20L)).thenReturn(Optional.of(authority(20L, governorate(99L))));
        }
        var request = request(requestedGovernorate, 20L);
        if (condition.equals("duplicate"))
            when(repository.existsByGovernorateAndNameArAndIdNot(governorate, request.nameAr(), 1L))
                    .thenReturn(true);
        switch (condition) {
            case "governorate", "authority" ->
                    assertThrows(ResourceNotFoundException.class, () -> service.update(1L, request));
            case "reassignment" -> status(409, () -> service.update(1L, request));
            case "mismatch" -> status(400, () -> service.update(1L, request));
            default -> assertThrows(DuplicateResourceException.class, () -> service.update(1L, request));
        }
        unchanged(before);
        var order = inOrder(repository, governorates, authorities);
        order.verify(repository).findByIdForUpdate(1L);
        order.verify(governorates).findById(requestedGovernorate);
        if (condition.equals("governorate") || condition.equals("reassignment")) {
            verifyNoInteractions(authorities);
        } else {
            order.verify(authorities).findById(20L);
        }
        if (condition.equals("duplicate"))
            order.verify(repository).existsByGovernorateAndNameArAndIdNot(governorate, request.nameAr(), 1L);
        verifyNoMoreInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"assign", "change", "clear", "retain"})
    void updateReplacesScalarsPreservesLifecycleAndResolvesAuthority(String operation) {
        locked();
        parents();
        saving();
        if (operation.equals("assign")) village.setLocalAuthority(null);
        Long authorityId = operation.equals("clear") ? null : operation.equals("change") ? 21L : 20L;
        var replacement = authority(21L, governorate);
        when(authorities.findById(21L)).thenReturn(Optional.of(replacement));
        var request = new VillageRequest(10L, authorityId, "Replacement", null, null, "New place",
                null, null, null, null);
        var before = mapper.toResponse(village);
        var result = service.update(1L, request);
        assertEditable(request, village);
        assertSame(governorate, village.getGovernorate());
        assertSame(authorityId == null ? null : authorityId == 21L ? replacement : authority,
                village.getLocalAuthority());
        assertEquals(before.id(), result.id());
        assertEquals(before.createdAt(), result.createdAt());
        assertEquals(before.archivedAt(), result.archivedAt());
        assertEquals(before.status(), result.status());
        assertEquals(MICROS, result.updatedAt());
        verify(repository).findByIdForUpdate(1L);
        verify(repository).existsByGovernorateAndNameArAndIdNot(governorate, request.nameAr(), 1L);
        verify(repository).saveAndFlush(same(village));
        verifyNoMoreInteractions(repository);
        verify(clock).instant();
        verifyNoMoreInteractions(clock);
        if (authorityId == null) verifyNoInteractions(authorities);
    }

    @Test
    void identicalPutRetainsSelfNameAndStillRefreshesTimestamp() {
        locked();
        parents();
        saving();
        var before = mapper.toResponse(village);
        var result = service.update(1L, request(10L, 20L));
        assertEquals(before.nameAr(), result.nameAr());
        assertEquals(before.nameEn(), result.nameEn());
        assertEquals(before.latitude(), result.latitude());
        assertEquals(MICROS, result.updatedAt());
        assertNotEquals(before.updatedAt(), result.updatedAt());
        verify(repository).existsByGovernorateAndNameArAndIdNot(governorate, before.nameAr(), 1L);
    }

    @ParameterizedTest
    @EnumSource(VillageStatus.class)
    void archivesEveryStatusWithoutChangingDomainOrRelationships(VillageStatus status) {
        locked();
        saving();
        village.setStatus(status);
        var before = mapper.toResponse(village);
        service.archive(1L);
        var after = mapper.toResponse(village);
        assertEquals(MICROS, after.archivedAt());
        assertEquals(MICROS, after.updatedAt());
        assertEquals(new VillageResponse(before.id(), before.governorateId(), before.localAuthorityId(),
                before.nameAr(), before.nameEn(), before.nameOrigin(), before.locationDescription(),
                before.latitude(), before.longitude(), before.elevationM(), before.areaKm2(),
                before.status(), before.createdAt(), MICROS, MICROS), after);
        assertSame(governorate, village.getGovernorate());
        assertSame(authority, village.getLocalAuthority());
        var order = inOrder(repository, clock);
        order.verify(repository).findByIdForUpdate(1L);
        order.verify(clock).instant();
        order.verify(repository).saveAndFlush(same(village));
        verifyNoMoreInteractions(repository, clock);
        verifyNoInteractions(governorates, authorities);
    }

    @Test
    void repeatedArchiveDoesNotReadClockMutateOrSave() {
        locked();
        village.setArchivedAt(OLD);
        var before = mapper.toResponse(village);
        service.archive(1L);
        unchanged(before);
        verify(repository).findByIdForUpdate(1L);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(governorates, authorities);
    }

    @Test
    void missingArchiveIsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.archive(1L));
        verify(repository).findByIdForUpdate(1L);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(clock, governorates, authorities);
    }

    @ParameterizedTest
    @ValueSource(strings = {"create", "update", "archive"})
    void integrityFailuresAreSanitizedAndNotRetried(String operation) {
        parents();
        locked();
        when(repository.saveAndFlush(any())).thenThrow(
                new DataIntegrityViolationException("PRIVATE SQL TABLE AND CONNECTION DETAILS"));
        var failure = status(409, () -> {
            switch (operation) {
                case "create" -> service.create(request(10L, 20L));
                case "update" -> service.update(1L, request(10L, 20L));
                default -> service.archive(1L);
            }
        });
        assertEquals("Village write conflicts with database constraints", failure.getReason());
        assertFalse(failure.toString().contains("PRIVATE"));
        assertNull(failure.getCause());
        var order = inOrder(repository);
        if (!operation.equals("create")) order.verify(repository).findByIdForUpdate(1L);
        if (operation.equals("create"))
            order.verify(repository).existsByGovernorateAndNameAr(governorate, "  Arabic  ");
        if (operation.equals("update"))
            order.verify(repository).existsByGovernorateAndNameArAndIdNot(governorate, "  Arabic  ", 1L);
        order.verify(repository).saveAndFlush(any());
        order.verifyNoMoreInteractions();
    }

    @ParameterizedTest
    @ValueSource(strings = {"update", "archive"})
    void lockFailuresPropagateWithoutMutationOrFurtherCalls(String operation) {
        var failure = new CannotAcquireLockException("Lock unavailable");
        when(repository.findByIdForUpdate(1L)).thenThrow(failure);
        var before = mapper.toResponse(village);
        assertSame(failure, assertThrows(CannotAcquireLockException.class, () -> {
            if (operation.equals("update")) service.update(1L, request(10L, 20L));
            else service.archive(1L);
        }));
        unchanged(before);
        verify(repository).findByIdForUpdate(1L);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(governorates, authorities);
    }

    @Test
    void declaresReadOnlyReadsAndTransactionalWrites() throws Exception {
        for (var method : VillageService.class.getDeclaredMethods()) {
            if (!java.lang.reflect.Modifier.isPublic(method.getModifiers())) continue;
            var annotation = method.getAnnotation(Transactional.class);
            assertNotNull(annotation, method.getName());
            boolean read = Set.of("getById", "getActive").contains(method.getName());
            assertEquals(read, annotation.readOnly(), method.getName());
            assertEquals(org.springframework.transaction.annotation.Propagation.REQUIRED,
                    annotation.propagation());
        }
        assertNull(VillageService.class.getAnnotation(Transactional.class));
    }

    private void assertEditable(VillageRequest request, Village value) {
        assertEquals(request.nameAr(), value.getNameAr());
        assertEquals(request.nameEn(), value.getNameEn());
        assertEquals(request.nameOrigin(), value.getNameOrigin());
        assertEquals(request.locationDescription(), value.getLocationDescription());
        assertEquals(request.latitude(), value.getLatitude());
        assertEquals(request.longitude(), value.getLongitude());
        assertEquals(request.elevationM(), value.getElevationM());
        assertEquals(request.areaKm2(), value.getAreaKm2());
    }
}