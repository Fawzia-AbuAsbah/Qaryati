
package com.qaryati.dataquality.service;

import com.qaryati.dataquality.entity.Source;
import com.qaryati.dataquality.repository.SourceRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
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

        Source saved = sourceRepository.saveAndFlush(source);

        Map<String, Object> afterData = new LinkedHashMap<>();
        afterData.put("title", saved.getTitle());
        afterData.put("sourceType", saved.getSourceType());

        auditLogService.record(
                "SOURCE",
                saved.getId(),
                "CREATE",
                null,
                afterData,
                "Source created"
        );

        return saved;
    }

    @Transactional(readOnly = true)
    public Source getSourceById(Long id) {
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
}
