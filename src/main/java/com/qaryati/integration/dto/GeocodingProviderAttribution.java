package com.qaryati.integration.dto;

/** Provider attribution that clients must retain when displaying or reusing a geocoding result. */
public record GeocodingProviderAttribution(
        String provider,
        String text,
        String url
) {
}
