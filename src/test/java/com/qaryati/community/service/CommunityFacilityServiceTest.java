package com.qaryati.community.service;

import com.qaryati.community.dto.CommunityFacilityRequest;
import com.qaryati.community.entity.CommunityFacility;
import com.qaryati.community.entity.CommunityFacilityType;
import com.qaryati.community.mapper.CommunityFacilityMapper;
import com.qaryati.community.repository.CommunityFacilityRepository;
import com.qaryati.village.api.VillageLookupApi;
import com.qaryati.village.api.VillageLookupResult;
import com.qaryati.village.api.VillageLookupStatus;
import com.qaryati.dataquality.api.AuditAction;
import com.qaryati.dataquality.api.AuditWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CommunityFacilityServiceTest {

    private CommunityFacilityRepository repository;
    private VillageLookupApi villages;
    private AuditWriter audits;
    private CommunityFacilityService service;

    @BeforeEach
    void setUp() {
        repository = mock(CommunityFacilityRepository.class);
        villages = mock(VillageLookupApi.class);
        audits = mock(AuditWriter.class);
        service = new CommunityFacilityService(repository, new CommunityFacilityMapper(), villages, audits);
    }

    @Test
    void createsFacilityForExistingVillage() {
        existingVillage(1L);
        when(repository.saveAndFlush(any(CommunityFacility.class)))
                .thenAnswer(invocation -> {
                    var facility = invocation.getArgument(0, CommunityFacility.class);
                    id(facility, 5L);
                    return facility;
                });

        var response = service.create(request(1L, "Village school", CommunityFacilityType.SCHOOL));

        assertEquals(1L, response.villageId());
        assertEquals(CommunityFacilityType.SCHOOL, response.facilityType());
        verify(repository).saveAndFlush(any(CommunityFacility.class));
        verify(audits).recordCommunityFacility(eq(5L), eq(AuditAction.CREATE), isNull(),
                argThat(snapshot -> snapshot.villageId().equals(1L)
                        && "Village school".equals(snapshot.name())
                        && "SCHOOL".equals(snapshot.facilityType())
                        && new BigDecimal("31.500000").equals(snapshot.latitude())));
    }

    @Test
    void rejectsMissingVillageBeforeWrite() {
        when(villages.findById(1L)).thenReturn(Optional.empty());

        assertStatus(404, () -> service.create(request(1L, "Village school", CommunityFacilityType.SCHOOL)));
        verifyNoInteractions(repository);
        verifyNoInteractions(audits);
    }

    @Test
    void getsAllOrFiltersByVillage() {
        var facility = new CommunityFacility(1L, "Village school", CommunityFacilityType.SCHOOL,
                null, null, null);
        when(repository.findAll()).thenReturn(List.of(facility));
        when(repository.findByVillageId(1L)).thenReturn(List.of(facility));

        assertEquals(1, service.getAll(null).size());
        assertEquals(1, service.getAll(1L).size());
        verify(repository).findAll();
        verify(repository).findByVillageId(1L);
    }

    @Test
    void rejectsInvalidIdsAndMissingFacilities() {
        assertStatus(400, () -> service.getById(0L));
        assertStatus(400, () -> service.getAll(-1L));
        assertStatus(400, () -> service.delete(0L));
        when(repository.findById(9L)).thenReturn(Optional.empty());
        assertStatus(404, () -> service.getById(9L));
    }

    @Test
    void updatesVillageAndCoordinatesAfterLookup() {
        var facility = new CommunityFacility(1L, "Village school", CommunityFacilityType.SCHOOL,
                null, null, null);
        id(facility, 5L);
        when(repository.findById(5L)).thenReturn(Optional.of(facility));
        existingVillage(2L);
        when(repository.saveAndFlush(facility)).thenReturn(facility);

        var response = service.update(5L, request(2L, "Village clinic", CommunityFacilityType.CLINIC));

        assertEquals(2L, response.villageId());
        assertEquals(CommunityFacilityType.CLINIC, response.facilityType());
        assertEquals(new BigDecimal("31.500000"), response.latitude());
        verify(audits).recordCommunityFacility(eq(5L), eq(AuditAction.UPDATE),
                argThat(snapshot -> snapshot.villageId().equals(1L) && snapshot.latitude() == null),
                argThat(snapshot -> snapshot.villageId().equals(2L)
                        && "CLINIC".equals(snapshot.facilityType())
                        && new BigDecimal("31.500000").equals(snapshot.latitude())));
    }

    @Test
    void deletesExistingFacility() {
        var facility = new CommunityFacility(1L, "Village school", CommunityFacilityType.SCHOOL,
                null, null, null);
        id(facility, 5L);
        when(repository.findById(5L)).thenReturn(Optional.of(facility));

        service.delete(5L);

        verify(repository).delete(facility);
        verify(repository).flush();
        verify(audits).recordCommunityFacility(eq(5L), eq(AuditAction.DELETE),
                argThat(snapshot -> snapshot.villageId().equals(1L) && snapshot.latitude() == null), isNull());
    }

    @Test
    void translatesDatabaseConstraintFailures() {
        existingVillage(1L);
        when(repository.saveAndFlush(any(CommunityFacility.class)))
                .thenThrow(new DataIntegrityViolationException("constraint"));

        assertStatus(409, () -> service.create(request(1L, "Village school", CommunityFacilityType.SCHOOL)));
        verifyNoInteractions(audits);
    }

    @Test
    void propagatesAuditFailureAfterSuccessfulCreate() {
        existingVillage(1L);
        when(repository.saveAndFlush(any(CommunityFacility.class))).thenAnswer(invocation -> {
            var facility = invocation.getArgument(0, CommunityFacility.class);
            id(facility, 5L);
            return facility;
        });
        doThrow(new IllegalStateException("audit failure")).when(audits).recordCommunityFacility(
                eq(5L), eq(AuditAction.CREATE), isNull(), any());

        assertEquals("audit failure", assertThrows(IllegalStateException.class,
                () -> service.create(request(1L, "Village school", CommunityFacilityType.SCHOOL))).getMessage());
        InOrder order = inOrder(repository, audits);
        order.verify(repository).saveAndFlush(any(CommunityFacility.class));
        order.verify(audits).recordCommunityFacility(eq(5L), eq(AuditAction.CREATE), isNull(), any());
    }

    private CommunityFacilityRequest request(Long villageId, String name, CommunityFacilityType type) {
        return new CommunityFacilityRequest(villageId, name, type, "Description",
                new BigDecimal("31.500000"), new BigDecimal("35.200000"));
    }

    private void existingVillage(Long id) {
        when(villages.findById(id)).thenReturn(Optional.of(
                new VillageLookupResult(id, VillageLookupStatus.DRAFT, false)));
    }

    private void assertStatus(int expectedStatus, Runnable operation) {
        var exception = assertThrows(ResponseStatusException.class, operation::run);
        assertEquals(expectedStatus, exception.getStatusCode().value());
    }

    private void id(CommunityFacility facility, Long id) {
        ReflectionTestUtils.setField(facility, "id", id);
    }
}
