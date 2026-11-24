
package com.qaryati.agriculture.controller;

import com.qaryati.agriculture.dto.VillageAgricultureRequest;
import com.qaryati.agriculture.dto.VillageAgricultureResponse;
import com.qaryati.agriculture.service.VillageAgricultureService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.List;

@RestController
@RequestMapping("/api/v1/village-agriculture")
public class VillageAgricultureController {

    private final VillageAgricultureService service;

    public VillageAgricultureController(
            VillageAgricultureService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('EDITOR', 'ADMIN')")
    public VillageAgricultureResponse create(
            @Valid @RequestBody VillageAgricultureRequest request) {

        return service.create(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('VIEWER', 'EDITOR', 'ADMIN')")
    public VillageAgricultureResponse getById(
            @PathVariable Long id) {

        return service.getById(id);
    }

    @GetMapping("/village/{villageId}")
    @PreAuthorize("hasAnyRole('VIEWER', 'EDITOR', 'ADMIN')")
    public List<VillageAgricultureResponse> getByVillage(
            @PathVariable Long villageId) {

        return service.getByVillageId(villageId);
    }

    @GetMapping("/product/{productId}")
    @PreAuthorize("hasAnyRole('VIEWER', 'EDITOR', 'ADMIN')")
    public List<VillageAgricultureResponse> getByProduct(
            @PathVariable Long productId) {

        return service.getByProductId(productId);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('EDITOR', 'ADMIN')")
    public VillageAgricultureResponse update(
            @PathVariable Long id,
            @Valid @RequestBody VillageAgricultureRequest request) {

        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
