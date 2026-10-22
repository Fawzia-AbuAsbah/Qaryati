
package com.qaryati.dataquality.repository;

import com.qaryati.dataquality.entity.Source;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SourceRepositoryIntegrationTest {

    @Autowired
    private SourceRepository sourceRepository;

    @Test
    void shouldSaveAndFindSource() {

        Source source = new Source(
                "Palestinian Population Census",
                "CENSUS"
        );

        source.setPublisher(
                "Palestinian Central Bureau of Statistics"
        );

        Source saved = sourceRepository.saveAndFlush(source);

        assertNotNull(saved.getId());

        Source found = sourceRepository.findById(saved.getId())
                .orElseThrow();

        assertEquals(
                "Palestinian Population Census",
                found.getTitle()
        );

        assertEquals("CENSUS", found.getSourceType());    }

    @Test
    void shouldFindSourceByTitleIgnoringCase() {

        Source source = new Source(
                "History of Palestinian Villages",
                "BOOK"
        );

        sourceRepository.saveAndFlush(source);

        List<Source> results =
                sourceRepository.findByTitleContainingIgnoreCase(
                        "palestinian"
                );

        assertTrue(
                results.stream().anyMatch(
                        s -> s.getTitle().equals(
                                "History of Palestinian Villages"
                        )
                )
        );
    }


    @Test
    void shouldFindSourcesByType() {

        Source source = new Source(
                "Village Heritage Book",
                "BOOK"
        );

        sourceRepository.saveAndFlush(source);

        List<Source> results =
                sourceRepository.findBySourceType("BOOK");

        assertTrue(
                results.stream().anyMatch(
                        s -> s.getTitle().equals("Village Heritage Book")
                )
        );
    }

}
