package com.qaryati.village.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.qaryati.village.entity.VillageRelationType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonAutoDetect(isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public record VillageRelationRequest(
        @Positive @JsonDeserialize(using = VillageIdDeserializer.class) Long relatedVillageId,
        @Size(max = 200) String relatedPlaceName,
        @NotNull VillageRelationType relationType,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal distanceKm
) {
    @AssertTrue(message = "Exactly one target is required; place names must be nonblank")
    public boolean isTargetValid() {
        return (relatedVillageId != null) != (relatedPlaceName != null)
                && (relatedPlaceName == null || !relatedPlaceName.isBlank());
    }
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported request field: " + name);
    }
    // Local to this DTO: reject fractional/coerced IDs without changing global Jackson settings.
    public static class VillageIdDeserializer extends ValueDeserializer<Long> {
        @Override
        public Long deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT)
                throw new IllegalArgumentException("relatedVillageId must be an integer");
            return parser.getLongValue();
        }
    }
}
