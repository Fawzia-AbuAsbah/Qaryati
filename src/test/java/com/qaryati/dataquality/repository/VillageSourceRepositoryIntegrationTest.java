
package com.qaryati.dataquality.repository;

import com.qaryati.dataquality.entity.VillageSource;
import com.qaryati.dataquality.entity.VillageSourceId;

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
class VillageSourceRepositoryIntegrationTest {

    @Autowired
    private VillageSourceRepository villageSourceRepository;

    @Test
    void shouldFindSourcesByVillageId() {

        List<VillageSource> results =
                villageSourceRepository.findByIdVillageId(999999L);

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void shouldFindVillagesBySourceId() {

        List<VillageSource> results =
                villageSourceRepository.findByIdSourceId(999999L);

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void shouldFindVillageSourceByCompositeId() {

        VillageSourceId id =
                new VillageSourceId(999999L, 999999L);

        assertTrue(villageSourceRepository.findById(id).isEmpty());
    }
}
