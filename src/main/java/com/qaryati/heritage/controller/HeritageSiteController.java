package com.qaryati.heritage.controller;

import com.qaryati.heritage.dto.*;
import com.qaryati.heritage.service.HeritageSiteService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@RequestMapping("/api/v1/heritage-sites")
public class HeritageSiteController {
    private final HeritageSiteService service;

    public HeritageSiteController(HeritageSiteService service) { this.service = service; }

    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    @PostMapping
    public ResponseEntity<HeritageSiteResponse> create(@Valid @RequestBody HeritageSiteRequest request) {
        HeritageSiteResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/heritage-sites/" + response.id())).body(response);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'EDITOR', 'VIEWER')")
    @GetMapping
    public HeritageSitePage getAll(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) Long villageId) {
        return service.getAll(page, size, villageId);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'EDITOR', 'VIEWER')")
    @GetMapping("/{id}")
    public HeritageSiteResponse getById(@PathVariable Long id) { return service.getById(id); }

    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    @PutMapping("/{id}")
    public HeritageSiteResponse update(@PathVariable Long id, @Valid @RequestBody HeritageSiteRequest request) {
        return service.update(id, request);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

