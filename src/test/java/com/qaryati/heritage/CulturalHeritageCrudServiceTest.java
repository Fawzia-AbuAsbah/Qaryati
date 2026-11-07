package com.qaryati.heritage;

import com.qaryati.heritage.dto.*;
import com.qaryati.heritage.entity.*;
import com.qaryati.heritage.repository.CulturalHeritageRepository;
import com.qaryati.heritage.service.CulturalHeritageCrudService;
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

class CulturalHeritageCrudServiceTest {
    static ValidatorFactory factory;
    CulturalHeritageRepository repository;
    VillageLookupApi villages;
    SourceLookup sources;
    CulturalHeritageSourceLinks links;
    AuditWriter audits;
    CulturalHeritageCrudService service;
    CulturalHeritage heritage;
    @BeforeAll static void open() { factory = Validation.buildDefaultValidatorFactory(); }
    @AfterAll static void close() { factory.close(); }
    @BeforeEach void setup() {
        repository = mock(CulturalHeritageRepository.class); villages = mock(VillageLookupApi.class);
        sources = mock(SourceLookup.class); links = mock(CulturalHeritageSourceLinks.class); audits = mock(AuditWriter.class);
        service = new CulturalHeritageCrudService(repository, villages, sources, links, audits, factory.getValidator());
        heritage = CulturalHeritage.draft(new CulturalHeritageRequest(1L, "Old", CulturalHeritageType.CRAFT, "Description"));
        ReflectionTestUtils.setField(heritage, "id", 3L);
        when(repository.findByIdForUpdate(3L)).thenReturn(Optional.of(heritage));
        when(repository.findById(3L)).thenReturn(Optional.of(heritage));
        when(repository.saveAndFlush(any())).thenAnswer(c -> {
            CulturalHeritage e = c.getArgument(0); ReflectionTestUtils.setField(e, "id", 3L); return e;
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
    static CulturalHeritageCrudRequest request(String name, List<Long> ids) {
        return new CulturalHeritageCrudRequest(1L, name, CulturalHeritageType.CRAFT, "Description", ids);
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
        verify(audits).recordCulturalHeritage(3L, AuditAction.CREATE, null,
                new CulturalHeritageAuditSnapshot(1L,"Created","CRAFT","Description",List.of(2L),"DRAFT"));
        var order = inOrder(repository, links, audits);
        order.verify(repository).saveAndFlush(any());
        order.verify(links).linkNewCulturalHeritage(3L, List.of(2L, 2L));
        order.verify(links).sourceIds(3L);
        order.verify(audits).recordCulturalHeritage(eq(3L), eq(AuditAction.CREATE), isNull(), any());
        when(links.sourceIds(3L)).thenReturn(List.of(2L), List.of(4L));
        var updated = service.update(3L, request("New", List.of(4L)));
        var before = org.mockito.ArgumentCaptor.forClass(CulturalHeritageAuditSnapshot.class);
        var after = org.mockito.ArgumentCaptor.forClass(CulturalHeritageAuditSnapshot.class);
        verify(audits).recordCulturalHeritage(eq(3L), eq(AuditAction.UPDATE), before.capture(), after.capture());
        assertEquals("Old", before.getValue().name()); assertEquals(List.of(2L), before.getValue().sourceIds());
        assertEquals("New", after.getValue().name()); assertEquals(List.of(4L), updated.sourceIds());
        assertThrows(UnsupportedOperationException.class, () -> before.getValue().sourceIds().add(9L));
        service.delete(3L);
        verify(audits).recordCulturalHeritage(eq(3L), eq(AuditAction.DELETE), any(), isNull());
        verify(repository).delete(heritage); verify(repository).flush();
    }
    @Test void duplicateAndOnlySpecificUniqueViolationTranslateToConflict() {
        when(repository.existsByVillageIdAndNameAndHeritageType(1L, "T", CulturalHeritageType.CRAFT)).thenReturn(true);
        status(409, () -> service.create(request("T", List.of(2L))));
        when(repository.existsByVillageIdAndNameAndHeritageType(1L, "T", CulturalHeritageType.CRAFT)).thenReturn(false);
        var sql = new java.sql.SQLException("duplicate", "23505");
        var constraint = new org.hibernate.exception.ConstraintViolationException("duplicate", sql, "cultural_heritage_village_id_name_heritage_type_key");
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
        verify(audits).recordCulturalHeritage(eq(3L), eq(AuditAction.UPDATE),
                eq(new CulturalHeritageAuditSnapshot(1L, "Old", "CRAFT", "Description", List.of(), "DRAFT")),
                eq(new CulturalHeritageAuditSnapshot(1L, "  New\n\u0646\u0635  ", "CRAFT", "Description", List.of(2L), "DRAFT")));
    }
    @Test void nameValidationAndVisibleVerifiedDetail() {
        for (String text : Arrays.asList((String)null)) {
            status(400, () -> service.create(request(text, List.of(2L))));
            status(400, () -> service.update(3L, request(text, List.of(2L))));
        }
        role("VIEWER");
        ReflectionTestUtils.setField(heritage, "verificationStatus", VerificationStatus.VERIFIED);
        when(repository.findByIdAndVerificationStatus(3L, VerificationStatus.VERIFIED)).thenReturn(Optional.of(heritage));
        assertEquals(VerificationStatus.VERIFIED, service.getById(3L).verificationStatus());
        verifyNoInteractions(villages);
    }
    @Test void httpValidationPreservesFoundationTextConstraintsAndAddsReferencePolicy() {
        for (var request : List.of(
                new CulturalHeritageCrudRequest(null,"Name",CulturalHeritageType.CRAFT,"",List.of(2L)),
                new CulturalHeritageCrudRequest(0L,"Name",CulturalHeritageType.CRAFT,"",List.of(2L)),
                new CulturalHeritageCrudRequest(1L,"x".repeat(201),CulturalHeritageType.CRAFT,"",List.of(2L)),
                new CulturalHeritageCrudRequest(1L,"Name",null,"",List.of(2L)),
                new CulturalHeritageCrudRequest(1L,"Name",CulturalHeritageType.CRAFT,null,List.of(2L)))) {
            status(400,()->service.create(request));
            status(400,()->service.update(3L,request));
        }
        status(400,()->service.create(null)); status(400,()->service.update(3L,null));
        verifyNoInteractions(repository,villages,links,audits);
    }
    @ParameterizedTest @EnumSource(CulturalHeritageType.class)
    void acceptsAllCategoriesAndBlankTextWithoutNormalization(CulturalHeritageType type) {
        for (String text : List.of("", " \t\n", "  \u062a\u0631\u0627\u062b  ")) {
            var created = service.create(new CulturalHeritageCrudRequest(1L, text, type, text, List.of(2L)));
            assertEquals(text, created.name()); assertEquals(text, created.description());
            assertEquals(type, created.heritageType());
        }
    }
    @Test void duplicateUpdateAndDatabaseRaceAreScopedToCulturalTuple() {
        when(repository.existsByVillageIdAndNameAndHeritageTypeAndIdNot(1L,"T",CulturalHeritageType.CRAFT,3L)).thenReturn(true);
        status(409, () -> service.update(3L,request("T",List.of(2L))));
        assertEquals("Old",heritage.getName());
        when(repository.existsByVillageIdAndNameAndHeritageTypeAndIdNot(1L,"T",CulturalHeritageType.CRAFT,3L)).thenReturn(false);
        for (String constraintName : List.of("cultural_heritage_village_id_name_heritage_type_key", "other_key")) {
            for (String state : List.of("23505", "23503")) {
                var constraint = new org.hibernate.exception.ConstraintViolationException("private",
                        new java.sql.SQLException("private",state), constraintName);
                var failure = new org.springframework.dao.DataIntegrityViolationException("private",constraint);
                doThrow(failure).when(repository).saveAndFlush(any());
                if (state.equals("23505") && constraintName.equals("cultural_heritage_village_id_name_heritage_type_key")) {
                    status(409, () -> service.update(3L,request("T",List.of(2L))));
                    status(409, () -> service.create(request("T",List.of(2L))));
                } else {
                    assertSame(failure,assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                            () -> service.update(3L,request("T",List.of(2L)))));
                    assertSame(failure,assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                            () -> service.create(request("T",List.of(2L)))));
                }
            }
        }
        verifyNoInteractions(audits);
    }
    @Test void sourceOnlyUpdateCapturesCompleteSnapshotsAndLegacyDeleteNeedsNoVillage() {
        when(links.sourceIds(3L)).thenReturn(List.of(),List.of(4L));
        service.update(3L,request("Old",List.of(4L,4L)));
        verify(audits).recordCulturalHeritage(3L,AuditAction.UPDATE,
                new CulturalHeritageAuditSnapshot(1L,"Old","CRAFT","Description",List.of(),"DRAFT"),
                new CulturalHeritageAuditSnapshot(1L,"Old","CRAFT","Description",List.of(4L),"DRAFT"));
        when(links.sourceIds(3L)).thenReturn(List.of());
        clearInvocations(villages);
        service.delete(3L);
        verifyNoInteractions(villages);
        verify(audits).recordCulturalHeritage(3L,AuditAction.DELETE,
                new CulturalHeritageAuditSnapshot(1L,"Old","CRAFT","Description",List.of(),"DRAFT"),null);
    }
    @ParameterizedTest @EnumSource(VerificationStatus.class)
    void requestedStatusOnlyNarrowsViewerVisibility(VerificationStatus status) {
        role("VIEWER");
        var pageable=PageRequest.of(0,1,Sort.by("id").ascending());
        when(repository.findVisiblePage(1L,CulturalHeritageType.FOOD,status,true,pageable)).thenReturn(Page.empty(pageable));
        when(links.sourceIdsByCulturalHeritageIds(List.of())).thenReturn(Map.of());
        var result=service.getAll(0,1,1L,CulturalHeritageType.FOOD,status);
        assertEquals(0,result.totalElements());
        verify(repository).findVisiblePage(1L,CulturalHeritageType.FOOD,status,true,pageable);
        verify(links).sourceIdsByCulturalHeritageIds(List.of());
    }
    @Test void unsupportedAndAnonymousRolesCannotUseAnyServiceOperation() {
        for (String unsupported : List.of("OTHER", "", "USER")) {
            role(unsupported);
            assertThrows(AccessDeniedException.class,()->service.getAll(0,20,null,null,null));
            assertThrows(AccessDeniedException.class,()->service.getById(3L));
            assertThrows(AccessDeniedException.class,()->service.create(request("T",List.of(2L))));
            assertThrows(AccessDeniedException.class,()->service.update(3L,request("T",List.of(2L))));
            assertThrows(AccessDeniedException.class,()->service.delete(3L));
        }
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key","anonymous",
                List.of(new SimpleGrantedAuthority("ROLE_VIEWER"))));
        assertThrows(AuthenticationCredentialsNotFoundException.class,()->service.getAll(0,20,null,null,null));
        assertThrows(AuthenticationCredentialsNotFoundException.class,()->service.getById(3L));
        assertThrows(AuthenticationCredentialsNotFoundException.class,()->service.create(request("T",List.of(2L))));
        assertThrows(AuthenticationCredentialsNotFoundException.class,()->service.update(3L,request("T",List.of(2L))));
        assertThrows(AuthenticationCredentialsNotFoundException.class,()->service.delete(3L));
        verifyNoInteractions(repository,links,villages,audits);
    }
    @Test void multipleRowsUseOneBatchAndNoPerRecordReads() {
        var second = CulturalHeritage.draft(new CulturalHeritageRequest(9L, "Second", CulturalHeritageType.CRAFT, "Description"));
        ReflectionTestUtils.setField(second, "id", 8L);
        when(repository.findVisiblePage(isNull(), isNull(), isNull(), eq(false), any())).thenReturn(new PageImpl<>(List.of(heritage, second)));
        when(links.sourceIdsByCulturalHeritageIds(List.of(3L, 8L))).thenReturn(Map.of(3L, List.of(2L), 8L, List.of()));
        var page = service.getAll(0, 20, null, null, null);
        assertEquals(List.of(2L), page.content().getFirst().sourceIds());
        assertEquals(List.of(), page.content().getLast().sourceIds());
        verify(links).sourceIdsByCulturalHeritageIds(List.of(3L, 8L));
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
        ReflectionTestUtils.setField(heritage, "verificationStatus", state);
        status(409, () -> service.update(3L, request("T", List.of(2L))));
        status(409, () -> service.delete(3L));
        assertEquals(state, service.getById(3L).verificationStatus());
        verifyNoInteractions(audits);
    }
    @Test void villageRulesAndDeleteWithoutRevalidation() {
        var moved = new CulturalHeritageCrudRequest(2L,"T",CulturalHeritageType.CRAFT,"Description",List.of(2L));
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
        doThrow(new NoSuchElementException("race")).when(links).linkNewCulturalHeritage(any(), any());
        status(404, () -> service.create(request("T", List.of(2L))));
        doThrow(new IllegalStateException("infrastructure")).when(links).replaceSources(any(), any());
        assertThrows(IllegalStateException.class, () -> service.update(3L, request("T", List.of(2L))));
        verifyNoInteractions(audits);
    }
    @ParameterizedTest @ValueSource(strings={"ADMIN","EDITOR","VIEWER"})
    void queryVisibilityBeforePagination(String role) {
        role(role);
        boolean verifiedOnly = role.equals("VIEWER");
        var pageable = PageRequest.of(0,20,Sort.by("id").ascending());
        when(repository.findVisiblePage(1L,null,null,verifiedOnly,pageable)).thenReturn(new PageImpl<>(List.of(heritage),pageable,42));
        when(links.sourceIdsByCulturalHeritageIds(List.of(3L))).thenReturn(Map.of(3L, List.of(2L)));
        var page = service.getAll(0,20,1L,null,null);
        verify(links).sourceIdsByCulturalHeritageIds(List.of(3L));
        verify(links, never()).sourceIds(any());
        assertEquals(42,page.totalElements()); assertEquals(3,page.totalPages());
        verify(repository).findVisiblePage(1L,null,null,verifiedOnly,pageable);
        status(400, () -> service.getAll(-1,20,null,null,null)); status(400, () -> service.getAll(0,101,null,null,null));
        status(400, () -> service.getAll(0,0,null,null,null)); status(400, () -> service.getAll(0,20,0L,null,null));
        status(400, () -> service.getById(0L));
    }
}
