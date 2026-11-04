package com.qaryati.village.controller;

import com.qaryati.common.exception.*;
import com.qaryati.security.config.*;
import com.qaryati.security.ratelimit.LoginRateLimitService;
import com.qaryati.security.service.DatabaseUserDetailsService;
import com.qaryati.village.dto.*;
import com.qaryati.village.entity.VillageStatus;
import com.qaryati.village.service.VillageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** MVC slice only: production bearer filter, authority converter and method security; no database. */
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
@WebMvcTest(VillageController.class)
@Import({SecurityConfig.class, JwtAuthenticationConverterConfig.class, GlobalExceptionHandler.class})
class VillageControllerTest {
    private static final String BASE = "/api/v1/villages";
    private static final String JSON = """
            {"governorateId":2,"nameAr":"Village","locationDescription":"Location"}
            """.trim();
    private static final VillageRequest REQUEST = new VillageRequest(2L, null, "Village",
            null, null, "Location", null, null, null, null);
    private static final Instant TIME = Instant.parse("2026-11-04T12:00:00.123456Z");
    private static final VillageResponse RESPONSE = new VillageResponse(1L, 2L, null, "Village",
            null, null, "Location", null, null, null, null, VillageStatus.DRAFT, TIME, TIME, null);

    @Autowired private MockMvc mvc;
    @Autowired private ApplicationContext context;
    @MockitoBean private VillageService service;
    @MockitoBean private JwtDecoder decoder;
    @MockitoBean private DatabaseUserDetailsService users;
    @MockitoBean private LoginRateLimitService rateLimits;

    static Stream<Arguments> roles() {
        return Stream.of("VIEWER", "EDITOR", "ADMIN", "NONE", "UNRELATED")
                .flatMap(role -> Stream.of("POST", "PUT", "DELETE", "PAGE", "ID")
                        .map(operation -> Arguments.of(role, operation)));
    }

    @ParameterizedTest
    @MethodSource("roles")
    void productionRoleMatrixAndExactDelegation(String role, String operation) throws Exception {
        authenticate(role);
        boolean read = operation.equals("PAGE") || operation.equals("ID");
        boolean allowed = role.equals("ADMIN") || role.equals("EDITOR") && !operation.equals("DELETE")
                || role.equals("VIEWER") && read;
        if (allowed) stub(operation);
        var result = mvc.perform(endpoint(operation).header("Authorization", "Bearer token"));
        if (!allowed) {
            result.andExpect(status().isForbidden());
            verifyNoInteractions(service);
        } else {
            result.andExpect(status().is(operation.equals("POST") ? 201 : operation.equals("DELETE") ? 204 : 200));
            if (operation.equals("DELETE")) result.andExpect(content().string(""));
            else if (operation.equals("PAGE")) {
                result.andExpect(jsonPath("$.content[0].id").value(1))
                        .andExpect(jsonPath("$.number").value(0))
                        .andExpect(jsonPath("$.size").value(20))
                        .andExpect(jsonPath("$.totalElements").value(1))
                        .andExpect(jsonPath("$.totalPages").value(1));
            } else assertVillage(result, "$");
            verifyDelegation(operation);
            verifyNoMoreInteractions(service);
        }
        verify(decoder).decode("token");
        verify(users).loadUserByUsername("tester");
        verifyNoInteractions(rateLimits);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE", "PAGE", "ID"})
    void anonymousDeniedBeforeService(String operation) throws Exception {
        mvc.perform(endpoint(operation)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service, decoder, users);
    }

    @Test
    void invalidBearerDeniedBeforeService() throws Exception {
        when(decoder.decode("bad")).thenThrow(new BadJwtException("Invalid token"));
        mvc.perform(endpoint("ID").header("Authorization", "Bearer bad")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service, users);
    }

    @ParameterizedTest
    @CsvSource({"0,1", "2,10", "0,100"})
    void explicitPaginationPreservesContentAndMetadata(int page, int size) throws Exception {
        authenticate("VIEWER");
        when(service.getActive(page, size)).thenReturn(
                new PageImpl<>(List.of(RESPONSE), PageRequest.of(page, size), 101));
        var result = mvc.perform(endpoint("PAGE").param("page", "" + page).param("size", "" + size)
                .header("Authorization", "Bearer token")).andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(page))
                .andExpect(jsonPath("$.size").value(size))
                .andExpect(jsonPath("$.totalElements").value(101))
                .andExpect(jsonPath("$.totalPages").value((101 + size - 1) / size))
                .andExpect(jsonPath("$.numberOfElements").value(1))
                .andExpect(jsonPath("$.*", org.hamcrest.Matchers.hasSize(11)))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.hasPrevious").value(page > 0))
                .andExpect(jsonPath("$.first").value(page == 0))
                .andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$.empty").value(false));
        assertVillage(result, "$.content[0]");
        verify(service).getActive(page, size);
        verifyNoMoreInteractions(service);
    }

    @Test
    void emptyPageSerializes() throws Exception {
        authenticate("VIEWER");
        when(service.getActive(0, 20)).thenReturn(Page.empty(PageRequest.of(0, 20)));
        mvc.perform(endpoint("PAGE").header("Authorization", "Bearer token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
        verify(service).getActive(0, 20);
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,-1", "0,101", "2147483647,100"})
    void servicePaginationRejectionsAre400WithoutClamping(int page, int size) throws Exception {
        authenticate("VIEWER");
        // Bounds are independently exercised against the real Service in VillageServiceTest.
        when(service.getActive(page, size)).thenThrow(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination"));
        error(mvc.perform(endpoint("PAGE").param("page", "" + page).param("size", "" + size)
                .header("Authorization", "Bearer token")), 400, "Bad Request", BASE)
                .andExpect(jsonPath("$.message").value("Invalid pagination"));
        verify(service).getActive(page, size);
        verifyNoMoreInteractions(service);
    }

    static Stream<Arguments> invalidBodies() {
        return Stream.of("POST", "PUT").flatMap(method -> Stream.of(
                new String[]{"governorateId", JSON.replace("\"governorateId\":2,", "")},
                new String[]{"governorateId", JSON.replace(":2", ":null")},
                new String[]{"governorateId", JSON.replace(":2", ":0")},
                new String[]{"nameAr", JSON.replace("\"Village\"", "\" \"")},
                new String[]{"nameAr", JSON.replace("\"Village\"", "null")},
                new String[]{"nameAr", JSON.replace("Village", "x".repeat(181))},
                new String[]{"locationDescription", JSON.replace("\"Location\"", "\"\"")},
                new String[]{"locationDescription", JSON.replace("\"Location\"", "null")},
                new String[]{"nameEn", add("\"nameEn\":\"" + "x".repeat(181) + "\"")},
                new String[]{"localAuthorityId", add("\"localAuthorityId\":0")},
                new String[]{"coordinatesPaired", add("\"latitude\":1")},
                new String[]{"coordinatesPaired", add("\"longitude\":1")},
                new String[]{"latitude", add("\"latitude\":90.000001,\"longitude\":1")},
                new String[]{"longitude", add("\"latitude\":1,\"longitude\":-180.000001")},
                new String[]{"latitudeRepresentable", add("\"latitude\":1.0000001,\"longitude\":1")},
                new String[]{"longitudeRepresentable", add("\"latitude\":1,\"longitude\":1.0000001")},
                new String[]{"elevationRepresentable", add("\"elevationM\":10000000")},
                new String[]{"elevationRepresentable", add("\"elevationM\":1.001")},
                new String[]{"areaRepresentable", add("\"areaKm2\":1000000000")},
                new String[]{"areaRepresentable", add("\"areaKm2\":0.0001")},
                new String[]{"areaKm2", add("\"areaKm2\":0")},
                new String[]{"areaKm2", add("\"areaKm2\":-1")}
        ).map(value -> Arguments.of(method, value[0], value[1])));
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void beanValidationStopsServiceWithUsefulErrors(String method, String field, String body) throws Exception {
        authenticate("ADMIN");
        error(mvc.perform(endpoint(method).content(body).header("Authorization", "Bearer token")),
                400, "Bad Request", path(method))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.startsWith(field + ": ")));
        verifyNoInteractions(service);
    }

    static Stream<Arguments> unsupported() {
        return Stream.of("POST", "PUT").flatMap(method ->
                Stream.of("unexpected", "id", "status", "createdAt", "updatedAt", "archivedAt")
                        .flatMap(field -> Stream.of("null", "\"supplied\"")
                                .map(value -> Arguments.of(method, field, value))));
    }

    @ParameterizedTest
    @MethodSource("unsupported")
    void unsupportedPropertiesIncludingNullAre400(String method, String field, String value) throws Exception {
        authenticate("ADMIN");
        mvc.perform(endpoint(method).content(add("\"" + field + "\":" + value))
                .header("Authorization", "Bearer token")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @CsvSource({"POST,BODY", "PUT,BODY", "POST,EMPTY", "PUT,EMPTY", "ID,PATH", "PUT,PATH",
            "DELETE,PATH", "PAGE,PAGE", "PAGE,SIZE", "PAGE,OVERFLOW"})
    void malformedRequestsAre400(String operation, String invalid) throws Exception {
        authenticate("ADMIN");
        var builder = endpoint(operation);
        switch (invalid) {
            case "BODY" -> builder.content("{");
            case "EMPTY" -> builder.content("");
            case "PATH" -> builder = request(HttpMethod.valueOf(operation.equals("ID") ? "GET" : operation),
                    BASE + "/abc").contentType(MediaType.APPLICATION_JSON).content(JSON);
            case "PAGE" -> builder.param("page", "abc");
            case "SIZE" -> builder.param("size", "abc");
            case "OVERFLOW" -> builder.param("page", "999999999999999999999");
        }
        mvc.perform(builder.header("Authorization", "Bearer token")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE", "ID"})
    void notFoundUsesCommonHandler(String operation) throws Exception {
        authenticate("ADMIN");
        RuntimeException failure = new ResourceNotFoundException("Village", 1L);
        failService(operation, failure);
        error(mvc.perform(endpoint(operation).header("Authorization", "Bearer token")),
                404, "Not Found", path(operation))
                .andExpect(jsonPath("$.message").value("Village with id 1 not found"));
        verifyDelegation(operation);
        verifyNoMoreInteractions(service);
    }

    @ParameterizedTest
    @CsvSource({"POST,409", "PUT,409", "DELETE,409", "POST,400", "PUT,400", "ID,400", "DELETE,400"})
    void serviceStatusErrorsUseCommonHandler(String operation, int code) throws Exception {
        authenticate("ADMIN");
        failService(operation, new ResponseStatusException(HttpStatus.valueOf(code), "Business rejection"));
        error(mvc.perform(endpoint(operation).header("Authorization", "Bearer token")),
                code, HttpStatus.valueOf(code).getReasonPhrase(), path(operation))
                .andExpect(jsonPath("$.message").value("Business rejection"));
        verifyDelegation(operation);
        verifyNoMoreInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT"})
    void duplicateNameUsesCommon409(String operation) throws Exception {
        authenticate("ADMIN");
        failService(operation, new DuplicateResourceException("Duplicate name"));
        error(mvc.perform(endpoint(operation).header("Authorization", "Bearer token")),
                409, "Conflict", path(operation)).andExpect(jsonPath("$.message").value("Duplicate name"));
    }

    @Test
    void archivedRecordReadableAndRepeatedDeleteDelegatesOnlyToArchive() throws Exception {
        authenticate("ADMIN");
        var archived = new VillageResponse(1L, 2L, null, "Village", null, null, "Location",
                null, null, null, null, VillageStatus.VERIFIED, TIME, TIME, TIME);
        when(service.getById(1L)).thenReturn(archived);
        mvc.perform(endpoint("ID").header("Authorization", "Bearer token")).andExpect(status().isOk())
                .andExpect(jsonPath("$.archivedAt").value(TIME.toString()))
                .andExpect(jsonPath("$.status").value("VERIFIED"));
        for (int i = 0; i < 2; i++)
            mvc.perform(endpoint("DELETE").header("Authorization", "Bearer token"))
                    .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(service).getById(1L);
        verify(service, times(2)).archive(1L);
        verifyNoMoreInteractions(service);
    }

    @Test
    void sliceHasNoPersistenceOrMigrationInfrastructure() {
        assertTrue(context.getBeansOfType(DataSource.class).isEmpty());
        assertTrue(context.getBeansOfType(jakarta.persistence.EntityManagerFactory.class).isEmpty());
        assertTrue(context.getBeansOfType(org.flywaydb.core.Flyway.class).isEmpty());
    }

    @org.junit.jupiter.api.AfterEach
    void noUnstablePageWarning(org.springframework.boot.test.system.CapturedOutput output) {
        assertFalse(output.getAll().contains("Serializing PageImpl instances as-is"));
    }

    @Test
    void emptyPageHasExactStableSchema() throws Exception {
        authenticate("VIEWER");
        when(service.getActive(0, 20)).thenReturn(Page.empty(PageRequest.of(0, 20)));
        mvc.perform(endpoint("PAGE").header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"content":[],"number":0,"size":20,"totalElements":0,"totalPages":0,
                         "numberOfElements":0,"first":true,"last":true,"hasNext":false,
                         "hasPrevious":false,"empty":true}
                        """, org.springframework.test.json.JsonCompareMode.STRICT));
    }
    private static String add(String fields) {
        return JSON.substring(0, JSON.length() - 1) + "," + fields + "}";
    }

    private void authenticate(String role) {
        when(decoder.decode("token")).thenReturn(Jwt.withTokenValue("token")
                .header("alg", "RS256").subject("tester").build());
        when(users.loadUserByUsername("tester")).thenReturn(User.withUsername("tester").password("unused")
                .roles(role.equals("NONE") ? new String[0] : new String[]{role}).build());
    }

    private String path(String operation) {
        return List.of("ID", "PUT", "DELETE").contains(operation) ? BASE + "/1" : BASE;
    }

    private MockHttpServletRequestBuilder endpoint(String operation) {
        var builder = request(HttpMethod.valueOf(List.of("POST", "PUT", "DELETE").contains(operation)
                ? operation : "GET"), path(operation));
        if (operation.equals("POST") || operation.equals("PUT"))
            builder.contentType(MediaType.APPLICATION_JSON).content(JSON);
        return builder;
    }

    private void stub(String operation) {
        switch (operation) {
            case "POST" -> when(service.create(REQUEST)).thenReturn(RESPONSE);
            case "PUT" -> when(service.update(1L, REQUEST)).thenReturn(RESPONSE);
            case "ID" -> when(service.getById(1L)).thenReturn(RESPONSE);
            case "PAGE" -> when(service.getActive(0, 20)).thenReturn(
                    new PageImpl<>(List.of(RESPONSE), PageRequest.of(0, 20), 1));
        }
    }

    private void failService(String operation, RuntimeException failure) {
        switch (operation) {
            case "POST" -> when(service.create(REQUEST)).thenThrow(failure);
            case "PUT" -> when(service.update(1L, REQUEST)).thenThrow(failure);
            case "ID" -> when(service.getById(1L)).thenThrow(failure);
            case "DELETE" -> doThrow(failure).when(service).archive(1L);
        }
    }

    private void verifyDelegation(String operation) {
        switch (operation) {
            case "POST" -> verify(service).create(REQUEST);
            case "PUT" -> verify(service).update(1L, REQUEST);
            case "ID" -> verify(service).getById(1L);
            case "PAGE" -> verify(service).getActive(0, 20);
            case "DELETE" -> verify(service).archive(1L);
        }
    }

    private void assertVillage(ResultActions result, String prefix) throws Exception {
        result.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath(prefix + ".id").value(1))
                .andExpect(jsonPath(prefix + ".governorateId").value(2))
                .andExpect(jsonPath(prefix + ".nameAr").value("Village"))
                .andExpect(jsonPath(prefix + ".locationDescription").value("Location"))
                .andExpect(jsonPath(prefix + ".status").value("DRAFT"))
                .andExpect(jsonPath(prefix + ".createdAt").value(TIME.toString()))
                .andExpect(jsonPath(prefix + ".updatedAt").value(TIME.toString()))
                .andExpect(jsonPath(prefix + ".archivedAt").doesNotExist());
    }

    private ResultActions error(ResultActions result, int code, String reason, String path) throws Exception {
        return result.andExpect(status().is(code)).andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(code)).andExpect(jsonPath("$.error").value(reason))
                .andExpect(jsonPath("$.path").value(path));
    }
}