package com.example.backend.extraction;

import org.springframework.stereotype.Service;

import com.example.backend.entity.ExtractionOutcome;
import com.example.backend.entity.ExtractionPath;

import org.springframework.beans.factory.annotation.Autowired;

@Service
public class ExtractionCoordinator {

    private final OpenRouterExtractionProvider openRouter;
    private final RuleBasedExtractionProvider ruleBased;

    @Autowired
    public ExtractionCoordinator(OpenRouterExtractionProvider openRouter, RuleBasedExtractionProvider ruleBased) {
        this.openRouter = openRouter;
        this.ruleBased = ruleBased;
    }

    /** Constructor retained for unit tests and small standalone callers. */
    public ExtractionCoordinator(OpenRouterExtractionProvider openRouter) {
        this(openRouter, new RuleBasedExtractionProvider(new UnitNormalizer(new com.fasterxml.jackson.databind.ObjectMapper())));
    }

    public ExtractionResult extract(String text) {
        if (openRouter.isAvailable()) {
            try {
                ProviderExtractionResult result = openRouter.extract(text);
                return new ExtractionResult(
                        result.document(),
                        ExtractionPath.OPENROUTER,
                        ExtractionOutcome.API_SUCCESS,
                        openRouter.providerName(),
                        openRouter.modelVersion(),
                        result.rawResponse(),
                        null);
            } catch (RuntimeException aiFailure) {
                return fallback(text, aiFailure);
            }
        }
        return fallback(text, null);
    }

    private ExtractionResult fallback(String text, RuntimeException aiFailure) {
        try {
            ProviderExtractionResult result = ruleBased.extract(text);
            return new ExtractionResult(result.document(), ExtractionPath.RULE_BASED,
                    ExtractionOutcome.RULE_BASED_FALLBACK, ruleBased.providerName(), ruleBased.modelVersion(),
                    result.rawResponse(), aiFailure == null ? null : "OpenRouter unavailable; deterministic fallback used");
        } catch (RuntimeException fallbackFailure) {
            if (aiFailure != null) throw aiFailure;
            throw new IllegalStateException("No extraction provider could understand this problem.", fallbackFailure);
        }
    }
}
