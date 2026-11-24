package com.qaryati.integration.adapter;

import com.qaryati.integration.dto.GeocodingResult;
import com.qaryati.integration.dto.GeocodingProviderAttribution;

import java.math.BigDecimal;
import java.util.List;

/** Provider-neutral port implemented by a geocoding provider adapter. */
public interface GeocodingProvider {

    String providerName();

    GeocodingProviderAttribution attribution();

    List<GeocodingResult> forward(String query);

    List<GeocodingResult> reverse(BigDecimal latitude, BigDecimal longitude);
}
