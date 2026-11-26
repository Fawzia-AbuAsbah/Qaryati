package com.qaryati.community.repository;

import com.qaryati.community.CommunityTestDatabaseGuard;
import com.qaryati.community.entity.EconomicActivity;
import com.qaryati.community.entity.EconomicActivityType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = CommunityTestDatabaseGuard.class)
@Transactional
class EconomicActivityRepositoryIntegrationTest {

    @Autowired
    private EconomicActivityRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void persistsFiltersAndEnforcesTheUniqueTuple() {
        Long villageId = villageId();
        var activity = repository.saveAndFlush(new EconomicActivity(villageId, "Olive farming",
                EconomicActivityType.AGRICULTURE, "Seasonal"));

        assertTrue(repository.findByVillageId(villageId).stream()
                .anyMatch(found -> found.getId().equals(activity.getId())));
        assertTrue(repository.existsByVillageIdAndNameAndActivityType(villageId, "Olive farming",
                EconomicActivityType.AGRICULTURE));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(new EconomicActivity(villageId, "Olive farming",
                        EconomicActivityType.AGRICULTURE, null)));
    }

    private Long villageId() {
        String suffix = UUID.randomUUID().toString();
        Long governorateId = jdbcTemplate.queryForObject("""
                INSERT INTO public.governorates (name_ar) VALUES (?) RETURNING id
                """, Long.class, "Community governorate " + suffix);
        return jdbcTemplate.queryForObject("""
                INSERT INTO public.villages (governorate_id, name_ar, location_description)
                VALUES (?, ?, ?) RETURNING id
                """, Long.class, governorateId, "Community village " + suffix, "Test location");
    }
}
