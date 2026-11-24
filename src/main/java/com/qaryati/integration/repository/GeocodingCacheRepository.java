package com.qaryati.integration.repository;

import com.qaryati.integration.entity.GeocodingCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GeocodingCacheRepository extends JpaRepository<GeocodingCache, Long> {
    Optional<GeocodingCache> findByProviderAndQueryHash(String provider, String queryHash);
}
