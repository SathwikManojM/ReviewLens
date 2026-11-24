package com.reviewlens.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** Structured JSON errors: {status, code, message, timestamp}. Internal details are logged, never returned. */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, Object>> api(ApiException e) {
        return body(e.getStatus().value(), e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage()).collect(Collectors.joining("; "));
        return body(400, "VALIDATION_FAILED", msg.isBlank() ? "Invalid request" : msg);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Map<String, Object>> denied(AccessDeniedException e) {
        return body(403, "FORBIDDEN", "You do not have permission to perform this action");
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Map<String, Object>> unauthenticated(AuthenticationException e) {
        return body(401, "UNAUTHENTICATED", "Authentication required");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String, Object>> tooLarge(MaxUploadSizeExceededException e) {
        return body(413, "FILE_TOO_LARGE", "File is too large (hard limit 300 MB)");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> other(Exception e) {
        if (e instanceof ErrorResponse er) {         // standard Spring MVC errors (400, 404, 405, 415 ...)
            int status = er.getStatusCode().value();
            HttpStatus hs = HttpStatus.resolve(status);
            String detail = er.getBody().getDetail();
            return body(status, hs == null ? "ERROR" : hs.name(), detail == null ? "Request could not be processed" : detail);
        }
        log.error("Unhandled error", e);
        return body(500, "INTERNAL_ERROR", "Unexpected server error. Please try again.");
    }

    private ResponseEntity<Map<String, Object>> body(int status, String code, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", status);
        m.put("code", code);
        m.put("message", message == null ? "" : message);
        m.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(status).body(m);
    }
}
