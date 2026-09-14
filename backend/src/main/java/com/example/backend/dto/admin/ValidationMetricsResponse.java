package com.example.backend.dto.admin;

public record ValidationMetricsResponse(long total, long failed, double failureRate) {
}
