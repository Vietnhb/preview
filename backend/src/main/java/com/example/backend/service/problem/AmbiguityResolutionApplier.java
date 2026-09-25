package com.example.backend.service.problem;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.example.backend.entity.problem.AmbiguityCase;
import com.example.backend.entity.enums.AmbiguityStatus;
import com.example.backend.entity.enums.ConfirmationState;
import com.example.backend.entity.problem.Specification;
import com.example.backend.ai.extraction.ExtractionProvider;
import com.example.backend.ai.extraction.model.AmbiguityItem;
import com.example.backend.ai.extraction.model.ProviderExtractionResult;
import com.example.backend.ai.extraction.model.SpecificationDocument;
import com.example.backend.ai.extraction.model.ConversationTurn;
import com.example.backend.exception.ApiException;
import com.example.backend.schema.routing.service.JevSchemaRoutingService;
import com.example.backend.simulation.assets.AssetSelectionService;
import com.example.backend.simulation.assets.VisualTargets;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AmbiguityResolutionApplier {
    private final ObjectMapper objectMapper;
    private final ExtractionProvider aiProvider;
    private final SchemaDefinitionService schemaDefinitions;
    private final SpecificationReadinessService readinessService;
    private final JevSchemaRoutingService schemaRouting;
    private final AssetSelectionService assetSelections;

    public void applyAll(Specification specification, Map<String, String> answers) {
        applyAll(specification, answers, List.of());
    }

    public void applyAll(Specification specification, Map<String, String> answers,
            List<ConversationTurn> conversation) {
        if (!aiProvider.isAvailable()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI extraction provider is not configured");
        }
        Map<String, String> safeAnswers = answers == null ? Map.of() : Map.copyOf(answers);
        ObjectNode current = currentDocument(specification);
        ProviderExtractionResult result = aiProvider.resolveAmbiguities(
                specification.getSubmission().getEditableText(), current, safeAnswers, conversation);
        if (declinedCompatibilityDecision(specification, result.resolutionDecisions())) {
            stopAfterDeclinedCompatibility(specification, safeAnswers);
            return;
        }
        SpecificationDocument document = result.document();
        applyDocument(specification, document);
        synchronizeCases(specification, document, safeAnswers);
        readinessService.ensureRequiredAmbiguities(specification);
        prepareAssetsAfterConfirmation(specification, conversation);
    }

    private boolean declinedCompatibilityDecision(Specification specification,
            List<com.example.backend.ai.extraction.model.ResolutionDecision> decisions) {
        Set<String> openCodes = specification.getAmbiguityCases().stream()
                .filter(item -> item.getStatus() == AmbiguityStatus.OPEN)
                .map(AmbiguityCase::getCode).collect(java.util.stream.Collectors.toSet());
        return decisions != null && decisions.stream().anyMatch(item -> openCodes.contains(item.code())
                && item.outcome() == com.example.backend.ai.extraction.model.ResolutionDecision.Outcome.DECLINE_SIMPLIFICATION);
    }

    private void stopAfterDeclinedCompatibility(Specification specification, Map<String, String> answers) {
        Instant now = Instant.now();
        for (AmbiguityCase item : specification.getAmbiguityCases()) {
            if (item.getStatus() != AmbiguityStatus.OPEN) continue;
            item.setStatus(AmbiguityStatus.REJECTED);
            item.setResolution(answers.getOrDefault(item.getCode(), "Stopped after compatibility was declined."));
            item.setResolvedAt(now);
        }
        specification.setConfirmationState(ConfirmationState.REJECTED);
        specification.setAmbiguity(objectMapper.createArrayNode());
    }

    public void prepareAssetsAfterConfirmation(Specification specification,
            List<ConversationTurn> conversation) {
        if (specification.getAssetSelection() != null || specification.getSubmission() == null
                || specification.getConfirmationState() != ConfirmationState.CONFIRMED
                || !readinessService.blockers(specification).isEmpty()) return;

        String source = specification.getSubmission().getEditableText();
        SpecificationDocument physicsDocument = specificationDocument(specification);
        try {
            var schema = schemaDefinitions.requireCurrentApproved(physicsDocument.schemaId(),
                    physicsDocument.schemaVersion());
            JsonNode visualization = schemaDefinitions.visualization(schema.getDefinition());
            if (!schema.hasSvgAsset()) {
                specification.setAssetSelection(assetSelections.createWithoutAssets(
                        physicsDocument, visualization, source));
                return;
            }
            ObjectNode summaryContext = objectMapper.valueToTree(physicsDocument);
            summaryContext.set("visualTargets", objectMapper.valueToTree(
                    VisualTargets.read(visualization)));
            JsonNode assetRequestSummary = aiProvider.summarizeAssetRequests(source, summaryContext);
            var assetRoute = schemaRouting.routeAssets(source, physicsDocument, conversation, assetRequestSummary);
            ProviderExtractionResult bound = aiProvider.bindVisualAssets(source,
                    objectMapper.valueToTree(physicsDocument), assetRoute, conversation);

            var verification = schemaRouting.verify(assetRoute, bound.document());
            SpecificationDocument visualDocument = verification.passed() ? bound.document()
                    : bound.document().withAmbiguities(verificationAmbiguities(
                            source, verification.findings(), conversation));
            applyDocument(specification, visualDocument);
            synchronizeCases(specification, visualDocument, Map.of());
            readinessService.ensureRequiredAmbiguities(specification, conversation);

            if (!verification.passed() || !visualDocument.ambiguities().isEmpty()
                    || !readinessService.blockers(specification).isEmpty()) return;
            specification.setAssetSelection(assetSelections.create(visualDocument, assetRoute.assets(), source));
        } catch (RuntimeException failure) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Dữ liệu mô phỏng đã được xác nhận nhưng chưa thể chuẩn bị hình minh họa. "
                            + "Chưa có mô phỏng nào được chạy; vui lòng thử lại.");
        }
    }

    private SpecificationDocument specificationDocument(Specification specification) {
        try {
            ObjectNode confirmed = withPersistedQuantities(currentDocument(specification), specification.getQuantities());
            return objectMapper.treeToValue(confirmed, SpecificationDocument.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot read the confirmed specification for asset routing.", exception);
        }
    }

    static ObjectNode withPersistedQuantities(ObjectNode projectedSpecification, JsonNode persistedQuantities) {
        ObjectNode complete = projectedSpecification.deepCopy();
        if (persistedQuantities != null && persistedQuantities.isArray()) {
            complete.set("quantities", persistedQuantities.deepCopy());
        }
        return complete;
    }

    private List<AmbiguityItem> verificationAmbiguities(String originalText, List<String> findings,
            List<ConversationTurn> conversation) {
        return aiProvider.phraseVerificationQuestions(originalText, findings, conversation);
    }

    private ObjectNode currentDocument(Specification specification) {
        ObjectNode current = objectMapper.createObjectNode();
        current.put("contractVersion", specification.getContractVersion());
        current.put("schemaVersion", specification.getSchemaVersion());
        current.put("topic", specification.getTopic());
        current.put("schemaId", specification.getSchemaId());
        current.set("objects", specification.getObjects());
        com.fasterxml.jackson.databind.node.ArrayNode quantities = objectMapper.createArrayNode();
        if (specification.getQuantities() != null && specification.getQuantities().isArray())
            specification.getQuantities().forEach(quantities::add);
        current.set("quantities", quantities);
        current.set("relations", specification.getRelations());
        if (specification.getEndCondition() != null)
            current.set("endCondition", specification.getEndCondition());
        current.put("confidence", specification.getConfidence());
        current.set("ambiguities", objectMapper.valueToTree(specification.getAmbiguityCases().stream()
                .filter(item -> item.getStatus() == AmbiguityStatus.OPEN)
                .map(item -> new AmbiguityItem(item.getCode(), item.getFieldPath(), item.getQuestion(),
                        item.getOptions() == null || !item.getOptions().isArray() ? List.of()
                                : objectMapper.convertValue(item.getOptions(),
                                        new com.fasterxml.jackson.core.type.TypeReference<List<String>>() { })))
                .toList()));
        current.set("visualBindings", specification.getAssetSelection() == null
                ? objectMapper.createArrayNode() : specification.getAssetSelection().path("bindings"));
        return current;
    }

    private void synchronizeCases(Specification specification, SpecificationDocument document,
            Map<String, String> answers) {
        Map<String, AmbiguityItem> returned = new HashMap<>();
        document.ambiguities().forEach(item -> returned.put(identity(item.fieldPath(), item.code()), item));
        Set<String> existingIdentities = new HashSet<>();
        Instant now = Instant.now();
        for (AmbiguityCase existing : specification.getAmbiguityCases()) {
            String identity = identity(existing.getFieldPath(), existing.getCode());
            existingIdentities.add(identity);
            AmbiguityItem updated = returned.get(identity);
            if (updated != null) {
                existing.setQuestion(updated.question());
                existing.setFieldPath(updated.fieldPath());
                existing.setOptions(objectMapper.valueToTree(updated.options()));
                existing.setStatus(AmbiguityStatus.OPEN);
                existing.setResolution(null);
                existing.setResolvedAt(null);
            } else if (answers.containsKey(existing.getCode()) && existing.getStatus() == AmbiguityStatus.OPEN) {
                existing.setResolution(answers.get(existing.getCode()).trim());
                existing.setStatus(AmbiguityStatus.RESOLVED);
                existing.setResolvedAt(now);
            }
        }
        for (AmbiguityItem item : document.ambiguities()) {
            if (existingIdentities.contains(identity(item.fieldPath(), item.code())))
                continue;
            AmbiguityCase created = new AmbiguityCase();
            created.setCode(item.code());
            created.setFieldPath(item.fieldPath());
            created.setQuestion(item.question());
            created.setOptions(objectMapper.valueToTree(item.options()));
            created.setStatus(AmbiguityStatus.OPEN);
            specification.addAmbiguityCase(created);
        }
        boolean open = specification.getAmbiguityCases().stream()
                .anyMatch(item -> item.getStatus() == AmbiguityStatus.OPEN);
        specification.setConfirmationState(open ? ConfirmationState.UNRESOLVED : ConfirmationState.CONFIRMED);
    }

    private String identity(String fieldPath, String code) {
        String value = fieldPath == null || fieldPath.isBlank() ? code : fieldPath;
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private void applyDocument(Specification specification, SpecificationDocument document) {
        specification.setContractVersion(SpecificationDocument.CURRENT_SCHEMA_VERSION);
        specification.setSchemaVersion(document.schemaVersion());
        specification.setTopic(document.topic());
        specification.setSchemaId(document.schemaId());
        specification.setObjects(objectMapper.valueToTree(document.objects()));
        specification.setQuantities(objectMapper.valueToTree(document.quantities()));
        specification.setRelations(objectMapper.valueToTree(document.relations()));
        specification.setConfidence(document.confidence());
        specification.setEndCondition(document.endCondition());
        specification.setAmbiguity(objectMapper.valueToTree(document.ambiguities()));
    }
}
