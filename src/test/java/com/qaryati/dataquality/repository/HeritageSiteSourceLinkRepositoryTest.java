package com.qaryati.dataquality.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HeritageSiteSourceLinkRepositoryTest {
    @Test void batchUsesOneLeftJoinAndSkipsEmptyInput() {
        var em = mock(EntityManager.class);
        var query = mock(Query.class);
        var repository = new HeritageSiteSourceLinkRepository(em);
        assertEquals(java.util.Map.of(), repository.sourceIdsBySiteIds(List.of()));
        verifyNoInteractions(em);
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter("ids", List.of(1L, 2L))).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(new Object[]{1L, 3L}, new Object[]{2L, null}));
        assertEquals(java.util.Map.of(1L, List.of(3L), 2L, List.of()),
                repository.sourceIdsBySiteIds(List.of(1L, 2L)));
        verify(em).createNativeQuery("SELECT s.id, l.source_id FROM public.heritage_sites s LEFT JOIN public.heritage_site_sources l"
                + " ON l.heritage_site_id = s.id WHERE s.id IN (:ids) ORDER BY s.id, l.source_id");
        verify(query).getResultList();
        verifyNoMoreInteractions(em);
    }
    @Test void queriesExistingTablesWithDeterministicOrderingAndParentLock() {
        var em = mock(EntityManager.class);
        var query = mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(2L, 3L));
        var repository = new HeritageSiteSourceLinkRepository(em);
        assertEquals(List.of(2L, 3L), repository.sourceIds(1L));
        verify(em).createNativeQuery("SELECT source_id FROM public.heritage_site_sources WHERE heritage_site_id = :id ORDER BY source_id");
        assertTrue(repository.siteExists(1L, true));
        verify(em).createNativeQuery("SELECT id FROM public.heritage_sites WHERE id = :id FOR UPDATE");
        repository.deleteLinks(1L);
        repository.insert(1L, 2L);
        verify(em).createNativeQuery("DELETE FROM public.heritage_site_sources WHERE heritage_site_id = :id");
        verify(em).createNativeQuery("INSERT INTO public.heritage_site_sources(heritage_site_id, source_id) VALUES (:event, :source)");
        verify(query, times(2)).executeUpdate();
    }
}
