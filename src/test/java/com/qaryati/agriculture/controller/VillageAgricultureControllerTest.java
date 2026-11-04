
package com.qaryati.agriculture.controller;

import com.qaryati.agriculture.dto.VillageAgricultureResponse;
import com.qaryati.agriculture.entity.AgricultureImportance;
import com.qaryati.agriculture.service.VillageAgricultureService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class VillageAgricultureControllerTest {

    private MockMvc mockMvc;
    private VillageAgricultureService service;

    @BeforeEach
    void setUp() {

        service = mock(VillageAgricultureService.class);

        VillageAgricultureController controller =
                new VillageAgricultureController(service);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .build();
    }

    @Test
    void shouldGetRelationById() throws Exception {

        var response = new VillageAgricultureResponse(
                1L,
                10L,
                5L,
                "Olive cultivation",
                AgricultureImportance.HIGH
        );

        when(service.getById(1L)).thenReturn(response);

        mockMvc.perform(
                        get("/api/v1/village-agriculture/1")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.villageId").value(10))
                .andExpect(jsonPath("$.productId").value(5))
                .andExpect(jsonPath("$.importance").value("HIGH"));

        verify(service).getById(1L);
    }

    @Test
    void shouldGetRelationsByVillage() throws Exception {

        var response = new VillageAgricultureResponse(
                1L,
                10L,
                5L,
                "Olive cultivation",
                AgricultureImportance.HIGH
        );

        when(service.getByVillageId(10L))
                .thenReturn(List.of(response));

        mockMvc.perform(
                        get("/api/v1/village-agriculture/village/10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].villageId").value(10))
                .andExpect(jsonPath("$[0].productId").value(5));

        verify(service).getByVillageId(10L);
    }

    @Test
    void shouldGetRelationsByProduct() throws Exception {

        var response = new VillageAgricultureResponse(
                1L,
                10L,
                5L,
                "Olive cultivation",
                AgricultureImportance.MEDIUM
        );

        when(service.getByProductId(5L))
                .thenReturn(List.of(response));

        mockMvc.perform(
                        get("/api/v1/village-agriculture/product/5")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productId").value(5))
                .andExpect(jsonPath("$[0].importance").value("MEDIUM"));

        verify(service).getByProductId(5L);
    }

    @Test
    void shouldReturn404WhenRelationNotFound() throws Exception {

        when(service.getById(999L))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Village agriculture record not found"
                ));

        mockMvc.perform(
                        get("/api/v1/village-agriculture/999")
                )
                .andExpect(status().isNotFound());

        verify(service).getById(999L);
    }
}
