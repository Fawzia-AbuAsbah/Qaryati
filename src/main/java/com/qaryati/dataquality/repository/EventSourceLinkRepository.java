package com.qaryati.dataquality.repository;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.stereotype.Repository;

/** SQL against existing tables; no dependency on Heritage's internal entity model. */
@Repository
public class EventSourceLinkRepository {
    private final EntityManager entityManager;
    public EventSourceLinkRepository(EntityManager entityManager) { this.entityManager = entityManager; }

    public boolean eventExists(Long eventId, boolean lock) {
        return !entityManager.createNativeQuery("SELECT id FROM public.historical_events WHERE id = :id"
                + (lock ? " FOR UPDATE" : ""))
                .setParameter("id", eventId).getResultList().isEmpty();
    }

    public List<Long> sourceIds(Long eventId) {
        List<?> rows = entityManager.createNativeQuery(
                "SELECT source_id FROM public.event_sources WHERE event_id = :id ORDER BY source_id")
                .setParameter("id", eventId).getResultList();
        return rows.stream().map(value -> ((Number) value).longValue()).toList();
    }

    public void deleteLinks(Long eventId) {
        entityManager.createNativeQuery("DELETE FROM public.event_sources WHERE event_id = :id")
                .setParameter("id", eventId).executeUpdate();
    }

    public void insert(Long eventId, Long sourceId) {
        entityManager.createNativeQuery("INSERT INTO public.event_sources(event_id, source_id) VALUES (:event, :source)")
                .setParameter("event", eventId).setParameter("source", sourceId).executeUpdate();
    }
}
