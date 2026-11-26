package com.qaryati.community.repository;

import com.qaryati.community.CommunityTestDatabaseGuard;
import com.qaryati.community.entity.CommunityFacility;
import com.qaryati.community.entity.CommunityFacilityType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = CommunityTestDatabaseGuard.class)
@Transactional
class CommunityFacilityRepositoryIntegrationTest {

    @Autowired
    private CommunityFacilityRepository repository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void persistsCoordinatesFiltersByVillageAndAllowsSameTuple() {
        Long villageId = villageId();
        var first = repository.saveAndFlush(new CommunityFacility(villageId, "Village school",
                CommunityFacilityType.SCHOOL, null, new BigDecimal("31.500000"),
                new BigDecimal("35.200000")));
        repository.saveAndFlush(new CommunityFacility(villageId, "Village school",
                CommunityFacilityType.SCHOOL, null, null, null));

        assertEquals(2, repository.findByVillageId(villageId).size());
        assertEquals(new BigDecimal("31.500000"), repository.findById(first.getId()).orElseThrow().getLatitude());
    }

    @Test
    void databaseRejectsUnpairedCoordinates() {
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(new CommunityFacility(villageId(), "Village school",
                        CommunityFacilityType.SCHOOL, null, new BigDecimal("31.5"), null)));
    }

    private Long villageId() {
        String suffix = UUID.randomUUID().toString();
        Long governorateId = jdbcTemplate.queryForObject("""
                INSERT INTO public.governorates (name_ar) VALUES (?) RETURNING id
                """, Long.class, "Facility governorate " + suffix);
        return jdbcTemplate.queryForObject("""
                INSERT INTO public.villages (governorate_id, name_ar, location_description)
                VALUES (?, ?, ?) RETURNING id
                """, Long.class, governorateId, "Facility village " + suffix, "Test location");
    }
}
