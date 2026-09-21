package com.example.backend.dto.support;

import com.example.backend.entity.enums.SupportStatus;
import jakarta.validation.constraints.Size;

public record UpdateSupportRequest(
        SupportStatus status,
        @Size(max = 10000) String response) {
}
