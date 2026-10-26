
package com.qaryati.dataquality.service;

import org.springframework.test.util.ReflectionTestUtils;

import com.qaryati.dataquality.entity.Source;
import com.qaryati.dataquality.repository.SourceRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SourceServiceTest {

    private SourceRepository sourceRepository;
    private AuditLogService auditLogService;
    private SourceService sourceService;

    @BeforeEach
    void setUp() {
        sourceRepository = mock(SourceRepository.class);
        auditLogService = mock(AuditLogService.class);

        sourceService = new SourceService(
                sourceRepository,
                auditLogService
        );
    }

    @Test
    void shouldCreateValidSource() {

        Source source = new Source(
                "Palestinian Population Census",
                "CENSUS"
        );


        when(sourceRepository.saveAndFlush(any(Source.class)))
                .thenAnswer(invocation -> {
                    Source saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", 1L);
                    return saved;
                });


        Source result = sourceService.createSource(source);

        assertEquals(
                "Palestinian Population Census",
                result.getTitle()
        );

        assertEquals("CENSUS", result.getSourceType());

        verify(sourceRepository).saveAndFlush(source);

        verify(auditLogService).record(
                eq("SOURCE"),
                eq(1L),
                eq("CREATE"),
                isNull(),
                argThat(data ->
                        "Palestinian Population Census".equals(
                                data.get("title")
                        )
                                && "CENSUS".equals(
                                data.get("sourceType")
                        )
                ),
                eq("Source created")
        );
    }

    @Test
    void shouldRejectSourceWithoutTitle() {

        Source source = new Source("", "BOOK");

        assertThrows(
                IllegalArgumentException.class,
                () -> sourceService.createSource(source)
        );

        verifyNoInteractions(sourceRepository, auditLogService);
    }

    @Test
    void shouldRejectSourceWithoutType() {

        Source source = new Source("Village History", "");

        assertThrows(
                IllegalArgumentException.class,
                () -> sourceService.createSource(source)
        );

        verifyNoInteractions(sourceRepository, auditLogService);
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
        verifyNoInteractions(auditLogService);
    }
}
