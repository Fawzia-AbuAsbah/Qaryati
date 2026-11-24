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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgriculturalProductValidationControllerTest {

    private MockMvc mockMvc;
    private AgriculturalProductService service;

    @BeforeEach
    void setUp() {
        service = mock(AgriculturalProductService.class);
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new AgriculturalProductController(service))
                .setValidator(validator)
                .build();
    }

    @Test
    void acceptsEachSupportedProductType() throws Exception {
        for (String productType : new String[]{"CROP", "LIVESTOCK", "PROCESSED_PRODUCT", "OTHER"}) {
            org.mockito.Mockito.reset(service);
            org.mockito.Mockito.when(service.create(any(AgriculturalProductRequest.class)))
                    .thenReturn(new AgriculturalProductResponse(1L, "Product", null, productType));

            mockMvc.perform(post("/api/v1/agricultural-products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"nameAr":"Product","productType":"%s"}
                                    """.formatted(productType)))
                    .andExpect(status().isCreated());

            verify(service).create(any(AgriculturalProductRequest.class));
        }
    }

    @Test
    void rejectsUnknownBlankAndNullProductTypesBeforeService() throws Exception {
        for (String productType : new String[]{"\"UNKNOWN\"", "\"\"", "null"}) {
            org.mockito.Mockito.reset(service);

            mockMvc.perform(post("/api/v1/agricultural-products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"nameAr":"Product","productType":%s}
                                    """.formatted(productType)))
                    .andExpect(status().isBadRequest());

            verify(service, never()).create(any(AgriculturalProductRequest.class));
        }
    }
}
