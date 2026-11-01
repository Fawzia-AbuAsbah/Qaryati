package com.qaryati.heritage;

import com.qaryati.heritage.dto.*;
import com.qaryati.heritage.entity.*;
import com.qaryati.heritage.mapper.VillageHistoryMapper;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.validation.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

class VillageHistoryTest {
    private static ValidatorFactory factory;
    private static Validator validator;
    private final VillageHistoryMapper mapper = new VillageHistoryMapper();

    @BeforeAll static void setup() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }
    @AfterAll static void close() { factory.close(); }

    static Stream<Arguments> invalidFields() {
        return Stream.of(Arguments.of(null, "Overview"), Arguments.of(1L, null),
                Arguments.of(1L, ""), Arguments.of(1L, " \t\n"));
    }

    @ParameterizedTest @MethodSource("invalidFields")
    void validatesDtoEntityAndDomain(Long villageId, String overview) {
        var request = new VillageHistoryRequest(villageId, overview);
        assertFalse(validator.validate(request).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> mapper.toEntity(request));
        var history = draft();
        ReflectionTestUtils.setField(history, "villageId", villageId);
        ReflectionTestUtils.setField(history, "overview", overview);
        assertFalse(validator.validate(history).isEmpty());
    }

    @Test void preservesArabicMultilineAndLongTextWithoutTrimming() {
        String text = "  تاريخ القرية\nتراثها وحكايات أهلها\n".repeat(4000) + "  ";
        var request = new VillageHistoryRequest(7L, text);
        var history = mapper.toEntity(request);
        assertTrue(validator.validate(request).isEmpty());
        assertTrue(validator.validate(history).isEmpty());
        assertEquals(text, history.getOverview());
        assertEquals(text, mapper.toResponse(history).overview());
        assertNull(history.getId());
        assertNull(history.getUpdatedAt());
        assertEquals(VerificationStatus.DRAFT, history.getVerificationStatus());
    }

    @Test void mapsEveryFieldAndPreservesManagedStateUntilJpaCallback() {
        var history = draft();
        Instant timestamp = Instant.parse("2020-01-01T00:00:00Z");
        ReflectionTestUtils.setField(history, "id", 42L);
        ReflectionTestUtils.setField(history, "updatedAt", timestamp);
        assertEquals(new VillageHistoryResponse(42L, 1L, "Overview", timestamp,
                VerificationStatus.DRAFT), mapper.toResponse(history));
        mapper.updateDraft(new VillageHistoryRequest(1L, " Revised\nنص "), history);
        assertEquals(" Revised\nنص ", history.getOverview());
        assertEquals(42L, history.getId());
        assertEquals(1L, history.getVillageId());
        assertEquals(timestamp, history.getUpdatedAt());
        assertEquals(VerificationStatus.DRAFT, history.getVerificationStatus());
    }

    @ParameterizedTest @EnumSource(VerificationStatus.class)
    void readsEveryStatusAndAllowsOnlyDraftRevision(VerificationStatus status) {
        var history = draft();
        ReflectionTestUtils.setField(history, "verificationStatus", status);
        assertTrue(validator.validate(history).isEmpty());
        assertEquals(status, mapper.toResponse(history).verificationStatus());
        if (status == VerificationStatus.DRAFT) {
            assertDoesNotThrow(history::requireDraftMutation);
            assertDoesNotThrow(() -> mapper.updateDraft(new VillageHistoryRequest(1L, "Changed"), history));
        } else {
            assertThrows(IllegalStateException.class, history::requireDraftMutation);
            assertThrows(IllegalStateException.class,
                    () -> mapper.updateDraft(new VillageHistoryRequest(1L, "Changed"), history));
            assertEquals("Overview", history.getOverview());
        }
    }

    @Test void rejectsNullStatus() {
        var history = draft();
        ReflectionTestUtils.setField(history, "verificationStatus", null);
        assertFalse(validator.validate(history).isEmpty());
        assertThrows(IllegalStateException.class, history::requireDraftMutation);
        assertThrows(IllegalStateException.class,
                () -> history.reviseDraft(new VillageHistoryRequest(1L, "Changed")));
        assertEquals("Overview", history.getOverview());
    }

    @Test void rejectsNullRequestsReassignmentAndInvalidRevisionAtomically() {
        assertThrows(IllegalArgumentException.class, () -> mapper.toEntity(null));
        var history = draft();
        var before = mapper.toResponse(history);
        for (VillageHistoryRequest request : Arrays.asList(null,
                new VillageHistoryRequest(2L, "Changed"), new VillageHistoryRequest(null, "Changed"),
                new VillageHistoryRequest(1L, null), new VillageHistoryRequest(1L, " \n"))) {
            assertThrows(IllegalArgumentException.class, () -> mapper.updateDraft(request, history));
            assertEquals(before, mapper.toResponse(history));
        }
    }

    @Test void lifecycleCallbacksMaintainTimestampWithoutSleeping() throws Exception {
        var history = draft();
        var insert = VillageHistory.class.getDeclaredMethod("initializeTimestamp");
        var update = VillageHistory.class.getDeclaredMethod("updateTimestamp");
        assertTrue(insert.isAnnotationPresent(PrePersist.class));
        assertTrue(update.isAnnotationPresent(PreUpdate.class));
        Instant before = Instant.now();
        ReflectionTestUtils.invokeMethod(history, "initializeTimestamp");
        assertFalse(history.getUpdatedAt().isBefore(before));
        assertFalse(history.getUpdatedAt().isAfter(Instant.now()));
        ReflectionTestUtils.setField(history, "updatedAt", Instant.EPOCH);
        before = Instant.now();
        ReflectionTestUtils.invokeMethod(history, "updateTimestamp");
        assertFalse(history.getUpdatedAt().isBefore(before));
        assertFalse(history.getUpdatedAt().isAfter(Instant.now()));
        assertEquals("Overview", history.getOverview());
    }

    @Test void requestAndEntityExposeOnlyFoundationFields() {
        assertEquals(Set.of("villageId", "overview"), new HashSet<>(Arrays.stream(
                VillageHistoryRequest.class.getRecordComponents()).map(c -> c.getName()).toList()));
        assertEquals(Set.of("id", "villageId", "overview", "updatedAt", "verificationStatus"),
                new HashSet<>(Arrays.stream(VillageHistory.class.getDeclaredFields()).map(f -> f.getName()).toList()));
        assertTrue(Arrays.stream(VillageHistory.class.getMethods()).noneMatch(m -> m.getName().startsWith("set")));
    }

    private VillageHistory draft() { return mapper.toEntity(new VillageHistoryRequest(1L, "Overview")); }
}
