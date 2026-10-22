
package com.qaryati.dataquality.service;

import com.qaryati.dataquality.entity.Source;
import com.qaryati.dataquality.repository.SourceRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SourceServiceTest {

    private SourceRepository sourceRepository;
    private SourceService sourceService;

    @BeforeEach
    void setUp() {
        sourceRepository = mock(SourceRepository.class);
        sourceService = new SourceService(sourceRepository);
    }

    @Test
    void shouldCreateValidSource() {

        Source source = new Source(
                "Palestinian Population Census",
                "CENSUS"
        );

        when(sourceRepository.save(any(Source.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Source result = sourceService.createSource(source);

        assertEquals("Palestinian Population Census",
                result.getTitle());

        assertEquals("CENSUS", result.getSourceType());

        verify(sourceRepository).save(source);
    }

    @Test
    void shouldRejectSourceWithoutTitle() {

        Source source = new Source("", "BOOK");

        assertThrows(
                IllegalArgumentException.class,
                () -> sourceService.createSource(source)
        );

        verifyNoInteractions(sourceRepository);
    }

    @Test
    void shouldRejectSourceWithoutType() {

        Source source = new Source("Village History", "");

        assertThrows(
                IllegalArgumentException.class,
                () -> sourceService.createSource(source)
        );

        verifyNoInteractions(sourceRepository);
    }

    @Test
    void shouldThrowWhenSourceNotFound() {

        when(sourceRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                NoSuchElementException.class,
                () -> sourceService.getSourceById(999L)
        );

        verify(sourceRepository).findById(999L);
    }
}
