package com.example.backend.ai.extraction;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.example.backend.ai.extraction.model.ExtractionResult;
import com.example.backend.entity.enums.ExtractionOutcome;
import com.example.backend.exception.ApiException;
import com.example.backend.schema.routing.model.SchemaRoutingDecision;
import com.example.backend.schema.routing.service.JevSchemaRoutingService;

@Service
public class ExtractionCoordinator {
    private final ExtractionProvider provider;
    private final JevSchemaRoutingService schemaRouting;

    public ExtractionCoordinator(ExtractionProvider provider, JevSchemaRoutingService schemaRouting) {
        this.provider = provider;
        this.schemaRouting = schemaRouting;
    }

    public ExtractionResult extract(String text) {
        if (!provider.isAvailable()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI extraction provider is not configured");
        }
        SchemaRoutingDecision route = schemaRouting.route(text);
        var result = provider.extract(text, route);
        var document = result.document();
        return new ExtractionResult(document, provider.path(), ExtractionOutcome.API_SUCCESS,
                provider.providerName(), provider.modelVersion(), result.rawResponse(), null, route);
    }

}
