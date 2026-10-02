package com.example.backend.system.problem.dto;

import com.example.backend.system.problem.model.enums.OcrStatus;
import java.util.UUID;

public record SourceAssetResponse(
        UUID id,
        String originalFilename,
        String contentType,
        long contentLength,
        OcrStatus ocrStatus,
        String ocrText,
        String ocrError) {
    public record Content(String contentType, String originalFilename, long contentLength, byte[] bytes) { }
}
