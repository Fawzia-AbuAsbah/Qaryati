package com.qaryati.community.mapper;

import com.qaryati.community.dto.EconomicActivityRequest;
import com.qaryati.community.entity.EconomicActivity;
import com.qaryati.community.entity.EconomicActivityType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EconomicActivityMapperTest {

    private final EconomicActivityMapper mapper = new EconomicActivityMapper();

    @Test
    void mapsAndUpdatesEverySchemaBackedField() {
        var created = mapper.toEntity(new EconomicActivityRequest(1L, "Olive farming",
                EconomicActivityType.AGRICULTURE, "Seasonal"));
        assertEquals(1L, created.getVillageId());
        assertEquals("Olive farming", created.getName());
        assertEquals(EconomicActivityType.AGRICULTURE, created.getActivityType());
        assertEquals("Seasonal", created.getDescription());

        mapper.updateEntity(new EconomicActivityRequest(2L, "Craft workshop",
                EconomicActivityType.CRAFT, null), created);
        assertEquals(2L, created.getVillageId());
        assertEquals("Craft workshop", created.getName());
        assertEquals(EconomicActivityType.CRAFT, created.getActivityType());
        assertEquals(null, created.getDescription());
    }
}
