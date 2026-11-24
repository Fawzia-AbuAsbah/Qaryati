package com.qaryati.integration.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "geocoding_cache", schema = "public", uniqueConstraints =
        @UniqueConstraint(columnNames = {"provider", "query_hash"}))
public class GeocodingCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String provider;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "query_hash", nullable = false, length = 64)
    private String queryHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_json", nullable = false, columnDefinition = "jsonb")
    private String responseJson;

    @Column(name = "cached_at", nullable = false)
    private Instant cachedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected GeocodingCache() {
    }

    public GeocodingCache(String provider, String queryHash, String responseJson, Instant cachedAt,
                          Instant expiresAt) {
        this.provider = provider;
        this.queryHash = queryHash;
        this.responseJson = responseJson;
        this.cachedAt = cachedAt;
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public String getProvider() { return provider; }
    public String getQueryHash() { return queryHash; }
    public String getResponseJson() { return responseJson; }
    public Instant getCachedAt() { return cachedAt; }
    public Instant getExpiresAt() { return expiresAt; }

    public void refresh(String responseJson, Instant cachedAt, Instant expiresAt) {
        this.responseJson = responseJson;
        this.cachedAt = cachedAt;
        this.expiresAt = expiresAt;
    }
}
