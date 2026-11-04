package com.qaryati.village.dto;
import com.qaryati.village.entity.VillageRelationType;
import jakarta.validation.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class VillageRelationRequestTest {
    static ValidatorFactory factory;
    static Validator validator;
    @BeforeAll static void setup() { factory = Validation.buildDefaultValidatorFactory(); validator = factory.getValidator(); }
    @AfterAll static void close() { factory.close(); }
    @ParameterizedTest @EnumSource(VillageRelationType.class)
    void acceptsBothTargetsAndBoundaries(VillageRelationType type) {
        assertTrue(validator.validate(new VillageRelationRequest(2L,null,type,null)).isEmpty());
        var request = new VillageRelationRequest(null," ".repeat(2)+"x".repeat(198),type,new BigDecimal("99999999.99"));
        assertTrue(validator.validate(request).isEmpty());
        assertTrue(request.relatedPlaceName().startsWith("  "));
        assertTrue(validator.validate(new VillageRelationRequest(null,"Place",type,BigDecimal.ZERO)).isEmpty());
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {" ","\t","\n"})
    void rejectsMissingOrBlankTarget(String text) {
        assertFalse(validator.validate(new VillageRelationRequest(null,text,VillageRelationType.OTHER,null)).isEmpty());
    }
    @ParameterizedTest @ValueSource(strings = {"-0.01","100000000","0.001","1.230"})
    void rejectsInvalidDistance(String value) {
        assertFalse(validator.validate(new VillageRelationRequest(2L,null,VillageRelationType.OTHER,new BigDecimal(value))).isEmpty());
    }
    @Test void rejectsBothTargetsBadIdsLengthAndMissingType() {
        for (var request : new VillageRelationRequest[]{
                new VillageRelationRequest(2L,"Place",VillageRelationType.OTHER,null),
                new VillageRelationRequest(0L,null,VillageRelationType.OTHER,null),
                new VillageRelationRequest(-1L,null,VillageRelationType.OTHER,null),
                new VillageRelationRequest(null,"x".repeat(201),VillageRelationType.OTHER,null),
                new VillageRelationRequest(2L,null,null,null)}) assertFalse(validator.validate(request).isEmpty());
    }
}
