package com.qaryati.heritage.controller;

import com.qaryati.common.response.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.time.Instant;

/** Heritage Site-scoped safe responses; bearer authentication failures remain in the security filter chain. */
@RestControllerAdvice(assignableTypes = HeritageSiteController.class)
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public class HeritageSiteExceptionHandler {
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> failure(Exception exception, HttpServletRequest request) {
        int status = 500;
        String message = "Unexpected error";
        if (exception instanceof org.springframework.security.core.AuthenticationException) {
            status = 401; message = "Authentication required";
        } else if (exception instanceof org.springframework.security.access.AccessDeniedException) {
            status = 403; message = "Insufficient permissions";
        } else if (exception instanceof org.springframework.web.server.ResponseStatusException response) {
            status = response.getStatusCode().value(); message = response.getReason();
        } else if (exception instanceof org.springframework.web.bind.MethodArgumentNotValidException
                || exception instanceof org.springframework.web.bind.ServletRequestBindingException) {
            status = 400; message = "Invalid heritage site request";
        }
        var httpStatus = org.springframework.http.HttpStatus.valueOf(status);
        return ResponseEntity.status(status).body(new ApiErrorResponse(Instant.now(), status,
                httpStatus.getReasonPhrase(), message, request.getRequestURI()));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiErrorResponse> malformedRequest(Exception exception, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(new ApiErrorResponse(Instant.now(), 400, "Bad Request",
                "Invalid heritage site request: check field names, types and coordinates", request.getRequestURI()));
    }
}

