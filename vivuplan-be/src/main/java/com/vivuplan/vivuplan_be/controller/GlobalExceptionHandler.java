package com.vivuplan.vivuplan_be.controller;

import com.vivuplan.vivuplan_be.exception.AiGenerationException;
import com.vivuplan.vivuplan_be.exception.BillingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(AiGenerationException.class)
    public ResponseEntity<Map<String, String>> handleAiGeneration(AiGenerationException e) {
        log.warn("AI generation error: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(BillingException.class)
    public ResponseEntity<Map<String, String>> handleBilling(BillingException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of(
                "error", e.getMessage(),
                "code", e.getCode()
        ));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException e) {
        String message = e.getReason() != null ? e.getReason() : e.getMessage();
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", message));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntime(RuntimeException e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        log.error("Runtime error: {}", message, e);

        if (message.contains("không có quyền") || message.contains("quyền")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", message));
        }
        if (message.contains("không tồn tại") || message.contains("Không tìm thấy")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", message));
        }

        return ResponseEntity.internalServerError().body(Map.of("error", "Đã xảy ra lỗi hệ thống. Vui lòng thử lại sau."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach(err -> {
            String field = ((FieldError) err).getField();
            errors.put(field, err.getDefaultMessage());
        });
        return ResponseEntity.badRequest().body(Map.of("error", "Dữ liệu không hợp lệ", "details", errors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneral(Exception e) {
        log.error("Unexpected error: {}", e.getMessage(), e);
        return ResponseEntity.internalServerError().body(Map.of("error", "Đã xảy ra lỗi hệ thống"));
    }
}
