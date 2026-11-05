package com.qaryati.heritage;

import com.qaryati.heritage.dto.*;
import com.qaryati.heritage.entity.*;
import com.qaryati.heritage.repository.HistoricalEventRepository;
import com.qaryati.heritage.service.HistoricalEventService;
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

class HistoricalEventServiceTest {
    static ValidatorFactory factory;
    HistoricalEventRepository repository;
    VillageLookupApi villages;
    SourceLookup sources;
    EventSourceLinks links;
    AuditWriter audits;
    HistoricalEventService service;
    HistoricalEvent event;
    @BeforeAll static void open() { factory = Validation.buildDefaultValidatorFactory(); }
    @AfterAll static void close() { factory.close(); }
    @BeforeEach void setup() {
        repository = mock(HistoricalEventRepository.class); villages = mock(VillageLookupApi.class);
        sources = mock(SourceLookup.class); links = mock(EventSourceLinks.class); audits = mock(AuditWriter.class);
        service = new HistoricalEventService(repository, villages, sources, links, audits, factory.getValidator());
        event = HistoricalEvent.draft(request("Old", List.of(2L)));
        ReflectionTestUtils.setField(event, "id", 3L);
        when(repository.findByIdForUpdate(3L)).thenReturn(Optional.of(event));
        when(repository.findById(3L)).thenReturn(Optional.of(event));
        when(repository.saveAndFlush(any())).thenAnswer(c -> {
            HistoricalEvent e = c.getArgument(0); ReflectionTestUtils.setField(e, "id", 3L); return e;
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
    static HistoricalEventRequest request(String title, List<Long> ids) {
        return new HistoricalEventRequest(1L, title, "Description", null, null, null, DatePrecision.UNKNOWN, ids);
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
        order.verify(links).linkNewEvent(3L, List.of(2L, 2L));
        order.verify(links).sourceIds(3L);
        order.verify(audits).recordHistoricalEvent(eq(3L), eq(AuditAction.CREATE), isNull(), any());
        when(links.sourceIds(3L)).thenReturn(List.of(2L), List.of(4L));
        var updated = service.update(3L, request("New", List.of(4L)));
        var before = org.mockito.ArgumentCaptor.forClass(HistoricalEventAuditSnapshot.class);
        var after = org.mockito.ArgumentCaptor.forClass(HistoricalEventAuditSnapshot.class);
        verify(audits).recordHistoricalEvent(eq(3L), eq(AuditAction.UPDATE), before.capture(), after.capture());
        assertEquals("Old", before.getValue().title()); assertEquals(List.of(2L), before.getValue().sourceIds());
        assertEquals("New", after.getValue().title()); assertEquals(List.of(4L), updated.sourceIds());
        assertThrows(UnsupportedOperationException.class, () -> before.getValue().sourceIds().add(9L));
        service.delete(3L);
        verify(audits).recordHistoricalEvent(eq(3L), eq(AuditAction.DELETE), any(), isNull());
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
        var moved = new HistoricalEventRequest(2L,"T","D",null,null,null,DatePrecision.UNKNOWN,List.of(2L));
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
        doThrow(new NoSuchElementException("race")).when(links).linkNewEvent(any(), any());
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
        var page = service.getAll(0,20,1L);
        assertEquals(42,page.totalElements()); assertEquals(3,page.totalPages());
        verify(repository).findVisiblePage(1L,filter,pageable);
        status(400, () -> service.getAll(-1,20,null)); status(400, () -> service.getAll(0,101,null));
        status(400, () -> service.getAll(0,0,null)); status(400, () -> service.getAll(0,20,0L));
        status(400, () -> service.getById(0L));
    }
}
