package com.qaryati.integration.controller;

import com.qaryati.common.exception.GlobalExceptionHandler;
import com.qaryati.integration.dto.GeocodingResponse;
import com.qaryati.integration.dto.GeocodingResult;
import com.qaryati.integration.service.GeocodingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GeocodingControllerTest {

    private MockMvc mockMvc;
    private GeocodingService service;

    @BeforeEach
    void setUp() {
        service = mock(GeocodingService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new GeocodingController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void searchesAndReverseGeocodes() throws Exception {
        when(service.search("Ramallah")).thenReturn(response(false));
        when(service.reverse(new BigDecimal("31.9"), new BigDecimal("35.2"))).thenReturn(response(true));

        mockMvc.perform(get("/api/v1/geocoding/search").param("query", "Ramallah"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cached").value(false))
                .andExpect(jsonPath("$.results[0].formattedAddress").value("Ramallah"));
        mockMvc.perform(get("/api/v1/geocoding/reverse").param("lat", "31.9").param("lon", "35.2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cached").value(true));

        verify(service).search("Ramallah");
        verify(service).reverse(new BigDecimal("31.9"), new BigDecimal("35.2"));
    }

    @Test
    void returnsBadRequestForMissingParameters() throws Exception {
        mockMvc.perform(get("/api/v1/geocoding/search"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/geocoding/reverse").param("lat", "31.9"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void returnsServiceValidationAndProviderFailures() throws Exception {
        when(service.search(" ")).thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Search query must not be blank"));
        when(service.reverse(new BigDecimal("91"), BigDecimal.ZERO))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Latitude must be between -90 and 90 and longitude must be between -180 and 180"));

        mockMvc.perform(get("/api/v1/geocoding/search").param("query", " "))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/geocoding/reverse").param("lat", "91").param("lon", "0"))
                .andExpect(status().isBadRequest());
    }

    private GeocodingResponse response(boolean cached) {
        return new GeocodingResponse(List.of(new GeocodingResult(new BigDecimal("31.9"), new BigDecimal("35.2"),
                "Ramallah", null, null, "Ramallah", "Palestine", "OpenStreetMap", "attribution")), cached);
    }
}
