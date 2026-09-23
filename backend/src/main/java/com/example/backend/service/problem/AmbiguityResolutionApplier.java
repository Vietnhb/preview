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
import com.example.backend.ai.extraction.CompatibilityFieldPaths;
import com.example.backend.ai.extraction.StructuredExtractionProvider;
import com.example.backend.ai.extraction.model.AmbiguityItem;
import com.example.backend.ai.extraction.model.ProviderExtractionResult;
import com.example.backend.ai.extraction.model.QuantityContractViolation;
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
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AmbiguityResolutionApplier.class);
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
        ProviderExtractionResult result;
        try {
            result = aiProvider.resolveAmbiguities(
                    specification.getSubmission().getEditableText(), current, safeAnswers, conversation);
        } catch (RuntimeException failure) {
            QuantityContractViolation violation = findCause(failure, QuantityContractViolation.class);
            if (violation == null) throw failure;
            result = reaskInvalidQuantity(specification, current, violation, conversation);
        }
        if (declinedCompatibilityDecision(specification, result.resolutionDecisions())) {
            stopAfterDeclinedCompatibility(specification, safeAnswers);
            return;
        }
        SpecificationDocument resolvedDocument;
        try {
            ObjectNode preservationBase = withPersistedQuantities(current, specification.getQuantities());
            JsonNode protectedJson = StructuredExtractionProvider.preserveConfirmedPhysics(objectMapper, preservationBase,
                    objectMapper.valueToTree(result.document()), result.resolutionDecisions());
            resolvedDocument = objectMapper.treeToValue(protectedJson, SpecificationDocument.class);
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot preserve confirmed physics facts during clarification.", failure);
        }
        boolean hasOpenCompatibility = hasOpenCompatibilityDecision(specification);
        boolean hasExplicitSimplification = hasAcceptedSimplificationDecision(specification,
                result.resolutionDecisions());
        boolean deferObjectReduction = hasOpenCapacityDecision(specification) && hasExplicitSimplification;
        if (deferObjectReduction) {
            resolvedDocument = askWhichObjectToRetain(specification, current, result.document(), conversation);
        }
        boolean acceptedSimplification = acceptedSimplification(specification, current, resolvedDocument,
                result.resolutionDecisions());
        boolean capacityObjectChoicePending = hasOpenCapacityTargetDecision(specification) && !acceptedSimplification;
        if (capacityObjectChoicePending && !deferObjectReduction) {
            resolvedDocument = preservePhysicsWhileKeepingClarifications(current, resolvedDocument);
        }
        boolean compatibilityResolved = !hasOpenCompatibility
                || compatibilityDecisionsResolved(specification, result.resolutionDecisions());
        if (!deferObjectReduction && hasOpenCompatibility && (!compatibilityResolved
                || hasExplicitSimplification && !acceptedSimplification)) {
            compatibilityResolved = false;
            resolvedDocument = preservePhysicsWhileKeepingClarifications(current, resolvedDocument);
        }
        var verified = deferObjectReduction || capacityObjectChoicePending
                ? new JevSchemaRoutingService.Verification(List.of())
                : schemaRouting.verifyPinned(resolvedDocument);
        var document = verified.passed() ? resolvedDocument
                : resolvedDocument.withAmbiguities(verificationAmbiguities(
                        specification.getSubmission().getEditableText(), verified.findings(), conversation));
        applyDocument(specification, document);
        synchronizeCases(specification, document, safeAnswers);
        if (deferObjectReduction) {
            specification.getAmbiguityCases().stream()
                    .filter(item -> CompatibilityFieldPaths.CAPACITY.equals(item.getFieldPath())
                            && item.getStatus() == AmbiguityStatus.RESOLVED)
                    .forEach(item -> item.setResolution("CAPACITY_CONSENT_PENDING_OBJECT_SELECTION"));
        }
        readinessService.ensureRequiredAmbiguities(specification, conversation);
        prepareAssetsAfterConfirmation(specification, conversation);
    }

    private boolean declinedCompatibilityDecision(Specification specification,
            List<com.example.backend.ai.extraction.model.ResolutionDecision> decisions) {
        Set<String> openCodes = specification.getAmbiguityCases().stream()
                .filter(item -> item.getStatus() == AmbiguityStatus.OPEN && !isRequiredInputPath(item.getFieldPath()))
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

    private SpecificationDocument askWhichObjectToRetain(Specification specification, ObjectNode current,
            SpecificationDocument rawCandidate,
            List<ConversationTurn> conversation) {
        List<AmbiguityItem> questions = new java.util.ArrayList<>(rawCandidate.ambiguities().stream()
                .filter(item -> !isRequiredInputPath(item.fieldPath())
                        && !CompatibilityFieldPaths.CAPACITY.equals(item.fieldPath()))
                .toList());
        AmbiguityItem targetQuestion = rawCandidate.ambiguities().stream()
                .filter(item -> CompatibilityFieldPaths.CAPACITY_RETAINED_OBJECT.equals(item.fieldPath()))
                .findFirst().orElse(null);
        if (targetQuestion == null) {
            String objectChoices = specification.getObjects().toString();
            String finding = "issue=JEV_CAPACITY_EXCEEDED; fieldPath="
                    + CompatibilityFieldPaths.CAPACITY_RETAINED_OBJECT
                    + "; objects=" + objectChoices
                    + "; guidance=The user has agreed to reduce the representation. Ask which specifically identified object must remain. Do not choose or remove an object yet.";
            targetQuestion = aiProvider.phraseVerificationQuestions(
                    specification.getSubmission().getEditableText(), List.of(finding), conversation).stream()
                    .filter(item -> CompatibilityFieldPaths.CAPACITY_RETAINED_OBJECT.equals(item.fieldPath()))
                    .findFirst().orElseThrow(() -> new IllegalStateException(
                            "AI clarification did not ask which object should remain."));
        }
        questions.add(targetQuestion);
        try {
            SpecificationDocument original = objectMapper.treeToValue(current, SpecificationDocument.class);
            return new SpecificationDocument(original.schemaVersion(), original.topic(), original.schemaId(),
                    original.objects(), original.quantities(), original.relations(), original.endCondition(),
                    original.confidence(), questions, original.contractVersion(), original.visualBindings());
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot preserve objects while asking which one to retain.", exception);
        }
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
            ObjectNode summaryContext = objectMapper.valueToTree(physicsDocument);
            summaryContext.set("visualTargets", objectMapper.valueToTree(
                    VisualTargets.read(schemaDefinitions.visualization(schema.getDefinition()))));
            JsonNode assetRequestSummary = aiProvider.summarizeAssetRequests(source, summaryContext);
            var assetRoute = schemaRouting.routeAssets(source, physicsDocument, conversation, assetRequestSummary);
            ProviderExtractionResult bound = aiProvider.bindVisualAssets(source,
                    objectMapper.valueToTree(physicsDocument), assetRoute, conversation);
            requirePhysicsUnchanged(physicsDocument, bound.document());

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
            org.slf4j.LoggerFactory.getLogger(AmbiguityResolutionApplier.class)
                    .warn("Asset preparation failed after the physics specification was confirmed.", failure);
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

    private void requirePhysicsUnchanged(SpecificationDocument before, SpecificationDocument after) {
        if (after == null || !java.util.Objects.equals(before.schemaId(), after.schemaId())
                || !java.util.Objects.equals(before.schemaVersion(), after.schemaVersion())
                || !sameJson(objectProjection(before.objects()), objectProjection(after.objects()))
                || !sameJson(quantityProjection(before.quantities()), quantityProjection(after.quantities()))
                || !sameJson(before.relations(), after.relations())
                || !sameJson(before.endCondition(), after.endCondition())) {
            throw new IllegalStateException("Visual binding modified the confirmed physics specification.");
        }
    }

    private JsonNode objectProjection(List<com.example.backend.ai.extraction.model.PhysicalObject> objects) {
        var projected = objectMapper.createArrayNode();
        if (objects == null) return projected;
        for (var object : objects) {
            ObjectNode item = projected.addObject();
            item.put("id", object.id());
            item.put("label", object.label());
            item.put("type", object.type());
            item.set("quantities", quantityProjection(object.quantities()));
        }
        return projected;
    }

    private JsonNode quantityProjection(List<com.example.backend.ai.extraction.model.PhysicalQuantity> quantities) {
        var projected = objectMapper.createArrayNode();
        if (quantities == null) return projected;
        for (var quantity : quantities) {
            ObjectNode item = projected.addObject();
            if (quantity.name() != null) item.put("name", quantity.name());
            if (quantity.symbol() != null) item.put("symbol", quantity.symbol());
            if (quantity.value() != null) item.put("value", quantity.value());
            if (quantity.originalUnit() != null) item.put("originalUnit", quantity.originalUnit());
        }
        return projected;
    }

    private boolean sameJson(Object before, Object after) {
        JsonNode beforeNode = objectMapper.valueToTree(before);
        JsonNode afterNode = objectMapper.valueToTree(after);
        return java.util.Objects.equals(beforeNode, afterNode);
    }

    private ProviderExtractionResult reaskInvalidQuantity(Specification specification, ObjectNode current,
            QuantityContractViolation violation, List<ConversationTurn> conversation) {
        String finding = "issue=INPUT_CONTRACT_MISMATCH; fieldPath=" + violation.fieldPath()
                + "; suppliedValue=" + violation.value() + "; suppliedUnit=" + violation.suppliedUnit()
                + "; expectedUnits=" + String.join(",", violation.expectedUnits())
                + "; guidance=Use the conversation to explain why this answer does not satisfy the selected schema input. "
                + "Ask what the user intended. Preserve the answer exactly as given; do not reinterpret or convert it.";
        List<AmbiguityItem> questions = aiProvider.phraseVerificationQuestions(
                specification.getSubmission().getEditableText(), List.of(finding), conversation);
        AmbiguityItem question = questions.stream().filter(item -> violation.fieldPath().equals(item.fieldPath()))
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "AI clarification did not target the invalid required input."));
        String code = specification.getAmbiguityCases().stream()
                .filter(item -> item.getStatus() == AmbiguityStatus.OPEN
                        && violation.fieldPath().equals(item.getFieldPath()))
                .map(AmbiguityCase::getCode).findFirst()
                .orElse("schema.required." + violation.fieldPath());
        AmbiguityItem replacement = new AmbiguityItem(code, violation.fieldPath(),
                question.question(), question.options());
        try {
            SpecificationDocument document = objectMapper.treeToValue(current, SpecificationDocument.class)
                    .withoutAmbiguitiesAt(Set.of(violation.fieldPath()))
                    .withAmbiguities(List.of(replacement));
            return new ProviderExtractionResult(document, null);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot preserve the specification while re-asking for an invalid quantity.", exception);
        }
    }

    private <T extends Throwable> T findCause(Throwable failure, Class<T> type) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (type.isInstance(current)) return type.cast(current);
        }
        return null;
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
        if (specification.getQuantities() != null && specification.getQuantities().isArray()) {
            specification.getQuantities().forEach(source -> {
                ObjectNode quantity = source.deepCopy();
                quantity.remove(List.of("normalizedValue", "normalizedUnit"));
                quantities.add(quantity);
            });
        }
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

    private boolean compatibilityDecisionsResolved(Specification specification,
            List<com.example.backend.ai.extraction.model.ResolutionDecision> decisions) {
        List<AmbiguityCase> pending = specification.getAmbiguityCases().stream()
                .filter(item -> item.getStatus() == AmbiguityStatus.OPEN && !isRequiredInputPath(item.getFieldPath()))
                .toList();
        if (pending.isEmpty()) return false;
        Map<String, com.example.backend.ai.extraction.model.ResolutionDecision> byCode = decisions.stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.example.backend.ai.extraction.model.ResolutionDecision::code, item -> item, (left, right) -> left));
        return pending.stream().allMatch(item -> {
            var decision = byCode.get(item.getCode());
            return decision != null && (decision.outcome()
                    == com.example.backend.ai.extraction.model.ResolutionDecision.Outcome.ANSWERED
                    || decision.outcome()
                    == com.example.backend.ai.extraction.model.ResolutionDecision.Outcome.ACCEPT_SIMPLIFICATION);
        });
    }

    private boolean hasOpenCompatibilityDecision(Specification specification) {
        return specification.getAmbiguityCases().stream().anyMatch(item ->
                item.getStatus() == AmbiguityStatus.OPEN && !isRequiredInputPath(item.getFieldPath()));
    }

    private boolean hasOpenCapacityDecision(Specification specification) {
        return specification.getAmbiguityCases().stream().anyMatch(item ->
                item.getStatus() == AmbiguityStatus.OPEN
                        && CompatibilityFieldPaths.CAPACITY.equals(item.getFieldPath()));
    }

    private boolean hasOpenCapacityTargetDecision(Specification specification) {
        return specification.getAmbiguityCases().stream().anyMatch(item ->
                item.getStatus() == AmbiguityStatus.OPEN
                        && CompatibilityFieldPaths.CAPACITY_RETAINED_OBJECT.equals(item.getFieldPath()));
    }

    private boolean acceptedSimplification(Specification specification, ObjectNode previous,
            SpecificationDocument revised,
            List<com.example.backend.ai.extraction.model.ResolutionDecision> decisions) {
        Map<String, String> openCompatibilityPaths = new HashMap<>();
        specification.getAmbiguityCases().stream()
                .filter(item -> item.getStatus() == AmbiguityStatus.OPEN && !isRequiredInputPath(item.getFieldPath()))
                .forEach(item -> openCompatibilityPaths.put(item.getCode(), item.getFieldPath()));
        boolean priorConsent = specification.getAmbiguityCases().stream().anyMatch(item ->
                CompatibilityFieldPaths.CAPACITY.equals(item.getFieldPath())
                        && item.getStatus() == AmbiguityStatus.RESOLVED
                        && "CAPACITY_CONSENT_PENDING_OBJECT_SELECTION".equals(item.getResolution()));
        List<com.example.backend.ai.extraction.model.ResolutionDecision> accepted = decisions.stream()
                .filter(item -> {
                    String path = openCompatibilityPaths.get(item.code());
                    boolean explicitConsent = item.outcome()
                            == com.example.backend.ai.extraction.model.ResolutionDecision.Outcome.ACCEPT_SIMPLIFICATION;
                    boolean selectedAfterConsent = priorConsent
                            && CompatibilityFieldPaths.CAPACITY_RETAINED_OBJECT.equals(path)
                            && item.outcome() == com.example.backend.ai.extraction.model.ResolutionDecision.Outcome.ANSWERED;
                    return explicitConsent || selectedAfterConsent;
                })
                .toList();
        if (accepted.isEmpty()) return false;
        Set<String> beforeIds = new HashSet<>();
        previous.path("objects").forEach(item -> beforeIds.add(item.path("id").asText()));
        Set<String> afterIds = revised.objects().stream().map(item -> item.id()).collect(java.util.stream.Collectors.toSet());
        beforeIds.removeAll(afterIds);
        Set<String> declaredOmissions = accepted.stream().flatMap(item -> item.omittedObjectIds().stream())
                .collect(java.util.stream.Collectors.toSet());
        return beforeIds.equals(declaredOmissions);
    }

    private boolean hasAcceptedSimplificationDecision(Specification specification,
            List<com.example.backend.ai.extraction.model.ResolutionDecision> decisions) {
        Set<String> openCompatibilityCodes = specification.getAmbiguityCases().stream()
                .filter(item -> item.getStatus() == AmbiguityStatus.OPEN && !isRequiredInputPath(item.getFieldPath()))
                .map(AmbiguityCase::getCode).collect(java.util.stream.Collectors.toSet());
        return decisions.stream().anyMatch(item -> openCompatibilityCodes.contains(item.code())
                && item.outcome() == com.example.backend.ai.extraction.model.ResolutionDecision.Outcome.ACCEPT_SIMPLIFICATION);
    }

    private SpecificationDocument preservePhysicsWhileKeepingClarifications(ObjectNode previous,
            SpecificationDocument candidate) {
        try {
            SpecificationDocument original = objectMapper.treeToValue(previous, SpecificationDocument.class);
            Map<String, AmbiguityItem> byPath = new java.util.LinkedHashMap<>();
            original.ambiguities().forEach(item -> byPath.put(item.fieldPath(), item));
            candidate.ambiguities().forEach(item -> byPath.put(item.fieldPath(), item));
            List<AmbiguityItem> ambiguities = List.copyOf(byPath.values());
            return new SpecificationDocument(original.schemaVersion(), original.topic(), original.schemaId(),
                    original.objects(), original.quantities(), original.relations(), original.endCondition(),
                    original.confidence(), ambiguities, original.contractVersion(), original.visualBindings());
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot preserve the pinned physics specification while awaiting a decision.", exception);
        }
    }

    private String identity(String fieldPath, String code) {
        String value = fieldPath == null || fieldPath.isBlank() ? code : fieldPath;
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private boolean isRequiredInputPath(String fieldPath) {
        return fieldPath != null && (fieldPath.startsWith("quantities.")
                || fieldPath.startsWith("objects.") && fieldPath.contains(".quantities."));
    }

    private void applyDocument(Specification specification, SpecificationDocument document) {
        specification.setContractVersion(SpecificationDocument.CURRENT_SCHEMA_VERSION);
        var schema = schemaDefinitions.requireCurrentApproved(document.schemaId(), document.schemaVersion());
        specification.setSchemaVersion(schema.getVersion());
        specification.setTopic(document.topic());
        specification.setSchemaId(document.schemaId());
        specification.setObjects(objectMapper.valueToTree(document.objects()));
        specification.setQuantities(schemaDefinitions.canonicalizeQuantities(
                objectMapper.valueToTree(document.quantities()), schema.getDefinition()));
        specification.setRelations(objectMapper.valueToTree(document.relations()));
        specification.setConfidence(document.confidence());
        specification.setEndCondition(document.endCondition());
        specification.setAmbiguity(objectMapper.valueToTree(document.ambiguities()));
    }
}
