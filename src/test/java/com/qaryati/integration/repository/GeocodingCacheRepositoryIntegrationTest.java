package com.qaryati.integration.repository;

import tools.jackson.databind.ObjectMapper;
import com.qaryati.integration.entity.GeocodingCache;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GeocodingCacheRepositoryIntegrationTest {

    @Autowired
    private GeocodingCacheRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void persistsAndRetrievesSha256HashAndJsonbPayload() throws Exception {
        String provider = provider();
        String queryHash = sha256("Ramallah, Palestine");
        String responseJson = """
                {"results":[{"lat":31.9038,"lon":35.2034,"formatted":"Ramallah, Palestine"}],
                "provider":"geoapify"}
                """;
        Instant cachedAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant expiresAt = cachedAt.plusSeconds(3_600);

        GeocodingCache saved = repository.saveAndFlush(
                new GeocodingCache(provider, queryHash, responseJson, cachedAt, expiresAt));
        entityManager.clear();

        GeocodingCache found = repository.findByProviderAndQueryHash(provider, queryHash).orElseThrow();

        assertNotNull(saved.getId());
        assertEquals(64, queryHash.length());
        assertTrue(queryHash.matches("[0-9a-f]{64}"));
        assertEquals(queryHash, found.getQueryHash());
        assertEquals(objectMapper.readTree(responseJson), objectMapper.readTree(found.getResponseJson()));
        assertEquals(cachedAt, found.getCachedAt());
        assertEquals(expiresAt, found.getExpiresAt());
    }

    @Test
    void persistsExpiredEntryForServiceLevelExpiryEvaluation() {
        String provider = provider();
        String queryHash = sha256("expired cache entry");
        Instant cachedAt = Instant.parse("2024-01-01T00:00:00Z");
        Instant expiresAt = cachedAt.plusSeconds(60);

        repository.saveAndFlush(new GeocodingCache(provider, queryHash, "[]", cachedAt, expiresAt));
        entityManager.clear();

        GeocodingCache found = repository.findByProviderAndQueryHash(provider, queryHash).orElseThrow();

        assertTrue(found.getExpiresAt().isAfter(found.getCachedAt()));
        assertTrue(found.getExpiresAt().isBefore(Instant.now()));
    }

    @Test
    void enforcesUniqueProviderAndQueryHashTuple() {
        String provider = provider();
        String queryHash = sha256("duplicate cache entry");
        Instant cachedAt = Instant.parse("2026-01-01T00:00:00Z");

        repository.saveAndFlush(new GeocodingCache(provider, queryHash, "[]", cachedAt,
                cachedAt.plusSeconds(60)));

        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(
                new GeocodingCache(provider, queryHash, "[]", cachedAt, cachedAt.plusSeconds(120))));
    }

    @Test
    void enforcesExpiryAfterCachedAtConstraint() {
        Instant cachedAt = Instant.parse("2026-01-01T00:00:00Z");

        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(
                new GeocodingCache(provider(), sha256("invalid expiry"), "[]", cachedAt, cachedAt)));
    }

    private String provider() {
        return "geoapify-test-" + UUID.randomUUID();
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError("SHA-256 must be available", exception);
        }
    }
}
