package com.qaryati.heritage;

import com.qaryati.heritage.dto.*;
import com.qaryati.heritage.entity.*;
import com.qaryati.heritage.mapper.HeritageSiteMapper;
import jakarta.validation.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

class HeritageSiteTest {
    private static ValidatorFactory factory;
    private static Validator validator;
    private final HeritageSiteMapper mapper = new HeritageSiteMapper();
    @BeforeAll static void setup() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }
    @AfterAll static void close() { factory.close(); }

    static Stream<Arguments> coordinates() {
        return Stream.of(
                Arguments.of(null, null, true),
                Arguments.of("0", null, false), Arguments.of(null, "0", false),
                Arguments.of("90.000000", "180.000000", true),
                Arguments.of("-90.000000", "-180.000000", true),
                Arguments.of("0.000001", "-0.000001", true),
                Arguments.of("89.999999", "179.999999", true),
                Arguments.of("90.000001", "0", false), Arguments.of("-90.000001", "0", false),
                Arguments.of("0", "180.000001", false), Arguments.of("0", "-180.000001", false),
                Arguments.of("0.0000001", "0", false), Arguments.of("0", "0.0000001", false),
                Arguments.of("1.0000000", "0", false), Arguments.of("0", "1.0000000", false),
                Arguments.of("1000", "0", false), Arguments.of("0", "1000", false),
                Arguments.of("1E+1", "1E+2", true), Arguments.of("0", "0", true));
    }

    @ParameterizedTest @MethodSource("coordinates")
    void validatesDtoEntityAndDomain(String latitude, String longitude, boolean valid) {
        var request = request(decimal(latitude), decimal(longitude));
        assertEquals(valid, validator.validate(request).isEmpty());
        var site = mapper.toEntity(request(null, null));
        ReflectionTestUtils.setField(site, "latitude", request.latitude());
        ReflectionTestUtils.setField(site, "longitude", request.longitude());
        assertEquals(valid, validator.validate(site).isEmpty());
        if (valid) assertDoesNotThrow(() -> mapper.toEntity(request));
        else assertThrows(IllegalArgumentException.class, () -> mapper.toEntity(request));
    }

    static Stream<Arguments> requiredFields() {
        return Stream.of(Arguments.of(null, "Name", HeritageSiteType.OTHER),
                Arguments.of(1L, null, HeritageSiteType.OTHER),
                Arguments.of(1L, "x".repeat(251), HeritageSiteType.OTHER),
                Arguments.of(1L, "Name", null));
    }
    @ParameterizedTest @MethodSource("requiredFields")
    void rejectsInvalidRequiredFields(Long village, String name, HeritageSiteType type) {
        var request = new HeritageSiteRequest(village, name, type, null, null, null, null);
        assertFalse(validator.validate(request).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> mapper.toEntity(request));
        var site = mapper.toEntity(request(null, null));
        ReflectionTestUtils.setField(site, "villageId", village);
        ReflectionTestUtils.setField(site, "name", name);
        ReflectionTestUtils.setField(site, "siteType", type);
        assertFalse(validator.validate(site).isEmpty());
    }

    @ParameterizedTest @EnumSource(HeritageSiteType.class)
    void acceptsEveryTypeAndSchemaNullableFields(HeritageSiteType type) {
        for (String name : List.of("", " ", "x".repeat(250))) {
            var request = new HeritageSiteRequest(0L, name, type, null, null, null, null);
            assertTrue(validator.validate(request).isEmpty());
            assertTrue(validator.validate(mapper.toEntity(request)).isEmpty());
        }
    }

    @Test void mapsAllFieldsAndPreservesManagedFields() {
        var request = new HeritageSiteRequest(1L, "Name", HeritageSiteType.SPRING,
                "Description", decimal("32.123456"), decimal("35.123456"), "Notes");
        var site = mapper.toEntity(request);
        assertNull(site.getId());
        assertEquals(VerificationStatus.DRAFT, site.getVerificationStatus());
        ReflectionTestUtils.setField(site, "id", 42L);
        assertEquals(new HeritageSiteResponse(42L, 1L, request.name(), request.siteType(),
                request.description(), request.latitude(), request.longitude(), request.conditionNotes(),
                VerificationStatus.DRAFT), mapper.toResponse(site));
        mapper.updateDraft(request(null, null), site);
        assertEquals(42L, site.getId());
        assertEquals(1L, site.getVillageId());
        assertEquals(VerificationStatus.DRAFT, site.getVerificationStatus());
        assertNull(site.getLatitude());
        assertNull(site.getLongitude());
        assertNull(site.getDescription());
        assertNull(site.getConditionNotes());
        assertEquals(HeritageSiteType.OTHER, site.getSiteType());
    }

    @ParameterizedTest @EnumSource(value = VerificationStatus.class, names = "DRAFT", mode = EnumSource.Mode.EXCLUDE)
    void blocksEveryNonDraftStatus(VerificationStatus status) {
        var site = mapper.toEntity(request(null, null));
        ReflectionTestUtils.setField(site, "verificationStatus", status);
        assertTrue(validator.validate(site).isEmpty());
        assertEquals(status, mapper.toResponse(site).verificationStatus());
        assertThrows(IllegalStateException.class, site::requireDraftMutation);
        assertThrows(IllegalStateException.class, () -> mapper.updateDraft(request(null, null), site));
    }

    @Test void rejectsInvalidRevisionsAtomicallyAndVillageReassignment() {
        var site = mapper.toEntity(request(decimal("30"), decimal("35")));
        var before = mapper.toResponse(site);
        assertDoesNotThrow(site::requireDraftMutation);
        assertThrows(IllegalArgumentException.class, () -> site.reviseDraft(request(decimal("91"), decimal("35"))));
        assertThrows(IllegalArgumentException.class, () -> site.reviseDraft(
                new HeritageSiteRequest(2L, "Changed", HeritageSiteType.OTHER, null, null, null, null)));
        assertThrows(IllegalArgumentException.class, () -> site.reviseDraft(null));
        assertThrows(IllegalArgumentException.class, () -> HeritageSite.draft(null));
        assertEquals(before, mapper.toResponse(site));
        ReflectionTestUtils.setField(site, "verificationStatus", null);
        assertFalse(validator.validate(site).isEmpty());
        assertThrows(IllegalStateException.class, site::requireDraftMutation);
    }

    @Test void exactVocabularyAndNoManagedRequestFieldsOrPublicSetters() {
        assertEquals(Set.of("ARCHAEOLOGICAL", "TRADITIONAL_BUILDING", "SPRING", "RELIGIOUS", "VIEWPOINT", "OTHER"),
                new HashSet<>(Arrays.stream(HeritageSiteType.values()).map(Enum::name).toList()));
        assertThrows(IllegalArgumentException.class, () -> HeritageSiteType.valueOf("INVALID"));
        assertEquals(Set.of("villageId", "name", "siteType", "description", "latitude", "longitude", "conditionNotes"),
                new HashSet<>(Arrays.stream(HeritageSiteRequest.class.getRecordComponents()).map(c -> c.getName()).toList()));
        assertTrue(Arrays.stream(HeritageSite.class.getMethods()).noneMatch(m -> m.getName().startsWith("set")));
    }

    static BigDecimal decimal(String value) { return value == null ? null : new BigDecimal(value); }
    static HeritageSiteRequest request(BigDecimal latitude, BigDecimal longitude) {
        return new HeritageSiteRequest(1L, "Name", HeritageSiteType.OTHER, null, latitude, longitude, null);
    }
}
