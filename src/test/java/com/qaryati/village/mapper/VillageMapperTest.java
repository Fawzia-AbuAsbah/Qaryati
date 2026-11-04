package com.qaryati.village.mapper;

import com.qaryati.village.dto.VillageRequest;
import com.qaryati.village.dto.VillageResponse;
import com.qaryati.village.entity.*;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.LazyInitializer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VillageMapperTest {
    private final VillageMapper mapper = new VillageMapper();

    private VillageRequest completeRequest() {
        return new VillageRequest(99L, 88L, "Arabic", "English", "Origin", "Location",
                new BigDecimal("32.123456"), new BigDecimal("35.654321"),
                new BigDecimal("-430.25"), new BigDecimal("12.345"));
    }

    private Village populatedVillage() {
        Village village = new Village();
        Governorate governorate = new Governorate();
        governorate.setId(2L);
        LocalAuthority authority = new LocalAuthority();
        authority.setId(3L);
        village.setGovernorate(governorate);
        village.setLocalAuthority(authority);
        village.setId(1L);
        village.setStatus(VillageStatus.VERIFIED);
        village.setCreatedAt(Instant.parse("2026-01-01T00:00:00.123456Z"));
        village.setUpdatedAt(Instant.parse("2026-02-01T00:00:00.123456Z"));
        village.setArchivedAt(Instant.parse("2026-03-01T00:00:00.123456Z"));
        mapper.updateEntity(completeRequest(), village);
        return village;
    }

    private void assertEditable(VillageRequest request, Village village) {
        assertEquals(request.nameAr(), village.getNameAr());
        assertEquals(request.nameEn(), village.getNameEn());
        assertEquals(request.nameOrigin(), village.getNameOrigin());
        assertEquals(request.locationDescription(), village.getLocationDescription());
        assertEquals(request.latitude(), village.getLatitude());
        assertEquals(request.longitude(), village.getLongitude());
        assertEquals(request.elevationM(), village.getElevationM());
        assertEquals(request.areaKm2(), village.getAreaKm2());
    }

    @Test
    void mapsEveryPersistedField() {
        Village village = populatedVillage();
        VillageResponse expected = new VillageResponse(1L, 2L, 3L, "Arabic", "English",
                "Origin", "Location", new BigDecimal("32.123456"), new BigDecimal("35.654321"),
                new BigDecimal("-430.25"), new BigDecimal("12.345"), VillageStatus.VERIFIED,
                village.getCreatedAt(), village.getUpdatedAt(), village.getArchivedAt());
        assertEquals(expected, mapper.toResponse(village));
    }

    @Test
    void handlesNullableAuthorityAndOptionalFields() {
        Village village = populatedVillage();
        village.setLocalAuthority(null);
        mapper.updateEntity(new VillageRequest(2L, null, "New", null, null, "Place",
                null, null, null, null), village);
        var response = mapper.toResponse(village);
        assertEquals(2L, response.governorateId());
        assertNull(response.localAuthorityId());
        assertNull(response.nameEn());
        assertNull(response.nameOrigin());
        assertNull(response.latitude());
        assertNull(response.longitude());
        assertNull(response.elevationM());
        assertNull(response.areaKm2());
    }

    @Test
    void newEntityLeavesAssociationsAndLifecycleFieldsForService() {
        var request = completeRequest();
        var village = mapper.toEntity(request);
        assertEditable(request, village);
        assertNull(village.getId());
        assertNull(village.getGovernorate());
        assertNull(village.getLocalAuthority());
        assertNull(village.getStatus());
        assertNull(village.getCreatedAt());
        assertNull(village.getUpdatedAt());
        assertNull(village.getArchivedAt());
    }

    @Test
    void updateAndFullPutClearPreserveAllProtectedFieldsAndRelationships() {
        Village village = populatedVillage();
        var governorate = village.getGovernorate();
        var authority = village.getLocalAuthority();
        var before = mapper.toResponse(village);
        for (var request : new VillageRequest[]{
                completeRequest(),
                new VillageRequest(999L, null, "Replacement", null, null, "New location",
                        null, null, null, null)}) {
            mapper.updateEntity(request, village);
            assertEditable(request, village);
            assertEquals(before.id(), village.getId());
            assertSame(governorate, village.getGovernorate());
            assertSame(authority, village.getLocalAuthority());
            assertEquals(before.status(), village.getStatus());
            assertEquals(before.createdAt(), village.getCreatedAt());
            assertEquals(before.updatedAt(), village.getUpdatedAt());
            assertEquals(before.archivedAt(), village.getArchivedAt());
        }
    }

    @Test
    void parentProxyIdentifiersDoNotRequireInitialization() {
        Governorate governorate = mock(Governorate.class,
                withSettings().extraInterfaces(HibernateProxy.class));
        LocalAuthority authority = mock(LocalAuthority.class,
                withSettings().extraInterfaces(HibernateProxy.class));
        LazyInitializer governorateInitializer = mock(LazyInitializer.class);
        LazyInitializer authorityInitializer = mock(LazyInitializer.class);
        when(((HibernateProxy) governorate).getHibernateLazyInitializer()).thenReturn(governorateInitializer);
        when(((HibernateProxy) authority).getHibernateLazyInitializer()).thenReturn(authorityInitializer);
        when(governorateInitializer.getInternalIdentifier()).thenReturn(10L);
        when(authorityInitializer.getInternalIdentifier()).thenReturn(20L);
        Village village = populatedVillage();
        village.setGovernorate(governorate);
        village.setLocalAuthority(authority);

        var response = mapper.toResponse(village);

        assertEquals(10L, response.governorateId());
        assertEquals(20L, response.localAuthorityId());
        verify((HibernateProxy) governorate).getHibernateLazyInitializer();
        verify((HibernateProxy) authority).getHibernateLazyInitializer();
        verify(governorateInitializer).getInternalIdentifier();
        verify(authorityInitializer).getInternalIdentifier();
        verifyNoMoreInteractions(governorate, authority, governorateInitializer, authorityInitializer);
    }
}