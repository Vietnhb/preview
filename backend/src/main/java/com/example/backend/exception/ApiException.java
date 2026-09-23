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

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() { return code; }
    public String getStep() { return step; }
}
