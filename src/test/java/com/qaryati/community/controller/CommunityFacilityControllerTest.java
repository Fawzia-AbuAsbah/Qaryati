package com.qaryati.community.controller;

import com.qaryati.common.exception.GlobalExceptionHandler;
import com.qaryati.community.dto.CommunityFacilityRequest;
import com.qaryati.community.dto.CommunityFacilityResponse;
import com.qaryati.community.entity.CommunityFacilityType;
import com.qaryati.community.service.CommunityFacilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CommunityFacilityControllerTest {

    private MockMvc mockMvc;
    private CommunityFacilityService service;

    @BeforeEach
    void setUp() {
        service = mock(CommunityFacilityService.class);
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new CommunityFacilityController(service))
                .setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createsFacility() throws Exception {
        when(service.create(any(CommunityFacilityRequest.class))).thenReturn(response());

        mockMvc.perform(post("/api/v1/community-facilities")
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.facilityType").value("SCHOOL"))
                .andExpect(jsonPath("$.latitude").value(31.5));

        verify(service).create(any(CommunityFacilityRequest.class));
    }

    @Test
    void getsAllAndFiltersByVillage() throws Exception {
        when(service.getAll(null)).thenReturn(List.of(response()));
        when(service.getAll(1L)).thenReturn(List.of(response()));

        mockMvc.perform(get("/api/v1/community-facilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Village school"));
        mockMvc.perform(get("/api/v1/community-facilities?villageId=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].villageId").value(1));
    }

    @Test
    void getsUpdatesAndDeletesById() throws Exception {
        when(service.getById(1L)).thenReturn(response());
        when(service.update(eq(1L), any(CommunityFacilityRequest.class))).thenReturn(response());

        mockMvc.perform(get("/api/v1/community-facilities/1"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/community-facilities/1")
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/community-facilities/1"))
                .andExpect(status().isNoContent());

        verify(service).getById(1L);
        verify(service).update(eq(1L), any(CommunityFacilityRequest.class));
        verify(service).delete(1L);
    }

    @Test
    void rejectsInvalidCoordinatesBeforeService() throws Exception {
        mockMvc.perform(post("/api/v1/community-facilities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"villageId":1,"name":"Facility","facilityType":"SCHOOL","latitude":31.5}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void rejectsUnknownFacilityTypeBeforeService() throws Exception {
        mockMvc.perform(post("/api/v1/community-facilities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"villageId":1,"name":"Facility","facilityType":"UNKNOWN"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    private CommunityFacilityResponse response() {
        return new CommunityFacilityResponse(1L, 1L, "Village school", CommunityFacilityType.SCHOOL,
                "Education", new BigDecimal("31.500000"), new BigDecimal("35.200000"));
    }

    private String body() {
        return """
                {"villageId":1,"name":"Village school","facilityType":"SCHOOL","description":"Education","latitude":31.500000,"longitude":35.200000}
                """;
    }
}
