package com.example.backend.extraction;

import com.example.backend.enums.OcrStatus;

public record OcrResult(OcrStatus status, String text, String errorMessage) {
}
