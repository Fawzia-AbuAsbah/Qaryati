package com.qaryati.dataquality.controller;

import com.qaryati.dataquality.entity.VillageSource;
import com.qaryati.dataquality.entity.VillageSourceId;
import com.qaryati.dataquality.exception.DataQualityExceptionHandler;
import com.qaryati.dataquality.repository.*;
import com.qaryati.dataquality.service.*;
import com.qaryati.security.config.SecurityConfig;
import com.qaryati.security.ratelimit.LoginRateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @ParameterizedTest
    @CsvSource({
            "/api/v1/villages/0/sources,Village ID must be positive",
            "/api/v1/villages/-1/sources,Village ID must be positive",
            "/api/v1/villages/-9223372036854775808/sources,Village ID must be positive",
            "/api/v1/sources/0/villages,Source ID must be positive",
            "/api/v1/sources/-1/villages,Source ID must be positive",
            "/api/v1/sources/-9223372036854775808/villages,Source ID must be positive",
            "/api/v1/villages/not-a-number/sources,Invalid parameter: villageId",
            "/api/v1/sources/not-a-number/villages,Invalid parameter: sourceId",
            "/api/v1/villages/9223372036854775808/sources,Invalid parameter: villageId",
            "/api/v1/sources/9223372036854775808/villages,Invalid parameter: sourceId"
    })
    @WithMockUser(roles = "VIEWER")
    void invalidLookupIdsReturn400WithoutRepositoryAccess(String path, String message) throws Exception {
        mvc.perform(get(path))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$", aMapWithSize(1)))
                .andExpect(jsonPath("$.error").value(message));
        verifyNoInteractions(links, sources, villages, audits);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @WithMockUser(roles = "VIEWER")
    void positiveLookupsPreserveReturnedLinks(boolean byVillage) throws Exception {
        var results = List.of(new VillageSource(1L, 5L, "  Citation note  "));
        if (byVillage) when(links.findByIdVillageId(1L)).thenReturn(results);
        else when(links.findByIdSourceId(5L)).thenReturn(results);

        mvc.perform(get(byVillage ? "/api/v1/villages/1/sources" : "/api/v1/sources/5/villages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].villageId").value(1))
                .andExpect(jsonPath("$[0].sourceId").value(5))
                .andExpect(jsonPath("$[0].claimNote").value("  Citation note  "));
        if (byVillage) verify(links).findByIdVillageId(1L);
        else verify(links).findByIdSourceId(5L);
        verifyNoMoreInteractions(links);
        verifyNoInteractions(sources, villages, audits);
    }

    @ParameterizedTest
    @CsvSource({
            "/api/v1/villages/1/sources,1,true",
            "/api/v1/villages/9223372036854775807/sources,9223372036854775807,true",
            "/api/v1/sources/5/villages,5,false",
            "/api/v1/sources/9223372036854775807/villages,9223372036854775807,false"
    })
    @WithMockUser(roles = "VIEWER")
    void positiveLookupIdsMayReturnEmptyArrays(String path, long id, boolean byVillage) throws Exception {
        if (byVillage) when(links.findByIdVillageId(id)).thenReturn(List.of());
        else when(links.findByIdSourceId(id)).thenReturn(List.of());

        mvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
        if (byVillage) verify(links).findByIdVillageId(id);
        else verify(links).findByIdSourceId(id);
        verifyNoMoreInteractions(links);
        verifyNoInteractions(sources, villages, audits);
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
