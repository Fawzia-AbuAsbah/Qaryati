
package com.qaryati.dataquality.controller;

import com.qaryati.dataquality.dto.CreateDataConflictRequest;
import com.qaryati.dataquality.dto.DataConflictResponse;
import com.qaryati.dataquality.dto.ResolveDataConflictRequest;
import com.qaryati.dataquality.enums.DataConflictStatus;
import com.qaryati.dataquality.service.DataConflictService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/data-conflicts")
@Validated
public class DataConflictController {

    private final DataConflictService conflictService;

    public DataConflictController(DataConflictService conflictService) {
        this.conflictService = conflictService;
    }

    @PostMapping
    public ResponseEntity<DataConflictResponse> createConflict(
            @Valid @RequestBody CreateDataConflictRequest request
    ) {
        DataConflictResponse response =
                conflictService.createConflict(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    public ResponseEntity<List<DataConflictResponse>> getConflicts(
            @RequestParam String entityType,
            @RequestParam @Positive Long entityId
    ) {
        return ResponseEntity.ok(
                conflictService.getConflicts(entityType, entityId)
        );
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<DataConflictResponse>> getByStatus(
            @PathVariable DataConflictStatus status
    ) {
        return ResponseEntity.ok(
                conflictService.getConflictsByStatus(status)
        );
    }

    @PatchMapping("/{id}/review")
    public ResponseEntity<DataConflictResponse> markInReview(
            @PathVariable @Positive Long id
    ) {
        return ResponseEntity.ok(
                conflictService.markInReview(id)
        );
    }

    @PatchMapping("/{id}/resolve")
    public ResponseEntity<DataConflictResponse> resolveConflict(
            @PathVariable @Positive Long id,
            @Valid @RequestBody ResolveDataConflictRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                conflictService.resolveConflict(id, request, authentication)
        );
    }
}
