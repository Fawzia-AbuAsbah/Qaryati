
package com.qaryati.village.controller;

import com.qaryati.village.dto.GovernorateRequest;
import com.qaryati.village.dto.GovernorateResponse;
import com.qaryati.village.service.GovernorateService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/governorates")
public class GovernorateController {

    private final GovernorateService service;

    public GovernorateController(
            GovernorateService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GovernorateResponse create(
            @Valid @RequestBody GovernorateRequest request) {
        return service.create(request);
    }

    @GetMapping
    public List<GovernorateResponse> getAll() {
        return service.getAll();
    }

    @GetMapping("/{id}")
    public GovernorateResponse getById(
            @PathVariable Long id) {
        return service.getById(id);
    }

    @PutMapping("/{id}")
    public GovernorateResponse update(
            @PathVariable Long id,
            @Valid @RequestBody GovernorateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
