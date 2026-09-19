package com.example.backend.ai.ocr;

public interface OcrProvider {
    String providerName();

    boolean isAvailable();

    OcrResult recognize(String contentType, byte[] content);
}
