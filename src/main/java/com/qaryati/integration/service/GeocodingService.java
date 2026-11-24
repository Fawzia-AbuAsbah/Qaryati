package com.qaryati.integration.service;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.qaryati.integration.adapter.GeocodingProvider;
import com.qaryati.integration.config.GeoapifyProperties;
import com.qaryati.integration.dto.GeocodingResponse;
import com.qaryati.integration.dto.GeocodingResult;
import com.qaryati.integration.entity.GeocodingCache;
import com.qaryati.integration.exception.GeocodingProviderException;
import com.qaryati.integration.repository.GeocodingCacheRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
public class GeocodingService {

    private static final int MAX_QUERY_LENGTH = 500;
    private static final TypeReference<List<GeocodingResult>> RESULTS = new TypeReference<>() { };

    private final GeocodingCacheRepository cacheRepository;
    private final GeocodingProvider provider;
    private final ObjectMapper objectMapper;
    private final GeoapifyProperties properties;

    public GeocodingService(GeocodingCacheRepository cacheRepository, GeocodingProvider provider,
                            ObjectMapper objectMapper, GeoapifyProperties properties) {
        this.cacheRepository = cacheRepository;
        this.provider = provider;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Transactional
    public GeocodingResponse search(String query) {
        String normalized = validateQuery(query);
        return locate("forward:" + normalized, () -> provider.forward(normalized));
    }

    @Transactional
    public GeocodingResponse reverse(BigDecimal latitude, BigDecimal longitude) {
        validateCoordinates(latitude, longitude);
        BigDecimal normalizedLatitude = latitude.stripTrailingZeros();
        BigDecimal normalizedLongitude = longitude.stripTrailingZeros();
        return locate("reverse:" + normalizedLatitude.toPlainString() + ":" + normalizedLongitude.toPlainString(),
                () -> provider.reverse(normalizedLatitude, normalizedLongitude));
    }

    private GeocodingResponse locate(String normalizedQuery, ResultLookup lookup) {
        String providerName = provider.providerName();
        String queryHash = hash(normalizedQuery);
        Instant now = Instant.now();

        Optional<GeocodingCache> cached = cacheRepository.findByProviderAndQueryHash(providerName, queryHash);
        if (cached.isPresent() && cached.get().getExpiresAt().isAfter(now)) {
            List<GeocodingResult> cachedResults = deserialize(cached.get().getResponseJson());
            if (cachedResults != null) {
                return new GeocodingResponse(cachedResults, true, provider.attribution());
            }
        }

        List<GeocodingResult> results;
        try {
            results = lookup.get();
        } catch (GeocodingProviderException ex) {
            throw translate(ex);
        }

        String responseJson;
        try {
            responseJson = objectMapper.writeValueAsString(results);
        } catch (JacksonException ex) {
            // Serialization of the internal result is a server defect, not a provider failure.
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to cache geocoding result");
        }

        cache(providerName, queryHash, responseJson, now, cached.orElse(null));
        return new GeocodingResponse(results, false, provider.attribution());
    }

    private void cache(String providerName, String queryHash, String responseJson, Instant now,
                       GeocodingCache existing) {
        Instant expiresAt = now.plus(properties.getCacheTtl());
        try {
            if (existing == null) {
                cacheRepository.saveAndFlush(new GeocodingCache(providerName, queryHash, responseJson, now, expiresAt));
            } else {
                existing.refresh(responseJson, now, expiresAt);
                cacheRepository.saveAndFlush(existing);
            }
        } catch (DataIntegrityViolationException conflict) {
            // A concurrent identical request may have populated the unique provider/hash row.
            // The provider result remains usable even if the competing cache write wins.
        }
    }

    private List<GeocodingResult> deserialize(String responseJson) {
        try {
            List<GeocodingResult> results = objectMapper.readValue(responseJson, RESULTS);
            if (results == null || results.stream().anyMatch(result -> !isValidResult(result))) {
                return null;
            }
            return results;
        } catch (JacksonException ex) {
            return null;
        }
    }

    private boolean isValidResult(GeocodingResult result) {
        return result != null && result.latitude() != null && result.longitude() != null
                && result.latitude().compareTo(BigDecimal.valueOf(-90)) >= 0
                && result.latitude().compareTo(BigDecimal.valueOf(90)) <= 0
                && result.longitude().compareTo(BigDecimal.valueOf(-180)) >= 0
                && result.longitude().compareTo(BigDecimal.valueOf(180)) <= 0;
    }

    private String validateQuery(String query) {
        if (query == null || query.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search query must not be blank");
        }
        String normalized = query.trim();
        if (normalized.length() > MAX_QUERY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search query must not exceed 500 characters");
        }
        return normalized;
    }

    private void validateCoordinates(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null
                || latitude.compareTo(BigDecimal.valueOf(-90)) < 0 || latitude.compareTo(BigDecimal.valueOf(90)) > 0
                || longitude.compareTo(BigDecimal.valueOf(-180)) < 0 || longitude.compareTo(BigDecimal.valueOf(180)) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Latitude must be between -90 and 90 and longitude must be between -180 and 180");
        }
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private ResponseStatusException translate(GeocodingProviderException ex) {
        return switch (ex.getReason()) {
            case CONFIGURATION -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Geocoding provider is not configured");
            case RATE_LIMITED -> new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Geocoding provider rate limit reached");
            case TIMEOUT -> new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                    "Geocoding provider timed out");
            case UPSTREAM, MALFORMED_RESPONSE, NETWORK -> new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Geocoding provider is unavailable");
        };
    }

    @FunctionalInterface
    private interface ResultLookup {
        List<GeocodingResult> get();
    }
}
