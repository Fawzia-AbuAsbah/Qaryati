package com.qaryati.dataquality.service;

import com.qaryati.dataquality.dto.CreateSourceRequest;
import com.qaryati.dataquality.entity.Source;
import com.qaryati.dataquality.enums.SourceType;
import com.qaryati.dataquality.repository.SourceRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Pure mocked service tests; HTTP validation/security is covered by SourcePutControllerTest.
 * No real persistence, pessimistic-lock behavior or database rollback is asserted here. */
class SourcePutServiceTest {
    private static final OffsetDateTime CREATED = OffsetDateTime.parse("2020-01-01T10:00:00Z");
    private static final String OLD_URL = "https://example.com/old?reference=synthetic-old";
    private static final String NEW_URL = "https://example.com/new?reference=synthetic-new";
    private static final String OLD_CITATION = "Synthetic old citation not copied into audit";
    private static final String NEW_CITATION = "Synthetic new citation not copied into audit";
    private final SourceRepository repository = mock(SourceRepository.class);
    private final AuditLogService audits = mock(AuditLogService.class);
    private final SourceService service = new SourceService(repository, audits);
    private Source original;

    @BeforeEach
    void existingSource() {
        original = new Source("Original", "BOOK");
        ReflectionTestUtils.setField(original, "id", 7L);
        ReflectionTestUtils.setField(original, "createdAt", CREATED);
        original.setPublisher("Old publisher");
        original.setAuthor("Old author");
        original.setUrl(OLD_URL);
        original.setPublicationDate(LocalDate.of(2010, 1, 2));
        original.setAccessedAt(LocalDate.of(2020, 1, 2));
        original.setCitation(OLD_CITATION);
    }

    private void updateExists() {
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(original));
        when(repository.saveAndFlush(original)).thenReturn(original);
    }

    private CreateSourceRequest replacement(String url, String citation) {
        return new CreateSourceRequest("Replacement", "New publisher", "New author", SourceType.WEBSITE,
                url, LocalDate.of(2022, 3, 4), LocalDate.of(2026, 11, 26), citation);
    }

    private Map<String, Object> beforeSnapshot() {
        return Map.of("title", "Original", "publisher", "Old publisher", "author", "Old author",
                "sourceType", "BOOK", "publicationDate", "2010-01-02", "accessedAt", "2020-01-02",
                "hasUrl", true, "hasCitation", true);
    }

    @Test
    void replacementPreservesIdentityAndCreationTimeAndCapturesExactAuditSnapshots() {
        updateExists();
        Source result = service.updateSource(7L, replacement(NEW_URL, NEW_CITATION));
        assertSame(original, result);
        assertAll(
                () -> assertEquals(7L, result.getId()),
                () -> assertEquals(CREATED, result.getCreatedAt()),
                () -> assertEquals("Replacement", result.getTitle()),
                () -> assertEquals("New publisher", result.getPublisher()),
                () -> assertEquals("New author", result.getAuthor()),
                () -> assertEquals("WEBSITE", result.getSourceType()),
                () -> assertEquals(NEW_URL, result.getUrl()),
                () -> assertEquals(LocalDate.of(2022, 3, 4), result.getPublicationDate()),
                () -> assertEquals(LocalDate.of(2026, 11, 26), result.getAccessedAt()),
                () -> assertEquals(NEW_CITATION, result.getCitation()));
        Map<String, Object> after = Map.of(
                "title", "Replacement", "publisher", "New publisher", "author", "New author",
                "sourceType", "WEBSITE", "publicationDate", "2022-03-04", "accessedAt", "2026-11-26",
                "hasUrl", true, "hasCitation", true, "urlChanged", true, "citationChanged", true);
        // Exact map equality rejects extra keys/values, including raw URL/citation, identity and timestamps.
        var order = inOrder(repository, audits);
        order.verify(repository).findByIdForUpdate(7L);
        order.verify(repository).saveAndFlush(original);
        order.verify(audits).record("SOURCE", 7L, "UPDATE", beforeSnapshot(), after, "Source updated");
        order.verifyNoMoreInteractions();
    }

    @Test
    void nullReplacementClearsEveryOptionalFieldAndRecordsItsAbsence() {
        updateExists();
        var request = new CreateSourceRequest("Replacement", null, null, SourceType.BOOK, null, null, null, null);
        Source result = service.updateSource(7L, request);
        assertAll(
                () -> assertNull(result.getPublisher()),
                () -> assertNull(result.getAuthor()),
                () -> assertNull(result.getUrl()),
                () -> assertNull(result.getPublicationDate()),
                () -> assertNull(result.getAccessedAt()),
                () -> assertNull(result.getCitation()),
                () -> assertEquals(7L, result.getId()),
                () -> assertEquals(CREATED, result.getCreatedAt()));
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("title", "Replacement");
        after.put("publisher", null);
        after.put("author", null);
        after.put("sourceType", "BOOK");
        after.put("publicationDate", null);
        after.put("accessedAt", null);
        after.put("hasUrl", false);
        after.put("hasCitation", false);
        after.put("urlChanged", true);
        after.put("citationChanged", true);
        verify(audits).record("SOURCE", 7L, "UPDATE", beforeSnapshot(), after, "Source updated");
        verifyNoMoreInteractions(audits);
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "false,true", "true,true"})
    void sensitiveFieldChangeFlagsAreIndependentWithoutCopyingTheirValues(boolean urlChanged, boolean citationChanged) {
        updateExists();
        service.updateSource(7L, replacement(urlChanged ? NEW_URL : OLD_URL,
                citationChanged ? NEW_CITATION : OLD_CITATION));
        verify(audits).record(eq("SOURCE"), eq(7L), eq("UPDATE"), eq(beforeSnapshot()),
                argThat(after -> after.size() == 10
                        && Boolean.valueOf(urlChanged).equals(after.get("urlChanged"))
                        && Boolean.valueOf(citationChanged).equals(after.get("citationChanged"))
                        && Boolean.TRUE.equals(after.get("hasUrl"))
                        && Boolean.TRUE.equals(after.get("hasCitation"))
                        && !after.containsKey("url") && !after.containsKey("citation")
                        && !after.containsValue(OLD_URL) && !after.containsValue(NEW_URL)
                        && !after.containsValue(OLD_CITATION) && !after.containsValue(NEW_CITATION)),
                eq("Source updated"));
        verifyNoMoreInteractions(audits);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
    void invalidIdentifiersNeverReachStorageOrAudit(Long id) {
        assertThrows(IllegalArgumentException.class, () -> service.updateSource(id, replacement(NEW_URL, NEW_CITATION)));
        verifyNoInteractions(repository, audits);
    }

    @Test
    void unknownSourceDoesNotSaveOrAudit() {
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> service.updateSource(7L, replacement(NEW_URL, NEW_CITATION)));
        verify(repository).findByIdForUpdate(7L);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(audits);
    }

    @Test
    void saveFailurePropagatesWithoutWritingAuditOrRetrying() {
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(original));
        var failure = new IllegalStateException("Synthetic save failure");
        when(repository.saveAndFlush(original)).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> service.updateSource(7L, replacement(NEW_URL, NEW_CITATION))));
        verify(repository).findByIdForUpdate(7L);
        verify(repository).saveAndFlush(original);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(audits);
    }

    @Test
    void auditFailureIsPropagatedAfterSaveWithoutRetryingOrClaimingRollback() {
        updateExists();
        var failure = new IllegalStateException("Synthetic audit failure");
        doThrow(failure).when(audits).record(eq("SOURCE"), eq(7L), eq("UPDATE"), anyMap(), anyMap(), eq("Source updated"));
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> service.updateSource(7L, replacement(NEW_URL, NEW_CITATION))));
        var order = inOrder(repository, audits);
        order.verify(repository).findByIdForUpdate(7L);
        order.verify(repository).saveAndFlush(original);
        order.verify(audits).record(eq("SOURCE"), eq(7L), eq("UPDATE"), anyMap(), anyMap(), eq("Source updated"));
        order.verifyNoMoreInteractions();
        // A mutated in-memory mock fixture cannot demonstrate database commit or rollback.
    }
}
