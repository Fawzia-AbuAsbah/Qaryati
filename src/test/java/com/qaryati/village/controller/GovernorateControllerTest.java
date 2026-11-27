package com.qaryati.village.controller;

import com.qaryati.common.exception.DuplicateResourceException;
import com.qaryati.common.exception.GlobalExceptionHandler;
import com.qaryati.common.exception.ResourceNotFoundException;
import com.qaryati.security.config.JwtAuthenticationConverterConfig;
import com.qaryati.security.config.SecurityConfig;
import com.qaryati.security.ratelimit.LoginRateLimitService;
import com.qaryati.security.service.DatabaseUserDetailsService;
import com.qaryati.village.dto.GovernorateRequest;
import com.qaryati.village.dto.GovernorateResponse;
import com.qaryati.village.service.GovernorateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.sql.DataSource;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Production filter chain, method security and JWT conversion; persistence and decoding are mocked. */
@WebMvcTest(GovernorateController.class)
@Import({SecurityConfig.class, JwtAuthenticationConverterConfig.class, GlobalExceptionHandler.class})
class GovernorateControllerTest {

    private static final String BASE_PATH = "/api/v1/governorates";
    private static final String REQUEST_JSON = """
            {"nameAr":"نابلس","nameEn":"Nablus","code":"NABLUS"}
            """;
    private static final GovernorateRequest REQUEST =
            new GovernorateRequest("نابلس", "Nablus", "NABLUS");
    private static final GovernorateResponse RESPONSE =
            new GovernorateResponse(1L, "نابلس", "Nablus", "NABLUS");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GovernorateService service;

    @Autowired private ApplicationContext context;
    @MockitoBean private JwtDecoder decoder;
    @MockitoBean private DatabaseUserDetailsService users;
    @MockitoBean private LoginRateLimitService rateLimits;

    @Test
    void createReturnsCreatedAndGovernorateResponse() throws Exception {
        authenticate("EDITOR");
        when(service.create(REQUEST)).thenReturn(RESPONSE);

        mockMvc.perform(post(BASE_PATH).header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST_JSON))
                .andExpect(status().isCreated())
                .andExpect(content().json("""
                        {"id":1,"nameAr":"نابلس","nameEn":"Nablus","code":"NABLUS"}
                        """));

        verify(service).create(REQUEST);
    }

    @Test
    void getAllReturnsMappedGovernorateResponses() throws Exception {
        authenticate("VIEWER");
        when(service.getAll()).thenReturn(List.of(RESPONSE));

        mockMvc.perform(get(BASE_PATH).header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [{"id":1,"nameAr":"نابلس","nameEn":"Nablus","code":"NABLUS"}]
                        """));

        verify(service).getAll();
    }

    @Test
    void getAllReturnsEmptyArrayWhenNoGovernoratesExist() throws Exception {
        authenticate("VIEWER");
        when(service.getAll()).thenReturn(List.of());

        mockMvc.perform(get(BASE_PATH).header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(service).getAll();
    }

    @Test
    void getByIdReturnsGovernorateResponse() throws Exception {
        authenticate("VIEWER");
        when(service.getById(1L)).thenReturn(RESPONSE);

        mockMvc.perform(get(BASE_PATH + "/1").header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"id":1,"nameAr":"نابلس","nameEn":"Nablus","code":"NABLUS"}
                        """));

        verify(service).getById(1L);
    }

    @Test
    void updateReturnsUpdatedGovernorateResponse() throws Exception {
        authenticate("EDITOR");
        GovernorateRequest request = new GovernorateRequest("جنين", "Jenin", "JENIN");
        when(service.update(1L, request))
                .thenReturn(new GovernorateResponse(1L, "جنين", "Jenin", "JENIN"));

        mockMvc.perform(put(BASE_PATH + "/1").header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nameAr":"جنين","nameEn":"Jenin","code":"JENIN"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"id":1,"nameAr":"جنين","nameEn":"Jenin","code":"JENIN"}
                        """));

        verify(service).update(1L, request);
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        authenticate("ADMIN");
        mockMvc.perform(delete(BASE_PATH + "/1").header("Authorization", "Bearer token"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(service).delete(1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT"})
    void writeRejectsBlankArabicNameWithCommonValidationError(String method) throws Exception {
        authenticate("EDITOR");
        String path = method.equals("POST") ? BASE_PATH : BASE_PATH + "/1";

        expectError(mockMvc.perform(request(HttpMethod.valueOf(method), path).header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nameAr":" ","nameEn":"Nablus","code":"NABLUS"}
                                """)),
                400, "Bad Request", "nameAr: Arabic name is required", path);

        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"nameAr", "nameEn", "code"})
    void createRejectsFieldsExceedingMaximumLength(String field) throws Exception {
        authenticate("EDITOR");
        int maxLength = field.equals("code") ? 32 : 120;
        String oversized = "x".repeat(maxLength + 1);
        String json = """
                {"nameAr":"%s","nameEn":"%s","code":"%s"}
                """.formatted(
                field.equals("nameAr") ? oversized : "نابلس",
                field.equals("nameEn") ? oversized : "Nablus",
                field.equals("code") ? oversized : "NABLUS");

        mockMvc.perform(post(BASE_PATH).header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.startsWith(field + ": ")))
                .andExpect(jsonPath("$.path").value(BASE_PATH));

        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "PUT", "DELETE"})
    void nonexistentGovernorateReturnsCommonNotFoundError(String method) throws Exception {
        authenticate("ADMIN");
        switch (method) {
            case "GET" -> when(service.getById(99L))
                    .thenThrow(new ResourceNotFoundException("Governorate", 99L));
            case "PUT" -> when(service.update(99L, REQUEST))
                    .thenThrow(new ResourceNotFoundException("Governorate", 99L));
            case "DELETE" -> doThrow(new ResourceNotFoundException("Governorate", 99L))
                    .when(service).delete(99L);
        }
        String path = BASE_PATH + "/99";

        expectError(mockMvc.perform(request(HttpMethod.valueOf(method), path).header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST_JSON)),
                404, "Not Found", "Governorate with id 99 not found", path);

        switch (method) {
            case "GET" -> verify(service).getById(99L);
            case "PUT" -> verify(service).update(99L, REQUEST);
            case "DELETE" -> verify(service).delete(99L);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE"})
    void duplicateOrReferencedGovernorateReturnsCommonConflictError(String method) throws Exception {
        authenticate("ADMIN");
        String message = method.equals("DELETE")
                ? "Cannot delete a governorate referenced by other records"
                : "Governorate code already exists";
        switch (method) {
            case "POST" -> when(service.create(REQUEST))
                    .thenThrow(new DuplicateResourceException(message));
            case "PUT" -> when(service.update(1L, REQUEST))
                    .thenThrow(new DuplicateResourceException(message));
            case "DELETE" -> doThrow(new DuplicateResourceException(message))
                    .when(service).delete(1L);
        }
        String path = method.equals("POST") ? BASE_PATH : BASE_PATH + "/1";

        expectError(mockMvc.perform(request(HttpMethod.valueOf(method), path).header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST_JSON)),
                409, "Conflict", message, path);

        switch (method) {
            case "POST" -> verify(service).create(REQUEST);
            case "PUT" -> verify(service).update(1L, REQUEST);
            case "DELETE" -> verify(service).delete(1L);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ALL", "ID", "POST", "PUT", "DELETE"})
    void unauthenticatedRequestsAreRejected(String operation) throws Exception {
        mockMvc.perform(endpoint(operation).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service, decoder, users, rateLimits);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE"})
    void authorizedBearerWritesDoNotRequireCsrf(String operation) throws Exception {
        authenticate("ADMIN");
        stubSuccess(operation);

        mockMvc.perform(endpoint(operation).header("Authorization", "Bearer token"))
                .andExpect(status().is(operation.equals("POST") ? 201 : operation.equals("DELETE") ? 204 : 200));

        verifyDelegation(operation);
        verifyNoMoreInteractions(service);
    }

    static Stream<Arguments> authorizationCases() {
        return Stream.of("VIEWER", "EDITOR", "ADMIN", "NONE", "UNRELATED")
                .flatMap(role -> Stream.of("ALL", "ID", "POST", "PUT", "DELETE")
                        .map(operation -> Arguments.of(role, operation)));
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("authorizationCases")
    void productionRolePolicyAndExactDelegation(String role, String operation) throws Exception {
        authenticate(role);
        boolean read = operation.equals("ALL") || operation.equals("ID");
        boolean allowed = role.equals("ADMIN")
                || role.equals("EDITOR") && !operation.equals("DELETE")
                || role.equals("VIEWER") && read;
        if (allowed) stubSuccess(operation);

        ResultActions result = mockMvc.perform(endpoint(operation).header("Authorization", "Bearer token"));
        if (!allowed) {
            result.andExpect(status().isForbidden());
            verifyNoInteractions(service);
        } else {
            result.andExpect(status().is(operation.equals("POST") ? 201 : operation.equals("DELETE") ? 204 : 200));
            if (operation.equals("DELETE")) {
                result.andExpect(content().string(""));
            } else {
                String prefix = operation.equals("ALL") ? "$[0]" : "$";
                result.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath(prefix + ".id").value(1))
                        .andExpect(jsonPath(prefix + ".nameAr").value(RESPONSE.nameAr()))
                        .andExpect(jsonPath(prefix + ".nameEn").value(RESPONSE.nameEn()))
                        .andExpect(jsonPath(prefix + ".code").value(RESPONSE.code()));
                if (operation.equals("ALL")) result.andExpect(jsonPath("$.length()").value(1));
            }
            verifyDelegation(operation);
            verifyNoMoreInteractions(service);
        }
        verify(decoder).decode("token");
        verify(users).loadUserByUsername("tester");
        verifyNoInteractions(rateLimits);
    }

    @Test
    void invalidBearerTokenIsRejectedBeforeService() throws Exception {
        when(decoder.decode("bad")).thenThrow(new BadJwtException("Invalid token"));

        mockMvc.perform(endpoint("ID").header("Authorization", "Bearer bad"))
                .andExpect(status().isUnauthorized());

        verify(decoder).decode("bad");
        verifyNoInteractions(service, users, rateLimits);
    }

    @Test
    void sliceContainsNoDatabaseOrMigrationInfrastructure() {
        assertThat(context.getBeansOfType(DataSource.class)).isEmpty();
        assertThat(context.getBeansOfType(jakarta.persistence.EntityManagerFactory.class)).isEmpty();
        assertThat(context.getBeansOfType(org.springframework.data.repository.Repository.class)).isEmpty();
        assertThat(context.getBeansOfType(org.flywaydb.core.Flyway.class)).isEmpty();
        for (String name : context.getBeanDefinitionNames()) {
            Class<?> type = context.getType(name, false);
            assertThat(type == null || !type.getName().startsWith("liquibase.")).isTrue();
        }
    }

    private void authenticate(String role) {
        when(decoder.decode("token")).thenReturn(Jwt.withTokenValue("token")
                .header("alg", "HS256").subject("tester").build());
        String[] roles = role.equals("NONE") ? new String[0] : new String[]{role};
        when(users.loadUserByUsername("tester")).thenReturn(User.withUsername("tester")
                .password("unused").roles(roles).build());
    }

    private MockHttpServletRequestBuilder endpoint(String operation) {
        String method = List.of("POST", "PUT", "DELETE").contains(operation) ? operation : "GET";
        String path = List.of("ID", "PUT", "DELETE").contains(operation) ? BASE_PATH + "/1" : BASE_PATH;
        var builder = request(HttpMethod.valueOf(method), path);
        if (method.equals("POST") || method.equals("PUT")) {
            builder.contentType(MediaType.APPLICATION_JSON).content(REQUEST_JSON);
        }
        return builder;
    }

    private void stubSuccess(String operation) {
        switch (operation) {
            case "ALL" -> when(service.getAll()).thenReturn(List.of(RESPONSE));
            case "ID" -> when(service.getById(1L)).thenReturn(RESPONSE);
            case "POST" -> when(service.create(REQUEST)).thenReturn(RESPONSE);
            case "PUT" -> when(service.update(1L, REQUEST)).thenReturn(RESPONSE);
            case "DELETE" -> { }
            default -> throw new IllegalArgumentException(operation);
        }
    }

    private void verifyDelegation(String operation) {
        switch (operation) {
            case "ALL" -> verify(service).getAll();
            case "ID" -> verify(service).getById(1L);
            case "POST" -> verify(service).create(REQUEST);
            case "PUT" -> verify(service).update(1L, REQUEST);
            case "DELETE" -> verify(service).delete(1L);
            default -> throw new IllegalArgumentException(operation);
        }
    }

    private void expectError(ResultActions result, int statusCode, String error,
                             String message, String path) throws Exception {
        result.andExpect(status().is(statusCode))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(statusCode))
                .andExpect(jsonPath("$.error").value(error))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.path").value(path));
    }
}
