package com.example.backend.dto.problem;

import java.util.UUID;

import com.example.backend.entity.enums.OcrStatus;

public record SourceAssetResponse(
        UUID id,
        String originalFilename,
        String contentType,
        long contentLength,
        OcrStatus ocrStatus,
        String ocrText,
        String ocrError) {
}
