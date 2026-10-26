
package com.qaryati.dataquality.controller;

import com.qaryati.dataquality.dto.LinkSourceRequest;
import com.qaryati.dataquality.dto.VillageSourceResponse;
import com.qaryati.dataquality.entity.VillageSource;
import com.qaryati.dataquality.service.VillageSourceService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class VillageSourceController {

    private final VillageSourceService villageSourceService;

    public VillageSourceController(
            VillageSourceService villageSourceService
    ) {
        this.villageSourceService = villageSourceService;
    }

    // Link a source to a village
    @PostMapping("/villages/{villageId}/sources")
    @PreAuthorize("hasAnyRole('EDITOR', 'ADMIN')")
    public ResponseEntity<VillageSourceResponse> linkSource(
            @PathVariable Long villageId,
            @Valid @RequestBody LinkSourceRequest request
    ) {
        VillageSource link = villageSourceService.linkSourceToVillage(
                villageId,
                request.sourceId(),
                request.claimNote()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(VillageSourceResponse.fromEntity(link));
    }

    // Get all source links for a village
    @GetMapping("/villages/{villageId}/sources")
    @PreAuthorize("hasAnyRole('VIEWER', 'EDITOR', 'ADMIN')")
    public ResponseEntity<List<VillageSourceResponse>> getVillageSources(
            @PathVariable Long villageId
    ) {
        List<VillageSourceResponse> results = villageSourceService
                .getSourcesForVillage(villageId)
                .stream()
                .map(VillageSourceResponse::fromEntity)
                .toList();

        return ResponseEntity.ok(results);
    }

    // Get all village links for a source
    @GetMapping("/sources/{sourceId}/villages")
    @PreAuthorize("hasAnyRole('VIEWER', 'EDITOR', 'ADMIN')")
    public ResponseEntity<List<VillageSourceResponse>> getSourceVillages(
            @PathVariable Long sourceId
    ) {
        List<VillageSourceResponse> results = villageSourceService
                .getVillagesForSource(sourceId)
                .stream()
                .map(VillageSourceResponse::fromEntity)
                .toList();

        return ResponseEntity.ok(results);
    }
}
