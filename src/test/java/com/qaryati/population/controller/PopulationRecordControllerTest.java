package com.qaryati.population.controller;

import com.qaryati.common.exception.GlobalExceptionHandler;
import com.qaryati.population.entity.Methodology;
import com.qaryati.population.entity.PopulationRecord;
import com.qaryati.population.entity.VerificationStatus;
import com.qaryati.population.service.PopulationRecordService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Production stateless bearer security, real service, database-free provider doubles. */
@WebMvcTest(PopulationRecordController.class)
@Import({PopulationRecordService.class, GlobalExceptionHandler.class, PopulationRecordExceptionHandler.class,
        com.qaryati.population.mapper.PopulationRecordMapper.class,
        com.qaryati.security.config.SecurityConfig.class,
        com.qaryati.security.config.JwtAuthenticationConverterConfig.class})
class PopulationRecordControllerTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.qaryati.population.repository.PopulationRecordRepository repository;
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
    private static final String BASE = "/api/v1/population-records";
    private static final String JSON = """
            {"villageId":1,"referenceYear":2020,"populationCount":100,"methodology":"CENSUS","sourceId":2}
            """;
    private static final Sort LIST_SORT = Sort.by(Sort.Order.desc("referenceYear"), Sort.Order.asc("id"));
    @Autowired private MockMvc mvc;
    @Autowired private ApplicationContext context;

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
        for (String json : java.util.List.of("{}", "{", JSON.replace("2020", "1799"),
                JSON.replace("2020", "2201"), JSON.replace("100", "-1"),
                JSON.replace("CENSUS", "INVALID"), JSON.replace("CENSUS", "census"),
                JSON.replace("\"sourceId\":2", "\"sourceId\":null"),
                JSON.replace("\"villageId\":1", "\"villageId\":0"),
                JSON.replace("}", ",\"referenceDate\":\"2021-01-01\"}"),
                JSON.replace("}", ",\"referenceDate\":\"2020-02-30\"}"),
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
        var record = new com.qaryati.population.entity.PopulationRecord(1L, 2020, null, 100,
                com.qaryati.population.entity.Methodology.CENSUS, 2L, null);
        org.springframework.test.util.ReflectionTestUtils.setField(record, "id", 1L);
        org.mockito.Mockito.when(villages.findById(1L)).thenReturn(java.util.Optional.of(
                new com.qaryati.village.api.VillageLookupResult(1L, com.qaryati.village.api.VillageLookupStatus.VERIFIED, false)));
        org.mockito.Mockito.when(sources.existsById(2L)).thenReturn(true);
        org.mockito.Mockito.when(repository.saveAndFlush(org.mockito.ArgumentMatchers.any())).thenAnswer(call -> {
            var saved = (com.qaryati.population.entity.PopulationRecord) call.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(saved, "id", 1L); return saved;
        });
        org.mockito.Mockito.when(repository.findById(1L)).thenReturn(java.util.Optional.of(record));
        org.mockito.Mockito.when(repository.findByIdForUpdate(1L)).thenReturn(java.util.Optional.of(record));
        org.mockito.Mockito.when(repository.findAll(org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
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
        org.mockito.Mockito.when(repository.findByVerificationStatus(
                org.mockito.ArgumentMatchers.eq(com.qaryati.population.entity.VerificationStatus.VERIFIED),
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
        var record = new com.qaryati.population.entity.PopulationRecord(1L, 2020, null, 100,
                com.qaryati.population.entity.Methodology.CENSUS, 2L, null);
        org.springframework.test.util.ReflectionTestUtils.setField(record, "verificationStatus",
                com.qaryati.population.entity.VerificationStatus.VERIFIED);
        org.mockito.Mockito.when(repository.findByIdForUpdate(1L)).thenReturn(java.util.Optional.of(record));
        mvc.perform(delete(BASE + "/1")).andExpect(status().isConflict());
    }

    @Test
    void filteredViewerPageUsesTheScopedVerifiedQueryBeforeMappingAndCounting() throws Exception {
        authenticateFilterReader("VIEWER");
        var records = List.of(observation(701L, 7L, 2022, VerificationStatus.VERIFIED),
                observation(702L, 7L, 2020, VerificationStatus.VERIFIED));
        when(repository.findByVillageIdAndVerificationStatus(eq(7L), eq(VerificationStatus.VERIFIED),
                any(Pageable.class))).thenReturn(new PageImpl<>(records, PageRequest.of(0, 20, LIST_SORT), 42));

        mvc.perform(get(BASE).param("villageId", "7").header("Authorization", "Bearer filter-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(5)))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].id", contains(701, 702)))
                .andExpect(jsonPath("$.content[*].villageId", everyItem(is(7))))
                .andExpect(jsonPath("$.content[*].verificationStatus", everyItem(is("VERIFIED"))))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(42))
                .andExpect(jsonPath("$.totalPages").value(3));

        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByVillageIdAndVerificationStatus(eq(7L), eq(VerificationStatus.VERIFIED),
                pageable.capture());
        assertPageable(pageable.getValue(), 0, 20);
        verifyNoMoreInteractions(repository);
        verifyReadDependenciesUntouched();
        verifyFilterBearerAuthentication();
    }

    @ParameterizedTest
    @ValueSource(strings = {"EDITOR", "ADMIN"})
    void filteredPrivilegedPageIncludesAllStatusesFromOnlyTheScopedQuery(String role) throws Exception {
        authenticateFilterReader(role);
        var records = List.of(observation(701L, 7L, 2023, VerificationStatus.DRAFT),
                observation(702L, 7L, 2022, VerificationStatus.UNDER_REVIEW),
                observation(703L, 7L, 2021, VerificationStatus.VERIFIED),
                observation(704L, 7L, 2020, VerificationStatus.REJECTED),
                observation(705L, 7L, 2019, VerificationStatus.DISPUTED));
        when(repository.findByVillageId(eq(7L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(records, PageRequest.of(0, 20, LIST_SORT), 45));

        mvc.perform(get(BASE).param("villageId", "7").header("Authorization", "Bearer filter-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(5)))
                .andExpect(jsonPath("$.content", hasSize(5)))
                .andExpect(jsonPath("$.content[*].villageId", everyItem(is(7))))
                .andExpect(jsonPath("$.content[*].verificationStatus",
                        contains("DRAFT", "UNDER_REVIEW", "VERIFIED", "REJECTED", "DISPUTED")))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(45))
                .andExpect(jsonPath("$.totalPages").value(3));

        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByVillageId(eq(7L), pageable.capture());
        assertPageable(pageable.getValue(), 0, 20);
        verifyNoMoreInteractions(repository);
        verifyReadDependenciesUntouched();
        verifyFilterBearerAuthentication();
    }

    @ParameterizedTest
    @ValueSource(strings = {"VIEWER", "EDITOR", "ADMIN"})
    void unknownPositiveVillageFilterReturnsAnEmptyPageWithoutAParentLookup(String role) throws Exception {
        authenticateFilterReader(role);
        Page<PopulationRecord> empty = Page.empty(PageRequest.of(2, 10, LIST_SORT));
        if (role.equals("VIEWER")) {
            when(repository.findByVillageIdAndVerificationStatus(eq(999L), eq(VerificationStatus.VERIFIED),
                    any(Pageable.class))).thenReturn(empty);
        } else {
            when(repository.findByVillageId(eq(999L), any(Pageable.class))).thenReturn(empty);
        }

        mvc.perform(get(BASE).param("villageId", "999").param("page", "2").param("size", "10")
                        .header("Authorization", "Bearer filter-token"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"content":[],"page":2,"size":10,"totalElements":0,"totalPages":0}
                        """, org.springframework.test.json.JsonCompareMode.STRICT));

        verifyScopedQuery(role, 999L, 2, 10);
        verifyReadDependenciesUntouched();
        verifyFilterBearerAuthentication();
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
    void nonpositiveVillageFilterReturns400WithoutRepositoryAccess(Long villageId) throws Exception {
        authenticateFilterReader("VIEWER");

        mvc.perform(get(BASE).param("villageId", villageId.toString())
                        .header("Authorization", "Bearer filter-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value(BASE));

        verifyNoInteractions(repository);
        verifyReadDependenciesUntouched();
        verifyFilterBearerAuthentication();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1.5", "1e3", "9223372036854775808", "-9223372036854775809"})
    void malformedOrOverflowingVillageFilterReturns400BeforeRepositoryAccess(String villageId) throws Exception {
        authenticateFilterReader("VIEWER");

        mvc.perform(get(BASE).param("villageId", villageId).header("Authorization", "Bearer filter-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value(BASE));

        verifyNoInteractions(repository);
        verifyReadDependenciesUntouched();
        verifyFilterBearerAuthentication();
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,-1", "0,101"})
    void villageFilterDoesNotRelaxExistingPaginationValidation(int page, int size) throws Exception {
        authenticateFilterReader("ADMIN");

        mvc.perform(get(BASE).param("villageId", "7").param("page", "" + page).param("size", "" + size)
                        .header("Authorization", "Bearer filter-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(repository);
        verifyReadDependenciesUntouched();
        verifyFilterBearerAuthentication();
    }

    @ParameterizedTest
    @CsvSource({"VIEWER,0,1", "VIEWER,2,10", "EDITOR,1,20", "ADMIN,0,100"})
    void filteredPaginationKeepsRequestedBoundsCountsAndYearThenIdSort(String role, int page, int size)
            throws Exception {
        authenticateFilterReader(role);
        var result = new PageImpl<>(List.of(observation(701L, 7L, 2020, VerificationStatus.VERIFIED)),
                PageRequest.of(page, size, LIST_SORT), 151);
        if (role.equals("VIEWER")) {
            when(repository.findByVillageIdAndVerificationStatus(eq(7L), eq(VerificationStatus.VERIFIED),
                    any(Pageable.class))).thenReturn(result);
        } else {
            when(repository.findByVillageId(eq(7L), any(Pageable.class))).thenReturn(result);
        }

        mvc.perform(get(BASE).param("villageId", "7").param("page", "" + page).param("size", "" + size)
                        .header("Authorization", "Bearer filter-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(5)))
                .andExpect(jsonPath("$.content[0].villageId").value(7))
                .andExpect(jsonPath("$.page").value(page))
                .andExpect(jsonPath("$.size").value(size))
                .andExpect(jsonPath("$.totalElements").value(151))
                .andExpect(jsonPath("$.totalPages").value((151 + size - 1) / size));

        verifyScopedQuery(role, 7L, page, size);
        verifyReadDependenciesUntouched();
        verifyFilterBearerAuthentication();
    }

    @ParameterizedTest
    @ValueSource(strings = {"VIEWER", "EDITOR", "ADMIN"})
    void absentVillageFilterPreservesTheOriginalRepositoryQueryAndPaginationDefaults(String role) throws Exception {
        authenticateFilterReader(role);
        var verification = role.equals("VIEWER") ? VerificationStatus.VERIFIED : VerificationStatus.DRAFT;
        var result = new PageImpl<>(List.of(observation(441L, 44L, 2020, verification)),
                PageRequest.of(0, 20, LIST_SORT), 51);
        if (role.equals("VIEWER")) {
            when(repository.findByVerificationStatus(eq(VerificationStatus.VERIFIED), any(Pageable.class)))
                    .thenReturn(result);
        } else {
            when(repository.findAll(any(Pageable.class))).thenReturn(result);
        }

        mvc.perform(get(BASE).header("Authorization", "Bearer filter-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(5)))
                .andExpect(jsonPath("$.content[0].villageId").value(44))
                .andExpect(jsonPath("$.content[0].verificationStatus").value(verification.name()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(51))
                .andExpect(jsonPath("$.totalPages").value(3));

        var pageable = ArgumentCaptor.forClass(Pageable.class);
        if (role.equals("VIEWER"))
            verify(repository).findByVerificationStatus(eq(VerificationStatus.VERIFIED), pageable.capture());
        else verify(repository).findAll(pageable.capture());
        assertPageable(pageable.getValue(), 0, 20);
        verifyNoMoreInteractions(repository);
        verifyReadDependenciesUntouched();
        verifyFilterBearerAuthentication();
    }

    @ParameterizedTest
    @ValueSource(strings = {"7", "0", "abc"})
    void filteredAnonymousRequestsRemainUnauthorized(String villageId) throws Exception {
        mvc.perform(get(BASE).param("villageId", villageId)).andExpect(status().isUnauthorized());
        verifyNoInteractions(repository, decoder, userDetails);
        verifyReadDependenciesUntouched();
    }

    @ParameterizedTest
    @CsvSource({"NONE,7", "UNRELATED,7", "NONE,0", "UNRELATED,-1"})
    void unsupportedRolesCannotUseVillageFilteringOrProbeValidationBeforeAuthorization(String role, String villageId)
            throws Exception {
        authenticateFilterReader(role);

        mvc.perform(get(BASE).param("villageId", villageId).header("Authorization", "Bearer filter-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.path").value(BASE));
        verifyNoInteractions(repository);
        verifyReadDependenciesUntouched();
        verifyFilterBearerAuthentication();
    }

    @Test
    void sliceContainsOnlyTheMockPopulationRepositoryAndNoPersistenceOrMigrationInfrastructure() {
        assertSame(repository, context.getBean(com.qaryati.population.repository.PopulationRecordRepository.class));
        assertTrue(mockingDetails(repository).isMock());
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
            // Liquibase need not be a compile-time dependency to assert absence from this MVC slice.
        }
    }

    private PopulationRecord observation(long id, long villageId, int year, VerificationStatus verification) {
        var record = new PopulationRecord(villageId, year, null, 100, Methodology.CENSUS, 2L, null);
        ReflectionTestUtils.setField(record, "id", id);
        ReflectionTestUtils.setField(record, "verificationStatus", verification);
        ReflectionTestUtils.setField(record, "createdAt", Instant.parse("2026-11-27T00:00:00Z"));
        ReflectionTestUtils.setField(record, "updatedAt", Instant.parse("2026-11-27T00:00:00Z"));
        return record;
    }

    private void authenticateFilterReader(String role) {
        when(decoder.decode("filter-token")).thenReturn(Jwt.withTokenValue("filter-token")
                .header("alg", "HS256").subject("member").build());
        when(userDetails.loadUserByUsername("member")).thenReturn(User.withUsername("member").password("unused")
                .roles(role.equals("NONE") ? new String[0] : new String[]{role}).build());
    }

    private void verifyFilterBearerAuthentication() {
        verify(decoder).decode("filter-token");
        verify(userDetails).loadUserByUsername("member");
    }

    private void verifyReadDependenciesUntouched() {
        verifyNoInteractions(villages, sources, audits, rateLimits);
    }

    private void verifyScopedQuery(String role, long villageId, int page, int size) {
        var pageable = ArgumentCaptor.forClass(Pageable.class);
        if (role.equals("VIEWER"))
            verify(repository).findByVillageIdAndVerificationStatus(eq(villageId), eq(VerificationStatus.VERIFIED),
                    pageable.capture());
        else verify(repository).findByVillageId(eq(villageId), pageable.capture());
        assertPageable(pageable.getValue(), page, size);
        verifyNoMoreInteractions(repository);
    }

    private void assertPageable(Pageable pageable, int page, int size) {
        assertEquals(page, pageable.getPageNumber());
        assertEquals(size, pageable.getPageSize());
        assertEquals(LIST_SORT, pageable.getSort());
    }

    private HttpMethod method(String operation) {
        return HttpMethod.valueOf(operation.equals("GET_ONE") ? "GET" : operation);
    }
}
