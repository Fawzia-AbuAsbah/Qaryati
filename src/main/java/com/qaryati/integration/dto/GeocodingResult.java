package com.qaryati.integration.dto;

import java.math.BigDecimal;

/** Provider-neutral geocoding result exposed by Qaryati. */
public record GeocodingResult(
        BigDecimal latitude,
        BigDecimal longitude,
        String formattedAddress,
        String addressLine1,
        String addressLine2,
        String city,
        String country,
        String sourceName,
        String sourceAttribution
) {
}
