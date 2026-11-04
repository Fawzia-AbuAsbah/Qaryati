package com.qaryati.village.controller;

import com.qaryati.village.dto.*;
import com.qaryati.village.service.VillageNameService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/villages/{villageId}/names")
public class VillageNameController {
    private final VillageNameService service;
    public VillageNameController(VillageNameService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('EDITOR', 'ADMIN')")
    public VillageNameResponse create(@PathVariable Long villageId, @Valid @RequestBody VillageNameRequest request) {
        return service.create(villageId, request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('VIEWER', 'EDITOR', 'ADMIN')")
    public List<VillageNameResponse> getAll(@PathVariable Long villageId) { return service.getAll(villageId); }

    @GetMapping("/{nameId}")
    @PreAuthorize("hasAnyRole('VIEWER', 'EDITOR', 'ADMIN')")
    public VillageNameResponse getById(@PathVariable Long villageId, @PathVariable Long nameId) {
        return service.getById(villageId, nameId);
    }

    @PutMapping("/{nameId}")
    @PreAuthorize("hasAnyRole('EDITOR', 'ADMIN')")
    public VillageNameResponse update(@PathVariable Long villageId, @PathVariable Long nameId,
                                      @Valid @RequestBody VillageNameRequest request) {
        return service.update(villageId, nameId, request);
    }

    @DeleteMapping("/{nameId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long villageId, @PathVariable Long nameId) {
        service.delete(villageId, nameId);
    }
}