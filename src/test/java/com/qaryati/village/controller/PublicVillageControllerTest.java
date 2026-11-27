package com.qaryati.village.controller;

import com.qaryati.common.exception.GlobalExceptionHandler;
import com.qaryati.common.exception.ResourceNotFoundException;
import com.qaryati.security.config.JwtAuthenticationConverterConfig;
import com.qaryati.security.config.SecurityConfig;
import com.qaryati.security.ratelimit.LoginRateLimitService;
import com.qaryati.security.service.DatabaseUserDetailsService;
import com.qaryati.village.dto.PublicVillageDetailResponse;
import com.qaryati.village.dto.PublicVillageResponse;
import com.qaryati.village.dto.VillageRequest;
import com.qaryati.village.dto.VillageResponse;
import com.qaryati.village.entity.VillageStatus;
import com.qaryati.village.service.VillageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Isolated MVC contract and production bearer-security checks. The mocked service
 * owns publication filtering; these tests do not establish database visibility.
 */
@WebMvcTest({PublicVillageController.class, VillageController.class})
@Import({SecurityConfig.class, JwtAuthenticationConverterConfig.class, GlobalExceptionHandler.class})
class PublicVillageControllerTest {
    private static final String PUBLIC_BASE = "/api/v1/public/villages";
    private static final String EDITORIAL_BASE = "/api/v1/villages";
    private static final String WRITE_JSON = """
            {"governorateId":2,"nameAr":"Village","locationDescription":"Location"}
            """.trim();
    private static final VillageRequest WRITE_REQUEST = new VillageRequest(2L, null, "Village",
            null, null, "Location", null, null, null, null);
    private static final PublicVillageResponse PUBLIC = new PublicVillageResponse(1L, "Village",
            "Village EN", 2L, 3L, new BigDecimal("31.900000"), new BigDecimal("35.200000"));
    private static final PublicVillageResponse NULLABLE_PUBLIC = new PublicVillageResponse(4L,
            "Village without optional values", null, 2L, null, null, null);
    private static final PublicVillageDetailResponse PUBLIC_DETAIL = new PublicVillageDetailResponse(1L,
            "Village", "Village EN", 2L, 3L, new BigDecimal("31.900000"), new BigDecimal("35.200000"),
            "Public origin", "Public location", new BigDecimal("700.25"), new BigDecimal("1.125"));
    private static final PublicVillageDetailResponse NULLABLE_PUBLIC_DETAIL = new PublicVillageDetailResponse(4L,
            "Village without optional values", null, 2L, null, null, null,
            null, "Location without optional detail values", null, null);
    private static final String PUBLIC_JSON = """
            {"id":1,"nameAr":"Village","nameEn":"Village EN","governorateId":2,
             "localAuthorityId":3,"latitude":31.9,"longitude":35.2}
            """.trim();
    private static final String NULLABLE_PUBLIC_JSON = """
            {"id":4,"nameAr":"Village without optional values","nameEn":null,
             "governorateId":2,"localAuthorityId":null,"latitude":null,"longitude":null}
            """.trim();
    private static final String PUBLIC_DETAIL_JSON = """
            {"id":1,"nameAr":"Village","nameEn":"Village EN","governorateId":2,
             "localAuthorityId":3,"latitude":31.9,"longitude":35.2,"nameOrigin":"Public origin",
             "locationDescription":"Public location","elevationM":700.25,"areaKm2":1.125}
            """.trim();
    private static final String NULLABLE_PUBLIC_DETAIL_JSON = """
            {"id":4,"nameAr":"Village without optional values","nameEn":null,
             "governorateId":2,"localAuthorityId":null,"latitude":null,"longitude":null,
             "nameOrigin":null,"locationDescription":"Location without optional detail values",
             "elevationM":null,"areaKm2":null}
            """.trim();
    private static final Instant TIME = Instant.parse("2026-11-04T12:00:00.123456Z");
    private static final VillageResponse EDITORIAL = new VillageResponse(1L, 2L, null, "Village",
            null, "Editorial origin", "Location", null, null, null, null,
            VillageStatus.DRAFT, TIME, TIME, null);

    @Autowired private MockMvc mvc;
    @Autowired private ApplicationContext context;
    @MockitoBean private VillageService service;
    @MockitoBean private JwtDecoder decoder;
    @MockitoBean private DatabaseUserDetailsService users;
    @MockitoBean private LoginRateLimitService rateLimits;

    @AfterEach
    void villageRequestsNeverInvokeTheLoginLimiter() {
        verifyNoInteractions(rateLimits);
    }

    @Test
    void anonymousListingHasExactlyElevenPageFieldsAndSevenPublicFieldsIncludingNulls() throws Exception {
        when(service.getPublicActive(0, 20, null, null, null, "id", "asc"))
                .thenReturn(new PageImpl<>(List.of(PUBLIC, NULLABLE_PUBLIC), PageRequest.of(0, 20), 2));

        var result = mvc.perform(get(PUBLIC_BASE)).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"content":[%s,%s],"number":0,"size":20,"totalElements":2,"totalPages":1,
                         "numberOfElements":2,"first":true,"last":true,"hasNext":false,
                         "hasPrevious":false,"empty":false}
                        """.formatted(PUBLIC_JSON, NULLABLE_PUBLIC_JSON), JsonCompareMode.STRICT))
                .andExpect(jsonPath("$.*", hasSize(11)))
                .andExpect(jsonPath("$.content[0].*", hasSize(7)))
                .andExpect(jsonPath("$.content[1].*", hasSize(7)));
        assertCollectionFieldsOnly(result, "$.content[0]");
        assertCollectionFieldsOnly(result, "$.content[1]");
        verify(service).getPublicActive(0, 20, null, null, null, "id", "asc");
        verifyNoMoreInteractions(service);
        verifyNoInteractions(decoder, users);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void anonymousDetailHasExactlyElevenPublicFieldsIncludingOptionalNulls(boolean optionalNulls) throws Exception {
        var response = optionalNulls ? NULLABLE_PUBLIC_DETAIL : PUBLIC_DETAIL;
        when(service.getPublicById(response.id())).thenReturn(response);

        var result = mvc.perform(get(PUBLIC_BASE + "/" + response.id())).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(optionalNulls ? NULLABLE_PUBLIC_DETAIL_JSON : PUBLIC_DETAIL_JSON,
                        JsonCompareMode.STRICT))
                .andExpect(jsonPath("$.*", hasSize(11)));
        assertNoEditorialFields(result, "$");
        verify(service).getPublicById(response.id());
        verifyNoMoreInteractions(service);
        verifyNoInteractions(decoder, users);
    }

    @ParameterizedTest
    @CsvSource({"0,1", "2,10", "0,100"})
    void publicPaginationPreservesTheExplicitPageContract(int page, int size) throws Exception {
        when(service.getPublicActive(page, size, null, null, null, "id", "asc"))
                .thenReturn(new PageImpl<>(List.of(PUBLIC), PageRequest.of(page, size), 101));

        var result = mvc.perform(get(PUBLIC_BASE).param("page", "" + page).param("size", "" + size))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(11)))
                .andExpect(jsonPath("$.content[0].*", hasSize(7)))
                .andExpect(jsonPath("$.number").value(page))
                .andExpect(jsonPath("$.size").value(size))
                .andExpect(jsonPath("$.totalElements").value(101))
                .andExpect(jsonPath("$.totalPages").value((101 + size - 1) / size))
                .andExpect(jsonPath("$.numberOfElements").value(1))
                .andExpect(jsonPath("$.first").value(page == 0))
                .andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.hasPrevious").value(page > 0))
                .andExpect(jsonPath("$.empty").value(false));
        assertCollectionFieldsOnly(result, "$.content[0]");
        verify(service).getPublicActive(page, size, null, null, null, "id", "asc");
        verifyNoMoreInteractions(service);
        verifyNoInteractions(decoder, users);
    }

    @ParameterizedTest
    @CsvSource({"0,20,0", "6,20,101"})
    void emptyAndOutOfRangePublicPagesKeepCountAndNavigationSemantics(int page, int size, long total)
            throws Exception {
        when(service.getPublicActive(page, size, null, null, null, "id", "asc"))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(page, size), total));

        mvc.perform(get(PUBLIC_BASE).param("page", "" + page).param("size", "" + size))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"content":[],"number":%d,"size":%d,"totalElements":%d,"totalPages":%d,
                         "numberOfElements":0,"first":%s,"last":true,"hasNext":false,
                         "hasPrevious":%s,"empty":true}
                        """.formatted(page, size, total, (total + size - 1) / size,
                        page == 0, page > 0), JsonCompareMode.STRICT));
        verify(service).getPublicActive(page, size, null, null, null, "id", "asc");
        verifyNoMoreInteractions(service);
    }

    @ParameterizedTest
    @CsvSource({"id,asc", "nameAr,desc", "nameEn,DESC"})
    void allowedPublicFiltersAndSortsDelegateWithoutAnEditorialStatus(String sortBy, String sortDir)
            throws Exception {
        when(service.getPublicActive(2, 10, "Olive %_!", 2L, 3L, sortBy, sortDir))
                .thenReturn(new PageImpl<>(List.of(PUBLIC), PageRequest.of(2, 10), 101));

        mvc.perform(get(PUBLIC_BASE).param("page", "2").param("size", "10")
                        .param("search", "Olive %_!").param("governorateId", "2")
                        .param("localAuthorityId", "3").param("sortBy", sortBy).param("sortDir", sortDir))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(2))
                .andExpect(jsonPath("$.content[0].id").value(1));
        verify(service).getPublicActive(2, 10, "Olive %_!", 2L, 3L, sortBy, sortDir);
        verifyNoMoreInteractions(service);
        verifyNoInteractions(decoder, users);
    }

    @Test
    void emptyDefaultedQueryValuesUseTheSamePublicDefaultsAsAbsentValues() throws Exception {
        stubPublicPage();

        mvc.perform(get(PUBLIC_BASE).param("page", "").param("size", "")
                        .param("sortBy", "").param("sortDir", ""))
                .andExpect(status().isOk());
        verify(service).getPublicActive(0, 20, null, null, null, "id", "asc");
        verifyNoMoreInteractions(service);
    }

    static Stream<Arguments> unexpectedParameters() {
        return Stream.of(PUBLIC_BASE, PUBLIC_BASE + "/1").flatMap(path ->
                Stream.of("status", "Status", "includeArchived", "archivedAt", "createdAt", "unexpected")
                        .flatMap(name -> Stream.of("", "VERIFIED", "DRAFT")
                                .map(value -> Arguments.of(path, name, value))));
    }

    @ParameterizedTest
    @MethodSource("unexpectedParameters")
    void unexpectedQueryNamesAreRejectedBeforeEitherPublicServiceMethod(String path, String name, String value)
            throws Exception {
        mvc.perform(get(path).param(name, value)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value(path));
        verifyNoInteractions(service, decoder, users);
    }

    @ParameterizedTest
    @ValueSource(strings = {PUBLIC_BASE, PUBLIC_BASE + "/1"})
    void repeatedStatusParametersCannotBypassThePublicQueryAllowlist(String path) throws Exception {
        mvc.perform(get(path).param("status", "", "VERIFIED", "DRAFT"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service, decoder, users);
    }

    @ParameterizedTest
    @ValueSource(strings = {"page", "size", "search", "governorateId", "localAuthorityId", "sortBy", "sortDir"})
    void publicDetailRejectsEvenCollectionQueryNames(String name) throws Exception {
        mvc.perform(get(PUBLIC_BASE + "/1").param(name, ""))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service, decoder, users);
    }

    @ParameterizedTest
    @CsvSource({"page,abc", "size,abc", "page,999999999999999999999",
            "governorateId,abc", "localAuthorityId,999999999999999999999"})
    void malformedPublicParametersAre400BeforeService(String name, String value) throws Exception {
        mvc.perform(get(PUBLIC_BASE).param(name, value)).andExpect(status().isBadRequest());
        verifyNoInteractions(service, decoder, users);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "999999999999999999999"})
    void malformedPublicDetailIdsAre400BeforeService(String id) throws Exception {
        mvc.perform(get(PUBLIC_BASE + "/" + id)).andExpect(status().isBadRequest());
        verifyNoInteractions(service, decoder, users);
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,-1", "0,101", "2147483647,100"})
    void publicServicePaginationErrorsRemain400WithoutClamping(int page, int size) throws Exception {
        // Real validation/publication predicates are covered by the service tests.
        when(service.getPublicActive(page, size, null, null, null, "id", "asc"))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination"));

        mvc.perform(get(PUBLIC_BASE).param("page", "" + page).param("size", "" + size))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid pagination"));
        verify(service).getPublicActive(page, size, null, null, null, "id", "asc");
        verifyNoMoreInteractions(service);
    }

    @ParameterizedTest
    @CsvSource({"search,181", "governorateId,0", "localAuthorityId,-1", "sortBy,createdAt",
            "sortBy,updatedAt", "sortBy,status", "sortBy,archivedAt", "sortBy,governorate.nameAr",
            "sortDir,sideways"})
    void publicServiceFilterAndSortErrorsRemain400(String name, String value) throws Exception {
        if (name.equals("search")) value = "x".repeat(181);
        when(service.getPublicActive(anyInt(), anyInt(), nullable(String.class), nullable(Long.class),
                nullable(Long.class), anyString(), anyString()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid public filter"));

        mvc.perform(get(PUBLIC_BASE).param(name, value)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid public filter"));
        verify(service).getPublicActive(anyInt(), anyInt(), nullable(String.class), nullable(Long.class),
                nullable(Long.class), anyString(), anyString());
        verifyNoMoreInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void publicServiceRejectsNonpositiveDetailIdsWith400(Long id) throws Exception {
        when(service.getPublicById(id))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Village ID must be positive"));

        mvc.perform(get(PUBLIC_BASE + "/" + id)).andExpect(status().isBadRequest());
        verify(service).getPublicById(id);
        verifyNoMoreInteractions(service);
    }

    @ParameterizedTest(name = "{0} detail uses the same 404 contract")
    @CsvSource({"DRAFT,11", "UNDER_REVIEW,12", "REJECTED,13", "ARCHIVED,14", "MISSING,99"})
    void serviceReportsMissingOrHiddenVillageThroughTheSame404Contract(String scenario, Long id) throws Exception {
        // The service decides whether a row is missing or hidden; MVC exposes neither a second lookup nor its reason.
        when(service.getPublicById(id)).thenThrow(new ResourceNotFoundException("Village", id));

        mvc.perform(get(PUBLIC_BASE + "/" + id)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.*", hasSize(5)))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Village with id " + id + " not found"))
                .andExpect(jsonPath("$.path").value(PUBLIC_BASE + "/" + id));
        verify(service).getPublicById(id);
        verifyNoMoreInteractions(service);
        verifyNoInteractions(decoder, users);
    }

    static Stream<Arguments> publicRoles() {
        return Stream.of("VIEWER", "EDITOR", "ADMIN", "NONE", "UNRELATED")
                .flatMap(role -> Stream.of(false, true).map(detail -> Arguments.of(role, detail)));
    }

    @ParameterizedTest
    @MethodSource("publicRoles")
    void validBearerRolesDoNotChangeThePublicProjectionForEitherRoute(String role, boolean detail)
            throws Exception {
        authenticate(role);
        if (detail) when(service.getPublicById(1L)).thenReturn(PUBLIC_DETAIL);
        else stubPublicPage();

        var result = mvc.perform(get(detail ? PUBLIC_BASE + "/1" : PUBLIC_BASE)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
        if (detail) {
            result.andExpect(content().json(PUBLIC_DETAIL_JSON, JsonCompareMode.STRICT))
                    .andExpect(jsonPath("$.*", hasSize(11)));
            assertNoEditorialFields(result, "$");
            verify(service).getPublicById(1L);
        } else {
            result.andExpect(content().json(singlePublicPageJson(), JsonCompareMode.STRICT));
            assertCollectionFieldsOnly(result, "$.content[0]");
            verify(service).getPublicActive(0, 20, null, null, null, "id", "asc");
        }
        verifyNoMoreInteractions(service);
        verify(decoder).decode("token");
        verify(users).loadUserByUsername("tester");
    }

    @ParameterizedTest
    @ValueSource(strings = {PUBLIC_BASE, PUBLIC_BASE + "/1", EDITORIAL_BASE + "/1"})
    void invalidBearerStillReturns401EvenOnAnAnonymousReadRoute(String path) throws Exception {
        when(decoder.decode("bad")).thenThrow(new BadJwtException("Invalid token"));

        mvc.perform(get(path).header("Authorization", "Bearer bad")).andExpect(status().isUnauthorized());
        verify(decoder).decode("bad");
        verifyNoInteractions(service, users);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingOrDisabledBearerUserCannotFallBackToAnonymousPublicAccess(boolean disabled) throws Exception {
        stubDecoder();
        if (disabled) {
            when(users.loadUserByUsername("tester")).thenReturn(User.withUsername("tester").password("unused")
                    .roles("ADMIN").disabled(true).build());
        } else {
            when(users.loadUserByUsername("tester")).thenThrow(new UsernameNotFoundException("Missing user"));
        }

        mvc.perform(get(PUBLIC_BASE + "/1").header("Authorization", "Bearer token"))
                .andExpect(status().isUnauthorized());
        verify(decoder).decode("token");
        verify(users).loadUserByUsername("tester");
        verifyNoInteractions(service);
    }

    static Stream<Arguments> editorialRoles() {
        return Stream.of("VIEWER", "EDITOR", "ADMIN", "NONE", "UNRELATED")
                .flatMap(role -> Stream.of("PAGE", "ID", "POST", "PUT", "DELETE")
                        .map(operation -> Arguments.of(role, operation)));
    }

    @ParameterizedTest
    @MethodSource("editorialRoles")
    void editorialFullReadsAndWritesRetainTheProductionRoleMatrix(String role, String operation) throws Exception {
        authenticate(role);
        boolean read = operation.equals("PAGE") || operation.equals("ID");
        boolean allowed = role.equals("ADMIN") || role.equals("EDITOR") && !operation.equals("DELETE")
                || role.equals("VIEWER") && read;
        if (allowed) stubEditorial(operation);

        var result = mvc.perform(editorialRequest(operation).header("Authorization", "Bearer token"));
        if (!allowed) {
            result.andExpect(status().isForbidden());
            verifyNoInteractions(service);
        } else {
            result.andExpect(status().is(operation.equals("POST") ? 201 : operation.equals("DELETE") ? 204 : 200));
            if (operation.equals("DELETE")) result.andExpect(content().string(""));
            else {
                String prefix = operation.equals("PAGE") ? "$.content[0]" : "$";
                result.andExpect(jsonPath(prefix + ".status").value("DRAFT"))
                        .andExpect(jsonPath(prefix + ".nameOrigin").value("Editorial origin"))
                        .andExpect(jsonPath(prefix + ".locationDescription").value("Location"))
                        .andExpect(jsonPath(prefix + ".createdAt").value(TIME.toString()))
                        .andExpect(jsonPath(prefix + ".updatedAt").value(TIME.toString()));
            }
            verifyEditorialDelegation(operation);
            verifyNoMoreInteractions(service);
        }
        verify(decoder).decode("token");
        verify(users).loadUserByUsername("tester");
    }

    static Stream<Arguments> anonymousProtectedRequests() {
        var arguments = Stream.<Arguments>builder();
        Stream.of("POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS").forEach(method -> {
            arguments.add(Arguments.of(method, PUBLIC_BASE));
            arguments.add(Arguments.of(method, PUBLIC_BASE + "/1"));
        });
        Stream.of(EDITORIAL_BASE, EDITORIAL_BASE + "/1", EDITORIAL_BASE + "/1/sources",
                PUBLIC_BASE + "/1/sources", PUBLIC_BASE + "/1/extra", "/api/v1/public/unknown",
                "/api/v1/sources", "/api/v1/sources/1/villages", "/api/v1/verifications",
                "/api/v1/data-conflicts", "/api/v1/audit-logs/entity", "/api/v1/geocoding/search",
                "/api/v1/population-records", "/api/v1/community-facilities", "/api/v1/auth/me")
                .forEach(path -> arguments.add(Arguments.of("GET", path)));
        arguments.add(Arguments.of("POST", EDITORIAL_BASE));
        arguments.add(Arguments.of("PUT", EDITORIAL_BASE + "/1"));
        arguments.add(Arguments.of("PATCH", EDITORIAL_BASE + "/1"));
        arguments.add(Arguments.of("DELETE", EDITORIAL_BASE + "/1"));
        return arguments.build();
    }

    @ParameterizedTest
    @MethodSource("anonymousProtectedRequests")
    void anonymousPermissionIsLimitedToTheTwoExactPublicGetRoutes(String method, String path) throws Exception {
        // Unloaded neighboring controllers are covered here at the filter boundary, not their method/service layer.
        var builder = request(HttpMethod.valueOf(method), path);
        if (List.of("POST", "PUT", "PATCH").contains(method))
            builder.contentType(MediaType.APPLICATION_JSON).content(WRITE_JSON);

        mvc.perform(builder).andExpect(status().isUnauthorized());
        verifyNoInteractions(service, decoder, users);
    }

    @Test
    void sliceHasNoDatasourceJpaOrMigrationInfrastructure() {
        assertTrue(context.getBeansOfType(DataSource.class).isEmpty());
        assertTrue(context.getBeansOfType(jakarta.persistence.EntityManagerFactory.class).isEmpty());
        assertTrue(context.getBeansOfType(org.flywaydb.core.Flyway.class).isEmpty());
        assertFalse(context.containsBean("flywayInitializer"));
        assertFalse(context.containsBean("liquibase"));
        try {
            Class<?> liquibase = Class.forName("liquibase.integration.spring.SpringLiquibase", false,
                    getClass().getClassLoader());
            assertEquals(0, context.getBeanNamesForType(liquibase, false, false).length);
        } catch (ClassNotFoundException absentOptionalDependency) {
            // No compile-time Liquibase dependency is needed to assert its absence from this slice.
        }
    }

    private void stubPublicPage() {
        when(service.getPublicActive(0, 20, null, null, null, "id", "asc"))
                .thenReturn(new PageImpl<>(List.of(PUBLIC), PageRequest.of(0, 20), 1));
    }

    private String singlePublicPageJson() {
        return """
                {"content":[%s],"number":0,"size":20,"totalElements":1,"totalPages":1,
                 "numberOfElements":1,"first":true,"last":true,"hasNext":false,
                 "hasPrevious":false,"empty":false}
                """.formatted(PUBLIC_JSON);
    }

    private ResultActions assertNoEditorialFields(ResultActions result, String prefix) throws Exception {
        for (String field : List.of("status", "createdAt", "updatedAt", "archivedAt", "sourceIds", "sources",
                "rawSources", "claimNote", "notes", "auditLogs", "users", "userId", "actorUserId", "reviewerUserId")) {
            result.andExpect(jsonPath(prefix + "." + field).doesNotHaveJsonPath());
        }
        return result;
    }

    private ResultActions assertCollectionFieldsOnly(ResultActions result, String prefix) throws Exception {
        assertNoEditorialFields(result, prefix);
        for (String field : List.of("nameOrigin", "locationDescription", "elevationM", "areaKm2")) {
            result.andExpect(jsonPath(prefix + "." + field).doesNotHaveJsonPath());
        }
        return result.andExpect(jsonPath(prefix + ".*", hasSize(7)));
    }

    private void stubDecoder() {
        when(decoder.decode("token")).thenReturn(Jwt.withTokenValue("token")
                .header("alg", "RS256").subject("tester").build());
    }

    private void authenticate(String role) {
        stubDecoder();
        when(users.loadUserByUsername("tester")).thenReturn(User.withUsername("tester").password("unused")
                .roles(role.equals("NONE") ? new String[0] : new String[]{role}).build());
    }

    private MockHttpServletRequestBuilder editorialRequest(String operation) {
        String path = List.of("ID", "PUT", "DELETE").contains(operation)
                ? EDITORIAL_BASE + "/1" : EDITORIAL_BASE;
        var builder = request(HttpMethod.valueOf(List.of("POST", "PUT", "DELETE").contains(operation)
                ? operation : "GET"), path);
        if (operation.equals("POST") || operation.equals("PUT"))
            builder.contentType(MediaType.APPLICATION_JSON).content(WRITE_JSON);
        return builder;
    }

    private void stubEditorial(String operation) {
        switch (operation) {
            case "POST" -> when(service.create(WRITE_REQUEST)).thenReturn(EDITORIAL);
            case "PUT" -> when(service.update(1L, WRITE_REQUEST)).thenReturn(EDITORIAL);
            case "ID" -> when(service.getById(1L)).thenReturn(EDITORIAL);
            case "PAGE" -> when(service.getActive(0, 20, null, null, null, null, "id", "asc"))
                    .thenReturn(new PageImpl<>(List.of(EDITORIAL), PageRequest.of(0, 20), 1));
        }
    }

    private void verifyEditorialDelegation(String operation) {
        switch (operation) {
            case "POST" -> verify(service).create(WRITE_REQUEST);
            case "PUT" -> verify(service).update(1L, WRITE_REQUEST);
            case "ID" -> verify(service).getById(1L);
            case "PAGE" -> verify(service).getActive(0, 20, null, null, null, null, "id", "asc");
            case "DELETE" -> verify(service).archive(1L);
        }
    }
}
