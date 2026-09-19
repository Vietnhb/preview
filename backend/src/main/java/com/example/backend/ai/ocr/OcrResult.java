package com.example.backend.ai.ocr;

import com.example.backend.entity.enums.OcrStatus;

public record OcrResult(OcrStatus status, String text, String errorMessage) {
}
