
package com.qaryati.common.exception;

import com.qaryati.common.response.ApiErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler();

    @Test
    void notFoundReturns404() {
        MockHttpServletRequest request =
                new MockHttpServletRequest();
        request.setRequestURI("/api/v1/governorates/99");

        ResponseEntity<ApiErrorResponse> response =
                handler.handleNotFound(
                        new ResourceNotFoundException("Governorate", 99L),
                        request
                );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().status());
        assertEquals(
                "Governorate with id 99 not found",
                response.getBody().message()
        );
        assertEquals(
                "/api/v1/governorates/99",
                response.getBody().path()
        );
        assertNotNull(response.getBody().timestamp());
    }

    @Test
    void duplicateReturns409() {
        MockHttpServletRequest request =
                new MockHttpServletRequest();
        request.setRequestURI("/api/v1/governorates");

        ResponseEntity<ApiErrorResponse> response =
                handler.handleDuplicate(
                        new DuplicateResourceException(
                                "Governorate Arabic name already exists"
                        ),
                        request
                );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().status());
        assertEquals(
                "Governorate Arabic name already exists",
                response.getBody().message()
        );
    }

    @Test
    void validationReturns400() {
        MockHttpServletRequest request =
                new MockHttpServletRequest();
        request.setRequestURI("/api/v1/governorates");

        org.springframework.validation.BindingResult bindingResult =
                new org.springframework.validation.BeanPropertyBindingResult(
                        new Object(), "governorateRequest");

        bindingResult.addError(
                new org.springframework.validation.FieldError(
                        "governorateRequest",
                        "nameAr",
                        "Arabic name is required"
                )
        );

        org.springframework.web.bind.MethodArgumentNotValidException exception =
                new org.springframework.web.bind.MethodArgumentNotValidException(
                        null, bindingResult
                );

        ResponseEntity<ApiErrorResponse> response =
                handler.handleValidation(exception, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().status());
        assertEquals(
                "nameAr: Arabic name is required",
                response.getBody().message()
        );
    }


}
