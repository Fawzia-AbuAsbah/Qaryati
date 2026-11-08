package com.qaryati.dataquality.service;

import com.qaryati.dataquality.api.VillageHistorySourceLinks;
import com.qaryati.dataquality.api.SourceLookup;
import com.qaryati.dataquality.repository.VillageHistorySourceLinkRepository;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VillageHistorySourceLinksService implements VillageHistorySourceLinks {
    private final VillageHistorySourceLinkRepository repository;
    private final SourceLookup sources;
    public VillageHistorySourceLinksService(VillageHistorySourceLinkRepository repository, SourceLookup sources) {
        this.repository = repository;
        this.sources = sources;
    }

    @Override @Transactional(readOnly = true)
    public List<Long> sourceIds(Long historyId) {
        requireHistory(historyId, false);
        return List.copyOf(repository.sourceIds(historyId));
    }

    @Override @Transactional
    public void linkNewHistory(Long historyId, Collection<Long> sourceIds) {
        var ids = validate(sourceIds);
        requireHistory(historyId, true);
        if (!repository.sourceIds(historyId).isEmpty())
            throw new IllegalStateException("Village history already has source links");
        ids.forEach(sources::requireExists);
        ids.forEach(id -> repository.insert(historyId, id));
    }

    @Override @Transactional
    public void replaceSources(Long historyId, Collection<Long> sourceIds) {
        var ids = validate(sourceIds);
        requireHistory(historyId, true);
        ids.forEach(sources::requireExists);
        repository.deleteLinks(historyId);
        ids.forEach(id -> repository.insert(historyId, id));
    }

    @Override @Transactional(readOnly = true)
    public Map<Long, List<Long>> sourceIdsByHistoryIds(Collection<Long> historyIds) {
        if (historyIds == null) throw new IllegalArgumentException("History IDs required");
        historyIds.forEach(VillageHistorySourceLinksService::positive);
        if (historyIds.isEmpty()) return Map.of();
        var result = repository.sourceIdsByHistoryIds(new TreeSet<>(historyIds));
        if (!result.keySet().containsAll(historyIds)) throw new NoSuchElementException("Village history not found");
        Map<Long, List<Long>> immutable = new LinkedHashMap<>();
        result.forEach((id, ids) -> immutable.put(id, ids.stream().distinct().sorted().toList()));
        return Collections.unmodifiableMap(immutable);
    }
    private void requireHistory(Long id, boolean lock) {
        positive(id);
        if (!repository.historyExists(id, lock)) throw new NoSuchElementException("Village history not found: " + id);
    }
    private static void positive(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("IDs must be positive");
    }
    private static SortedSet<Long> validate(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) throw new IllegalArgumentException("Sources must not be empty");
        ids.forEach(VillageHistorySourceLinksService::positive);
        return new TreeSet<>(ids);
    }
}
