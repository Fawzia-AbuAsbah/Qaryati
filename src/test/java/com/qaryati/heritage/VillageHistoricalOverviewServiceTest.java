package com.qaryati.heritage;

import com.qaryati.heritage.dto.*;
import com.qaryati.heritage.entity.*;
import com.qaryati.heritage.repository.VillageHistoryRepository;
import com.qaryati.heritage.service.VillageHistoricalOverviewService;
import com.qaryati.dataquality.api.*;
import com.qaryati.village.api.*;
import jakarta.validation.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.data.domain.*;
import org.springframework.security.authentication.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VillageHistoricalOverviewServiceTest {
    static ValidatorFactory factory;
    VillageHistoryRepository repository;
    VillageLookupApi villages;
    SourceLookup sources;
    VillageHistorySourceLinks links;
    AuditWriter audits;
    VillageHistoricalOverviewService service;
    VillageHistory event;
    @BeforeAll static void open() { factory = Validation.buildDefaultValidatorFactory(); }
    @AfterAll static void close() { factory.close(); }
    @BeforeEach void setup() {
        repository = mock(VillageHistoryRepository.class); villages = mock(VillageLookupApi.class);
        sources = mock(SourceLookup.class); links = mock(VillageHistorySourceLinks.class); audits = mock(AuditWriter.class);
        service = new VillageHistoricalOverviewService(repository, villages, sources, links, audits, factory.getValidator());
        event = VillageHistory.draft(new VillageHistoryRequest(1L, "Old"));
        ReflectionTestUtils.setField(event, "id", 3L);
        when(repository.findByIdForUpdate(3L)).thenReturn(Optional.of(event));
        when(repository.findById(3L)).thenReturn(Optional.of(event));
        when(repository.saveAndFlush(any())).thenAnswer(c -> {
            VillageHistory e = c.getArgument(0); ReflectionTestUtils.setField(e, "id", 3L); return e;
        });
        when(villages.findById(1L)).thenReturn(Optional.of(new VillageLookupResult(1L, VillageLookupStatus.VERIFIED, false)));
        when(links.sourceIds(3L)).thenReturn(List.of(2L));
        role("ADMIN");
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    static void role(String role) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "user", null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
    static VillageHistoricalOverviewRequest request(String title, List<Long> ids) {
        return new VillageHistoricalOverviewRequest(1L, title, ids);
    }
    void status(int expected, Runnable call) {
        assertEquals(expected, assertThrows(ResponseStatusException.class, call::run).getStatusCode().value());
    }
    @ParameterizedTest @ValueSource(strings = {"ADMIN", "EDITOR"})
    void privilegedCrudAndSnapshots(String role) {
        role(role);
        var created = service.create(request("Created", List.of(2L, 2L)));
        assertEquals(VerificationStatus.DRAFT, created.verificationStatus());
        assertEquals(List.of(2L), created.sourceIds());
        var order = inOrder(repository, links, audits);
        order.verify(repository).saveAndFlush(any());
        order.verify(links).linkNewHistory(3L, List.of(2L, 2L));
        order.verify(links).sourceIds(3L);
        order.verify(audits).recordVillageHistory(eq(3L), eq(AuditAction.CREATE), isNull(), any());
        when(links.sourceIds(3L)).thenReturn(List.of(2L), List.of(4L));
        var updated = service.update(3L, request("New", List.of(4L)));
        var before = org.mockito.ArgumentCaptor.forClass(VillageHistoryAuditSnapshot.class);
        var after = org.mockito.ArgumentCaptor.forClass(VillageHistoryAuditSnapshot.class);
        verify(audits).recordVillageHistory(eq(3L), eq(AuditAction.UPDATE), before.capture(), after.capture());
        assertEquals("Old", before.getValue().overview()); assertEquals(List.of(2L), before.getValue().sourceIds());
        assertEquals("New", after.getValue().overview()); assertEquals(List.of(4L), updated.sourceIds());
        assertThrows(UnsupportedOperationException.class, () -> before.getValue().sourceIds().add(9L));
        service.delete(3L);
        verify(audits).recordVillageHistory(eq(3L), eq(AuditAction.DELETE), any(), isNull());
        verify(repository).delete(event); verify(repository).flush();
    }
    @Test void duplicateAndOnlySpecificUniqueViolationTranslateToConflict() {
        when(repository.existsByVillageId(1L)).thenReturn(true);
        status(409, () -> service.create(request("T", List.of(2L))));
        when(repository.existsByVillageId(1L)).thenReturn(false);
        var sql = new java.sql.SQLException("duplicate", "23505");
        var constraint = new org.hibernate.exception.ConstraintViolationException("duplicate", sql, "village_histories_village_id_key");
        doThrow(new org.springframework.dao.DataIntegrityViolationException("private", constraint)).when(repository).saveAndFlush(any());
        status(409, () -> service.create(request("T", List.of(2L))));
        var unrelated = new org.springframework.dao.DataIntegrityViolationException("unrelated");
        doThrow(unrelated).when(repository).saveAndFlush(any());
        assertSame(unrelated, assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> service.create(request("T", List.of(2L)))));
    }
    @Test void legacySourceLessDraftNeedsSourcesAndRetainsCompleteBeforeState() {
        when(links.sourceIds(3L)).thenReturn(List.of(), List.of(2L));
        status(400, () -> service.update(3L, request("New", List.of())));
        service.update(3L, request("  New\n\u0646\u0635  ", List.of(2L)));
        verify(audits).recordVillageHistory(eq(3L), eq(AuditAction.UPDATE),
                eq(new VillageHistoryAuditSnapshot(1L, "Old", List.of(), "DRAFT")),
                eq(new VillageHistoryAuditSnapshot(1L, "  New\n\u0646\u0635  ", List.of(2L), "DRAFT")));
    }
    @Test void overviewValidationAndVisibleVerifiedDetail() {
        for (String text : Arrays.asList(null, "", " \n\t")) {
            status(400, () -> service.create(request(text, List.of(2L))));
            status(400, () -> service.update(3L, request(text, List.of(2L))));
        }
        role("VIEWER");
        ReflectionTestUtils.setField(event, "verificationStatus", VerificationStatus.VERIFIED);
        when(repository.findByIdAndVerificationStatus(3L, VerificationStatus.VERIFIED)).thenReturn(Optional.of(event));
        assertEquals(VerificationStatus.VERIFIED, service.getById(3L).verificationStatus());
        verifyNoInteractions(villages);
    }
    @ParameterizedTest @ValueSource(strings = {"", " \t\n"})
    void updatesLegacyBlankDraftAndAuditsOriginalText(String legacyOverview) {
        ReflectionTestUtils.setField(event, "overview", legacyOverview);
        when(links.sourceIds(3L)).thenReturn(List.of(), List.of(2L));

        var updated = service.update(3L, request("Corrected overview", List.of(2L)));

        assertEquals("Corrected overview", updated.overview());
        assertEquals(VerificationStatus.DRAFT, updated.verificationStatus());
        assertEquals(List.of(2L), updated.sourceIds());
        verify(repository).saveAndFlush(event);
        verify(links).replaceSources(3L, List.of(2L));
        verify(audits).recordVillageHistory(3L, AuditAction.UPDATE,
                new VillageHistoryAuditSnapshot(1L, legacyOverview, List.of(), "DRAFT"),
                new VillageHistoryAuditSnapshot(1L, "Corrected overview", List.of(2L), "DRAFT"));
    }
    @ParameterizedTest @ValueSource(strings = {"", " \t\n"})
    void deletesLegacyBlankDraftAndAuditsOriginalText(String legacyOverview) {
        ReflectionTestUtils.setField(event, "overview", legacyOverview);
        when(links.sourceIds(3L)).thenReturn(List.of());

        service.delete(3L);

        var order = inOrder(repository, links, audits);
        order.verify(repository).findByIdForUpdate(3L);
        order.verify(links).sourceIds(3L);
        order.verify(repository).delete(event);
        order.verify(repository).flush();
        order.verify(audits).recordVillageHistory(3L, AuditAction.DELETE,
                new VillageHistoryAuditSnapshot(1L, legacyOverview, List.of(), "DRAFT"), null);
        verifyNoInteractions(villages, sources);
    }
    @Test void multipleRowsUseOneBatchAndNoPerHistoryReads() {
        var second = VillageHistory.draft(new VillageHistoryRequest(9L, "Second"));
        ReflectionTestUtils.setField(second, "id", 8L);
        when(repository.findVisiblePage(isNull(), isNull(), any())).thenReturn(new PageImpl<>(List.of(event, second)));
        when(links.sourceIdsByHistoryIds(List.of(3L, 8L))).thenReturn(Map.of(3L, List.of(2L), 8L, List.of()));
        var page = service.getAll(0, 20, null);
        assertEquals(List.of(2L), page.content().getFirst().sourceIds());
        assertEquals(List.of(), page.content().getLast().sourceIds());
        verify(links).sourceIdsByHistoryIds(List.of(3L, 8L));
        verify(links, never()).sourceIds(any());
    }
    @Test void serviceAuthorization() {
        role("VIEWER");
        assertThrows(AccessDeniedException.class, () -> service.create(request("T", List.of(2L))));
        assertThrows(AccessDeniedException.class, () -> service.update(3L, request("T", List.of(2L))));
        assertThrows(AccessDeniedException.class, () -> service.delete(3L));
        status(404, () -> service.getById(3L));
        verify(repository).findByIdAndVerificationStatus(3L, VerificationStatus.VERIFIED);
        SecurityContextHolder.clearContext();
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> service.getById(3L));
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> service.create(request("T", List.of(2L))));
        role("OTHER"); assertThrows(AccessDeniedException.class, () -> service.getById(3L));
    }
    @ParameterizedTest @EnumSource(value=VerificationStatus.class, names="DRAFT", mode=EnumSource.Mode.EXCLUDE)
    void nonDraftCannotMutate(VerificationStatus state) {
        ReflectionTestUtils.setField(event, "verificationStatus", state);
        status(409, () -> service.update(3L, request("T", List.of(2L))));
        status(409, () -> service.delete(3L));
        assertEquals(state, service.getById(3L).verificationStatus());
        verifyNoInteractions(audits);
    }
    @Test void villageRulesAndDeleteWithoutRevalidation() {
        var moved = new VillageHistoricalOverviewRequest(2L,"T",List.of(2L));
        status(409, () -> service.update(3L, moved));
        when(villages.findById(1L)).thenReturn(Optional.empty());
        status(404, () -> service.create(request("T", List.of(2L))));
        for (var state : VillageLookupStatus.values()) {
            if (state == VillageLookupStatus.VERIFIED) continue;
            when(villages.findById(1L)).thenReturn(Optional.of(new VillageLookupResult(1L,state,false)));
            status(409, () -> service.create(request("T", List.of(2L))));
            status(409, () -> service.update(3L, request("T", List.of(2L))));
        }
        when(villages.findById(1L)).thenReturn(Optional.of(new VillageLookupResult(1L,VillageLookupStatus.VERIFIED,true)));
        status(409, () -> service.create(request("T", List.of(2L))));
        clearInvocations(villages); service.getById(3L); service.delete(3L); verifyNoInteractions(villages);
    }
    @Test void validatesSourcesAndProviderFailures() {
        for (List<Long> ids : Arrays.asList(null, List.<Long>of(), List.of(0L), List.of(-1L), Arrays.asList((Long)null)))
            status(400, () -> service.create(request("T", ids)));
        doThrow(new NoSuchElementException("private source details")).when(sources).requireExists(2L);
        status(404, () -> service.create(request("T", List.of(2L))));
        reset(sources);
        doThrow(new NoSuchElementException("race")).when(links).linkNewHistory(any(), any());
        status(404, () -> service.create(request("T", List.of(2L))));
        doThrow(new IllegalStateException("infrastructure")).when(links).replaceSources(any(), any());
        assertThrows(IllegalStateException.class, () -> service.update(3L, request("T", List.of(2L))));
        verifyNoInteractions(audits);
    }
    @ParameterizedTest @ValueSource(strings={"ADMIN","EDITOR","VIEWER"})
    void queryVisibilityBeforePagination(String role) {
        role(role);
        VerificationStatus filter = role.equals("VIEWER") ? VerificationStatus.VERIFIED : null;
        var pageable = PageRequest.of(0,20,Sort.by("id").ascending());
        when(repository.findVisiblePage(1L,filter,pageable)).thenReturn(new PageImpl<>(List.of(event),pageable,42));
        when(links.sourceIdsByHistoryIds(List.of(3L))).thenReturn(Map.of(3L, List.of(2L)));
        var page = service.getAll(0,20,1L);
        verify(links).sourceIdsByHistoryIds(List.of(3L));
        verify(links, never()).sourceIds(any());
        assertEquals(42,page.totalElements()); assertEquals(3,page.totalPages());
        verify(repository).findVisiblePage(1L,filter,pageable);
        status(400, () -> service.getAll(-1,20,null)); status(400, () -> service.getAll(0,101,null));
        status(400, () -> service.getAll(0,0,null)); status(400, () -> service.getAll(0,20,0L));
        status(400, () -> service.getById(0L));
    }
}
