package com.qaryati.dataquality.service;

import com.qaryati.dataquality.dto.CreateVerificationRequest;
import com.qaryati.dataquality.dto.VerificationRecordResponse;
import com.qaryati.dataquality.entity.VerificationRecord;
import com.qaryati.dataquality.enums.VerificationDecision;
import com.qaryati.dataquality.repository.VerificationRecordRepository;
import com.qaryati.dataquality.repository.VillageExistsRepository;
import com.qaryati.security.api.UserLookup;
import com.qaryati.security.api.UserSummary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VerificationRecordServiceTest {
    private final VerificationRecordRepository repository = mock(VerificationRecordRepository.class);
    private final VillageExistsRepository villages = mock(VillageExistsRepository.class);
    private final UserLookup lookup = mock(UserLookup.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final VerificationRecordService service = new VerificationRecordService(repository, lookup, villages, audit);

    @AfterEach
    void clearContext() { SecurityContextHolder.clearContext(); }

    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(" Reviewer ", null, List.of()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"VILLAGE", " VILLAGE ", "\tVILLAGE\n"})
    void retrievesNormalizedTargetAndPreservesResponseFieldsAndRepositoryOrder(String entityType) {
        var reviewedAt = OffsetDateTime.parse("2026-11-08T10:00:00Z");
        var latest = new VerificationRecord("VILLAGE", 7L, 42L, VerificationDecision.VERIFIED, "Reviewed reason");
        ReflectionTestUtils.setField(latest, "id", 9L);
        ReflectionTestUtils.setField(latest, "reviewedAt", reviewedAt);
        var older = new VerificationRecord("VILLAGE", 7L, 43L, VerificationDecision.NEEDS_REVISION, null);
        ReflectionTestUtils.setField(older, "id", 8L);
        ReflectionTestUtils.setField(older, "reviewedAt", reviewedAt.minusDays(1));
        when(repository.findByEntityTypeAndEntityIdOrderByReviewedAtDesc("VILLAGE", 7L))
                .thenReturn(List.of(latest, older));

        assertEquals(List.of(
                new VerificationRecordResponse(9L, "VILLAGE", 7L, 42L, VerificationDecision.VERIFIED, "Reviewed reason", reviewedAt),
                new VerificationRecordResponse(8L, "VILLAGE", 7L, 43L, VerificationDecision.NEEDS_REVISION, null, reviewedAt.minusDays(1))),
                service.getVerifications(entityType, 7L));
        verify(repository).findByEntityTypeAndEntityIdOrderByReviewedAtDesc("VILLAGE", 7L);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(villages, lookup, audit);
    }

    @Test
    void validTargetWithoutRecordsStillReturnsEmptyListWithoutExistenceLookup() {
        when(repository.findByEntityTypeAndEntityIdOrderByReviewedAtDesc("VILLAGE", Long.MAX_VALUE))
                .thenReturn(List.of());
        assertEquals(List.of(), service.getVerifications("VILLAGE", Long.MAX_VALUE));
        verifyNoInteractions(villages, lookup, audit);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "village", "POPULATION_RECORD", "VILLAGES"})
    void rejectsInvalidRetrievalTypesBeforeRepositoryAccess(String entityType) {
        assertThrows(IllegalArgumentException.class, () -> service.getVerifications(entityType, 7L));
        verifyNoInteractions(repository, villages, lookup, audit);
    }

    @Test
    void rejectsOverlongRetrievalTypeBeforeRepositoryAccess() {
        assertEquals("Invalid entity type", assertThrows(IllegalArgumentException.class,
                () -> service.getVerifications("VILLAGE" + " ".repeat(74), 7L)).getMessage());
        verifyNoInteractions(repository, villages, lookup, audit);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void rejectsInvalidRetrievalIdsBeforeRepositoryAccess(Long entityId) {
        assertEquals("Entity ID must be positive", assertThrows(IllegalArgumentException.class,
                () -> service.getVerifications("VILLAGE", entityId)).getMessage());
        verifyNoInteractions(repository, villages, lookup, audit);
    }

    @ParameterizedTest
    @CsvSource({"VERIFIED,VERIFY", "REJECTED,REJECT", "NEEDS_REVISION,UPDATE"})
    void attributesReviewerAndAuditsEveryDecision(VerificationDecision decision, String action) {
        authenticate();
        when(lookup.findByUsername(" Reviewer ")).thenReturn(Optional.of(new UserSummary(42L, true)));
        when(villages.existsById(7L)).thenReturn(true);
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
            VerificationRecord record = invocation.getArgument(0);
            assertEquals(42L, record.getReviewerUserId());
            ReflectionTestUtils.setField(record, "id", 9L);
            return record;
        });
        var response = service.createVerification(new CreateVerificationRequest(" VILLAGE ", 7L, decision, "Reason"));
        assertEquals(42L, response.reviewerUserId());
        assertEquals("VILLAGE", response.entityType());
        assertEquals(decision, response.decision());
        assertEquals("Reason", response.reason());
        verify(audit).record("VERIFICATION_RECORD", 9L, action, null,
                Map.of("entityType", "VILLAGE", "entityId", 7L, "reviewerId", 42L, "decision", decision.name()),
                "Verification decision recorded");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rejectsMissingOrDisabledReviewerBeforeRequestValidation(boolean missing) {
        authenticate();
        when(lookup.findByUsername(" Reviewer ")).thenReturn(missing ? Optional.empty() : Optional.of(new UserSummary(42L, false)));
        var error = missing
                ? assertThrows(IllegalArgumentException.class, () -> service.createVerification(null))
                : assertThrows(IllegalStateException.class, () -> service.createVerification(null));
        assertEquals(missing ? "Reviewer user not found" : "Reviewer account is disabled", error.getMessage());
        verifyNoInteractions(repository, villages, audit);
    }

    @Test
    void rejectsAbsentAndUnauthenticatedActorsBeforeLookup() {
        assertEquals("Authenticated user is required", assertThrows(IllegalArgumentException.class,
                () -> service.createVerification(null)).getMessage());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("reviewer", null));
        assertEquals("Authenticated user is required", assertThrows(IllegalArgumentException.class,
                () -> service.createVerification(null)).getMessage());
        verifyNoInteractions(lookup, repository, villages, audit);
    }

    @Test
    void validatesEntityBeforeVillageLookupAndWrites() {
        authenticate();
        when(lookup.findByUsername(" Reviewer ")).thenReturn(Optional.of(new UserSummary(42L, true)));
        assertEquals("Invalid entity type", assertThrows(IllegalArgumentException.class,
                () -> service.createVerification(new CreateVerificationRequest(" ", -1L, null, null))).getMessage());
        assertEquals("Entity ID must be positive", assertThrows(IllegalArgumentException.class,
                () -> service.createVerification(new CreateVerificationRequest("VILLAGE", -1L, null, null))).getMessage());
        assertEquals("Verification decision is required", assertThrows(IllegalArgumentException.class,
                () -> service.createVerification(new CreateVerificationRequest("VILLAGE", 7L, null, null))).getMessage());
        verifyNoInteractions(repository, villages, audit);
    }
}
