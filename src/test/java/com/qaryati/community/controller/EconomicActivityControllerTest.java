package com.qaryati.community.controller;

import com.qaryati.common.exception.GlobalExceptionHandler;
import com.qaryati.community.dto.EconomicActivityRequest;
import com.qaryati.community.dto.EconomicActivityResponse;
import com.qaryati.community.entity.EconomicActivityType;
import com.qaryati.community.service.EconomicActivityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EconomicActivityControllerTest {

    private MockMvc mockMvc;
    private EconomicActivityService service;

    @BeforeEach
    void setUp() {
        service = mock(EconomicActivityService.class);
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new EconomicActivityController(service))
                .setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createsActivity() throws Exception {
        when(service.create(any(EconomicActivityRequest.class))).thenReturn(response());

        mockMvc.perform(post("/api/v1/economic-activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.villageId").value(1))
                .andExpect(jsonPath("$.activityType").value("CRAFT"));

        verify(service).create(any(EconomicActivityRequest.class));
    }

    @Test
    void getsAllAndFiltersByVillage() throws Exception {
        when(service.getAll(null)).thenReturn(List.of(response()));
        when(service.getAll(1L)).thenReturn(List.of(response()));

        mockMvc.perform(get("/api/v1/economic-activities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Craft workshop"));
        mockMvc.perform(get("/api/v1/economic-activities?villageId=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].villageId").value(1));

        verify(service).getAll(null);
        verify(service).getAll(1L);
    }

    @Test
    void getsUpdatesAndDeletesById() throws Exception {
        when(service.getById(1L)).thenReturn(response());
        when(service.update(eq(1L), any(EconomicActivityRequest.class))).thenReturn(response());

        mockMvc.perform(get("/api/v1/economic-activities/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
        mockMvc.perform(put("/api/v1/economic-activities/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/economic-activities/1"))
                .andExpect(status().isNoContent());

        verify(service).getById(1L);
        verify(service).update(eq(1L), any(EconomicActivityRequest.class));
        verify(service).delete(1L);
    }

    @Test
    void rejectsInvalidRequestBeforeService() throws Exception {
        mockMvc.perform(post("/api/v1/economic-activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"villageId":0,"name":"","activityType":"UNKNOWN"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    private EconomicActivityResponse response() {
        return new EconomicActivityResponse(1L, 1L, "Craft workshop", EconomicActivityType.CRAFT,
                "Local handicrafts");
    }

    private String body() {
        return """
                {"villageId":1,"name":"Craft workshop","activityType":"CRAFT","description":"Local handicrafts"}
                """;
    }
}
