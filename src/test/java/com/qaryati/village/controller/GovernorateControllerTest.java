package com.qaryati.village.controller;

import com.qaryati.common.exception.DuplicateResourceException;
import com.qaryati.common.exception.GlobalExceptionHandler;
import com.qaryati.common.exception.ResourceNotFoundException;
import com.qaryati.village.dto.GovernorateRequest;
import com.qaryati.village.dto.GovernorateResponse;
import com.qaryati.village.service.GovernorateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GovernorateController.class)
@Import(GlobalExceptionHandler.class)
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

    @Test
    @WithMockUser
    void createReturnsCreatedAndGovernorateResponse() throws Exception {
        when(service.create(REQUEST)).thenReturn(RESPONSE);

        mockMvc.perform(post(BASE_PATH).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST_JSON))
                .andExpect(status().isCreated())
                .andExpect(content().json("""
                        {"id":1,"nameAr":"نابلس","nameEn":"Nablus","code":"NABLUS"}
                        """));

        verify(service).create(REQUEST);
    }

    @Test
    @WithMockUser
    void getAllReturnsMappedGovernorateResponses() throws Exception {
        when(service.getAll()).thenReturn(List.of(RESPONSE));

        mockMvc.perform(get(BASE_PATH))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [{"id":1,"nameAr":"نابلس","nameEn":"Nablus","code":"NABLUS"}]
                        """));

        verify(service).getAll();
    }

    @Test
    @WithMockUser
    void getAllReturnsEmptyArrayWhenNoGovernoratesExist() throws Exception {
        when(service.getAll()).thenReturn(List.of());

        mockMvc.perform(get(BASE_PATH))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(service).getAll();
    }

    @Test
    @WithMockUser
    void getByIdReturnsGovernorateResponse() throws Exception {
        when(service.getById(1L)).thenReturn(RESPONSE);

        mockMvc.perform(get(BASE_PATH + "/1"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"id":1,"nameAr":"نابلس","nameEn":"Nablus","code":"NABLUS"}
                        """));

        verify(service).getById(1L);
    }

    @Test
    @WithMockUser
    void updateReturnsUpdatedGovernorateResponse() throws Exception {
        GovernorateRequest request = new GovernorateRequest("جنين", "Jenin", "JENIN");
        when(service.update(1L, request))
                .thenReturn(new GovernorateResponse(1L, "جنين", "Jenin", "JENIN"));

        mockMvc.perform(put(BASE_PATH + "/1").with(csrf())
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
    @WithMockUser
    void deleteReturnsNoContent() throws Exception {
        mockMvc.perform(delete(BASE_PATH + "/1").with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(service).delete(1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT"})
    @WithMockUser
    void writeRejectsBlankArabicNameWithCommonValidationError(String method) throws Exception {
        String path = method.equals("POST") ? BASE_PATH : BASE_PATH + "/1";

        expectError(mockMvc.perform(request(HttpMethod.valueOf(method), path).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nameAr":" ","nameEn":"Nablus","code":"NABLUS"}
                                """)),
                400, "Bad Request", "nameAr: Arabic name is required", path);

        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"nameAr", "nameEn", "code"})
    @WithMockUser
    void createRejectsFieldsExceedingMaximumLength(String field) throws Exception {
        int maxLength = field.equals("code") ? 32 : 120;
        String oversized = "x".repeat(maxLength + 1);
        String json = """
                {"nameAr":"%s","nameEn":"%s","code":"%s"}
                """.formatted(
                field.equals("nameAr") ? oversized : "نابلس",
                field.equals("nameEn") ? oversized : "Nablus",
                field.equals("code") ? oversized : "NABLUS");

        mockMvc.perform(post(BASE_PATH).with(csrf())
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
    @WithMockUser
    void nonexistentGovernorateReturnsCommonNotFoundError(String method) throws Exception {
        switch (method) {
            case "GET" -> when(service.getById(99L))
                    .thenThrow(new ResourceNotFoundException("Governorate", 99L));
            case "PUT" -> when(service.update(99L, REQUEST))
                    .thenThrow(new ResourceNotFoundException("Governorate", 99L));
            case "DELETE" -> doThrow(new ResourceNotFoundException("Governorate", 99L))
                    .when(service).delete(99L);
        }
        String path = BASE_PATH + "/99";

        expectError(mockMvc.perform(request(HttpMethod.valueOf(method), path).with(csrf())
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
    @WithMockUser
    void duplicateOrReferencedGovernorateReturnsCommonConflictError(String method) throws Exception {
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

        expectError(mockMvc.perform(request(HttpMethod.valueOf(method), path).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST_JSON)),
                409, "Conflict", message, path);

        switch (method) {
            case "POST" -> verify(service).create(REQUEST);
            case "PUT" -> verify(service).update(1L, REQUEST);
            case "DELETE" -> verify(service).delete(1L);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST", "PUT", "DELETE"})
    void unauthenticatedRequestsAreRejected(String method) throws Exception {
        String path = method.equals("PUT") || method.equals("DELETE")
                ? BASE_PATH + "/1" : BASE_PATH;

        mockMvc.perform(request(HttpMethod.valueOf(method), path).with(csrf())
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST_JSON))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE"})
    @WithMockUser
    void writeWithoutCsrfTokenIsForbidden(String method) throws Exception {
        String path = method.equals("POST") ? BASE_PATH : BASE_PATH + "/1";

        mockMvc.perform(request(HttpMethod.valueOf(method), path)
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST_JSON))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
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