
package com.qaryati.agriculture.controller;

import com.qaryati.agriculture.dto.VillageAgricultureResponse;
import com.qaryati.agriculture.service.VillageAgricultureService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.List;

@RestController
@RequestMapping("/api/v1/village-agriculture")
@PreAuthorize("hasAnyRole('VIEWER', 'EDITOR', 'ADMIN')")
public class VillageAgricultureController {

    private final VillageAgricultureService service;

    public VillageAgricultureController(
            VillageAgricultureService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public ResponseEntity<VillageAgricultureResponse> getById(
            @PathVariable Long id) {

        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping("/village/{villageId}")
    public ResponseEntity<List<VillageAgricultureResponse>> getByVillage(
            @PathVariable Long villageId) {

        return ResponseEntity.ok(service.getByVillageId(villageId));
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<List<VillageAgricultureResponse>> getByProduct(
            @PathVariable Long productId) {

        return ResponseEntity.ok(service.getByProductId(productId));
    }
}
