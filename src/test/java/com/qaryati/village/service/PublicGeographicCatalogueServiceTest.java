package com.qaryati.village.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.qaryati.village.dto.PublicGovernorateResponse;
import com.qaryati.village.dto.PublicLocalAuthorityResponse;
import com.qaryati.village.entity.Governorate;
import com.qaryati.village.entity.LocalAuthority;
import com.qaryati.village.entity.LocalAuthorityType;
import com.qaryati.village.repository.GovernorateRepository;
import com.qaryati.village.repository.LocalAuthorityRepository;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.LazyInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.data.repository.query.parser.PartTree;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicGeographicCatalogueServiceTest {
    private static final Sort ID_ASC = Sort.by(Sort.Direction.ASC, "id");

    private final GovernorateRepository governorates = mock(GovernorateRepository.class);
    private final LocalAuthorityRepository authorities = mock(LocalAuthorityRepository.class);
    private final PublicGeographicCatalogueService service =
            new PublicGeographicCatalogueService(governorates, authorities);

    @BeforeEach void clearAuthenticationBeforeRead() { SecurityContextHolder.clearContext(); }
    @AfterEach void clearAuthenticationAfterRead() { SecurityContextHolder.clearContext(); }

    @Test void governoratesExposeOnlyPublicFieldsAndPreserveNullableEnglishNames() {
        var first = governorate(1L, "محافظة تجريبية", null);
        var second = governorate(2L, "محافظة ثانية", "Synthetic governorate");
        when(governorates.findAll(ID_ASC)).thenReturn(List.of(first, second));

        var result = service.getGovernorates();

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(List.of(new PublicGovernorateResponse(1L, "محافظة تجريبية", null),
                new PublicGovernorateResponse(2L, "محافظة ثانية", "Synthetic governorate")), result);
        verify(governorates).findAll(ID_ASC);
        verifyNoMoreInteractions(governorates);
        verifyNoInteractions(authorities);
    }

    @Test void emptyGovernorateCatalogueReturnsAnEmptyList() {
        when(governorates.findAll(ID_ASC)).thenReturn(List.of());

        assertEquals(List.of(), service.getGovernorates());

        verify(governorates).findAll(ID_ASC);
        verifyNoMoreInteractions(governorates);
        verifyNoInteractions(authorities);
    }

    @Test void absentGovernorateFilterListsAuthoritiesAcrossParentsWithFixedSort() {
        var first = authority(3L, "مجلس تجريبي", null, governorate(7L, "Parent one", null));
        var second = authority(4L, "بلدية تجريبية", "Synthetic authority", governorate(8L, "Parent two", null));
        when(authorities.findAll(ID_ASC)).thenReturn(List.of(first, second));

        var result = service.getLocalAuthorities(null);

        assertEquals(List.of(new PublicLocalAuthorityResponse(3L, "مجلس تجريبي", null, 7L),
                new PublicLocalAuthorityResponse(4L, "بلدية تجريبية", "Synthetic authority", 8L)), result);
        verify(authorities).findAll(ID_ASC);
        verifyNoMoreInteractions(authorities);
        verifyNoInteractions(governorates);
    }

    @ParameterizedTest @ValueSource(longs = {1L, Long.MAX_VALUE})
    void positiveGovernorateFilterQueriesTheParentIdentifierWithoutLookingUpTheParent(long governorateId) {
        var row = authority(5L, "مجلس محدد", null, governorate(governorateId, "Parent", null));
        when(authorities.findByGovernorate_Id(governorateId, ID_ASC)).thenReturn(List.of(row));

        assertEquals(List.of(new PublicLocalAuthorityResponse(5L, "مجلس محدد", null, governorateId)),
                service.getLocalAuthorities(governorateId));

        verify(authorities).findByGovernorate_Id(governorateId, ID_ASC);
        verifyNoMoreInteractions(authorities);
        verifyNoInteractions(governorates);
    }

    @ParameterizedTest @ValueSource(longs = {1L, Long.MAX_VALUE})
    void unknownPositiveGovernorateReturnsAnEmptyCatalogueWithoutCheckingParentExistence(long governorateId) {
        when(authorities.findByGovernorate_Id(governorateId, ID_ASC)).thenReturn(List.of());

        assertEquals(List.of(), service.getLocalAuthorities(governorateId));

        verify(authorities).findByGovernorate_Id(governorateId, ID_ASC);
        verifyNoMoreInteractions(authorities);
        verifyNoInteractions(governorates);
    }

    @Test void emptyUnfilteredAuthorityCatalogueReturnsAnEmptyList() {
        when(authorities.findAll(ID_ASC)).thenReturn(List.of());

        assertEquals(List.of(), service.getLocalAuthorities(null));

        verify(authorities).findAll(ID_ASC);
        verifyNoMoreInteractions(authorities);
        verifyNoInteractions(governorates);
    }

    @ParameterizedTest @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
    void invalidGovernorateFiltersFailBeforeAnyRepositoryAccess(long governorateId) {
        var failure = assertThrows(ResponseStatusException.class,
                () -> service.getLocalAuthorities(governorateId));

        assertEquals(400, failure.getStatusCode().value());
        verifyNoInteractions(governorates, authorities);
    }

    @Test void authorityProjectionReadsTheProxyInternalIdentifierWithoutInitializingItsParent() {
        var parent = mock(Governorate.class, withSettings().extraInterfaces(HibernateProxy.class));
        var initializer = mock(LazyInitializer.class);
        when(((HibernateProxy) parent).getHibernateLazyInitializer()).thenReturn(initializer);
        when(initializer.getInternalIdentifier()).thenReturn(19L);
        var row = authority(6L, "مجلس تجريبي", null, parent);
        when(authorities.findAll(ID_ASC)).thenReturn(List.of(row));

        assertEquals(List.of(new PublicLocalAuthorityResponse(6L, "مجلس تجريبي", null, 19L)),
                service.getLocalAuthorities(null));

        verify(authorities).findAll(ID_ASC);
        verify((HibernateProxy) parent).getHibernateLazyInitializer();
        verify(initializer).getInternalIdentifier();
        verifyNoMoreInteractions(authorities, parent, initializer);
        verifyNoInteractions(governorates);
    }

    @Test void governorateRepositoryFailuresPropagate() {
        var failure = new IllegalStateException("synthetic governorate failure");
        when(governorates.findAll(ID_ASC)).thenThrow(failure);

        assertSame(failure, assertThrows(IllegalStateException.class, service::getGovernorates));

        verify(governorates).findAll(ID_ASC);
        verifyNoMoreInteractions(governorates);
        verifyNoInteractions(authorities);
    }

    @Test void unfilteredAuthorityRepositoryFailuresPropagate() {
        var failure = new IllegalStateException("synthetic authority failure");
        when(authorities.findAll(ID_ASC)).thenThrow(failure);

        assertSame(failure, assertThrows(IllegalStateException.class, () -> service.getLocalAuthorities(null)));

        verify(authorities).findAll(ID_ASC);
        verifyNoMoreInteractions(authorities);
        verifyNoInteractions(governorates);
    }

    @Test void filteredAuthorityRepositoryFailuresPropagateWithoutAnUnfilteredFallback() {
        var failure = new IllegalStateException("synthetic filtered authority failure");
        when(authorities.findByGovernorate_Id(7L, ID_ASC)).thenThrow(failure);

        assertSame(failure, assertThrows(IllegalStateException.class, () -> service.getLocalAuthorities(7L)));

        verify(authorities).findByGovernorate_Id(7L, ID_ASC);
        verifyNoMoreInteractions(authorities);
        verifyNoInteractions(governorates);
    }

    @Test void publicDtoRecordsHaveExplicitScalarAllowlistsAndRetainNullFields() {
        assertTrue(PublicGovernorateResponse.class.isRecord());
        assertEquals(List.of("id", "nameAr", "nameEn"),
                Arrays.stream(PublicGovernorateResponse.class.getRecordComponents()).map(component -> component.getName()).toList());
        assertEquals(List.of(Long.class, String.class, String.class),
                Arrays.stream(PublicGovernorateResponse.class.getRecordComponents()).map(component -> component.getType()).toList());
        assertEquals(JsonInclude.Include.ALWAYS, PublicGovernorateResponse.class.getAnnotation(JsonInclude.class).value());
        assertTrue(PublicLocalAuthorityResponse.class.isRecord());
        assertEquals(List.of("id", "nameAr", "nameEn", "governorateId"),
                Arrays.stream(PublicLocalAuthorityResponse.class.getRecordComponents()).map(component -> component.getName()).toList());
        assertEquals(List.of(Long.class, String.class, String.class, Long.class),
                Arrays.stream(PublicLocalAuthorityResponse.class.getRecordComponents()).map(component -> component.getType()).toList());
        assertEquals(JsonInclude.Include.ALWAYS, PublicLocalAuthorityResponse.class.getAnnotation(JsonInclude.class).value());
        verifyNoInteractions(governorates, authorities);
    }

    @Test void catalogueReadsDeclareReadOnlyTransactions() throws Exception {
        var classTransaction = PublicGeographicCatalogueService.class.getAnnotation(Transactional.class);
        assertNotNull(classTransaction);
        assertTrue(classTransaction.readOnly());
        for (var method : List.of(PublicGeographicCatalogueService.class.getMethod("getGovernorates"),
                PublicGeographicCatalogueService.class.getMethod("getLocalAuthorities", Long.class))) {
            var transaction = method.getAnnotation(Transactional.class);
            assertTrue((transaction == null ? classTransaction : transaction).readOnly());
        }
        verifyNoInteractions(governorates, authorities);
    }

    @Test void filteredDerivedQueryTargetsTheNestedGovernorateIdentifier() throws Exception {
        var method = LocalAuthorityRepository.class.getMethod("findByGovernorate_Id", Long.class, Sort.class);
        assertNull(method.getAnnotation(org.springframework.data.jpa.repository.Query.class));
        var tree = new PartTree(method.getName(), LocalAuthority.class);
        var alternatives = tree.stream().toList();
        assertEquals(1, alternatives.size());
        var parts = alternatives.getFirst().stream().toList();
        assertEquals(1, parts.size());
        assertEquals("governorate.id", parts.getFirst().getProperty().toDotPath());
        assertEquals(Part.Type.SIMPLE_PROPERTY, parts.getFirst().getType());
        verifyNoInteractions(governorates, authorities);
    }

    private Governorate governorate(Long id, String nameAr, String nameEn) {
        var governorate = new Governorate();
        governorate.setId(id);
        governorate.setNameAr(nameAr);
        governorate.setNameEn(nameEn);
        governorate.setCode("SYNTHETIC_INTERNAL_CODE");
        return governorate;
    }

    private LocalAuthority authority(Long id, String nameAr, String nameEn, Governorate governorate) {
        var authority = new LocalAuthority();
        authority.setId(id);
        authority.setNameAr(nameAr);
        authority.setNameEn(nameEn);
        authority.setGovernorate(governorate);
        authority.setAuthorityType(LocalAuthorityType.OTHER);
        return authority;
    }
}
