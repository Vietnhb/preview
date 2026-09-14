package com.example.backend.extraction;

public interface ExtractionProvider {
    String providerName();

    String modelVersion();

    boolean isAvailable();

    ProviderExtractionResult extract(String text);
}
