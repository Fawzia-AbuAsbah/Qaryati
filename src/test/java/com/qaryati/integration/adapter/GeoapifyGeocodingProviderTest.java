package com.qaryati.integration.adapter;

import tools.jackson.databind.ObjectMapper;
import com.qaryati.integration.client.GeoapifyHttpResponse;
import com.qaryati.integration.client.GeoapifyHttpTransport;
import com.qaryati.integration.config.GeoapifyProperties;
import com.qaryati.integration.exception.GeocodingProviderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpTimeoutException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GeoapifyGeocodingProviderTest {

    private GeoapifyHttpTransport transport;
    private GeoapifyProperties properties;
    private GeoapifyGeocodingProvider provider;

    @BeforeEach
    void setUp() {
        transport = mock(GeoapifyHttpTransport.class);
        properties = new GeoapifyProperties();
        properties.setApiKey("test-key");
        provider = new GeoapifyGeocodingProvider(transport, new ObjectMapper(), properties);
    }

    @Test
    void mapsDocumentedForwardResponseAndRetainsAttribution() throws Exception {
        when(transport.get(any(), any())).thenReturn(ok(resultJson()));

        var results = provider.forward("Ramallah");

        assertEquals(1, results.size());
        assertEquals(new BigDecimal("31.9038"), results.getFirst().latitude());
        assertEquals("OpenStreetMap", results.getFirst().sourceName());
        assertEquals("© OpenStreetMap contributors", results.getFirst().sourceAttribution());
        assertEquals("Powered by Geoapify", provider.attribution().text());
        assertEquals("https://www.geoapify.com/", provider.attribution().url());
        verify(transport).get(argThat(uri -> uri.getPath().endsWith("/search")
                && uri.getQuery().contains("text=Ramallah")
                && uri.getQuery().contains("format=json")), any());
    }

    @Test
    void mapsReverseResponseAndAllowsZeroResults() throws Exception {
        when(transport.get(any(), any())).thenReturn(ok("{\"results\":[]}"));

        List<?> results = provider.reverse(new BigDecimal("31.9"), new BigDecimal("35.2"));

        assertTrue(results.isEmpty());
        verify(transport).get(argThat(uri -> uri.getPath().endsWith("/reverse")
                && uri.getQuery().contains("lat=31.9")
                && uri.getQuery().contains("lon=35.2")), any());
    }

    @Test
    void rejectsMissingApiKeyWithoutMakingRequest() {
        properties.setApiKey(" ");

        assertReason(GeocodingProviderException.Reason.CONFIGURATION, () -> provider.forward("Ramallah"));
        verifyNoInteractions(transport);
    }

    @Test
    void translatesRateLimitAndTimeout() throws Exception {
        when(transport.get(any(), any())).thenReturn(new GeoapifyHttpResponse(429, "{}"));
        assertReason(GeocodingProviderException.Reason.RATE_LIMITED, () -> provider.forward("Ramallah"));

        reset(transport);
        when(transport.get(any(), any())).thenThrow(new HttpTimeoutException("timeout"));
        assertReason(GeocodingProviderException.Reason.TIMEOUT, () -> provider.forward("Ramallah"));
    }

    @Test
    void translatesOtherClientFailuresAndRetriesBoundedNetworkFailures() throws Exception {
        when(transport.get(any(), any())).thenReturn(new GeoapifyHttpResponse(400, "{}"));
        assertReason(GeocodingProviderException.Reason.UPSTREAM, () -> provider.forward("Ramallah"));

        reset(transport);
        when(transport.get(any(), any())).thenThrow(new IOException("network failure"));
        assertReason(GeocodingProviderException.Reason.NETWORK, () -> provider.forward("Ramallah"));
        verify(transport, times(2)).get(any(), any());
    }

    @Test
    void retriesOneServerFailureThenReturnsSuccess() throws Exception {
        when(transport.get(any(), any())).thenReturn(new GeoapifyHttpResponse(503, "{}"), ok(resultJson()));

        assertEquals(1, provider.forward("Ramallah").size());
        verify(transport, times(2)).get(any(), any());
    }

    @Test
    void rejectsMalformedProviderResponse() throws Exception {
        when(transport.get(any(), any())).thenReturn(ok("{\"results\":[{\"formatted\":\"Missing coordinates\"}]}"));

        assertReason(GeocodingProviderException.Reason.MALFORMED_RESPONSE, () -> provider.forward("Ramallah"));
    }

    private GeoapifyHttpResponse ok(String body) {
        return new GeoapifyHttpResponse(200, body);
    }

    private String resultJson() {
        return """
                {"results":[{"lat":31.9038,"lon":35.2034,"formatted":"Ramallah, Palestine",
                "address_line1":"Ramallah","address_line2":"Palestine","city":"Ramallah",
                "country":"Palestine","datasource":{"sourcename":"OpenStreetMap",
                "attribution":"© OpenStreetMap contributors"}}]}
                """;
    }

    private void assertReason(GeocodingProviderException.Reason expected, Runnable call) {
        var exception = assertThrows(GeocodingProviderException.class, call::run);
        assertEquals(expected, exception.getReason());
    }
}
