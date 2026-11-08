package com.qaryati.heritage.controller;

import com.qaryati.heritage.dto.*;
import com.qaryati.heritage.service.VillageHistoricalOverviewService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@RequestMapping("/api/v1/village-historical-overviews")
public class VillageHistoricalOverviewController {
    private final VillageHistoricalOverviewService service;

    public VillageHistoricalOverviewController(VillageHistoricalOverviewService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<VillageHistoricalOverviewResponse> create(@Valid @RequestBody VillageHistoricalOverviewRequest request) {
        VillageHistoricalOverviewResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/village-historical-overviews/" + response.id())).body(response);
    }

    @GetMapping
    public VillageHistoricalOverviewPage getAll(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) Long villageId) {
        return service.getAll(page, size, villageId);
    }

    @GetMapping("/{id}")
    public VillageHistoricalOverviewResponse getById(@PathVariable Long id) { return service.getById(id); }

    @PutMapping("/{id}")
    public VillageHistoricalOverviewResponse update(@PathVariable Long id, @Valid @RequestBody VillageHistoricalOverviewRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
