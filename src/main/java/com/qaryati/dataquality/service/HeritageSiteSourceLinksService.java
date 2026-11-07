package com.qaryati.dataquality.service;

import com.qaryati.dataquality.api.HeritageSiteSourceLinks;
import com.qaryati.dataquality.api.SourceLookup;
import com.qaryati.dataquality.repository.HeritageSiteSourceLinkRepository;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HeritageSiteSourceLinksService implements HeritageSiteSourceLinks {
    private final HeritageSiteSourceLinkRepository repository;
    private final SourceLookup sources;
    public HeritageSiteSourceLinksService(HeritageSiteSourceLinkRepository repository, SourceLookup sources) {
        this.repository = repository;
        this.sources = sources;
    }

    @Override @Transactional(readOnly = true)
    public List<Long> sourceIds(Long heritageSiteId) {
        requireSite(heritageSiteId, false);
        return List.copyOf(repository.sourceIds(heritageSiteId));
    }

    @Override @Transactional
    public void linkNewSite(Long heritageSiteId, Collection<Long> sourceIds) {
        var ids = validate(sourceIds);
        requireSite(heritageSiteId, true);
        if (!repository.sourceIds(heritageSiteId).isEmpty())
            throw new IllegalStateException("Site already has source links");
        ids.forEach(sources::requireExists);
        ids.forEach(id -> repository.insert(heritageSiteId, id));
    }

    @Override @Transactional
    public void replaceSources(Long heritageSiteId, Collection<Long> sourceIds) {
        var ids = validate(sourceIds);
        requireSite(heritageSiteId, true);
        ids.forEach(sources::requireExists);
        repository.deleteLinks(heritageSiteId);
        ids.forEach(id -> repository.insert(heritageSiteId, id));
    }

    @Override @Transactional(readOnly = true)
    public Map<Long, List<Long>> sourceIdsBySiteIds(Collection<Long> siteIds) {
        if (siteIds == null) throw new IllegalArgumentException("Site IDs are required");
        siteIds.forEach(HeritageSiteSourceLinksService::positive);
        if (siteIds.isEmpty()) return Map.of();
        var ids = new TreeSet<>(siteIds);
        var result = repository.sourceIdsBySiteIds(ids);
        if (!result.keySet().equals(ids)) throw new NoSuchElementException("Heritage site not found");
        var copy = new LinkedHashMap<Long, List<Long>>();
        ids.forEach(id -> copy.put(id, result.get(id).stream().distinct().sorted().toList()));
        return Collections.unmodifiableMap(copy);
    }

    private void requireSite(Long id, boolean lock) {
        positive(id);
        if (!repository.siteExists(id, lock)) throw new NoSuchElementException("Heritage site not found: " + id);
    }
    private static void positive(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("IDs must be positive");
    }
    private static SortedSet<Long> validate(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) throw new IllegalArgumentException("Sources must not be empty");
        ids.forEach(HeritageSiteSourceLinksService::positive);
        return new TreeSet<>(ids);
    }
}
