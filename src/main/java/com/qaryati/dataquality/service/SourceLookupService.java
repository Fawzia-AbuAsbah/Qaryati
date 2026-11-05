package com.qaryati.dataquality.service;

import com.qaryati.dataquality.api.SourceLookup;
import com.qaryati.dataquality.repository.SourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.NoSuchElementException;

@Service
@Transactional(readOnly = true)
public class SourceLookupService implements SourceLookup {
    private final SourceRepository repository;

    public SourceLookupService(SourceRepository repository) { this.repository = repository; }

    @Override
    public boolean existsById(Long sourceId) {
        if (sourceId == null || sourceId <= 0) {
            throw new IllegalArgumentException("Source ID must be positive");
        }
        return repository.existsById(sourceId);
    }

    @Override
    public void requireExists(Long sourceId) {
        if (!existsById(sourceId)) {
            throw new NoSuchElementException("Source not found: " + sourceId);
        }
    }
}
