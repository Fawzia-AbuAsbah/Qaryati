package com.qaryati.heritage;

import com.qaryati.common.exception.GlobalExceptionHandler;
import com.qaryati.heritage.controller.*;
import com.qaryati.heritage.service.HistoricalEventService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Production stateless bearer security, real service, database-free provider doubles. */
@WebMvcTest(HistoricalEventController.class)
@Import({HistoricalEventService.class, GlobalExceptionHandler.class, HistoricalEventExceptionHandler.class,
        com.qaryati.heritage.controller.HistoricalEventController.class,
        com.qaryati.security.config.SecurityConfig.class,
        com.qaryati.security.config.JwtAuthenticationConverterConfig.class})
class HistoricalEventControllerTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.qaryati.heritage.repository.HistoricalEventRepository repository;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.qaryati.village.api.VillageLookupApi villages;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.qaryati.dataquality.api.SourceLookup sources;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.qaryati.dataquality.api.AuditWriter audits;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    org.springframework.security.oauth2.jwt.JwtDecoder decoder;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.qaryati.security.service.DatabaseUserDetailsService userDetails;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.qaryati.security.ratelimit.LoginRateLimitService rateLimits;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.qaryati.dataquality.api.EventSourceLinks links;
    private static final String BASE = "/api/v1/historical-events";
    private static final String JSON = """
            {"villageId":1,"title":"Title","description":"Description","datePrecision":"UNKNOWN","sourceIds":[2]}
            """;
    @Autowired private MockMvc mvc;

    @ParameterizedTest
    @ValueSource(strings = {"GET_ONE", "POST", "PUT", "DELETE"})
    @WithMockUser(roles = "VIEWER")
    void viewerCannotWriteAndHiddenItemsAreNotFound(String operation) throws Exception {
        String path = path(operation);
        mvc.perform(request(method(operation), path)
                        .contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().is(operation.equals("GET_ONE") ? 404 : 403))
                .andExpect(jsonPath("$.path").value(path));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "GET_ONE", "POST", "PUT", "DELETE"})
    void unauthenticatedRequestsAreUnauthorized(String operation) throws Exception {
        mvc.perform(request(method(operation), path(operation))
                        .accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE"})
    @WithMockUser(roles = "ADMIN")
    void writesUseBearerSecurityWithoutCsrf(String operation) throws Exception {
        mvc.perform(request(method(operation), path(operation))
                        .contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT"})
    @WithMockUser(roles = "EDITOR")
    void invalidAndProtectedFieldsReturnCommonBadRequestShape(String operation) throws Exception {
        for (String json : java.util.List.of("{}", "{", JSON.replace("UNKNOWN", "INVALID"),
                JSON.replace("[2]", "[]"), JSON.replace("[2]", "[-1]"),
                JSON.replace("[2]", "[null]"), JSON.replace("[2]", "null"),
                JSON.replace("Title", ""),
                JSON.replace("\"villageId\":1", "\"villageId\":0"),
                JSON.replace("}", ",\"eventDate\":\"2021-01-01\"}"),
                JSON.replace("}", ",\"eventDate\":\"2020-02-30\"}"),
                JSON.replace("}", ",\"id\":99}"),
                JSON.replace("}", ",\"id\":null}"),
                JSON.replace("}", ",\"verificationStatus\":\"VERIFIED\"}"),
                JSON.replace("}", ",\"verificationStatus\":\"DRAFT\"}"),
                JSON.replace("}", ",\"verificationStatus\":null}"),
                JSON.replace("}", ",\"createdAt\":\"2020-01-01T00:00:00Z\"}"),
                JSON.replace("}", ",\"updatedAt\":\"2020-01-01T00:00:00Z\"}"),
                JSON.replace("}", ",\"unexpected\":true}"))) {
            mvc.perform(request(method(operation), path(operation))
                            .contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.timestamp").isNotEmpty())
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message").isNotEmpty())
                    .andExpect(jsonPath("$.path").value(path(operation)));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET_ONE", "PUT", "DELETE"})
    @WithMockUser(roles = "ADMIN")
    void malformedPathIdReturnsBadRequest(String operation) throws Exception {
        mvc.perform(request(method(operation), BASE + "/invalid")
                        .contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    private String path(String operation) {
        return operation.equals("GET") || operation.equals("POST") ? BASE : BASE + "/1";
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "EDITOR"})
    void privilegedCrudWorksWithoutCsrf(String role) throws Exception {
        var user = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("member").roles(role);
        var record = com.qaryati.heritage.entity.HistoricalEvent.draft(HistoricalEventServiceTest.request("Title", java.util.List.of(2L)));
        org.springframework.test.util.ReflectionTestUtils.setField(record, "id", 1L);
        org.mockito.Mockito.when(villages.findById(1L)).thenReturn(java.util.Optional.of(
                new com.qaryati.village.api.VillageLookupResult(1L, com.qaryati.village.api.VillageLookupStatus.VERIFIED, false)));
        org.mockito.Mockito.when(sources.existsById(2L)).thenReturn(true);
        org.mockito.Mockito.when(repository.saveAndFlush(org.mockito.ArgumentMatchers.any())).thenAnswer(call -> {
            var saved = (com.qaryati.heritage.entity.HistoricalEvent) call.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(saved, "id", 1L); return saved;
        });
        org.mockito.Mockito.when(repository.findById(1L)).thenReturn(java.util.Optional.of(record));
        org.mockito.Mockito.when(repository.findByIdForUpdate(1L)).thenReturn(java.util.Optional.of(record));
        org.mockito.Mockito.when(repository.findVisiblePage(org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(record)));
        mvc.perform(post(BASE).with(user).contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isCreated()).andExpect(header().string("Location", BASE + "/1"))
                .andExpect(jsonPath("$.verificationStatus").value("DRAFT"));
        mvc.perform(get(BASE).with(user)).andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(BASE + "/1").with(user)).andExpect(status().isOk());
        mvc.perform(put(BASE + "/1").with(user).contentType(MediaType.APPLICATION_JSON).content(JSON)).andExpect(status().isOk());
        mvc.perform(delete(BASE + "/1").with(user)).andExpect(status().isNoContent());
    }

    @org.junit.jupiter.api.Test
    void invalidBearerIsUnauthorized() throws Exception {
        org.mockito.Mockito.when(decoder.decode("invalid")).thenThrow(new org.springframework.security.oauth2.jwt.BadJwtException("private detail"));
        mvc.perform(get(BASE).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
    }

    @org.junit.jupiter.api.Test
    void bearerUsesDatabaseRolesAndRejectsDisabledUsers() throws Exception {
        var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("valid")
                .header("alg", "HS256").subject("member").build();
        org.mockito.Mockito.when(decoder.decode("valid")).thenReturn(jwt);
        org.mockito.Mockito.when(userDetails.loadUserByUsername("member")).thenReturn(
                org.springframework.security.core.userdetails.User.withUsername("member")
                        .password("unused").roles("VIEWER").build());
        mvc.perform(get(BASE + "/1").header("Authorization", "Bearer valid")).andExpect(status().isNotFound());
        mvc.perform(delete(BASE + "/1").header("Authorization", "Bearer valid")).andExpect(status().isForbidden());
        org.mockito.Mockito.when(userDetails.loadUserByUsername("member")).thenReturn(
                org.springframework.security.core.userdetails.User.withUsername("member")
                        .password("unused").roles("ADMIN").disabled(true).build());
        mvc.perform(get(BASE).header("Authorization", "Bearer valid")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"?page=-1", "?size=0", "?size=101", "?page=abc", "?size=2147483648"})
    @WithMockUser(roles = "VIEWER")
    void invalidPaginationIsBadRequest(String query) throws Exception {
        mvc.perform(get(BASE + query)).andExpect(status().isBadRequest());
    }

    @org.junit.jupiter.api.Test
    @WithMockUser(roles = "VIEWER")
    void viewerPageUsesFilteredQuery() throws Exception {
        org.mockito.Mockito.when(repository.findVisiblePage(org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(com.qaryati.heritage.entity.VerificationStatus.VERIFIED),
                org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(org.springframework.data.domain.Page.empty());
        mvc.perform(get(BASE)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @org.junit.jupiter.api.Test
    @WithMockUser(roles = "ADMIN")
    void infrastructureFailureDoesNotLeakDetails() throws Exception {
        org.mockito.Mockito.when(repository.findById(1L)).thenThrow(new org.springframework.dao.DataIntegrityViolationException("secret SQL"));
        mvc.perform(get(BASE + "/1")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Unexpected error"));
    }

    @org.junit.jupiter.api.Test
    @WithMockUser(roles = "ADMIN")
    void nonDraftDeletionIsConflict() throws Exception {
        var record = com.qaryati.heritage.entity.HistoricalEvent.draft(HistoricalEventServiceTest.request("Title", java.util.List.of(2L)));
        org.springframework.test.util.ReflectionTestUtils.setField(record, "verificationStatus",
                com.qaryati.heritage.entity.VerificationStatus.VERIFIED);
        org.mockito.Mockito.when(repository.findByIdForUpdate(1L)).thenReturn(java.util.Optional.of(record));
        mvc.perform(delete(BASE + "/1")).andExpect(status().isConflict());
    }

    @org.junit.jupiter.api.Test
    @WithMockUser(roles = "EDITOR")
    void providerFailuresHaveSafeStatusAndMessages() throws Exception {
        org.mockito.Mockito.when(villages.findById(1L)).thenReturn(java.util.Optional.of(
                new com.qaryati.village.api.VillageLookupResult(1L, com.qaryati.village.api.VillageLookupStatus.VERIFIED, false)));
        org.mockito.Mockito.doThrow(new java.util.NoSuchElementException("private source details"))
                .when(sources).requireExists(2L);
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Historical event or source not found"));
        org.mockito.Mockito.reset(sources);
        org.mockito.Mockito.when(repository.saveAndFlush(org.mockito.ArgumentMatchers.any())).thenAnswer(call -> {
            var saved = (com.qaryati.heritage.entity.HistoricalEvent) call.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(saved, "id", 1L); return saved;
        });
        org.mockito.Mockito.doThrow(new IllegalStateException("private audit details"))
                .when(audits).recordHistoricalEvent(org.mockito.ArgumentMatchers.eq(1L),
                        org.mockito.ArgumentMatchers.eq(com.qaryati.dataquality.api.AuditAction.CREATE),
                        org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any());
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Unexpected error"));
    }
    private HttpMethod method(String operation) {
        return HttpMethod.valueOf(operation.equals("GET_ONE") ? "GET" : operation);
    }
}


