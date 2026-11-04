package com.qaryati.village.controller;

import com.qaryati.village.dto.*;
import com.qaryati.village.service.VillageRelationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/villages/{villageId}/relations")
public class VillageRelationController {
    private final VillageRelationService service;
    public VillageRelationController(VillageRelationService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('EDITOR', 'ADMIN')")
    public VillageRelationResponse create(@PathVariable Long villageId, @Valid @RequestBody VillageRelationRequest request) {
        return service.create(villageId, request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('VIEWER', 'EDITOR', 'ADMIN')")
    public List<VillageRelationResponse> getAll(@PathVariable Long villageId) { return service.getAll(villageId); }

    @GetMapping("/{relationId}")
    @PreAuthorize("hasAnyRole('VIEWER', 'EDITOR', 'ADMIN')")
    public VillageRelationResponse getById(@PathVariable Long villageId, @PathVariable Long relationId) {
        return service.getById(villageId, relationId);
    }

    @PutMapping("/{relationId}")
    @PreAuthorize("hasAnyRole('EDITOR', 'ADMIN')")
    public VillageRelationResponse update(@PathVariable Long villageId, @PathVariable Long relationId,
                                      @Valid @RequestBody VillageRelationRequest request) {
        return service.update(villageId, relationId, request);
    }

    @DeleteMapping("/{relationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long villageId, @PathVariable Long relationId) {
        service.delete(villageId, relationId);
    }
}
