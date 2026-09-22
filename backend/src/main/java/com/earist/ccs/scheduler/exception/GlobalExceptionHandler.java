package com.earist.ccs.scheduler.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fieldErrors = new HashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(err -> fieldErrors.put(err.getField(), err.getDefaultMessage()));

        log.warn("Validation failed: {}", fieldErrors);
        return ResponseEntity.badRequest().body(Map.of(
                "error", "Validation failed",
                "fields", fieldErrors
        ));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("Data integrity violation: {}", e.getMessage());
        return ResponseEntity.status(409).body(Map.of(
                "error", "Duplicate or invalid data",
                "message", "This record already exists or violates a constraint"
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("Invalid argument: {}", e.getMessage());
        return ResponseEntity.status(400).body(Map.of(
                "error", "Invalid input",
                "message", e.getMessage()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(500).body(Map.of(
                "error", "Internal server error",
                "message", e.getMessage() != null ? e.getMessage() : "Unknown error"
        ));
    }
}