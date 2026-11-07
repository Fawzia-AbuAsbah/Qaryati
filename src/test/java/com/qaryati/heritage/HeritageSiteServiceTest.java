package com.qaryati.heritage;

import com.qaryati.heritage.dto.*;
import com.qaryati.heritage.entity.*;
import com.qaryati.heritage.repository.HeritageSiteRepository;
import com.qaryati.heritage.service.HeritageSiteService;
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

class HeritageSiteServiceTest {
    static ValidatorFactory factory;
    HeritageSiteRepository repository;
    VillageLookupApi villages;
    HeritageSiteSourceLinks links;
    AuditWriter audits;
    HeritageSiteService service;
    HeritageSite event;
    @BeforeAll static void open() { factory = Validation.buildDefaultValidatorFactory(); }
    @AfterAll static void close() { factory.close(); }
    @BeforeEach void setup() {
        repository = mock(HeritageSiteRepository.class); villages = mock(VillageLookupApi.class);
        links = mock(HeritageSiteSourceLinks.class); audits = mock(AuditWriter.class);
        service = new HeritageSiteService(repository, villages, links, audits, factory.getValidator());
        event = HeritageSite.draft(request("Old", List.of(2L)));
        ReflectionTestUtils.setField(event, "id", 3L);
        when(repository.findByIdForUpdate(3L)).thenReturn(Optional.of(event));
        when(repository.findById(3L)).thenReturn(Optional.of(event));
        when(repository.saveAndFlush(any())).thenAnswer(c -> {
            HeritageSite e = c.getArgument(0); ReflectionTestUtils.setField(e, "id", 3L); return e;
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
    static HeritageSiteRequest request(String title, List<Long> ids) {
        return new HeritageSiteRequest(1L, title, HeritageSiteType.SPRING, "Description", null, null, "Notes", ids);
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
        order.verify(links).linkNewSite(3L, List.of(2L));
        order.verify(links).sourceIds(3L);
        order.verify(audits).recordHeritageSite(eq(3L), eq(AuditAction.CREATE), isNull(), any());
        when(links.sourceIds(3L)).thenReturn(List.of(2L), List.of(4L));
        var updated = service.update(3L, request("New", List.of(4L)));
        var before = org.mockito.ArgumentCaptor.forClass(HeritageSiteAuditSnapshot.class);
        var after = org.mockito.ArgumentCaptor.forClass(HeritageSiteAuditSnapshot.class);
        verify(audits).recordHeritageSite(eq(3L), eq(AuditAction.UPDATE), before.capture(), after.capture());
        assertEquals("Old", before.getValue().name()); assertEquals(List.of(2L), before.getValue().sourceIds());
        assertEquals("New", after.getValue().name()); assertEquals(List.of(4L), updated.sourceIds());
        assertThrows(UnsupportedOperationException.class, () -> before.getValue().sourceIds().add(9L));
        service.delete(3L);
        verify(audits).recordHeritageSite(eq(3L), eq(AuditAction.DELETE), any(), isNull());
        verify(repository).delete(event); verify(repository).flush();
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
        var moved = new HeritageSiteRequest(2L,"T",HeritageSiteType.OTHER,"D",null,null,null,List.of(2L));
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
        doThrow(new NoSuchElementException("private source details")).when(links).linkNewSite(any(), any());
        status(404, () -> service.create(request("T", List.of(2L))));
        reset(links);
        doThrow(new NoSuchElementException("race")).when(links).linkNewSite(any(), any());
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
        when(links.sourceIdsBySiteIds(List.of(3L))).thenReturn(Map.of(3L, List.of(2L)));
        var page = service.getAll(0,20,1L);
        assertEquals(42,page.totalElements()); assertEquals(3,page.totalPages());
        verify(repository).findVisiblePage(1L,filter,pageable);
        verify(links).sourceIdsBySiteIds(List.of(3L));
        verify(links, never()).sourceIds(any());
        assertEquals(List.of(2L), page.content().getFirst().sourceIds());
        status(400, () -> service.getAll(-1,20,null)); status(400, () -> service.getAll(0,101,null));
        status(400, () -> service.getAll(0,0,null)); status(400, () -> service.getAll(0,20,0L));
        status(400, () -> service.getById(0L));
    }

    @Test void emptyPageDoesNotLoadAssociations() {
        when(repository.findVisiblePage(isNull(), isNull(), any())).thenReturn(Page.empty());
        assertTrue(service.getAll(0, 20, null).content().isEmpty());
        verifyNoInteractions(links);
    }

    @Test void multipleSitesUseOneBatchAndNoIndividualSourceReads() {
        var second = HeritageSite.draft(request("Second", List.of(4L)));
        ReflectionTestUtils.setField(second, "id", 5L);
        when(repository.findVisiblePage(isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(event, second)));
        when(links.sourceIdsBySiteIds(List.of(3L, 5L)))
                .thenReturn(Map.of(3L, List.of(2L), 5L, List.of(4L, 6L)));
        var page = service.getAll(0, 20, null);
        assertEquals(List.of(2L), page.content().get(0).sourceIds());
        assertEquals(List.of(4L, 6L), page.content().get(1).sourceIds());
        verify(links).sourceIdsBySiteIds(List.of(3L, 5L));
        verifyNoMoreInteractions(links);
    }

    @ParameterizedTest @EnumSource(VerificationStatus.class)
    void viewerReadsOnlyVerifiedAndNeverChecksParentEligibility(VerificationStatus state) {
        role("VIEWER");
        ReflectionTestUtils.setField(event, "verificationStatus", state);
        when(repository.findByIdAndVerificationStatus(3L, VerificationStatus.VERIFIED))
                .thenReturn(state == VerificationStatus.VERIFIED ? Optional.of(event) : Optional.empty());
        if (state == VerificationStatus.VERIFIED)
            assertEquals(state, service.getById(3L).verificationStatus());
        else status(404, () -> service.getById(3L));
        verifyNoInteractions(villages);
    }

    @Test void sourceIdsAreRequiredOnUpdateAndNormalizedForBothWrites() {
        for (List<Long> ids : Arrays.asList(null, List.<Long>of(), List.of(0L), Arrays.asList((Long)null)))
            status(400, () -> service.update(3L, request("New", ids)));
        service.create(request("New", List.of(4L, 2L, 4L)));
        service.update(3L, request("New", List.of(4L, 2L, 4L)));
        verify(links).linkNewSite(3L, List.of(2L, 4L));
        verify(links).replaceSources(3L, List.of(2L, 4L));
    }

    @Test void snapshotsContainAllMetadataAndRemainImmutableAcrossRevision() {
        var old = new HeritageSiteRequest(1L, "Before", HeritageSiteType.ARCHAEOLOGICAL, "Old description",
                new java.math.BigDecimal("32.123456"), new java.math.BigDecimal("35.123456"), "Old notes", List.of(2L));
        event.reviseDraft(old);
        var revised = new HeritageSiteRequest(1L, "After", HeritageSiteType.VIEWPOINT, null,
                null, null, null, List.of(4L));
        when(links.sourceIds(3L)).thenReturn(List.of(2L), List.of(4L));
        service.update(3L, revised);
        var before = org.mockito.ArgumentCaptor.forClass(HeritageSiteAuditSnapshot.class);
        var after = org.mockito.ArgumentCaptor.forClass(HeritageSiteAuditSnapshot.class);
        verify(audits).recordHeritageSite(eq(3L), eq(AuditAction.UPDATE), before.capture(), after.capture());
        assertEquals(new HeritageSiteAuditSnapshot(1L, "Before", "ARCHAEOLOGICAL", "Old description",
                old.latitude(), old.longitude(), "Old notes", List.of(2L), "DRAFT"), before.getValue());
        var expected = new HeritageSiteAuditSnapshot(1L, "After", "VIEWPOINT", null,
                null, null, null, List.of(4L), "DRAFT");
        assertEquals(expected, after.getValue());
        service.delete(3L);
        verify(audits).recordHeritageSite(3L, AuditAction.DELETE, expected, null);
    }

    @Test void missingSitesAndInvalidIdsFailBeforeMutation() {
        status(404, () -> service.getById(9L));
        status(404, () -> service.update(9L, request("New", List.of(2L))));
        status(404, () -> service.delete(9L));
        status(400, () -> service.update(-1L, request("New", List.of(2L))));
        status(400, () -> service.delete(null));
        verifyNoInteractions(audits);
    }
}
