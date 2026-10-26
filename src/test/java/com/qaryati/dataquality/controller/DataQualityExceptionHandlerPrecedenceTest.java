package com.qaryati.dataquality.controller;

import com.qaryati.common.exception.GlobalExceptionHandler;
import com.qaryati.dataquality.exception.DataQualityExceptionHandler;
import com.qaryati.dataquality.service.SourceService;
import com.qaryati.security.config.SecurityConfig;
import com.qaryati.security.ratelimit.LoginRateLimitService;
import com.qaryati.village.controller.GovernorateController;
import com.qaryati.village.service.GovernorateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({SourceController.class, GovernorateController.class})
@Import({GlobalExceptionHandler.class, DataQualityExceptionHandler.class, SecurityConfig.class})
class DataQualityExceptionHandlerPrecedenceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SourceService sourceService;

    @MockitoBean
    private GovernorateService governorateService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private JwtAuthenticationConverter jwtAuthenticationConverter;

    @MockitoBean
    private LoginRateLimitService loginRateLimitService;

    @Test
    @WithMockUser(roles = "EDITOR")
    void dataQualityValidationTakesPrecedenceOverGlobalHandler() throws Exception {
        mockMvc.perform(post("/api/v1/sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceType":"BOOK"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"error":"Invalid request data"}
                        """))
                .andExpect(jsonPath("$").value(org.hamcrest.Matchers.aMapWithSize(1)));

        verifyNoInteractions(sourceService, governorateService);
    }

    @Test
    @WithMockUser(roles = "EDITOR")
    void validationOutsideDataQualityStillUsesGlobalHandler() throws Exception {
        mockMvc.perform(post("/api/v1/governorates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nameAr":" "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("nameAr: Arabic name is required"))
                .andExpect(jsonPath("$.path").value("/api/v1/governorates"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());

        verifyNoInteractions(sourceService, governorateService);
    }

    @Test
    void unauthenticatedInvalidRequestIsRejectedBeforeValidation() throws Exception {
        mockMvc.perform(post("/api/v1/sources")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(sourceService, governorateService);
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerCannotCreateSource() throws Exception {
        mockMvc.perform(post("/api/v1/sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Test source","sourceType":"BOOK"}
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(sourceService, governorateService);
    }
}
