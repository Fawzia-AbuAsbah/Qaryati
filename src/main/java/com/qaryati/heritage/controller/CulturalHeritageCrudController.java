package com.qaryati.heritage.controller;

import com.qaryati.heritage.dto.*;
import com.qaryati.heritage.service.CulturalHeritageCrudService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@RequestMapping("/api/v1/cultural-heritage")
public class CulturalHeritageCrudController {
    private final CulturalHeritageCrudService service;

    public CulturalHeritageCrudController(CulturalHeritageCrudService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<CulturalHeritageCrudResponse> create(@Valid @RequestBody CulturalHeritageCrudRequest request) {
        CulturalHeritageCrudResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/cultural-heritage/" + response.id())).body(response);
    }

    @GetMapping
    public CulturalHeritageCrudPage getAll(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) Long villageId,
                                      @RequestParam(required = false) com.qaryati.heritage.entity.CulturalHeritageType heritageType,
                                      @RequestParam(required = false) com.qaryati.heritage.entity.VerificationStatus verificationStatus) {
        return service.getAll(page, size, villageId, heritageType, verificationStatus);
    }

    @GetMapping("/{id}")
    public CulturalHeritageCrudResponse getById(@PathVariable Long id) { return service.getById(id); }

    @PutMapping("/{id}")
    public CulturalHeritageCrudResponse update(@PathVariable Long id, @Valid @RequestBody CulturalHeritageCrudRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
