package com.qaryati.integration.service;

import tools.jackson.databind.ObjectMapper;
import com.qaryati.integration.adapter.GeocodingProvider;
import com.qaryati.integration.config.GeoapifyProperties;
import com.qaryati.integration.dto.GeocodingResult;
import com.qaryati.integration.dto.GeocodingProviderAttribution;
import com.qaryati.integration.entity.GeocodingCache;
import com.qaryati.integration.exception.GeocodingProviderException;
import com.qaryati.integration.repository.GeocodingCacheRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GeocodingServiceTest {

    private GeocodingCacheRepository cacheRepository;
    private GeocodingProvider provider;
    private GeocodingService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        cacheRepository = mock(GeocodingCacheRepository.class);
        provider = mock(GeocodingProvider.class);
        objectMapper = new ObjectMapper();
        var properties = new GeoapifyProperties();
        properties.setCacheTtl(java.time.Duration.ofHours(1));
        when(provider.providerName()).thenReturn("geoapify");
        when(provider.attribution()).thenReturn(new GeocodingProviderAttribution(
                "geoapify", "Powered by Geoapify", "https://www.geoapify.com/"));
        service = new GeocodingService(cacheRepository, provider, objectMapper, properties);
    }

    @Test
    void obtainsForwardResultOnCacheMissAndCachesIt() {
        when(cacheRepository.findByProviderAndQueryHash(eq("geoapify"), anyString())).thenReturn(Optional.empty());
        when(provider.forward("Ramallah")).thenReturn(List.of(result()));
        when(cacheRepository.saveAndFlush(any(GeocodingCache.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.search("  Ramallah  ");

        assertFalse(response.cached());
        assertEquals("Ramallah", response.results().getFirst().formattedAddress());
        assertEquals("geoapify", response.providerAttribution().provider());
        verify(provider).forward("Ramallah");
        verify(cacheRepository).saveAndFlush(any(GeocodingCache.class));
    }

    @Test
    void returnsUnexpiredCacheWithoutCallingProvider() throws Exception {
        String json = objectMapper.writeValueAsString(List.of(result()));
        var cached = new GeocodingCache("geoapify", "hash", json, Instant.now(), Instant.now().plusSeconds(300));
        when(cacheRepository.findByProviderAndQueryHash(eq("geoapify"), anyString())).thenReturn(Optional.of(cached));

        var response = service.search("Ramallah");

        assertTrue(response.cached());
        assertEquals(1, response.results().size());
        assertEquals("Powered by Geoapify", response.providerAttribution().text());
        verify(provider, never()).forward(anyString());
    }

    @Test
    void malformedCacheIsIgnoredAndRefreshedFromProvider() {
        var cached = new GeocodingCache("geoapify", "hash", "not-json", Instant.now(), Instant.now().plusSeconds(300));
        when(cacheRepository.findByProviderAndQueryHash(eq("geoapify"), anyString())).thenReturn(Optional.of(cached));
        when(provider.forward("Ramallah")).thenReturn(List.of(result()));
        when(cacheRepository.saveAndFlush(cached)).thenReturn(cached);

        assertFalse(service.search("Ramallah").cached());
        verify(provider).forward("Ramallah");
        verify(cacheRepository).saveAndFlush(cached);
    }

    @Test
    void invalidCachedCoordinatesAreIgnoredAndRefreshedFromProvider() throws Exception {
        String invalidJson = objectMapper.writeValueAsString(List.of(new GeocodingResult(
                new BigDecimal("91"), new BigDecimal("35.2"), "Invalid", null, null,
                null, null, null, null)));
        var cached = new GeocodingCache("geoapify", "hash", invalidJson, Instant.now(), Instant.now().plusSeconds(300));
        when(cacheRepository.findByProviderAndQueryHash(eq("geoapify"), anyString())).thenReturn(Optional.of(cached));
        when(provider.forward("Ramallah")).thenReturn(List.of(result()));
        when(cacheRepository.saveAndFlush(cached)).thenReturn(cached);

        assertFalse(service.search("Ramallah").cached());
        verify(provider).forward("Ramallah");
    }

    @Test
    void supportsReverseAndCachesEmptyProviderResult() {
        when(cacheRepository.findByProviderAndQueryHash(eq("geoapify"), anyString())).thenReturn(Optional.empty());
        when(provider.reverse(new BigDecimal("31.9"), new BigDecimal("35.2"))).thenReturn(List.of());
        when(cacheRepository.saveAndFlush(any(GeocodingCache.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.reverse(new BigDecimal("31.90"), new BigDecimal("35.20"));

        assertFalse(response.cached());
        assertTrue(response.results().isEmpty());
        verify(provider).reverse(new BigDecimal("31.9"), new BigDecimal("35.2"));
    }

    @Test
    void rejectsBadInputBeforeCacheOrProvider() {
        assertStatus(400, () -> service.search(" "));
        assertStatus(400, () -> service.reverse(new BigDecimal("91"), BigDecimal.ZERO));
        verifyNoInteractions(cacheRepository, provider);
    }

    @Test
    void translatesProviderFailuresToSanitizedHttpStatus() {
        when(cacheRepository.findByProviderAndQueryHash(eq("geoapify"), anyString())).thenReturn(Optional.empty());
        doThrow(new GeocodingProviderException(GeocodingProviderException.Reason.CONFIGURATION))
                .when(provider).forward("Ramallah");
        assertStatus(503, () -> service.search("Ramallah"));

        doThrow(new GeocodingProviderException(GeocodingProviderException.Reason.RATE_LIMITED))
                .when(provider).forward("Ramallah");
        assertStatus(429, () -> service.search("Ramallah"));

        doThrow(new GeocodingProviderException(GeocodingProviderException.Reason.TIMEOUT))
                .when(provider).forward("Ramallah");
        assertStatus(504, () -> service.search("Ramallah"));

        doThrow(new GeocodingProviderException(GeocodingProviderException.Reason.MALFORMED_RESPONSE))
                .when(provider).forward("Ramallah");
        assertStatus(502, () -> service.search("Ramallah"));
    }

    @Test
    void toleratesConcurrentCacheInsertConflict() {
        when(cacheRepository.findByProviderAndQueryHash(eq("geoapify"), anyString())).thenReturn(Optional.empty());
        when(provider.forward("Ramallah")).thenReturn(List.of(result()));
        when(cacheRepository.saveAndFlush(any(GeocodingCache.class)))
                .thenThrow(new DataIntegrityViolationException("unique"));

        assertFalse(service.search("Ramallah").cached());
    }

    private GeocodingResult result() {
        return new GeocodingResult(new BigDecimal("31.9"), new BigDecimal("35.2"), "Ramallah", null,
                null, "Ramallah", "Palestine", "OpenStreetMap", "attribution");
    }

    private void assertStatus(int expected, Runnable call) {
        var exception = assertThrows(ResponseStatusException.class, call::run);
        assertEquals(expected, exception.getStatusCode().value());
    }
}
