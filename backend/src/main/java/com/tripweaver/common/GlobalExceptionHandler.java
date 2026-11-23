package com.tripweaver.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Maps exceptions to {@link ApiError}. Never returns stack traces. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> api(ApiException e) {
        return ResponseEntity.status(e.status()).body(new ApiError(e.errorCode(), e.getMessage(), e.details()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalid(MethodArgumentNotValidException e) {
        Map<String, Object> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> fields.put(f.getField(), f.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_FAILED", "Request is invalid", fields));
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class,
        IllegalArgumentException.class
    })
    ResponseEntity<ApiError> badRequest(Exception e) {
        log.debug("Bad request: {}", e.getMessage());
        String msg = e instanceof IllegalArgumentException ? e.getMessage() : "Malformed request";
        return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", msg, Map.of()));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiError> conflict(OptimisticLockingFailureException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("VERSION_CONFLICT", "The resource was changed by someone else", Map.of()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> forbidden(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError("FORBIDDEN", "Insufficient permissions", Map.of()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> noResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("NOT_FOUND", "Resource not found", Map.of()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> method(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ApiError("METHOD_NOT_ALLOWED", "Method not allowed", Map.of()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", "Unexpected server error", Map.of()));
    }
}
