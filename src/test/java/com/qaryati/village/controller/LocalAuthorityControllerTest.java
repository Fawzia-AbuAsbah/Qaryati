package com.qaryati.village.controller;

import com.qaryati.common.exception.DuplicateResourceException;
import com.qaryati.common.exception.GlobalExceptionHandler;
import com.qaryati.common.exception.ResourceNotFoundException;
import com.qaryati.security.config.JwtAuthenticationConverterConfig;
import com.qaryati.security.config.SecurityConfig;
import com.qaryati.security.ratelimit.LoginRateLimitService;
import com.qaryati.security.service.DatabaseUserDetailsService;
import com.qaryati.village.dto.LocalAuthorityRequest;
import com.qaryati.village.dto.LocalAuthorityResponse;
import com.qaryati.village.entity.LocalAuthorityType;
import com.qaryati.village.service.LocalAuthorityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.sql.DataSource;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Imports the real filter chain, method security and JWT authority converter.
 * Only external authentication collaborators and the feature service are mocked.
 * WebMvcTest does not enable datasource/JPA/Flyway auto-configuration.
 * No mock authentication postprocessor or CSRF token bypasses the bearer filter.
 */
@WebMvcTest(LocalAuthorityController.class)
@Import({SecurityConfig.class, JwtAuthenticationConverterConfig.class, GlobalExceptionHandler.class})
class LocalAuthorityControllerTest {

    private static final String BASE = "/api/v1/local-authorities";
    private static final String JSON = """
            {"governorateId":2,"nameAr":"Council","nameEn":"Council","authorityType":"MUNICIPALITY"}
            """;
    private static final String RESPONSE_JSON = """
            {"id":1,"governorateId":2,"nameAr":"Council","nameEn":"Council","authorityType":"MUNICIPALITY"}
            """;
    private static final LocalAuthorityRequest REQUEST =
            new LocalAuthorityRequest(2L, "Council", "Council", LocalAuthorityType.MUNICIPALITY);
    private static final LocalAuthorityResponse RESPONSE =
            new LocalAuthorityResponse(1L, 2L, "Council", "Council", LocalAuthorityType.MUNICIPALITY);

    @Autowired private MockMvc mvc;
    @Autowired private ApplicationContext context;
    @MockitoBean private LocalAuthorityService service;
    @MockitoBean private JwtDecoder decoder;
    @MockitoBean private DatabaseUserDetailsService users;
    @MockitoBean private LoginRateLimitService rateLimits;

    // ALL, FILTERED and ID exercise both branches of the collection handler and the ID handler.
    static Stream<org.junit.jupiter.params.provider.Arguments> authorizationCases() {
        return Stream.of("VIEWER", "EDITOR", "ADMIN", "NONE", "UNRELATED")
                .flatMap(role -> Stream.of("POST", "PUT", "DELETE", "ALL", "FILTERED", "ID")
                        .map(operation -> org.junit.jupiter.params.provider.Arguments.of(role, operation)));
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("authorizationCases")
    void productionRolePolicyAndExactDelegationWithoutCsrf(String role, String operation) throws Exception {
        authenticate(role);
        boolean read = List.of("ALL", "FILTERED", "ID").contains(operation);
        boolean allowed = role.equals("ADMIN")
                || role.equals("EDITOR") && !operation.equals("DELETE")
                || role.equals("VIEWER") && read;

        if (allowed) stubSuccess(operation);
        ResultActions result = mvc.perform(endpoint(operation)
                .header("Authorization", "Bearer token"));

        if (!allowed) {
            result.andExpect(status().isForbidden());
            verifyNoInteractions(service);
        } else {
            int expected = operation.equals("POST") ? 201 : operation.equals("DELETE") ? 204 : 200;
            result.andExpect(status().is(expected));
            if (operation.equals("DELETE")) {
                result.andExpect(content().string(""));
            } else {
                result.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                        .andExpect(content().json(
                                operation.equals("ALL") || operation.equals("FILTERED")
                                        ? "[" + RESPONSE_JSON + "]" : RESPONSE_JSON));
            }
            verifyDelegation(operation);
            verifyNoMoreInteractions(service);
        }
        verify(decoder).decode("token");
        verify(users).loadUserByUsername("tester");
        verifyNoInteractions(rateLimits);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE", "ALL", "FILTERED", "ID"})
    void anonymousCannotInvokeAnyHandler(String operation) throws Exception {
        mvc.perform(endpoint(operation))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate",
                        "Bearer resource_metadata=\"http://localhost/.well-known/oauth-protected-resource\""));
        verifyNoInteractions(service, decoder, users);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ALL", "FILTERED"})
    void emptyCollections(String operation) throws Exception {
        authenticate("VIEWER");
        if (operation.equals("ALL")) when(service.getAll()).thenReturn(List.of());
        else when(service.getByGovernorate(2L)).thenReturn(List.of());
        mvc.perform(endpoint(operation).header("Authorization", "Bearer token"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        verifyDelegation(operation);
        verifyNoMoreInteractions(service);
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> invalidRequests() {
        return Stream.of("POST", "PUT").flatMap(method -> Stream.of(
                new String[]{"governorateId", JSON.replace("\"governorateId\":2,", "")},
                new String[]{"governorateId", JSON.replace("\"governorateId\":2", "\"governorateId\":0")},
                new String[]{"governorateId", JSON.replace("\"governorateId\":2", "\"governorateId\":-1")},
                new String[]{"nameAr", JSON.replace("\"nameAr\":\"Council\"", "\"nameAr\":\" \"")},
                new String[]{"nameAr", JSON.replace("\"nameAr\":\"Council\"", "\"nameAr\":\"" + "x".repeat(181) + "\"")},
                new String[]{"nameEn", JSON.replace("\"nameEn\":\"Council\"", "\"nameEn\":\"" + "x".repeat(181) + "\"")},
                new String[]{"authorityType", JSON.replace(",\"authorityType\":\"MUNICIPALITY\"", "")}
        ).map(value -> org.junit.jupiter.params.provider.Arguments.of(method, value[0], value[1])));
    }

    @ParameterizedTest(name = "{0}: invalid {1} [{index}]")
    @MethodSource("invalidRequests")
    void invalidDtoFields(String method, String field, String json) throws Exception {
        authenticate("ADMIN");
        expectError(mvc.perform(endpoint(method).content(json)
                .header("Authorization", "Bearer token")), 400, "Bad Request", path(method))
                .andExpect(jsonPath("$.message", startsWith(field + ": ")));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @CsvSource({"POST,UNKNOWN_ENUM", "PUT,UNKNOWN_ENUM", "POST,MALFORMED", "PUT,MALFORMED",
            "ID,NONNUMERIC", "PUT,NONNUMERIC", "DELETE,NONNUMERIC", "FILTERED,NONNUMERIC"})
    void invalidJsonAndNumericParameters(String operation, String invalid) throws Exception {
        authenticate("ADMIN");
        MockHttpServletRequestBuilder builder;
        if (invalid.equals("NONNUMERIC")) {
            builder = operation.equals("FILTERED")
                    ? request(HttpMethod.GET, BASE).param("governorateId", "abc")
                    : request(HttpMethod.valueOf(operation.equals("ID") ? "GET" : operation), BASE + "/abc")
                            .contentType(MediaType.APPLICATION_JSON).content(JSON);
        } else {
            builder = endpoint(operation).content(invalid.equals("MALFORMED")
                    ? "{" : JSON.replace("MUNICIPALITY", "UNKNOWN"));
        }
        mvc.perform(builder.header("Authorization", "Bearer token"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE", "ID", "FILTERED"})
    void notFoundUsesExistingHandler(String operation) throws Exception {
        authenticate("ADMIN");
        ResourceNotFoundException error = new ResourceNotFoundException("LocalAuthority", 1L);
        switch (operation) {
            case "POST" -> when(service.create(REQUEST)).thenThrow(error);
            case "PUT" -> when(service.update(1L, REQUEST)).thenThrow(error);
            case "DELETE" -> doThrow(error).when(service).delete(1L);
            case "ID" -> when(service.getById(1L)).thenThrow(error);
            case "FILTERED" -> when(service.getByGovernorate(2L)).thenThrow(error);
            default -> throw new IllegalArgumentException(operation);
        }
        expectError(mvc.perform(endpoint(operation).header("Authorization", "Bearer token")),
                404, "Not Found", path(operation))
                .andExpect(jsonPath("$.message").value("LocalAuthority with id 1 not found"));
        verifyDelegation(operation);
        verifyNoMoreInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE"})
    void conflictUsesExistingHandler(String operation) throws Exception {
        authenticate("ADMIN");
        DuplicateResourceException error = new DuplicateResourceException("Constraint conflict");
        switch (operation) {
            case "POST" -> when(service.create(REQUEST)).thenThrow(error);
            case "PUT" -> when(service.update(1L, REQUEST)).thenThrow(error);
            case "DELETE" -> doThrow(error).when(service).delete(1L);
            default -> throw new IllegalArgumentException(operation);
        }
        expectError(mvc.perform(endpoint(operation).header("Authorization", "Bearer token")),
                409, "Conflict", path(operation))
                .andExpect(jsonPath("$.message").value("Constraint conflict"));
        verifyDelegation(operation);
        verifyNoMoreInteractions(service);
    }

    @Test
    void invalidBearerTokenIsRejectedBeforeService() throws Exception {
        when(decoder.decode("bad")).thenThrow(new BadJwtException("Invalid token"));
        mvc.perform(endpoint("ID").header("Authorization", "Bearer bad"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service, users);
    }

    @Test
    void sliceContainsNoDatabaseOrMigrationInfrastructure() {
        assertThat(context.getBeansOfType(DataSource.class)).isEmpty();
        assertThat(context.getBeansOfType(jakarta.persistence.EntityManagerFactory.class)).isEmpty();
        assertThat(context.getBeansOfType(org.flywaydb.core.Flyway.class)).isEmpty();
    }

    private void authenticate(String role) {
        when(decoder.decode("token")).thenReturn(Jwt.withTokenValue("token")
                .header("alg", "RS256").subject("tester").build());
        String[] roles = role.equals("NONE") ? new String[0] : new String[]{role};
        when(users.loadUserByUsername("tester")).thenReturn(User.withUsername("tester")
                .password("unused").roles(roles).build());
    }

    private MockHttpServletRequestBuilder endpoint(String operation) {
        String method = switch (operation) {
            case "POST", "PUT", "DELETE" -> operation;
            default -> "GET";
        };
        MockHttpServletRequestBuilder builder = request(HttpMethod.valueOf(method), path(operation));
        if (operation.equals("FILTERED")) builder.param("governorateId", "2");
        if (method.equals("POST") || method.equals("PUT")) {
            builder.contentType(MediaType.APPLICATION_JSON).content(JSON);
        }
        return builder;
    }

    private String path(String operation) {
        return List.of("PUT", "DELETE", "ID").contains(operation) ? BASE + "/1" : BASE;
    }

    private void stubSuccess(String operation) {
        switch (operation) {
            case "POST" -> when(service.create(REQUEST)).thenReturn(RESPONSE);
            case "PUT" -> when(service.update(1L, REQUEST)).thenReturn(RESPONSE);
            case "ID" -> when(service.getById(1L)).thenReturn(RESPONSE);
            case "ALL" -> when(service.getAll()).thenReturn(List.of(RESPONSE));
            case "FILTERED" -> when(service.getByGovernorate(2L)).thenReturn(List.of(RESPONSE));
            case "DELETE" -> { }
            default -> throw new IllegalArgumentException(operation);
        }
    }

    private void verifyDelegation(String operation) {
        switch (operation) {
            case "POST" -> verify(service).create(REQUEST);
            case "PUT" -> verify(service).update(1L, REQUEST);
            case "DELETE" -> verify(service).delete(1L);
            case "ID" -> verify(service).getById(1L);
            case "ALL" -> verify(service).getAll();
            case "FILTERED" -> verify(service).getByGovernorate(2L);
            default -> throw new IllegalArgumentException(operation);
        }
    }

    private ResultActions expectError(ResultActions result, int code, String error, String path)
            throws Exception {
        return result.andExpect(status().is(code))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(code))
                .andExpect(jsonPath("$.error").value(error))
                .andExpect(jsonPath("$.path").value(path));
    }
}