package com.qaryati.village.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record VillageRequest(
        @NotNull(message = "Governorate is required")
        @Positive
        Long governorateId,

        @Positive
        Long localAuthorityId,

        @NotBlank(message = "Arabic name is required")
        @Size(max = 180)
        String nameAr,

        @Size(max = 180)
        String nameEn,

        String nameOrigin,

        @NotBlank(message = "Location description is required")
        String locationDescription,

        @DecimalMin("-90") @DecimalMax("90")
        BigDecimal latitude,

        @DecimalMin("-180") @DecimalMax("180")
        BigDecimal longitude,

        BigDecimal elevationM,

        @DecimalMin(value = "0", inclusive = false)
        BigDecimal areaKm2
) {
    @AssertTrue(message = "Latitude and longitude must both be supplied or both be null")
    public boolean isCoordinatesPaired() {
        return (latitude == null) == (longitude == null);
    }

    @AssertTrue(message = "Latitude must fit NUMERIC(9,6) without rounding")
    public boolean isLatitudeRepresentable() {
        return fitsNumeric(latitude, 9, 6);
    }

    @AssertTrue(message = "Longitude must fit NUMERIC(9,6) without rounding")
    public boolean isLongitudeRepresentable() {
        return fitsNumeric(longitude, 9, 6);
    }

    @AssertTrue(message = "Elevation must fit NUMERIC(9,2) without rounding")
    public boolean isElevationRepresentable() {
        return fitsNumeric(elevationM, 9, 2);
    }

    @AssertTrue(message = "Area must fit NUMERIC(12,3) without rounding")
    public boolean isAreaRepresentable() {
        return fitsNumeric(areaKm2, 12, 3);
    }

    private static boolean fitsNumeric(BigDecimal value, int precision, int scale) {
        if (value == null || value.signum() == 0) {
            return true;
        }
        // Inspect numeric value, accepting trailing zeros and scientific notation.
        // Long arithmetic avoids overflow for extreme BigDecimal scales.
        BigDecimal normalized = value.stripTrailingZeros();
        long integerDigits = (long) normalized.precision() - normalized.scale();
        return normalized.scale() <= scale && integerDigits <= precision - scale;
    }

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported request field: " + name);
    }
}