package com.qaryati.dataquality.repository;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.stereotype.Repository;

/** SQL against existing tables; no dependency on Heritage's internal entity model. */
@Repository
public class CulturalHeritageSourceLinkRepository {
    private final EntityManager entityManager;
    public CulturalHeritageSourceLinkRepository(EntityManager entityManager) { this.entityManager = entityManager; }

    public boolean culturalHeritageExists(Long culturalHeritageId, boolean lock) {
        return !entityManager.createNativeQuery("SELECT id FROM public.cultural_heritage WHERE id = :id"
                + (lock ? " FOR UPDATE" : ""))
                .setParameter("id", culturalHeritageId).getResultList().isEmpty();
    }

    public List<Long> sourceIds(Long culturalHeritageId) {
        List<?> rows = entityManager.createNativeQuery(
                "SELECT source_id FROM public.cultural_heritage_sources WHERE cultural_heritage_id = :id ORDER BY source_id")
                .setParameter("id", culturalHeritageId).getResultList();
        return rows.stream().map(value -> ((Number) value).longValue()).toList();
    }

    public java.util.Map<Long, List<Long>> sourceIdsByCulturalHeritageIds(java.util.Collection<Long> ids) {
        List<?> rows = entityManager.createNativeQuery("SELECT h.id, s.source_id FROM public.cultural_heritage h LEFT JOIN public.cultural_heritage_sources s ON s.cultural_heritage_id = h.id WHERE h.id IN (:ids) ORDER BY h.id, s.source_id")
                .setParameter("ids", ids).getResultList();
        java.util.Map<Long, List<Long>> result = new java.util.LinkedHashMap<>();
        for (Object row : rows) {
            Object[] values = (Object[]) row;
            var sources = result.computeIfAbsent(((Number) values[0]).longValue(), key -> new java.util.ArrayList<>());
            if (values[1] != null) sources.add(((Number) values[1]).longValue());
        }
        return result;
    }
    public void deleteLinks(Long culturalHeritageId) {
        entityManager.createNativeQuery("DELETE FROM public.cultural_heritage_sources WHERE cultural_heritage_id = :id")
                .setParameter("id", culturalHeritageId).executeUpdate();
    }

    public void insert(Long culturalHeritageId, Long sourceId) {
        entityManager.createNativeQuery("INSERT INTO public.cultural_heritage_sources(cultural_heritage_id, source_id) VALUES (:target, :source)")
                .setParameter("target", culturalHeritageId).setParameter("source", sourceId).executeUpdate();
    }
}
