package com.qaryati.dataquality.controller;

import com.qaryati.dataquality.entity.VillageSourceId;
import com.qaryati.dataquality.exception.DataQualityExceptionHandler;
import com.qaryati.dataquality.repository.*;
import com.qaryati.dataquality.service.*;
import com.qaryati.security.config.SecurityConfig;
import com.qaryati.security.ratelimit.LoginRateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real controller, service authorization and error advice; persistence is mocked. */
@WebMvcTest(VillageSourceController.class)
@Import({VillageSourceService.class, DataQualityExceptionHandler.class, SecurityConfig.class})
class VillageSourceControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean VillageSourceRepository links;
    @MockitoBean SourceRepository sources;
    @MockitoBean VillageExistsRepository villages;
    @MockitoBean AuditLogService audits;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean JwtAuthenticationConverter converter;
    @MockitoBean LoginRateLimitService rateLimits;

    @BeforeEach void referencesExist() {
        when(villages.existsById(1L)).thenReturn(true);
        when(sources.existsById(5L)).thenReturn(true);
    }

    @Test @WithMockUser(roles = "EDITOR")
    void successfulInsertPreservesNoteAndReturnsCreated() throws Exception {
        when(links.insertIfAbsent(1L, 5L, "  Citation note  ")).thenReturn(1);
        mvc.perform(post("/api/v1/villages/1/sources").contentType("application/json")
                        .content("{\"sourceId\":5,\"claimNote\":\"  Citation note  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.villageId").value(1))
                .andExpect(jsonPath("$.sourceId").value(5))
                .andExpect(jsonPath("$.claimNote").value("  Citation note  "));
        verify(links, never()).saveAndFlush(any());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    @WithMockUser(roles = "ADMIN")
    void bothConcurrentAndPrecheckedDuplicatesReturn409(boolean precheckFindsDuplicate) throws Exception {
        when(links.existsById(new VillageSourceId(1L, 5L))).thenReturn(precheckFindsDuplicate);
        // Zero rows inserted models a competing committed insert after a stale precheck.
        when(links.insertIfAbsent(1L, 5L, "Replacement note")).thenReturn(0);
        mvc.perform(post("/api/v1/villages/1/sources").contentType("application/json")
                        .content("{\"sourceId\":5,\"claimNote\":\"Replacement note\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Source is already linked to this village"));
        verify(links, times(precheckFindsDuplicate ? 0 : 1)).insertIfAbsent(1L, 5L, "Replacement note");
        verify(links, never()).saveAndFlush(any());
        verifyNoInteractions(audits);
    }

    @Test @WithMockUser(roles = "VIEWER")
    void viewerCannotCreateLink() throws Exception {
        mvc.perform(post("/api/v1/villages/1/sources").contentType("application/json")
                        .content("{\"sourceId\":5}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(links, audits);
    }

    @Test void anonymousCannotCreateLink() throws Exception {
        mvc.perform(post("/api/v1/villages/1/sources").contentType("application/json")
                        .content("{\"sourceId\":5}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(links, audits);
    }
}
