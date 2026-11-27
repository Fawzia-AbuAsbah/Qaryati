package com.qaryati.village.controller;

import com.qaryati.common.exception.GlobalExceptionHandler;
import com.qaryati.security.config.JwtAuthenticationConverterConfig;
import com.qaryati.security.config.SecurityConfig;
import com.qaryati.security.ratelimit.LoginRateLimitService;
import com.qaryati.security.service.DatabaseUserDetailsService;
import com.qaryati.village.dto.GovernorateRequest;
import com.qaryati.village.dto.GovernorateResponse;
import com.qaryati.village.dto.LocalAuthorityRequest;
import com.qaryati.village.dto.LocalAuthorityResponse;
import com.qaryati.village.entity.Governorate;
import com.qaryati.village.entity.LocalAuthority;
import com.qaryati.village.entity.LocalAuthorityType;
import com.qaryati.village.repository.GovernorateRepository;
import com.qaryati.village.repository.LocalAuthorityRepository;
import com.qaryati.village.service.GovernorateService;
import com.qaryati.village.service.LocalAuthorityService;
import com.qaryati.village.service.PublicGeographicCatalogueService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.sql.DataSource;
import java.net.URI;
import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Real public service, production bearer security and DTO serialization with mocked repositories.
 * Repository selection/sort arguments are checked here; no live query or database ordering is established.
 */
@WebMvcTest({PublicGeographicCatalogueController.class, GovernorateController.class, LocalAuthorityController.class})
@Import({PublicGeographicCatalogueService.class, SecurityConfig.class,
        JwtAuthenticationConverterConfig.class, GlobalExceptionHandler.class})
class PublicGeographicCatalogueControllerTest {
    private static final String PUBLIC_GOV = "/api/v1/public/governorates";
    private static final String PUBLIC_LA = "/api/v1/public/local-authorities";
    private static final String PRIVATE_GOV = "/api/v1/governorates";
    private static final String PRIVATE_LA = "/api/v1/local-authorities";
    private static final Sort ID_SORT = Sort.by("id");
    private static final String PUBLIC_GOV_JSON =
            "{\"id\":1,\"nameAr\":\"Governorate\",\"nameEn\":\"Governorate EN\"}";
    private static final String PUBLIC_LA_JSON =
            "{\"id\":2,\"nameAr\":\"Council\",\"nameEn\":\"Council EN\",\"governorateId\":1}";
    private static final String GOV_WRITE_JSON = """
            {"nameAr":"Governorate","nameEn":"Governorate EN","code":"EDITORIAL_CODE"}
            """.trim();
    private static final String GOV_EDITORIAL_JSON = """
            {"id":1,"nameAr":"Governorate","nameEn":"Governorate EN","code":"EDITORIAL_CODE"}
            """.trim();
    private static final GovernorateRequest GOV_REQUEST =
            new GovernorateRequest("Governorate", "Governorate EN", "EDITORIAL_CODE");
    private static final GovernorateResponse GOV_RESPONSE =
            new GovernorateResponse(1L, "Governorate", "Governorate EN", "EDITORIAL_CODE");
    private static final String LA_WRITE_JSON = """
            {"governorateId":1,"nameAr":"Council","nameEn":"Council EN","authorityType":"MUNICIPALITY"}
            """.trim();
    private static final String LA_EDITORIAL_JSON = """
            {"id":1,"governorateId":1,"nameAr":"Council","nameEn":"Council EN","authorityType":"MUNICIPALITY"}
            """.trim();
    private static final LocalAuthorityRequest LA_REQUEST =
            new LocalAuthorityRequest(1L, "Council", "Council EN", LocalAuthorityType.MUNICIPALITY);
    private static final LocalAuthorityResponse LA_RESPONSE =
            new LocalAuthorityResponse(1L, 1L, "Council", "Council EN", LocalAuthorityType.MUNICIPALITY);

    @Autowired private MockMvc mvc;
    @Autowired private ApplicationContext context;
    @MockitoBean private GovernorateRepository governorates;
    @MockitoBean private LocalAuthorityRepository authorities;
    @MockitoBean private GovernorateService governorateService;
    @MockitoBean private LocalAuthorityService localAuthorityService;
    @MockitoBean private JwtDecoder decoder;
    @MockitoBean private DatabaseUserDetailsService users;
    @MockitoBean private LoginRateLimitService rateLimits;

    @AfterEach
    void catalogueRequestsDoNotUseTheLoginLimiter() {
        verifyNoInteractions(rateLimits);
    }

    @Test
    void anonymousGovernoratesUseStableIdSortAndExactlyThreePublicFieldsIncludingNullEnglishName() throws Exception {
        when(governorates.findAll(ID_SORT)).thenReturn(List.of(governorate(1L, "Governorate", "Governorate EN"),
                governorate(9L, "Governorate without English", null)));

        mvc.perform(get(PUBLIC_GOV)).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        [{"id":1,"nameAr":"Governorate","nameEn":"Governorate EN"},
                         {"id":9,"nameAr":"Governorate without English","nameEn":null}]
                        """, JsonCompareMode.STRICT))
                .andExpect(jsonPath("$[0].*", hasSize(3)))
                .andExpect(jsonPath("$[1].*", hasSize(3)))
                .andExpect(jsonPath("$[0].code").doesNotHaveJsonPath())
                .andExpect(jsonPath("$[1].code").doesNotHaveJsonPath());

        verify(governorates).findAll(ID_SORT);
        verifyNoMoreInteractions(governorates);
        verifyNoInteractions(authorities, governorateService, localAuthorityService, decoder, users);
    }

    @Test
    void anonymousAuthoritiesWithoutAFilterUseStableIdSortAndAFlatFourFieldProjection() throws Exception {
        when(authorities.findAll(ID_SORT)).thenReturn(List.of(authority(2L, 1L, "Council", "Council EN"),
                authority(8L, 9L, "Council without English", null)));

        mvc.perform(get(PUBLIC_LA)).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        [{"id":2,"nameAr":"Council","nameEn":"Council EN","governorateId":1},
                         {"id":8,"nameAr":"Council without English","nameEn":null,"governorateId":9}]
                        """, JsonCompareMode.STRICT))
                .andExpect(jsonPath("$[0].*", hasSize(4)))
                .andExpect(jsonPath("$[1].*", hasSize(4)))
                .andExpect(jsonPath("$[0].authorityType").doesNotHaveJsonPath())
                .andExpect(jsonPath("$[0].governorate").doesNotHaveJsonPath());

        verify(authorities).findAll(ID_SORT);
        verifyNoMoreInteractions(authorities);
        verifyNoInteractions(governorates, governorateService, localAuthorityService, decoder, users);
    }

    @Test
    void anonymousAuthorityFilterIsAppliedByTheSortedRepositoryQueryWithoutAParentLookup() throws Exception {
        when(authorities.findByGovernorate_Id(1L, ID_SORT)).thenReturn(List.of(
                authority(2L, 1L, "Council", "Council EN"), authority(4L, 1L, "Second council", null)));

        mvc.perform(get(PUBLIC_LA).param("governorateId", "1")).andExpect(status().isOk())
                .andExpect(content().json("""
                        [{"id":2,"nameAr":"Council","nameEn":"Council EN","governorateId":1},
                         {"id":4,"nameAr":"Second council","nameEn":null,"governorateId":1}]
                        """, JsonCompareMode.STRICT))
                .andExpect(jsonPath("$[0].*", hasSize(4)))
                .andExpect(jsonPath("$[1].*", hasSize(4)));

        verify(authorities).findByGovernorate_Id(1L, ID_SORT);
        verifyNoMoreInteractions(authorities);
        verifyNoInteractions(governorates, governorateService, localAuthorityService, decoder, users);
    }

    @ParameterizedTest
    @ValueSource(strings = {PUBLIC_GOV, PUBLIC_LA})
    void emptyCataloguesAreArraysWithoutPagination(String path) throws Exception {
        if (path.equals(PUBLIC_GOV)) when(governorates.findAll(ID_SORT)).thenReturn(List.of());
        else when(authorities.findAll(ID_SORT)).thenReturn(List.of());

        mvc.perform(get(path)).andExpect(status().isOk())
                .andExpect(content().json("[]", JsonCompareMode.STRICT));
        if (path.equals(PUBLIC_GOV)) {
            verify(governorates).findAll(ID_SORT);
            verifyNoMoreInteractions(governorates);
            verifyNoInteractions(authorities);
        } else {
            verify(authorities).findAll(ID_SORT);
            verifyNoMoreInteractions(authorities);
            verifyNoInteractions(governorates);
        }
        verifyNoInteractions(governorateService, localAuthorityService, decoder, users);
    }

    @Test
    void unknownPositiveGovernorateFilterReturnsAnEmptyArrayWithoutCheckingParentExistence() throws Exception {
        when(authorities.findByGovernorate_Id(999L, ID_SORT)).thenReturn(List.of());

        mvc.perform(get(PUBLIC_LA).param("governorateId", "999")).andExpect(status().isOk())
                .andExpect(content().json("[]", JsonCompareMode.STRICT));
        verify(authorities).findByGovernorate_Id(999L, ID_SORT);
        verifyNoMoreInteractions(authorities);
        verifyNoInteractions(governorates, governorateService, localAuthorityService, decoder, users);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
    void realPublicServiceRejectsNonpositiveGovernorateFiltersBeforeRepositoryAccess(Long id) throws Exception {
        mvc.perform(get(PUBLIC_LA).param("governorateId", id.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value(PUBLIC_LA));
        verifyNoCatalogueAccess();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1.5", "1e2", "9223372036854775808", "-9223372036854775809"})
    void malformedAndOverflowingGovernorateFiltersAre400BeforeRepositoryAccess(String id) throws Exception {
        mvc.perform(get(PUBLIC_LA).param("governorateId", id)).andExpect(status().isBadRequest());
        verifyNoCatalogueAccess();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void presentBlankGovernorateFilterCannotBecomeAnUnfilteredRead(String value) throws Exception {
        mvc.perform(get(PUBLIC_LA).param("governorateId", value)).andExpect(status().isBadRequest());
        verifyNoCatalogueAccess();
    }

    @Test
    void valuelessGovernorateQueryCannotBecomeAnUnfilteredRead() throws Exception {
        mvc.perform(get(URI.create(PUBLIC_LA + "?governorateId"))).andExpect(status().isBadRequest());
        verifyNoCatalogueAccess();
    }

    static Stream<Arguments> duplicateFilters() {
        return Stream.of(new String[]{"1", "1"}, new String[]{"1", "9"},
                new String[]{"", "1"}, new String[]{"1", ""})
                .map(values -> Arguments.of((Object) values));
    }

    @ParameterizedTest
    @MethodSource("duplicateFilters")
    void repeatedGovernorateFiltersAreRejectedEvenIfValuesMatch(String[] values) throws Exception {
        mvc.perform(get(PUBLIC_LA).param("governorateId", values)).andExpect(status().isBadRequest());
        verifyNoCatalogueAccess();
    }

    static Stream<Arguments> unexpectedParameters() {
        return Stream.of(PUBLIC_GOV, PUBLIC_LA).flatMap(path ->
                Stream.of("page", "size", "search", "status", "sortBy", "sortDir", "code", "authorityType",
                                "GovernorateId", "unexpected")
                        .flatMap(name -> Stream.of("", "supplied")
                                .map(value -> Arguments.of(path, name, value))));
    }

    @ParameterizedTest
    @MethodSource("unexpectedParameters")
    void unknownQueryNamesAreRejectedBeforeEitherCatalogueQuery(String path, String name, String value)
            throws Exception {
        mvc.perform(get(path).param(name, value)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value(path));
        verifyNoCatalogueAccess();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1", "0", "abc"})
    void governorateCollectionRejectsTheAuthorityOnlyFilter(String value) throws Exception {
        mvc.perform(get(PUBLIC_GOV).param("governorateId", value)).andExpect(status().isBadRequest());
        verifyNoCatalogueAccess();
    }

    static Stream<Arguments> publicRoles() {
        return Stream.of("VIEWER", "EDITOR", "ADMIN", "NONE", "UNRELATED")
                .flatMap(role -> Stream.of("GOV", "LA", "LA_FILTER")
                        .map(operation -> Arguments.of(role, operation)));
    }

    @ParameterizedTest
    @MethodSource("publicRoles")
    void allValidBearerRolesReceiveOnlyTheSamePublicCatalogueProjection(String role, String operation)
            throws Exception {
        authenticate(role);
        stubPublicCatalogue(operation);

        var result = mvc.perform(publicRequest(operation).header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
        result.andExpect(content().json("[" + (operation.equals("GOV") ? PUBLIC_GOV_JSON : PUBLIC_LA_JSON) + "]",
                JsonCompareMode.STRICT));
        if (operation.equals("GOV")) result.andExpect(jsonPath("$[0].code").doesNotHaveJsonPath());
        else result.andExpect(jsonPath("$[0].authorityType").doesNotHaveJsonPath())
                .andExpect(jsonPath("$[0].governorate").doesNotHaveJsonPath());
        verifyPublicCatalogue(operation);
        verifyNoInteractions(governorateService, localAuthorityService);
        verifyBearerAuthentication();
    }

    @ParameterizedTest
    @ValueSource(strings = {PUBLIC_GOV, PUBLIC_LA})
    void invalidBearerCannotFallBackToAnonymousCatalogueAccess(String path) throws Exception {
        when(decoder.decode("bad")).thenThrow(new BadJwtException("Invalid token"));

        mvc.perform(get(path).header("Authorization", "Bearer bad")).andExpect(status().isUnauthorized());
        verify(decoder).decode("bad");
        verifyNoInteractions(governorates, authorities, governorateService, localAuthorityService, users);
    }

    static Stream<Arguments> unavailableUsers() {
        return Stream.of(PUBLIC_GOV, PUBLIC_LA).flatMap(path ->
                Stream.of(false, true).map(disabled -> Arguments.of(path, disabled)));
    }

    @ParameterizedTest
    @MethodSource("unavailableUsers")
    void missingOrDisabledBearerUsersCannotFallBackToAnonymousAccess(String path, boolean disabled) throws Exception {
        stubDecoder();
        if (disabled) when(users.loadUserByUsername("tester")).thenReturn(User.withUsername("tester")
                .password("unused").roles("ADMIN").disabled(true).build());
        else when(users.loadUserByUsername("tester")).thenThrow(new UsernameNotFoundException("Missing user"));

        mvc.perform(get(path).header("Authorization", "Bearer token")).andExpect(status().isUnauthorized());
        verifyBearerAuthentication();
        verifyNoInteractions(governorates, authorities, governorateService, localAuthorityService);
    }

    static Stream<Arguments> editorialRoles() {
        return Stream.of("VIEWER", "EDITOR", "ADMIN", "NONE", "UNRELATED").flatMap(role ->
                Stream.of("GOV", "LA").flatMap(catalogue ->
                        (catalogue.equals("GOV") ? Stream.of("ALL", "ID", "POST", "PUT", "DELETE")
                                : Stream.of("ALL", "FILTERED", "ID", "POST", "PUT", "DELETE"))
                                .map(operation -> Arguments.of(catalogue, role, operation))));
    }

    @ParameterizedTest
    @MethodSource("editorialRoles")
    void existingEditorialCatalogueRoleMatrixAndFullResponsesRemainUnchanged(
            String catalogue, String role, String operation) throws Exception {
        authenticate(role);
        boolean read = List.of("ALL", "FILTERED", "ID").contains(operation);
        boolean allowed = role.equals("ADMIN") || role.equals("EDITOR") && !operation.equals("DELETE")
                || role.equals("VIEWER") && read;
        if (allowed) stubEditorial(catalogue, operation);

        var result = mvc.perform(editorialRequest(catalogue, operation).header("Authorization", "Bearer token"));
        if (!allowed) {
            result.andExpect(status().isForbidden());
            verifyNoInteractions(governorateService, localAuthorityService);
        } else {
            result.andExpect(status().is(operation.equals("POST") ? 201 : operation.equals("DELETE") ? 204 : 200));
            if (operation.equals("DELETE")) result.andExpect(content().string(""));
            else {
                String expected = catalogue.equals("GOV") ? GOV_EDITORIAL_JSON : LA_EDITORIAL_JSON;
                if (operation.equals("ALL") || operation.equals("FILTERED")) expected = "[" + expected + "]";
                result.andExpect(content().json(expected, JsonCompareMode.STRICT));
            }
            verifyEditorial(catalogue, operation);
        }
        verifyNoInteractions(governorates, authorities);
        verifyBearerAuthentication();
    }

    static Stream<Arguments> protectedAnonymousRequests() {
        var arguments = Stream.<Arguments>builder();
        for (String path : List.of(PUBLIC_GOV, PUBLIC_LA)) {
            for (String method : List.of("POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"))
                arguments.add(Arguments.of(method, path));
            arguments.add(Arguments.of("GET", path + "/1"));
            arguments.add(Arguments.of("GET", path + "/1/extra"));
        }
        for (String path : List.of(PRIVATE_GOV, PRIVATE_GOV + "/1", PRIVATE_LA, PRIVATE_LA + "/1",
                PUBLIC_GOV + "/1/local-authorities", PUBLIC_LA + "/1/villages", "/api/v1/public/unknown",
                "/api/v1/sources", "/api/v1/verifications", "/api/v1/data-conflicts", "/api/v1/audit-logs/entity",
                "/api/v1/geocoding/search", "/api/v1/population-records", "/api/v1/auth/me"))
            arguments.add(Arguments.of("GET", path));
        for (String base : List.of(PRIVATE_GOV, PRIVATE_LA)) {
            arguments.add(Arguments.of("POST", base));
            arguments.add(Arguments.of("PUT", base + "/1"));
            arguments.add(Arguments.of("DELETE", base + "/1"));
        }
        return arguments.build();
    }

    @ParameterizedTest
    @MethodSource("protectedAnonymousRequests")
    void anonymousCataloguePermissionDoesNotOpenOtherMethodsDetailsNestedOrProtectedRoutes(
            String method, String path) throws Exception {
        // Neighboring controllers outside this slice are checked at the filter boundary only.
        mvc.perform(request(HttpMethod.valueOf(method), path).contentType(MediaType.APPLICATION_JSON)
                        .content(GOV_WRITE_JSON)).andExpect(status().isUnauthorized());
        verifyNoCatalogueAccess();
    }

    @Test
    void sliceUsesTheRealPublicServiceMockRepositoriesAndNoPersistenceOrMigrationInfrastructure() {
        assertFalse(mockingDetails(context.getBean(PublicGeographicCatalogueService.class)).isMock());
        assertSame(governorates, context.getBean(GovernorateRepository.class));
        assertSame(authorities, context.getBean(LocalAuthorityRepository.class));
        assertTrue(mockingDetails(governorates).isMock());
        assertTrue(mockingDetails(authorities).isMock());
        assertTrue(context.getBeansOfType(DataSource.class).isEmpty());
        assertTrue(context.getBeansOfType(jakarta.persistence.EntityManagerFactory.class).isEmpty());
        assertTrue(context.getBeansOfType(org.flywaydb.core.Flyway.class).isEmpty());
        assertFalse(context.containsBean("flywayInitializer"));
        assertFalse(context.containsBean("liquibase"));
        try {
            Class<?> type = Class.forName("liquibase.integration.spring.SpringLiquibase", false,
                    getClass().getClassLoader());
            assertEquals(0, context.getBeanNamesForType(type, false, false).length);
        } catch (ClassNotFoundException absentOptionalDependency) {
            // No optional Liquibase compile-time dependency is introduced for this absence assertion.
        }
    }

    private Governorate governorate(long id, String nameAr, String nameEn) {
        var governorate = new Governorate();
        governorate.setId(id);
        governorate.setNameAr(nameAr);
        governorate.setNameEn(nameEn);
        governorate.setCode("EDITORIAL_CODE");
        return governorate;
    }

    private LocalAuthority authority(long id, long governorateId, String nameAr, String nameEn) {
        var authority = new LocalAuthority();
        authority.setId(id);
        authority.setGovernorate(governorate(governorateId, "Governorate", "Governorate EN"));
        authority.setNameAr(nameAr);
        authority.setNameEn(nameEn);
        authority.setAuthorityType(LocalAuthorityType.MUNICIPALITY);
        return authority;
    }

    private void verifyNoCatalogueAccess() {
        verifyNoInteractions(governorates, authorities, governorateService, localAuthorityService, decoder, users);
    }

    private MockHttpServletRequestBuilder publicRequest(String operation) {
        var builder = get(operation.equals("GOV") ? PUBLIC_GOV : PUBLIC_LA);
        return operation.equals("LA_FILTER") ? builder.param("governorateId", "1") : builder;
    }

    private void stubPublicCatalogue(String operation) {
        switch (operation) {
            case "GOV" -> when(governorates.findAll(ID_SORT))
                    .thenReturn(List.of(governorate(1L, "Governorate", "Governorate EN")));
            case "LA" -> when(authorities.findAll(ID_SORT))
                    .thenReturn(List.of(authority(2L, 1L, "Council", "Council EN")));
            case "LA_FILTER" -> when(authorities.findByGovernorate_Id(1L, ID_SORT))
                    .thenReturn(List.of(authority(2L, 1L, "Council", "Council EN")));
            default -> throw new IllegalArgumentException("Unknown test operation");
        }
    }

    private void verifyPublicCatalogue(String operation) {
        switch (operation) {
            case "GOV" -> verify(governorates).findAll(ID_SORT);
            case "LA" -> verify(authorities).findAll(ID_SORT);
            case "LA_FILTER" -> verify(authorities).findByGovernorate_Id(1L, ID_SORT);
            default -> throw new IllegalArgumentException("Unknown test operation");
        }
        verifyNoMoreInteractions(governorates, authorities);
    }

    private MockHttpServletRequestBuilder editorialRequest(String catalogue, String operation) {
        String base = catalogue.equals("GOV") ? PRIVATE_GOV : PRIVATE_LA;
        String path = List.of("ID", "PUT", "DELETE").contains(operation) ? base + "/1" : base;
        String method = List.of("POST", "PUT", "DELETE").contains(operation) ? operation : "GET";
        var builder = request(HttpMethod.valueOf(method), path);
        if (operation.equals("FILTERED")) builder.param("governorateId", "1");
        if (operation.equals("POST") || operation.equals("PUT")) builder.contentType(MediaType.APPLICATION_JSON)
                .content(catalogue.equals("GOV") ? GOV_WRITE_JSON : LA_WRITE_JSON);
        return builder;
    }

    private void stubEditorial(String catalogue, String operation) {
        if (catalogue.equals("GOV")) {
            switch (operation) {
                case "ALL" -> when(governorateService.getAll()).thenReturn(List.of(GOV_RESPONSE));
                case "ID" -> when(governorateService.getById(1L)).thenReturn(GOV_RESPONSE);
                case "POST" -> when(governorateService.create(GOV_REQUEST)).thenReturn(GOV_RESPONSE);
                case "PUT" -> when(governorateService.update(1L, GOV_REQUEST)).thenReturn(GOV_RESPONSE);
            }
        } else {
            switch (operation) {
                case "ALL" -> when(localAuthorityService.getAll()).thenReturn(List.of(LA_RESPONSE));
                case "FILTERED" -> when(localAuthorityService.getByGovernorate(1L)).thenReturn(List.of(LA_RESPONSE));
                case "ID" -> when(localAuthorityService.getById(1L)).thenReturn(LA_RESPONSE);
                case "POST" -> when(localAuthorityService.create(LA_REQUEST)).thenReturn(LA_RESPONSE);
                case "PUT" -> when(localAuthorityService.update(1L, LA_REQUEST)).thenReturn(LA_RESPONSE);
            }
        }
    }

    private void verifyEditorial(String catalogue, String operation) {
        if (catalogue.equals("GOV")) {
            switch (operation) {
                case "ALL" -> verify(governorateService).getAll();
                case "ID" -> verify(governorateService).getById(1L);
                case "POST" -> verify(governorateService).create(GOV_REQUEST);
                case "PUT" -> verify(governorateService).update(1L, GOV_REQUEST);
                case "DELETE" -> verify(governorateService).delete(1L);
            }
        } else {
            switch (operation) {
                case "ALL" -> verify(localAuthorityService).getAll();
                case "FILTERED" -> verify(localAuthorityService).getByGovernorate(1L);
                case "ID" -> verify(localAuthorityService).getById(1L);
                case "POST" -> verify(localAuthorityService).create(LA_REQUEST);
                case "PUT" -> verify(localAuthorityService).update(1L, LA_REQUEST);
                case "DELETE" -> verify(localAuthorityService).delete(1L);
            }
        }
        verifyNoMoreInteractions(governorateService, localAuthorityService);
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

    private void verifyBearerAuthentication() {
        verify(decoder).decode("token");
        verify(users).loadUserByUsername("tester");
    }
}
