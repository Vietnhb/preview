package com.example.backend.ai.extraction;

import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;

import com.example.backend.entity.enums.ExtractionOutcome;
import com.example.backend.exception.ApiException;
import com.example.backend.ai.extraction.model.ExtractionResult;
import com.example.backend.ai.extraction.model.ProviderExtractionResult;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExtractionCoordinator {

    private final ExtractionProvider provider;

    public ExtractionResult extract(String text) {
        if (!provider.isAvailable()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI extraction provider is not configured");
        }
        ProviderExtractionResult result = provider.extract(text);
        return new ExtractionResult(
                result.document(),
                provider.path(),
                ExtractionOutcome.API_SUCCESS,
                provider.providerName(),
                provider.modelVersion(),
                result.rawResponse(),
                null);
    }
}
