package com.qaryati.community.mapper;

import com.qaryati.community.dto.CommunityFacilityRequest;
import com.qaryati.community.entity.CommunityFacilityType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommunityFacilityMapperTest {

    private final CommunityFacilityMapper mapper = new CommunityFacilityMapper();

    @Test
    void mapsAndUpdatesAllSchemaBackedFields() {
        var entity = mapper.toEntity(request(1L, "Village school", CommunityFacilityType.SCHOOL,
                new BigDecimal("31.500000"), new BigDecimal("35.200000")));
        assertEquals(1L, entity.getVillageId());
        assertEquals(CommunityFacilityType.SCHOOL, entity.getFacilityType());
        assertEquals(new BigDecimal("31.500000"), entity.getLatitude());

        mapper.updateEntity(request(2L, "Village clinic", CommunityFacilityType.CLINIC, null, null), entity);
        assertEquals(2L, entity.getVillageId());
        assertEquals("Village clinic", entity.getName());
        assertEquals(CommunityFacilityType.CLINIC, entity.getFacilityType());
        assertEquals(null, entity.getLatitude());
        assertEquals(null, entity.getLongitude());
    }

    private CommunityFacilityRequest request(Long villageId, String name, CommunityFacilityType type,
                                             BigDecimal latitude, BigDecimal longitude) {
        return new CommunityFacilityRequest(villageId, name, type, "Description", latitude, longitude);
    }
}
