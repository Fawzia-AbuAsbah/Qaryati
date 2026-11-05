package com.qaryati.dataquality.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventSourceLinkRepositoryTest {
    @Test void queriesExistingTablesWithDeterministicOrderingAndParentLock() {
        var em = mock(EntityManager.class);
        var query = mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(2L, 3L));
        var repository = new EventSourceLinkRepository(em);
        assertEquals(List.of(2L, 3L), repository.sourceIds(1L));
        verify(em).createNativeQuery("SELECT source_id FROM public.event_sources WHERE event_id = :id ORDER BY source_id");
        assertTrue(repository.eventExists(1L, true));
        verify(em).createNativeQuery("SELECT id FROM public.historical_events WHERE id = :id FOR UPDATE");
        repository.deleteLinks(1L);
        repository.insert(1L, 2L);
        verify(em).createNativeQuery("DELETE FROM public.event_sources WHERE event_id = :id");
        verify(em).createNativeQuery("INSERT INTO public.event_sources(event_id, source_id) VALUES (:event, :source)");
        verify(query, times(2)).executeUpdate();
    }
}
