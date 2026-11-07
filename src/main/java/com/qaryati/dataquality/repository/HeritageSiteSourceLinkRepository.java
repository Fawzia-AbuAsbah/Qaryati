package com.qaryati.dataquality.repository;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.stereotype.Repository;

/** SQL against existing tables; no dependency on Heritage's internal entity model. */
@Repository
public class HeritageSiteSourceLinkRepository {
    private final EntityManager entityManager;
    public HeritageSiteSourceLinkRepository(EntityManager entityManager) { this.entityManager = entityManager; }

    public boolean siteExists(Long heritageSiteId, boolean lock) {
        return !entityManager.createNativeQuery("SELECT id FROM public.heritage_sites WHERE id = :id"
                + (lock ? " FOR UPDATE" : ""))
                .setParameter("id", heritageSiteId).getResultList().isEmpty();
    }

    public List<Long> sourceIds(Long heritageSiteId) {
        List<?> rows = entityManager.createNativeQuery(
                "SELECT source_id FROM public.heritage_site_sources WHERE heritage_site_id = :id ORDER BY source_id")
                .setParameter("id", heritageSiteId).getResultList();
        return rows.stream().map(value -> ((Number) value).longValue()).toList();
    }

    /** LEFT JOIN includes existing sites without sources and permits missing-site detection. */
    public java.util.Map<Long, List<Long>> sourceIdsBySiteIds(java.util.Collection<Long> ids) {
        if (ids.isEmpty()) return java.util.Map.of();
        List<?> rows = entityManager.createNativeQuery(
                "SELECT s.id, l.source_id FROM public.heritage_sites s LEFT JOIN public.heritage_site_sources l"
                + " ON l.heritage_site_id = s.id WHERE s.id IN (:ids) ORDER BY s.id, l.source_id")
                .setParameter("ids", ids).getResultList();
        var result = new java.util.LinkedHashMap<Long, List<Long>>();
        for (Object value : rows) {
            Object[] row = (Object[]) value;
            var sources = result.computeIfAbsent(((Number) row[0]).longValue(), key -> new java.util.ArrayList<>());
            if (row[1] != null) sources.add(((Number) row[1]).longValue());
        }
        return result;
    }

    public void deleteLinks(Long heritageSiteId) {
        entityManager.createNativeQuery("DELETE FROM public.heritage_site_sources WHERE heritage_site_id = :id")
                .setParameter("id", heritageSiteId).executeUpdate();
    }

    public void insert(Long heritageSiteId, Long sourceId) {
        entityManager.createNativeQuery("INSERT INTO public.heritage_site_sources(heritage_site_id, source_id) VALUES (:event, :source)")
                .setParameter("event", heritageSiteId).setParameter("source", sourceId).executeUpdate();
    }
}
