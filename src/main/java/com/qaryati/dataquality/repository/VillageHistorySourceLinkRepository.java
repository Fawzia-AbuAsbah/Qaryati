package com.qaryati.dataquality.repository;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.stereotype.Repository;

/** SQL against existing tables; no dependency on Heritage's internal entity model. */
@Repository
public class VillageHistorySourceLinkRepository {
    private final EntityManager entityManager;
    public VillageHistorySourceLinkRepository(EntityManager entityManager) { this.entityManager = entityManager; }

    public boolean historyExists(Long historyId, boolean lock) {
        return !entityManager.createNativeQuery("SELECT id FROM public.village_histories WHERE id = :id"
                + (lock ? " FOR UPDATE" : ""))
                .setParameter("id", historyId).getResultList().isEmpty();
    }

    public List<Long> sourceIds(Long historyId) {
        List<?> rows = entityManager.createNativeQuery(
                "SELECT source_id FROM public.history_sources WHERE village_history_id = :id ORDER BY source_id")
                .setParameter("id", historyId).getResultList();
        return rows.stream().map(value -> ((Number) value).longValue()).toList();
    }

    public java.util.Map<Long, List<Long>> sourceIdsByHistoryIds(java.util.Collection<Long> ids) {
        List<?> rows = entityManager.createNativeQuery("SELECT h.id, s.source_id FROM public.village_histories h LEFT JOIN public.history_sources s ON s.village_history_id = h.id WHERE h.id IN (:ids) ORDER BY h.id, s.source_id")
                .setParameter("ids", ids).getResultList();
        java.util.Map<Long, List<Long>> result = new java.util.LinkedHashMap<>();
        for (Object row : rows) {
            Object[] values = (Object[]) row;
            var sources = result.computeIfAbsent(((Number) values[0]).longValue(), key -> new java.util.ArrayList<>());
            if (values[1] != null) sources.add(((Number) values[1]).longValue());
        }
        return result;
    }
    public void deleteLinks(Long historyId) {
        entityManager.createNativeQuery("DELETE FROM public.history_sources WHERE village_history_id = :id")
                .setParameter("id", historyId).executeUpdate();
    }

    public void insert(Long historyId, Long sourceId) {
        entityManager.createNativeQuery("INSERT INTO public.history_sources(village_history_id, source_id) VALUES (:event, :source)")
                .setParameter("event", historyId).setParameter("source", sourceId).executeUpdate();
    }
}
