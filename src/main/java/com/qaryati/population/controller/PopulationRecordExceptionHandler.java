package com.qaryati.population.controller;

import com.qaryati.common.response.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.time.Instant;

/** Handles only Population MVC parsing failures; shared and security handlers remain unchanged. */
@RestControllerAdvice(assignableTypes = PopulationRecordController.class)
public class PopulationRecordExceptionHandler {
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiErrorResponse> malformedRequest(Exception exception, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(new ApiErrorResponse(Instant.now(), 400, "Bad Request",
                "Invalid population request: check field names, types, dates and methodology", request.getRequestURI()));
    }
}
