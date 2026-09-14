package com.example.backend.extraction;

import org.springframework.stereotype.Service;

import com.example.backend.entity.ExtractionOutcome;
import com.example.backend.entity.ExtractionPath;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExtractionCoordinator {

    private final OpenRouterExtractionProvider openRouter;

    public ExtractionResult extract(String text) {
        if (!openRouter.isAvailable()) {
            throw new IllegalStateException("OPENROUTER_API_KEY is required for problem understanding.");
        }
        ProviderExtractionResult result = openRouter.extract(text);
        return new ExtractionResult(
                result.document(),
                ExtractionPath.OPENROUTER,
                ExtractionOutcome.API_SUCCESS,
                openRouter.providerName(),
                openRouter.modelVersion(),
                result.rawResponse(),
                null);
    }
}
