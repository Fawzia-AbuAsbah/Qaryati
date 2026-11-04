
package com.qaryati.agriculture.mapper;

import com.qaryati.agriculture.dto.VillageAgricultureRequest;
import com.qaryati.agriculture.dto.VillageAgricultureResponse;
import com.qaryati.agriculture.entity.AgricultureImportance;
import com.qaryati.agriculture.entity.VillageAgriculture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VillageAgricultureMapperTest {

    private VillageAgricultureMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new VillageAgricultureMapper();
    }

    @Test
    void shouldConvertRequestToEntity() {

        VillageAgricultureRequest request =
                new VillageAgricultureRequest(
                        10L,
                        5L,
                        "Olive cultivation",
                        AgricultureImportance.HIGH
                );

        VillageAgriculture entity = mapper.toEntity(request);

        assertNull(entity.getId());
        assertEquals(10L, entity.getVillageId());
        assertEquals(5L, entity.getProductId());
        assertEquals("Olive cultivation", entity.getDescription());
        assertEquals(AgricultureImportance.HIGH,
                entity.getImportance());
    }

    @Test
    void shouldConvertEntityToResponse() {

        VillageAgriculture entity = new VillageAgriculture(
                10L,
                5L,
                "Olive cultivation",
                AgricultureImportance.MEDIUM
        );

        VillageAgricultureResponse response =
                mapper.toResponse(entity);

        assertEquals(10L, response.villageId());
        assertEquals(5L, response.productId());
        assertEquals("Olive cultivation", response.description());
        assertEquals(AgricultureImportance.MEDIUM,
                response.importance());
    }

    @Test
    void shouldUpdateOnlyDescriptionAndImportance() {

        VillageAgriculture entity = new VillageAgriculture(
                10L,
                5L,
                "Old description",
                AgricultureImportance.LOW
        );

        VillageAgricultureRequest request =
                new VillageAgricultureRequest(
                        99L,
                        88L,
                        "Updated description",
                        AgricultureImportance.HIGH
                );

        mapper.updateEntity(request, entity);

        assertEquals(10L, entity.getVillageId());
        assertEquals(5L, entity.getProductId());
        assertEquals("Updated description", entity.getDescription());
        assertEquals(AgricultureImportance.HIGH,
                entity.getImportance());
    }
}
