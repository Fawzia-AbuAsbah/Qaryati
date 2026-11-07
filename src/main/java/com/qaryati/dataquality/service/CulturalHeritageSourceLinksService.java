package com.qaryati.dataquality.service;

import com.qaryati.dataquality.api.CulturalHeritageSourceLinks;
import com.qaryati.dataquality.api.SourceLookup;
import com.qaryati.dataquality.repository.CulturalHeritageSourceLinkRepository;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CulturalHeritageSourceLinksService implements CulturalHeritageSourceLinks {
    private final CulturalHeritageSourceLinkRepository repository;
    private final SourceLookup sources;
    public CulturalHeritageSourceLinksService(CulturalHeritageSourceLinkRepository repository, SourceLookup sources) {
        this.repository = repository;
        this.sources = sources;
    }

    @Override @Transactional(readOnly = true)
    public List<Long> sourceIds(Long culturalHeritageId) {
        requireCulturalHeritage(culturalHeritageId, false);
        return repository.sourceIds(culturalHeritageId).stream().distinct().sorted().toList();
    }

    @Override @Transactional
    public void linkNewCulturalHeritage(Long culturalHeritageId, Collection<Long> sourceIds) {
        var ids = validate(sourceIds);
        requireCulturalHeritage(culturalHeritageId, true);
        if (!repository.sourceIds(culturalHeritageId).isEmpty())
            throw new IllegalStateException("Cultural heritage already has source links");
        ids.forEach(sources::requireExists);
        ids.forEach(id -> repository.insert(culturalHeritageId, id));
    }

    @Override @Transactional
    public void replaceSources(Long culturalHeritageId, Collection<Long> sourceIds) {
        var ids = validate(sourceIds);
        requireCulturalHeritage(culturalHeritageId, true);
        ids.forEach(sources::requireExists);
        repository.deleteLinks(culturalHeritageId);
        ids.forEach(id -> repository.insert(culturalHeritageId, id));
    }

    @Override @Transactional(readOnly = true)
    public Map<Long, List<Long>> sourceIdsByCulturalHeritageIds(Collection<Long> culturalHeritageIds) {
        if (culturalHeritageIds == null) throw new IllegalArgumentException("Cultural heritage IDs required");
        culturalHeritageIds.forEach(CulturalHeritageSourceLinksService::positive);
        if (culturalHeritageIds.isEmpty()) return Map.of();
        var result = repository.sourceIdsByCulturalHeritageIds(new TreeSet<>(culturalHeritageIds));
        if (!result.keySet().containsAll(culturalHeritageIds)) throw new NoSuchElementException("Cultural heritage not found");
        Map<Long, List<Long>> immutable = new LinkedHashMap<>();
        result.forEach((id, ids) -> immutable.put(id, ids.stream().distinct().sorted().toList()));
        return Collections.unmodifiableMap(immutable);
    }
    private void requireCulturalHeritage(Long id, boolean lock) {
        positive(id);
        if (!repository.culturalHeritageExists(id, lock)) throw new NoSuchElementException("Cultural heritage not found: " + id);
    }
    private static void positive(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("IDs must be positive");
    }
    private static List<Long> validate(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) throw new IllegalArgumentException("Sources must not be empty");
        ids.forEach(CulturalHeritageSourceLinksService::positive);
        return ids.stream().distinct().sorted().toList();
    }
}
