package com.smartnotes_ai.smartnotes_ai.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handle(Exception ex) {
        log.error("Error: ", ex);
        return ResponseEntity.status(500).body(Map.of(
                "error", ex.getClass().getSimpleName(),
                "message", ex.getMessage() == null ? "Unknown error" : ex.getMessage()
        ));
    }
}