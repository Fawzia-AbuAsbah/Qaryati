package com.qaryati.dataquality.service;

import com.qaryati.dataquality.api.EventSourceLinks;
import com.qaryati.dataquality.api.SourceLookup;
import com.qaryati.dataquality.repository.EventSourceLinkRepository;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventSourceLinksService implements EventSourceLinks {
    private final EventSourceLinkRepository repository;
    private final SourceLookup sources;
    public EventSourceLinksService(EventSourceLinkRepository repository, SourceLookup sources) {
        this.repository = repository;
        this.sources = sources;
    }

    @Override @Transactional(readOnly = true)
    public List<Long> sourceIds(Long eventId) {
        requireEvent(eventId, false);
        return List.copyOf(repository.sourceIds(eventId));
    }

    @Override @Transactional
    public void linkNewEvent(Long eventId, Collection<Long> sourceIds) {
        var ids = validate(sourceIds);
        requireEvent(eventId, true);
        if (!repository.sourceIds(eventId).isEmpty())
            throw new IllegalStateException("Event already has source links");
        ids.forEach(sources::requireExists);
        ids.forEach(id -> repository.insert(eventId, id));
    }

    @Override @Transactional
    public void replaceSources(Long eventId, Collection<Long> sourceIds) {
        var ids = validate(sourceIds);
        requireEvent(eventId, true);
        ids.forEach(sources::requireExists);
        repository.deleteLinks(eventId);
        ids.forEach(id -> repository.insert(eventId, id));
    }

    private void requireEvent(Long id, boolean lock) {
        positive(id);
        if (!repository.eventExists(id, lock)) throw new NoSuchElementException("Historical event not found: " + id);
    }
    private static void positive(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("IDs must be positive");
    }
    private static SortedSet<Long> validate(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) throw new IllegalArgumentException("Sources must not be empty");
        ids.forEach(EventSourceLinksService::positive);
        return new TreeSet<>(ids);
    }
}
