package com.qaryati.integration.dto;

import java.util.List;

public record GeocodingResponse(List<GeocodingResult> results, boolean cached,
                                GeocodingProviderAttribution providerAttribution) {

    /** Compatibility constructor for internal callers that do not need attribution metadata. */
    public GeocodingResponse(List<GeocodingResult> results, boolean cached) {
        this(results, cached, null);
    }
}
