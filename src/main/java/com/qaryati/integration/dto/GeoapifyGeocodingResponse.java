package com.qaryati.integration.dto;

import java.math.BigDecimal;
import java.util.List;

/** Geoapify JSON response shape; intentionally kept inside the integration module. */
public record GeoapifyGeocodingResponse(List<Result> results) {
    public record Result(
            BigDecimal lat,
            BigDecimal lon,
            String formatted,
            String addressLine1,
            String addressLine2,
            String city,
            String country,
            Datasource datasource
    ) {
    }

    public record Datasource(String sourcename, String attribution) {
    }
}
