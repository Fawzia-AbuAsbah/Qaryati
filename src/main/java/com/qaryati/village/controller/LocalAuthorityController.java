package com.qaryati.village.controller;

import com.qaryati.village.dto.LocalAuthorityRequest;
import com.qaryati.village.dto.LocalAuthorityResponse;
import com.qaryati.village.service.LocalAuthorityService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/local-authorities")
public class LocalAuthorityController {

    private final LocalAuthorityService service;

    public LocalAuthorityController(LocalAuthorityService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('EDITOR', 'ADMIN')")
    public LocalAuthorityResponse create(@Valid @RequestBody LocalAuthorityRequest request) {
        return service.create(request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('VIEWER', 'EDITOR', 'ADMIN')")
    public List<LocalAuthorityResponse> getAll(
            @RequestParam(required = false) Long governorateId) {
        return governorateId == null ? service.getAll() : service.getByGovernorate(governorateId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('VIEWER', 'EDITOR', 'ADMIN')")
    public LocalAuthorityResponse getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('EDITOR', 'ADMIN')")
    public LocalAuthorityResponse update(
            @PathVariable Long id, @Valid @RequestBody LocalAuthorityRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}