package com.qaryati.heritage.controller;

import com.qaryati.heritage.dto.*;
import com.qaryati.heritage.service.HistoricalEventService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@RequestMapping("/api/v1/historical-events")
public class HistoricalEventController {
    private final HistoricalEventService service;

    public HistoricalEventController(HistoricalEventService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<HistoricalEventResponse> create(@Valid @RequestBody HistoricalEventRequest request) {
        HistoricalEventResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/historical-events/" + response.id())).body(response);
    }

    @GetMapping
    public HistoricalEventPage getAll(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) Long villageId) {
        return service.getAll(page, size, villageId);
    }

    @GetMapping("/{id}")
    public HistoricalEventResponse getById(@PathVariable Long id) { return service.getById(id); }

    @PutMapping("/{id}")
    public HistoricalEventResponse update(@PathVariable Long id, @Valid @RequestBody HistoricalEventRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

