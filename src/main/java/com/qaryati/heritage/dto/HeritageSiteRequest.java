package com.qaryati.heritage.dto;

import com.qaryati.heritage.entity.HeritageSiteType;
import com.qaryati.heritage.validation.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

@ValidCoordinatePair
public record HeritageSiteRequest(
        @NotNull Long villageId,
        @NotNull @Size(max = 250) String name,
        @NotNull HeritageSiteType siteType,
        String description,
        @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 3, fraction = 6) BigDecimal latitude,
        @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 6) BigDecimal longitude,
        String conditionNotes) implements CoordinateFields {
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
}
