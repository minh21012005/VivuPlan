package com.vivuplan.vivuplan_be.controller;

import com.vivuplan.vivuplan_be.exception.AiGenerationException;
import com.vivuplan.vivuplan_be.exception.BillingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
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

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntime(RuntimeException e) {
        log.error("Runtime error: {}", e.getMessage());
        HttpStatus status = e.getMessage().contains("không có quyền") || e.getMessage().contains("quyền")
                ? HttpStatus.FORBIDDEN : HttpStatus.INTERNAL_SERVER_ERROR;
        if (e.getMessage().contains("không tồn tại") || e.getMessage().contains("Không tìm thấy"))
            status = HttpStatus.NOT_FOUND;
        return ResponseEntity.status(status).body(Map.of("error", e.getMessage()));
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
