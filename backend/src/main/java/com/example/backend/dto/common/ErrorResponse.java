package com.example.backend.dto.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ErrorResponse {
    private int status;
    private String message;
    private String code;
    private String step;

    public ErrorResponse(int status, String message) {
        this(status, message, null, null);
    }
}
