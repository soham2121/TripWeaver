package com.tripweaver.common;

import java.util.Map;
import org.springframework.http.HttpStatus;

/** Business error carrying an HTTP status, stable error code and safe details. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;
    private final Map<String, Object> details;

    public ApiException(HttpStatus status, String errorCode, String message) {
        this(status, errorCode, message, Map.of());
    }

    public ApiException(HttpStatus status, String errorCode, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.details = details;
    }

    public static ApiException notFound(String entity, Object id) {
        return new ApiException(
                HttpStatus.NOT_FOUND, "NOT_FOUND", entity + " not found", Map.of("id", String.valueOf(id)));
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permissions");
    }

    public HttpStatus status() {
        return status;
    }

    public String errorCode() {
        return errorCode;
    }

    public Map<String, Object> details() {
        return details;
    }
}
