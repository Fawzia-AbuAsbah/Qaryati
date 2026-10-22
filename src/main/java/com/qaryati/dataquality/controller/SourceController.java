
package com.qaryati.dataquality.controller;

import com.qaryati.dataquality.dto.CreateSourceRequest;
import com.qaryati.dataquality.dto.SourceResponse;
import com.qaryati.dataquality.entity.Source;
import com.qaryati.dataquality.service.SourceService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sources")
public class SourceController {

    private final SourceService sourceService;

    public SourceController(SourceService sourceService) {
        this.sourceService = sourceService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('EDITOR', 'ADMIN')")
    public ResponseEntity<SourceResponse> createSource(
            @Valid @RequestBody CreateSourceRequest request) {

        Source source = new Source(
                request.title(),
                request.sourceType().name()
        );

        source.setPublisher(request.publisher());
        source.setAuthor(request.author());
        source.setUrl(request.url());
        source.setPublicationDate(request.publicationDate());
        source.setAccessedAt(request.accessedAt());
        source.setCitation(request.citation());

        Source saved = sourceService.createSource(source);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(SourceResponse.fromEntity(saved));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('VIEWER', 'EDITOR', 'ADMIN')")
    public ResponseEntity<List<SourceResponse>> getAllSources() {

        List<SourceResponse> sources = sourceService
                .getAllSources()
                .stream()
                .map(SourceResponse::fromEntity)
                .toList();

        return ResponseEntity.ok(sources);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('VIEWER', 'EDITOR', 'ADMIN')")
    public ResponseEntity<SourceResponse> getSourceById(
            @PathVariable Long id) {

        Source source = sourceService.getSourceById(id);

        return ResponseEntity.ok(
                SourceResponse.fromEntity(source)
        );
    }

}
