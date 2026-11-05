package com.qaryati.dataquality.service;

import com.qaryati.dataquality.api.SourceLookup;
import com.qaryati.dataquality.repository.SourceRepository;
import org.junit.jupiter.api.Test;
import java.util.NoSuchElementException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SourceLookupServiceTest {
    private final SourceRepository repository = mock(SourceRepository.class);
    private final SourceLookup lookup = new SourceLookupService(repository);

    @Test
    void checksAndRequiresExistingSources() {
        when(repository.existsById(7L)).thenReturn(true);
        assertTrue(lookup.existsById(7L));
        assertDoesNotThrow(() -> lookup.requireExists(7L));
        assertFalse(lookup.existsById(8L));
        assertThrows(NoSuchElementException.class, () -> lookup.requireExists(8L));
    }

    @Test
    void rejectsInvalidIdsBeforePersistence() {
        for (Long id : new Long[]{null, 0L, -1L}) {
            assertThrows(IllegalArgumentException.class, () -> lookup.existsById(id));
            assertThrows(IllegalArgumentException.class, () -> lookup.requireExists(id));
        }
        verifyNoInteractions(repository);
    }
}
