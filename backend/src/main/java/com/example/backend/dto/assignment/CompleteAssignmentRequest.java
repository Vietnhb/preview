package com.example.backend.dto.assignment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompleteAssignmentRequest(
        @NotBlank @Size(max = 4000) String conclusion,
        @Size(max = 4000) String answerText,
        Double estimatedValue) {
    public CompleteAssignmentRequest(String conclusion) {
        this(conclusion, null, null);
    }
}
