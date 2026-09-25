package com.example.backend.ai.extraction;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.example.backend.ai.extraction.model.ExtractionResult;
import com.example.backend.ai.extraction.model.SpecificationDocument;
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
        requireSupportedSchema(route);
        var result = provider.extract(text, route);
        var selected = route.selectedCandidate().orElseThrow();
        var modelDocument = result.document();
        var document = new SpecificationDocument(
                selected.schemaVersion(), selected.topic(), selected.schemaId(), modelDocument.objects(),
                modelDocument.quantities(), modelDocument.relations(), modelDocument.endCondition(),
                modelDocument.confidence(), modelDocument.ambiguities(), modelDocument.contractVersion(),
                modelDocument.visualBindings());
        return new ExtractionResult(document, provider.path(), ExtractionOutcome.API_SUCCESS,
                provider.providerName(), provider.modelVersion(), result.rawResponse(), null, route);
    }

    static void requireSupportedSchema(SchemaRoutingDecision routingDecision) {
        if (routingDecision.status() == SchemaRoutingDecision.Status.AMBIGUOUS) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Xin lỗi, PhysLive chưa hỗ trợ mô tả này.");
        }
    }
}
