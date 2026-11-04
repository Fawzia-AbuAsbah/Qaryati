package com.qaryati.village.controller;

import com.qaryati.village.dto.VillageRequest;
import com.qaryati.village.dto.VillageResponse;
import com.qaryati.village.service.VillageService;
import jakarta.validation.Valid;
import com.qaryati.village.dto.VillagePageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/villages")
public class VillageController {

    private final VillageService service;

    public VillageController(VillageService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('EDITOR', 'ADMIN')")
    public VillageResponse create(@Valid @RequestBody VillageRequest request) {
        return service.create(request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('VIEWER', 'EDITOR', 'ADMIN')")
    public VillagePageResponse getActive(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return VillagePageResponse.from(service.getActive(page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('VIEWER', 'EDITOR', 'ADMIN')")
    public VillageResponse getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('EDITOR', 'ADMIN')")
    public VillageResponse update(
            @PathVariable Long id, @Valid @RequestBody VillageRequest request) {
        return service.update(id, request);
    }

    // DELETE archives the Village; the service preserves the row and repeat-call timestamps.
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void archive(@PathVariable Long id) {
        service.archive(id);
    }
}
