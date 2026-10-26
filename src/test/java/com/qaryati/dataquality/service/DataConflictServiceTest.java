package com.qaryati.dataquality.service;

import com.qaryati.dataquality.dto.ResolveDataConflictRequest;
import com.qaryati.dataquality.entity.DataConflict;
import com.qaryati.dataquality.enums.DataConflictStatus;
import com.qaryati.dataquality.repository.DataConflictRepository;
import com.qaryati.dataquality.repository.VillageExistsRepository;
import com.qaryati.security.api.UserLookup;
import com.qaryati.security.api.UserSummary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DataConflictServiceTest {
    private final DataConflictRepository repository = mock(DataConflictRepository.class);
    private final UserLookup lookup = mock(UserLookup.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final DataConflictService service = new DataConflictService(repository,
            mock(VillageExistsRepository.class), lookup, audit);

    @AfterEach
    void clearContext() { SecurityContextHolder.clearContext(); }

    private DataConflict conflict(boolean inReview) {
        var conflict = new DataConflict("VILLAGE", 7L, "population", List.of(Map.of("value", 1), Map.of("value", 2)));
        ReflectionTestUtils.setField(conflict, "id", 9L);
        if (inReview) conflict.markInReview();
        when(repository.findByIdForUpdate(9L)).thenReturn(Optional.of(conflict));
        return conflict;
    }

    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(" Reviewer ", null, List.of()));
    }

    @Test
    void resolvesWithReviewerIdAndPreservesAuditSnapshots() {
        var conflict = conflict(true);
        authenticate();
        when(lookup.findByUsername(" Reviewer ")).thenReturn(Optional.of(new UserSummary(42L, true)));
        when(repository.saveAndFlush(conflict)).thenReturn(conflict);
        var response = service.resolveConflict(9L, new ResolveDataConflictRequest(" Checked "));
        assertEquals(42L, conflict.getResolvedBy());
        assertEquals("Checked", conflict.getResolutionNotes());
        assertEquals(DataConflictStatus.RESOLVED, conflict.getStatus());
        assertNotNull(conflict.getResolvedAt());
        assertEquals(42L, response.resolvedBy());
        verify(audit).record(eq("DATA_CONFLICT"), eq(9L), eq("UPDATE"),
                argThat(before -> before.get("status").equals("IN_REVIEW") && before.get("resolvedBy") == null),
                argThat(after -> after.get("status").equals("RESOLVED") && after.get("resolvedBy").equals(42L)
                        && after.get("resolvedAt").equals(conflict.getResolvedAt().toString())
                        && !after.containsKey("resolutionNotes") && !after.containsKey("conflictingValues")),
                eq("Conflict resolved"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rejectsMissingOrDisabledReviewerWithoutMutation(boolean missing) {
        var conflict = conflict(true);
        authenticate();
        when(lookup.findByUsername(" Reviewer ")).thenReturn(missing ? Optional.empty() : Optional.of(new UserSummary(42L, false)));
        var error = missing
                ? assertThrows(IllegalArgumentException.class, () -> service.resolveConflict(9L, new ResolveDataConflictRequest("Checked")))
                : assertThrows(IllegalStateException.class, () -> service.resolveConflict(9L, new ResolveDataConflictRequest("Checked")));
        assertEquals(missing ? "Reviewer user not found" : "Reviewer account is disabled", error.getMessage());
        assertEquals(DataConflictStatus.IN_REVIEW, conflict.getStatus());
        assertNull(conflict.getResolvedBy());
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @Test
    void preservesStateNotesAndAuthenticationValidationOrder() {
        var conflict = conflict(false);
        assertEquals("Only IN_REVIEW conflicts can be resolved", assertThrows(IllegalStateException.class,
                () -> service.resolveConflict(9L, null)).getMessage());
        conflict.markInReview();
        assertEquals("Resolution notes are required", assertThrows(IllegalArgumentException.class,
                () -> service.resolveConflict(9L, new ResolveDataConflictRequest(" "))).getMessage());
        assertEquals("Authenticated user is required", assertThrows(IllegalArgumentException.class,
                () -> service.resolveConflict(9L, new ResolveDataConflictRequest("Checked"))).getMessage());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("reviewer", null));
        assertEquals("Authenticated user is required", assertThrows(IllegalArgumentException.class,
                () -> service.resolveConflict(9L, new ResolveDataConflictRequest("Checked"))).getMessage());
        verifyNoInteractions(lookup, audit);
        verify(repository, never()).saveAndFlush(any());
    }
}
