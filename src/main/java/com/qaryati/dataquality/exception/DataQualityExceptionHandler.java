
package com.qaryati.dataquality.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice(basePackages = "com.qaryati.dataquality")
public class DataQualityExceptionHandler {

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> handleConstraintViolation() {
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid request data"));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(
            NoSuchElementException ex
    ) {
        String message = ex.getMessage();

        if (message != null && message.startsWith("Source not found")) {
            message = "Source not found";
        }

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                        "error",
                        message != null ? message : "Resource not found"
                ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidData(
            IllegalArgumentException ex
    ) {
        String message = ex.getMessage();

        return ResponseEntity
                .badRequest()
                .body(Map.of(
                        "error",
                        message != null ? message : "Invalid request data"
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(
            MethodArgumentNotValidException ex
    ) {
        return ResponseEntity
                .badRequest()
                .body(Map.of(
                        "error",
                        "Invalid request data"
                ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleInvalidJson(
            HttpMessageNotReadableException ex
    ) {
        return ResponseEntity
                .badRequest()
                .body(Map.of(
                        "error",
                        "Invalid request body or source type"
                ));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex
    ) {
        return ResponseEntity
                .badRequest()
                .body(Map.of(
                        "error",
                        "Invalid parameter: " + ex.getName()
                ));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleConflict(
            IllegalStateException ex
    ) {
        String message = ex.getMessage();

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of(
                        "error",
                        message != null ? message : "Operation conflict"
                ));
    }
}
