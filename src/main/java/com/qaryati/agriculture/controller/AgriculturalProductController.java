
package com.qaryati.agriculture.controller;

import com.qaryati.agriculture.dto.AgriculturalProductRequest;
import com.qaryati.agriculture.dto.AgriculturalProductResponse;
import com.qaryati.agriculture.service.AgriculturalProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agricultural-products")
public class AgriculturalProductController {

    private final AgriculturalProductService service;

    public AgriculturalProductController(
            AgriculturalProductService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AgriculturalProductResponse create(
            @Valid @RequestBody AgriculturalProductRequest request) {
        return service.create(request);
    }

    @GetMapping
    public List<AgriculturalProductResponse> getAll() {
        return service.getAll();
    }

    @GetMapping("/{id}")
    public AgriculturalProductResponse getById(
            @PathVariable Long id) {
        return service.getById(id);
    }

    @PutMapping("/{id}")
    public AgriculturalProductResponse update(
            @PathVariable Long id,
            @Valid @RequestBody AgriculturalProductRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
