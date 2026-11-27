package com.qaryati.dataquality.controller;

import com.qaryati.dataquality.dto.CreateSourceRequest;
import com.qaryati.dataquality.entity.Source;
import com.qaryati.dataquality.enums.SourceType;
import com.qaryati.dataquality.exception.DataQualityExceptionHandler;
import com.qaryati.dataquality.repository.SourceRepository;
import com.qaryati.dataquality.service.AuditLogService;
import com.qaryati.dataquality.service.SourceService;
import com.qaryati.security.config.SecurityConfig;
import com.qaryati.security.ratelimit.LoginRateLimitService;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real PUT controller/service/security/validation; no datasource, server or real audit storage. */
@WebMvcTest(SourceController.class)
@Import({SourceService.class, DataQualityExceptionHandler.class, SecurityConfig.class})
class SourcePutControllerTest {
    private static final String PATH = "/api/v1/sources/{id}";
    private static final String MINIMAL = """
            {"title":"Replacement","sourceType":"BOOK"}
            """;
    private static final String COMPLETE = """
            {"title":"Replacement","publisher":"New publisher","author":"New author",
             "sourceType":"WEBSITE","url":"https://example.com/new?reference=synthetic",
             "publicationDate":"2022-03-04","accessedAt":"2026-11-26","citation":"New synthetic citation"}
            """;
    private static final OffsetDateTime CREATED = OffsetDateTime.parse("2020-01-01T10:00:00Z");

    @Autowired MockMvc mvc;
    @Autowired SourceService service;
    @Autowired ApplicationContext context;
    @MockitoBean SourceRepository repository;
    @MockitoBean AuditLogService audits;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean JwtAuthenticationConverter converter;
    @MockitoBean LoginRateLimitService rateLimits;
    private Source original;

    @BeforeEach
    void existingSource() {
        original = new Source("Original", "BOOK");
        ReflectionTestUtils.setField(original, "id", 7L);
        ReflectionTestUtils.setField(original, "createdAt", CREATED);
        original.setPublisher("Old publisher");
        original.setAuthor("Old author");
        original.setUrl("https://example.com/old");
        original.setPublicationDate(LocalDate.of(2010, 1, 2));
        original.setAccessedAt(LocalDate.of(2020, 1, 2));
        original.setCitation("Old synthetic citation");
    }

    private void updateExists() {
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(original));
        when(repository.saveAndFlush(original)).thenReturn(original);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EDITOR", "ADMIN"})
    void eachExplicitWriterRoleReplacesAllFieldsAndReturnsExactResponse(String role) throws Exception {
        updateExists();
        // Each account has this role alone, including ADMIN without VIEWER/EDITOR.
        mvc.perform(put(PATH, 7).with(user("synthetic-writer").roles(role))
                        .contentType("application/json").content(COMPLETE))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"id":7,"title":"Replacement","publisher":"New publisher","author":"New author",
                         "sourceType":"WEBSITE","url":"https://example.com/new?reference=synthetic",
                         "publicationDate":"2022-03-04","accessedAt":"2026-11-26",
                         "citation":"New synthetic citation","createdAt":"2020-01-01T10:00:00Z"}
                        """, JsonCompareMode.STRICT));
        assertEquals(7L, original.getId());
        assertEquals(CREATED, original.getCreatedAt());
        verify(repository).findByIdForUpdate(7L);
        verify(repository).saveAndFlush(original);
        verifyNoMoreInteractions(repository);
        verify(audits).record(eq("SOURCE"), eq(7L), eq("UPDATE"), anyMap(), anyMap(), eq("Source updated"));
        verifyNoMoreInteractions(audits);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @WithMockUser(roles = "EDITOR")
    void omittedAndExplicitNullOptionalFieldsBothClearPreviousValues(boolean explicitNulls) throws Exception {
        updateExists();
        String body = explicitNulls ? """
                {"title":"Replacement","sourceType":"BOOK","publisher":null,"author":null,
                 "url":null,"publicationDate":null,"accessedAt":null,"citation":null}
                """ : MINIMAL;
        mvc.perform(put(PATH, 7).contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"id":7,"title":"Replacement","sourceType":"BOOK","publisher":null,"author":null,
                         "url":null,"publicationDate":null,"accessedAt":null,"citation":null,
                         "createdAt":"2020-01-01T10:00:00Z"}
                        """, JsonCompareMode.STRICT));
        verify(audits).record(eq("SOURCE"), eq(7L), eq("UPDATE"), anyMap(),
                argThat(after -> Boolean.FALSE.equals(after.get("hasUrl"))
                        && Boolean.FALSE.equals(after.get("hasCitation"))
                        && Boolean.TRUE.equals(after.get("urlChanged"))
                        && Boolean.TRUE.equals(after.get("citationChanged"))),
                eq("Source updated"));
    }

    @Test
    @WithMockUser(roles = "EDITOR")
    void exactFieldLengthLimitsAreAccepted() throws Exception {
        updateExists();
        String body = """
                {"title":"%s","sourceType":"BOOK","publisher":"%s","author":"%s"}
                """.formatted("t".repeat(350), "p".repeat(200), "a".repeat(200));
        mvc.perform(put(PATH, 7).contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("t".repeat(350)))
                .andExpect(jsonPath("$.publisher").value("p".repeat(200)))
                .andExpect(jsonPath("$.author").value("a".repeat(200)));
    }

    @ParameterizedTest
    @EnumSource(SourceType.class)
    @WithMockUser(roles = "ADMIN")
    void everySupportedSourceTypeIsAccepted(SourceType type) throws Exception {
        updateExists();
        mvc.perform(put(PATH, 7).contentType("application/json")
                        .content("{\"title\":\"Replacement\",\"sourceType\":\"" + type.name() + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sourceType").value(type.name()));
    }

    static Stream<String> invalidBodies() {
        return Stream.of(
                "{}", "{\"sourceType\":\"BOOK\"}",
                "{\"title\":null,\"sourceType\":\"BOOK\"}",
                "{\"title\":\"\",\"sourceType\":\"BOOK\"}",
                "{\"title\":\"  \",\"sourceType\":\"BOOK\"}",
                "{\"title\":\"Replacement\"}",
                "{\"title\":\"Replacement\",\"sourceType\":null}",
                "{\"title\":\"Replacement\",\"sourceType\":\"UNKNOWN\"}",
                "{\"title\":\"Replacement\",\"sourceType\":\"book\"}",
                "{\"title\":\"" + "t".repeat(351) + "\",\"sourceType\":\"BOOK\"}",
                "{\"title\":\"Replacement\",\"sourceType\":\"BOOK\",\"publisher\":\"" + "p".repeat(201) + "\"}",
                "{\"title\":\"Replacement\",\"sourceType\":\"BOOK\",\"author\":\"" + "a".repeat(201) + "\"}",
                "{\"title\":\"Replacement\",\"sourceType\":\"BOOK\",\"url\":\"ftp://example.com\"}",
                "{\"title\":\"Replacement\",\"sourceType\":\"BOOK\",\"url\":\"/relative\"}",
                "{\"title\":\"Replacement\",\"sourceType\":\"BOOK\",\"url\":\"https://\"}",
                "{\"title\":\"Replacement\",\"sourceType\":\"BOOK\",\"url\":\"https://example.com/white space\"}",
                "{\"title\":\"Replacement\",\"sourceType\":\"BOOK\",\"url\":\"javascript:alert(1)\"}",
                "{\"title\":\"Replacement\",\"sourceType\":\"BOOK\",\"url\":\"\"}",
                "{\"title\":\"Replacement\",\"sourceType\":\"BOOK\",\"publicationDate\":\"not-a-date\"}",
                "{\"title\":\"Replacement\",\"sourceType\":\"BOOK\",\"accessedAt\":\"not-a-date\"}",
                "{", "null", "[]", "");
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    @WithMockUser(roles = "EDITOR")
    void invalidReplacementIsRejectedBeforeStorageOrAuditing(String body) throws Exception {
        mvc.perform(put(PATH, 7).contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$", aMapWithSize(1)))
                .andExpect(jsonPath("$.error").isString());
        assertEquals("Original", original.getTitle());
        verifyNoInteractions(repository, audits);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "-9223372036854775808", "not-a-number", "9223372036854775808", "1.5"})
    @WithMockUser(roles = "EDITOR")
    void invalidOrNonpositiveIdsReturn400WithoutStorageOrAudit(String id) throws Exception {
        mvc.perform(put(PATH, id).contentType("application/json").content(MINIMAL))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").isString());
        verifyNoInteractions(repository, audits);
    }

    @ParameterizedTest
    @ValueSource(longs = {7L, Long.MAX_VALUE})
    @WithMockUser(roles = "EDITOR")
    void unknownPositiveIdsReturn404WithoutSavingOrAuditing(long id) throws Exception {
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.empty());
        mvc.perform(put(PATH, id).contentType("application/json").content(MINIMAL))
                .andExpect(status().isNotFound())
                .andExpect(content().json("{\"error\":\"Source not found\"}", JsonCompareMode.STRICT));
        verify(repository).findByIdForUpdate(id);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(audits);
    }

    @ParameterizedTest
    @ValueSource(strings = {"VIEWER", "", "UNRECOGNIZED"})
    void accountsWithoutAnExplicitWriteRoleAreForbidden(String role) throws Exception {
        String[] roles = role.isEmpty() ? new String[0] : new String[]{role};
        mvc.perform(put(PATH, 7).with(user("synthetic-reader").roles(roles))
                        .contentType("application/json").content(MINIMAL))
                .andExpect(status().isForbidden());
        verifyNoInteractions(repository, audits);
    }

    @Test
    void anonymousCannotUpdate() throws Exception {
        mvc.perform(put(PATH, 7).contentType("application/json").content(MINIMAL))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(repository, audits);
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void serviceProxyAlsoDeniesViewerWithoutAControllerCall() {
        var request = new CreateSourceRequest("Replacement", null, null, SourceType.BOOK, null, null, null, null);
        assertThrows(AccessDeniedException.class, () -> service.updateSource(7L, request));
        verifyNoInteractions(repository, audits);
    }

    @Test
    void sliceUsesMockedStorageWithoutDatabaseOrMigrationInfrastructure() {
        assertTrue(mockingDetails(repository).isMock());
        assertTrue(mockingDetails(audits).isMock());
        for (Class<?> type : new Class<?>[]{DataSource.class, EntityManagerFactory.class,
                JdbcTemplate.class, org.flywaydb.core.Flyway.class,
                org.springframework.transaction.PlatformTransactionManager.class}) {
            assertEquals(0, context.getBeanNamesForType(type, false, false).length, type.getName());
        }
        assertFalse(context.containsBean("flywayInitializer"));
        verifyNoInteractions(repository, audits);
    }
}
