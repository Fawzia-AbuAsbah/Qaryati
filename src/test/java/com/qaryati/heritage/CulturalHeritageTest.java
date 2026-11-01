package com.qaryati.heritage;

import com.qaryati.heritage.dto.*;
import com.qaryati.heritage.entity.*;
import com.qaryati.heritage.mapper.CulturalHeritageMapper;
import jakarta.validation.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

class CulturalHeritageTest {
    private static ValidatorFactory factory;
    private static Validator validator;
    private final CulturalHeritageMapper mapper = new CulturalHeritageMapper();

    @BeforeAll static void setup() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }
    @AfterAll static void close() { factory.close(); }

    static Stream<Arguments> invalidFields() {
        return Stream.of(Arguments.of(null, "Name", CulturalHeritageType.CRAFT, "Text"),
                Arguments.of(1L, null, CulturalHeritageType.CRAFT, "Text"),
                Arguments.of(1L, "x".repeat(201), CulturalHeritageType.CRAFT, "Text"),
                Arguments.of(1L, "Name", null, "Text"),
                Arguments.of(1L, "Changed", CulturalHeritageType.FOOD, null));
    }

    @ParameterizedTest @MethodSource("invalidFields")
    void rejectsInvalidFieldsAcrossDtoEntityAndDomainWithoutPartialRevision(
            Long village, String name, CulturalHeritageType type, String description) {
        var request = new CulturalHeritageRequest(village, name, type, description);
        assertFalse(validator.validate(request).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> mapper.toEntity(request));
        var heritage = mapper.toEntity(request());
        var before = mapper.toResponse(heritage);
        assertThrows(IllegalArgumentException.class, () -> mapper.updateDraft(request, heritage));
        assertEquals(before, mapper.toResponse(heritage));
        ReflectionTestUtils.setField(heritage, "villageId", village);
        ReflectionTestUtils.setField(heritage, "name", name);
        ReflectionTestUtils.setField(heritage, "heritageType", type);
        ReflectionTestUtils.setField(heritage, "description", description);
        assertFalse(validator.validate(heritage).isEmpty());
    }

    @ParameterizedTest @EnumSource(CulturalHeritageType.class)
    void acceptsEveryCategoryAndPreservesSqlCompatibleText(CulturalHeritageType type) {
        for (String name : List.of("", " \t\n", "x".repeat(200), "  تراث القرية  ")) {
            for (String description : List.of("", " \t\n", "  تراث\nحرف وطعام\n".repeat(4000))) {
                var request = new CulturalHeritageRequest(0L, name, type, description);
                assertTrue(validator.validate(request).isEmpty());
                var heritage = mapper.toEntity(request);
                assertTrue(validator.validate(heritage).isEmpty());
                assertEquals(name, heritage.getName());
                assertEquals(description, heritage.getDescription());
                assertEquals(type, heritage.getHeritageType());
            }
        }
    }

    @Test void mapsAllFieldsAndPreservesManagedStateOnRevision() {
        var heritage = mapper.toEntity(request());
        assertNull(heritage.getId());
        assertEquals(VerificationStatus.DRAFT, heritage.getVerificationStatus());
        ReflectionTestUtils.setField(heritage, "id", 42L);
        assertEquals(new CulturalHeritageResponse(42L, 1L, "Name", CulturalHeritageType.CRAFT,
                "Description", VerificationStatus.DRAFT), mapper.toResponse(heritage));
        mapper.updateDraft(new CulturalHeritageRequest(1L, " Revised ", CulturalHeritageType.FOOD, "\n"), heritage);
        assertEquals(new CulturalHeritageResponse(42L, 1L, " Revised ", CulturalHeritageType.FOOD,
                "\n", VerificationStatus.DRAFT), mapper.toResponse(heritage));
    }

    @ParameterizedTest @EnumSource(value = VerificationStatus.class, names = "DRAFT", mode = EnumSource.Mode.EXCLUDE)
    void readsEveryNonDraftStatusAndBlocksRevision(VerificationStatus status) {
        var heritage = mapper.toEntity(request());
        ReflectionTestUtils.setField(heritage, "verificationStatus", status);
        assertTrue(validator.validate(heritage).isEmpty());
        var before = mapper.toResponse(heritage);
        assertEquals(status, before.verificationStatus());
        assertThrows(IllegalStateException.class, heritage::requireDraftMutation);
        assertThrows(IllegalStateException.class, () -> mapper.updateDraft(request(), heritage));
        assertEquals(before, mapper.toResponse(heritage));
    }

    @Test void rejectsNullRequestsVillageReassignmentAndNullStatus() {
        assertThrows(IllegalArgumentException.class, () -> CulturalHeritage.draft(null));
        assertThrows(IllegalArgumentException.class, () -> mapper.toEntity(null));
        var heritage = mapper.toEntity(request());
        assertDoesNotThrow(heritage::requireDraftMutation);
        var before = mapper.toResponse(heritage);
        assertThrows(IllegalArgumentException.class, () -> mapper.updateDraft(null, heritage));
        assertThrows(IllegalArgumentException.class, () -> mapper.updateDraft(
                new CulturalHeritageRequest(2L, "Changed", CulturalHeritageType.FOOD, "Changed"), heritage));
        assertEquals(before, mapper.toResponse(heritage));
        ReflectionTestUtils.setField(heritage, "verificationStatus", null);
        assertFalse(validator.validate(heritage).isEmpty());
        assertThrows(IllegalStateException.class, heritage::requireDraftMutation);
        assertThrows(IllegalStateException.class, () -> mapper.updateDraft(request(), heritage));
    }

    @Test void exactVocabularyRequestFieldsAndNoPublicSettersOrTransitions() {
        assertEquals(Set.of("CRAFT", "FOOD", "TRADITION", "PRACTICE", "OTHER"),
                new HashSet<>(Arrays.stream(CulturalHeritageType.values()).map(Enum::name).toList()));
        assertThrows(IllegalArgumentException.class, () -> CulturalHeritageType.valueOf("INVALID"));
        assertEquals(Set.of("DRAFT", "UNDER_REVIEW", "VERIFIED", "REJECTED", "DISPUTED"),
                new HashSet<>(Arrays.stream(VerificationStatus.values()).map(Enum::name).toList()));
        assertEquals(Set.of("villageId", "name", "heritageType", "description"),
                new HashSet<>(Arrays.stream(CulturalHeritageRequest.class.getRecordComponents()).map(c -> c.getName()).toList()));
        assertEquals(Set.of("id", "villageId", "name", "heritageType", "description", "verificationStatus"),
                new HashSet<>(Arrays.stream(CulturalHeritage.class.getDeclaredFields()).map(f -> f.getName()).toList()));
        assertTrue(Arrays.stream(CulturalHeritage.class.getMethods()).noneMatch(m -> m.getName().startsWith("set")));
        assertTrue(Arrays.stream(CulturalHeritage.class.getDeclaredMethods())
                .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers()))
                .allMatch(m -> Set.of("draft", "reviseDraft", "requireDraftMutation", "getId", "getVillageId",
                        "getName", "getHeritageType", "getDescription", "getVerificationStatus").contains(m.getName())));
    }

    static CulturalHeritageRequest request() {
        return new CulturalHeritageRequest(1L, "Name", CulturalHeritageType.CRAFT, "Description");
    }
}
