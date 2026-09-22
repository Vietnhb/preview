package com.example.backend.ai.extraction;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;

import com.example.backend.entity.enums.ExtractionOutcome;
import com.example.backend.exception.ApiException;
import com.example.backend.ai.extraction.model.ExtractionResult;
import com.example.backend.ai.extraction.model.ProviderExtractionResult;
import com.example.backend.schema.routing.model.SchemaRoutingDecision;
import com.example.backend.schema.routing.service.JevSchemaRoutingService;

@Service
public class ExtractionCoordinator {

    private final ExtractionProvider provider;
    private final java.util.function.Function<String, SchemaRoutingDecision> schemaRouting;

    @Autowired
    public ExtractionCoordinator(ExtractionProvider provider, JevSchemaRoutingService schemaRouting) {
        this.provider = provider;
        this.schemaRouting = schemaRouting::route;
    }

    public ExtractionResult extract(String text) {
        if (!provider.isAvailable()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI extraction provider is not configured");
        }
        SchemaRoutingDecision routingDecision = schemaRouting.apply(text);
        ProviderExtractionResult result = provider.extract(text, routingDecision);
        return new ExtractionResult(
                result.document(),
                provider.path(),
                ExtractionOutcome.API_SUCCESS,
                provider.providerName(),
                provider.modelVersion(),
                result.rawResponse(),
                null,
                routingDecision,
                result.assetSelection());
    }
}
