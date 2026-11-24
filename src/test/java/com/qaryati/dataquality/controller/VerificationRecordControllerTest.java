package com.qaryati.dataquality.controller;

import com.qaryati.dataquality.entity.VerificationRecord;
import com.qaryati.dataquality.enums.VerificationDecision;
import com.qaryati.dataquality.exception.DataQualityExceptionHandler;
import com.qaryati.dataquality.repository.VerificationRecordRepository;
import com.qaryati.dataquality.repository.VillageExistsRepository;
import com.qaryati.dataquality.service.AuditLogService;
import com.qaryati.dataquality.service.VerificationRecordService;
import com.qaryati.security.api.UserLookup;
import com.qaryati.security.config.SecurityConfig;
import com.qaryati.security.ratelimit.LoginRateLimitService;
import java.time.OffsetDateTime;
import java.util.List;
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
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real retrieval service, security and advice; all persistence dependencies are mocked. */
@WebMvcTest(VerificationRecordController.class)
@Import({VerificationRecordService.class, DataQualityExceptionHandler.class, SecurityConfig.class})
class VerificationRecordControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean VerificationRecordRepository repository;
    @MockitoBean VillageExistsRepository villages;
    @MockitoBean UserLookup users;
    @MockitoBean AuditLogService audits;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean JwtAuthenticationConverter converter;
    @MockitoBean LoginRateLimitService rateLimits;

    @ParameterizedTest @ValueSource(strings = {"VIEWER", "EDITOR", "ADMIN"})
    void authenticatedRolesRetrieveUnchangedResponse(String role) throws Exception {
        var record = new VerificationRecord("VILLAGE", 7L, 42L, VerificationDecision.VERIFIED, "Reviewed reason");
        ReflectionTestUtils.setField(record, "id", 9L);
        ReflectionTestUtils.setField(record, "reviewedAt", OffsetDateTime.parse("2026-11-08T10:00:00Z"));
        when(repository.findByEntityTypeAndEntityIdOrderByReviewedAtDesc("VILLAGE", 7L))
                .thenReturn(List.of(record));

        mvc.perform(get("/api/v1/verifications").with(user("reader").roles(role))
                        .param("entityType", "VILLAGE").param("entityId", "7"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [{"id":9,"entityType":"VILLAGE","entityId":7,"reviewerUserId":42,
                          "decision":"VERIFIED","reason":"Reviewed reason","reviewedAt":"2026-11-08T10:00:00Z"}]
                        """, JsonCompareMode.STRICT));
        verifyNoInteractions(villages, users, audits);
    }

    @Test @WithMockUser(roles = "VIEWER")
    void trimsSupportedTypeAndReturnsEmptyArrayWhenNoRecordsExist() throws Exception {
        when(repository.findByEntityTypeAndEntityIdOrderByReviewedAtDesc("VILLAGE", 7L)).thenReturn(List.of());
        mvc.perform(get("/api/v1/verifications").param("entityType", " VILLAGE ").param("entityId", "7"))
                .andExpect(status().isOk()).andExpect(content().json("[]", JsonCompareMode.STRICT));
        verify(repository).findByEntityTypeAndEntityIdOrderByReviewedAtDesc("VILLAGE", 7L);
        verifyNoInteractions(villages, users, audits);
    }

    @ParameterizedTest @ValueSource(strings = {"", " ", "village", "SOURCE", "VILLAGES"})
    @WithMockUser(roles = "VIEWER")
    void invalidEntityTypesReturn400WithoutRepositoryAccess(String entityType) throws Exception {
        mvc.perform(get("/api/v1/verifications").param("entityType", entityType).param("entityId", "7"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").isString());
        verifyNoInteractions(repository, villages, users, audits);
    }

    @Test @WithMockUser(roles = "VIEWER")
    void overlongEntityTypeReturns400() throws Exception {
        mvc.perform(get("/api/v1/verifications").param("entityType", "VILLAGE" + " ".repeat(74)).param("entityId", "7"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("Invalid entity type"));
        verifyNoInteractions(repository, villages, users, audits);
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "-9223372036854775808"})
    @WithMockUser(roles = "VIEWER")
    void nonpositiveIdsReturn400WithoutRepositoryAccess(String entityId) throws Exception {
        mvc.perform(get("/api/v1/verifications").param("entityType", "VILLAGE").param("entityId", entityId))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("Entity ID must be positive"));
        verifyNoInteractions(repository, villages, users, audits);
    }

    @ParameterizedTest @ValueSource(strings = {"not-a-number", "9223372036854775808"})
    @WithMockUser(roles = "VIEWER")
    void malformedIdsReturn400(String entityId) throws Exception {
        mvc.perform(get("/api/v1/verifications").param("entityType", "VILLAGE").param("entityId", entityId))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("Invalid parameter: entityId"));
        verifyNoInteractions(repository, villages, users, audits);
    }

    @ParameterizedTest @ValueSource(strings = {"entityType", "entityId"})
    @WithMockUser(roles = "VIEWER")
    void missingRequiredParametersReturn400(String missingParameter) throws Exception {
        var request = get("/api/v1/verifications");
        if (!missingParameter.equals("entityType")) request.param("entityType", "VILLAGE");
        if (!missingParameter.equals("entityId")) request.param("entityId", "7");
        mvc.perform(request).andExpect(status().isBadRequest());
        verifyNoInteractions(repository, villages, users, audits);
    }

    @Test void anonymousRequestIsRejectedBeforeInputValidation() throws Exception {
        mvc.perform(get("/api/v1/verifications").param("entityType", "SOURCE").param("entityId", "0"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(repository, villages, users, audits);
    }
}
