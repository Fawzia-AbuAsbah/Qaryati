
package com.qaryati.agriculture.controller;

import com.qaryati.agriculture.dto.AgriculturalProductRequest;
import com.qaryati.agriculture.dto.AgriculturalProductResponse;
import com.qaryati.agriculture.service.AgriculturalProductService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AgriculturalProductControllerTest {

    private MockMvc mockMvc;
    private AgriculturalProductService service;

    @BeforeEach
    void setUp() {
        service = mock(AgriculturalProductService.class);

        AgriculturalProductController controller =
                new AgriculturalProductController(service);

        LocalValidatorFactoryBean validator =
                new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .build();
    }

    @Test
    void shouldReturnAllProducts() throws Exception {

        when(service.getAll()).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/v1/agricultural-products"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(service).getAll();
    }

    @Test
    void shouldCreateProduct() throws Exception {

        AgriculturalProductResponse response =
                new AgriculturalProductResponse(
                        1L, "زيتون", "Olives", "CROP"
                );

        when(service.create(any(AgriculturalProductRequest.class)))
                .thenReturn(response);

        String json = """
                {
                    "nameAr": "زيتون",
                    "nameEn": "Olives",
                    "productType": "CROP"
                }
                """;

        mockMvc.perform(post("/api/v1/agricultural-products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nameAr").value("زيتون"))
                .andExpect(jsonPath("$.productType").value("CROP"));

        verify(service).create(any(AgriculturalProductRequest.class));
    }

    @Test
    void shouldRejectBlankArabicName() throws Exception {

        String json = """
                {
                    "nameAr": "",
                    "nameEn": "Olives",
                    "productType": "CROP"
                }
                """;

        mockMvc.perform(post("/api/v1/agricultural-products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());

        verify(service, never())
                .create(any(AgriculturalProductRequest.class));
    }
}
