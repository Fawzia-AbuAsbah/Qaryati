package com.qaryati.population.controller;

import com.qaryati.population.dto.*;
import com.qaryati.population.service.PopulationRecordService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/population-records")
public class PopulationRecordController {
    private final PopulationRecordService service;

    public PopulationRecordController(PopulationRecordService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<PopulationRecordResponse> create(@Valid @RequestBody PopulationRecordRequest request) {
        PopulationRecordResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/population-records/" + response.id())).body(response);
    }

    @GetMapping
    public List<PopulationRecordResponse> getAll() { return service.getAll(); }

    @GetMapping("/{id}")
    public PopulationRecordResponse getById(@PathVariable Long id) { return service.getById(id); }

    @PutMapping("/{id}")
    public PopulationRecordResponse update(@PathVariable Long id, @Valid @RequestBody PopulationRecordRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
