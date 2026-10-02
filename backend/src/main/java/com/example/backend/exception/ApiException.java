package com.example.backend.exception;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final String step;

    public ApiException(HttpStatus status, String message) {
        this(status, message, null, null);
    }

    public ApiException(HttpStatus status, String message, String code, String step) {
        super(message);
        this.status = status;
        this.code = code;
        this.step = step;
    }

    // Business callers describe the failure here; HTTP status selection stays in this shared boundary.
    public static ApiException badRequest(String message) { return new ApiException(HttpStatus.BAD_REQUEST, message); }
    public static ApiException badRequest(String message, String code, String step) { return new ApiException(HttpStatus.BAD_REQUEST, message, code, step); }
    public static ApiException unauthorized(String message) { return new ApiException(HttpStatus.UNAUTHORIZED, message); }
    public static ApiException unauthorized(String message, String code, String step) { return new ApiException(HttpStatus.UNAUTHORIZED, message, code, step); }
    public static ApiException forbidden(String message) { return new ApiException(HttpStatus.FORBIDDEN, message); }
    public static ApiException forbidden(String message, String code, String step) { return new ApiException(HttpStatus.FORBIDDEN, message, code, step); }
    public static ApiException notFound(String message) { return new ApiException(HttpStatus.NOT_FOUND, message); }
    public static ApiException notFound(String message, String code, String step) { return new ApiException(HttpStatus.NOT_FOUND, message, code, step); }
    public static ApiException conflict(String message) { return new ApiException(HttpStatus.CONFLICT, message); }
    public static ApiException conflict(String message, String code, String step) { return new ApiException(HttpStatus.CONFLICT, message, code, step); }
    public static ApiException gone(String message) { return new ApiException(HttpStatus.GONE, message); }
    public static ApiException gone(String message, String code, String step) { return new ApiException(HttpStatus.GONE, message, code, step); }
    public static ApiException unsupportedMedia(String message) { return new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, message); }
    public static ApiException unsupportedMedia(String message, String code, String step) { return new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, message, code, step); }
    public static ApiException unprocessable(String message) { return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, message); }
    public static ApiException unprocessable(String message, String code, String step) { return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, message, code, step); }
    public static ApiException internal(String message) { return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, message); }
    public static ApiException internal(String message, String code, String step) { return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, message, code, step); }
    public static ApiException upstream(String message) { return new ApiException(HttpStatus.BAD_GATEWAY, message); }
    public static ApiException upstream(String message, String code, String step) { return new ApiException(HttpStatus.BAD_GATEWAY, message, code, step); }
    public static ApiException unavailable(String message) { return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, message); }
    public static ApiException unavailable(String message, String code, String step) { return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, message, code, step); }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() { return code; }
    public String getStep() { return step; }
}
