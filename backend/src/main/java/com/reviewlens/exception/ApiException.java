package com.reviewlens.exception;

import org.springframework.http.HttpStatus;

/** Expected, client-facing error with an HTTP status and a stable machine-readable code. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String message) {
        this(status, status.name(), message);
    }

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }

    public static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", what + " not found");
    }

    public static ApiException badRequest(String msg) {
        return new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", msg);
    }

    public static ApiException conflict(String code, String msg) {
        return new ApiException(HttpStatus.CONFLICT, code, msg);
    }

    public static ApiException forbidden(String code, String msg) {
        return new ApiException(HttpStatus.FORBIDDEN, code, msg);
    }
}
