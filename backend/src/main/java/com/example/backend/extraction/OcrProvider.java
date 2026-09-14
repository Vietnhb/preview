package com.example.backend.extraction;

public interface OcrProvider {
    String providerName();

    boolean isAvailable();

    OcrResult recognize(String contentType, byte[] content);
}
