package com.qaryati.integration.controller;

import com.qaryati.integration.dto.GeocodingResponse;
import com.qaryati.integration.service.GeocodingService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/geocoding")
public class GeocodingController {

    private final GeocodingService service;

    public GeocodingController(GeocodingService service) {
        this.service = service;
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('VIEWER', 'EDITOR', 'ADMIN')")
    public GeocodingResponse search(@RequestParam String query) {
        return service.search(query);
    }

    @GetMapping("/reverse")
    @PreAuthorize("hasAnyRole('VIEWER', 'EDITOR', 'ADMIN')")
    public GeocodingResponse reverse(@RequestParam("lat") BigDecimal latitude,
                                     @RequestParam("lon") BigDecimal longitude) {
        return service.reverse(latitude, longitude);
    }
}
