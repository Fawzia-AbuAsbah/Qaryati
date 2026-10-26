
package com.qaryati.dataquality.service;

import com.qaryati.dataquality.entity.Source;
import com.qaryati.dataquality.repository.SourceRepository;
import com.qaryati.dataquality.dto.CreateSourceRequest;
import com.qaryati.dataquality.enums.SourceType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
@Validated
@PreAuthorize("hasAnyRole('VIEWER', 'EDITOR', 'ADMIN')")
public class SourceService {

    private final SourceRepository sourceRepository;
    private final AuditLogService auditLogService;

    public SourceService(
            SourceRepository sourceRepository,
            AuditLogService auditLogService
    ) {
        this.sourceRepository = sourceRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('EDITOR', 'ADMIN')")
    public Source createSource(Source source) {

        if (source == null) {
            throw new IllegalArgumentException(
                    "Source is required"
            );
        }

        if (source.getTitle() == null ||
                source.getTitle().isBlank()) {
            throw new IllegalArgumentException(
                    "Source title is required"
            );
        }

        if (source.getSourceType() == null ||
                source.getSourceType().isBlank()) {
            throw new IllegalArgumentException(
                    "Source type is required"
            );
        }

        validateSource(source);
        if (source.getId() != null) {
            throw new IllegalArgumentException("New source must not have an ID");
        }
        Source saved = sourceRepository.saveAndFlush(source);

        auditLogService.record(
                "SOURCE",
                saved.getId(),
                "CREATE",
                null,
                auditData(saved),
                "Source created"
        );

        return saved;
    }

    @Transactional(readOnly = true)
    public Source getSourceById(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Source ID must be positive");
        }
        return sourceRepository.findById(id)
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "Source not found: " + id
                        )
                );
    }

    @Transactional(readOnly = true)
    public List<Source> getAllSources() {
        return sourceRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Source> searchByTitle(String title) {
        return sourceRepository
                .findByTitleContainingIgnoreCase(title);
    }

    @Transactional(readOnly = true)
    public List<Source> getSourcesByType(String sourceType) {
        return sourceRepository.findBySourceType(sourceType);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('EDITOR', 'ADMIN')")
    public Source updateSource(Long id, @NotNull @Valid CreateSourceRequest request) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Source ID must be positive");
        }
        Source source = sourceRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NoSuchElementException("Source not found: " + id));
        Map<String, Object> before = auditData(source);
        boolean urlChanged = !java.util.Objects.equals(source.getUrl(), request.url());
        boolean citationChanged = !java.util.Objects.equals(source.getCitation(), request.citation());
        source.setTitle(request.title());
        source.setPublisher(request.publisher());
        source.setAuthor(request.author());
        source.setSourceType(request.sourceType().name());
        source.setUrl(request.url());
        source.setPublicationDate(request.publicationDate());
        source.setAccessedAt(request.accessedAt());
        source.setCitation(request.citation());
        validateSource(source);
        Source saved = sourceRepository.saveAndFlush(source);
        Map<String, Object> after = auditData(saved);
        after.put("urlChanged", urlChanged);
        after.put("citationChanged", citationChanged);
        auditLogService.record("SOURCE", id, "UPDATE", before, after, "Source updated");
        return saved;
    }

    private void validateSource(Source source) {
        if (source.getTitle() == null || source.getTitle().isBlank() || source.getTitle().length() > 350
                || (source.getPublisher() != null && source.getPublisher().length() > 200)
                || (source.getAuthor() != null && source.getAuthor().length() > 200)) {
            throw new IllegalArgumentException("Invalid source fields");
        }
        try {
            SourceType.valueOf(source.getSourceType());
            if (source.getUrl() != null) {
                var uri = java.net.URI.create(source.getUrl());
                if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                        || uri.getHost() == null) {
                    throw new IllegalArgumentException("Invalid source URL");
                }
            }
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new IllegalArgumentException("Invalid source type or URL");
        }
    }

    private Map<String, Object> auditData(Source source) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("title", source.getTitle());
        data.put("publisher", source.getPublisher());
        data.put("author", source.getAuthor());
        data.put("sourceType", source.getSourceType());
        data.put("publicationDate", source.getPublicationDate() == null ? null : source.getPublicationDate().toString());
        data.put("accessedAt", source.getAccessedAt() == null ? null : source.getAccessedAt().toString());
        // URLs may contain credentials or tokens; citations are unrestricted free text.
        data.put("hasUrl", source.getUrl() != null);
        data.put("hasCitation", source.getCitation() != null);
        return data;
    }
}
