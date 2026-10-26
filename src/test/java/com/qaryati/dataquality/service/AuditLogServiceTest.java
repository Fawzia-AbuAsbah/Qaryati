package com.qaryati.dataquality.service;

import com.qaryati.dataquality.entity.AuditLog;
import com.qaryati.dataquality.repository.AuditLogRepository;
import com.qaryati.security.api.UserLookup;
import com.qaryati.security.api.UserSummary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuditLogServiceTest {
    private final AuditLogRepository repository = mock(AuditLogRepository.class);
    private final UserLookup lookup = mock(UserLookup.class);
    private final AuditLogService service = new AuditLogService(repository, lookup);

    @AfterEach
    void clearContext() { SecurityContextHolder.clearContext(); }

    private void authenticate(String name) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(name, null, List.of()));
    }

    private void record() {
        service.record("VILLAGE", 7L, "UPDATE", Map.of("name", "before"),
                Map.of("name", "after"), "Changed");
    }

    @Test
    void recordsIdAndExactAuthenticationNameAndSnapshots() {
        authenticate(" Reviewer ");
        when(lookup.findByUsername(" Reviewer ")).thenReturn(Optional.of(new UserSummary(42L, true)));
        record();
        var captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        var log = captor.getValue();
        assertEquals(42L, log.getActorUserId());
        assertEquals(" Reviewer ", log.getActorLabel());
        assertEquals("VILLAGE", log.getEntityType());
        assertEquals(7L, log.getEntityId());
        assertEquals("UPDATE", log.getAction());
        assertEquals(Map.of("name", "before"), log.getBeforeData());
        assertEquals(Map.of("name", "after"), log.getAfterData());
        assertEquals("Changed", log.getReason());
        assertNull(log.getCorrelationId());
    }

    @Test
    void rejectsMissingActor() {
        authenticate("reviewer");
        when(lookup.findByUsername("reviewer")).thenReturn(Optional.empty());
        assertEquals("Audit actor not found", assertThrows(IllegalStateException.class, this::record).getMessage());
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsDisabledActor() {
        authenticate("reviewer");
        when(lookup.findByUsername("reviewer")).thenReturn(Optional.of(new UserSummary(42L, false)));
        assertEquals("Audit actor account is disabled", assertThrows(IllegalStateException.class, this::record).getMessage());
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "anonymousUser"})
    void rejectsInvalidAuthenticationNames(String name) {
        authenticate(name);
        assertEquals("Authenticated user required for audit", assertThrows(IllegalStateException.class, this::record).getMessage());
        verifyNoInteractions(lookup, repository);
    }

    @Test
    void rejectsAbsentAndUnauthenticatedActors() {
        assertEquals("Authenticated user required for audit", assertThrows(IllegalStateException.class, this::record).getMessage());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("reviewer", null));
        assertEquals("Authenticated user required for audit", assertThrows(IllegalStateException.class, this::record).getMessage());
        verifyNoInteractions(lookup, repository);
    }

    @Test
    void validatesAuditDataBeforeAuthenticationOrLookup() {
        assertEquals("Invalid audit entity type", assertThrows(IllegalArgumentException.class,
                () -> service.record(" ", -1L, "INVALID", null, null, null)).getMessage());
        assertEquals("Invalid audit entity ID", assertThrows(IllegalArgumentException.class,
                () -> service.record("VILLAGE", -1L, "INVALID", null, null, null)).getMessage());
        assertEquals("Invalid audit action", assertThrows(IllegalArgumentException.class,
                () -> service.record("VILLAGE", 7L, "INVALID", null, null, null)).getMessage());
        verifyNoInteractions(lookup, repository);
    }
}
