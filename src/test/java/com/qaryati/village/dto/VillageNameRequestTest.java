package com.qaryati.village.dto;
import com.qaryati.village.entity.VillageNameType;
import jakarta.validation.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;

class VillageNameRequestTest {
    static ValidatorFactory factory;
    static Validator validator;
    @BeforeAll static void setup() { factory = Validation.buildDefaultValidatorFactory(); validator = factory.getValidator(); }
    @AfterAll static void close() { factory.close(); }
    @ParameterizedTest @EnumSource(VillageNameType.class)
    void acceptsEveryTypeAndBoundaries(VillageNameType type) {
        assertTrue(validator.validate(new VillageNameRequest("n".repeat(200), "l".repeat(12), type, null)).isEmpty());
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {" ", "\t", "\n"})
    void rejectsBlankName(String name) { assertFalse(validator.validate(new VillageNameRequest(name, "ar", VillageNameType.FORMER, null)).isEmpty()); }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {" ", "\t", "\n"})
    void rejectsBlankLanguage(String lang) { assertFalse(validator.validate(new VillageNameRequest("Name", lang, VillageNameType.FORMER, null)).isEmpty()); }
    @Test void rejectsOversizedAndMissingType() {
        assertFalse(validator.validate(new VillageNameRequest("n".repeat(201), "ar", VillageNameType.FORMER, null)).isEmpty());
        assertFalse(validator.validate(new VillageNameRequest("Name", "l".repeat(13), VillageNameType.FORMER, null)).isEmpty());
        assertFalse(validator.validate(new VillageNameRequest("Name", "ar", null, null)).isEmpty());
    }
    @Test void preservesTextAndAcceptsUnrestrictedNotes() {
        var request = new VillageNameRequest("  قرية  ", " EN ", VillageNameType.ALTERNATIVE, "n".repeat(20000));
        assertTrue(validator.validate(request).isEmpty());
        assertEquals("  قرية  ", request.name()); assertEquals(" EN ", request.languageCode());
    }
}