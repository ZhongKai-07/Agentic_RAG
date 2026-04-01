package com.rag.adapter.inbound.rest;

import com.rag.domain.knowledge.exception.KnowledgeBaseEmptyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("Data integrity violation: {}", e.getMostSpecificCause().getMessage());
        String message = "Data conflict";
        if (e.getMessage() != null && e.getMessage().contains("unique constraint")) {
            message = "A record with the same unique value already exists";
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody("DUPLICATE", message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody("NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleBadState(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody("CONFLICT", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        var errors = e.getBindingResult().getFieldErrors().stream()
            .map(f -> f.getField() + ": " + f.getDefaultMessage()).toList();
        Map<String, Object> body = errorBody("VALIDATION_ERROR", "Validation failed");
        body.put("messages", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxSize(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(errorBody("FILE_TOO_LARGE", "Maximum upload size exceeded (100MB limit)"));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, Object>> handleSecurity(SecurityException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBody("FORBIDDEN", e.getMessage()));
    }

    @ExceptionHandler(KnowledgeBaseEmptyException.class)
    public ResponseEntity<Map<String, Object>> handleKnowledgeBaseEmpty(KnowledgeBaseEmptyException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(errorBody("KNOWLEDGE_BASE_EMPTY", e.getMessage()));
    }

    private Map<String, Object> errorBody(String error, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", error);
        body.put("message", message);
        body.put("requestId", MDC.get("correlationId"));
        body.put("timestamp", Instant.now().toString());
        return body;
    }
}
