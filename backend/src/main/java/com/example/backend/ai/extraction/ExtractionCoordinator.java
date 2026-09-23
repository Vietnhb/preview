package com.example.backend.ai.extraction;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;

import java.util.List;

import com.example.backend.entity.enums.ExtractionOutcome;
import com.example.backend.exception.ApiException;
import com.example.backend.ai.extraction.model.ExtractionResult;
import com.example.backend.ai.extraction.model.ProviderExtractionResult;
import com.example.backend.ai.extraction.model.SpecificationDocument;
import com.example.backend.ai.extraction.model.ConversationTurn;
import com.example.backend.schema.routing.model.SchemaRoutingDecision;
import com.example.backend.schema.routing.service.JevSchemaRoutingService;
import com.example.backend.simulation.assets.AssetSelectionService;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ExtractionCoordinator {

    private final ExtractionProvider provider;
    private final JevSchemaRoutingService schemaRouting;
    private final AssetSelectionService assetSelections;
    private final ObjectMapper objectMapper;

    @Autowired
    public ExtractionCoordinator(ExtractionProvider provider, JevSchemaRoutingService schemaRouting,
            AssetSelectionService assetSelections, ObjectMapper objectMapper) {
        this.provider = provider;
        this.schemaRouting = schemaRouting;
        this.assetSelections = assetSelections;
        this.objectMapper = objectMapper;
    }

    public ExtractionCoordinator(ExtractionProvider provider, JevSchemaRoutingService schemaRouting,
            AssetSelectionService assetSelections) {
        this(provider, schemaRouting, assetSelections, new ObjectMapper());
    }

    /** Source-compatible constructor for isolated extraction tests. */
    public ExtractionCoordinator(ExtractionProvider provider, JevSchemaRoutingService schemaRouting) {
        this(provider, schemaRouting, null);
    }

    public ExtractionResult extract(String text) {
        if (!provider.isAvailable()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI extraction provider is not configured");
        }
        SchemaRoutingDecision routingDecision = schemaRouting.route(text);
        List<String> routeFindings = "JEV_CAPACITY_EXCEEDED".equals(routingDecision.reasonCode())
                ? schemaRouting.capacityFindings(routingDecision) : List.of();
        ProviderExtractionResult result = routeFindings.isEmpty()
                ? provider.extract(text, routingDecision)
                : provider.extract(text, routingDecision, routeFindings);
        if (routingDecision.status() == SchemaRoutingDecision.Status.AMBIGUOUS
                && !"JEV_CAPACITY_EXCEEDED".equals(routingDecision.reasonCode())
                && result.document().ambiguities().stream().noneMatch(item -> "schemaId".equals(item.fieldPath()))) {
            result = new ProviderExtractionResult(result.document().withAmbiguities(
                    provider.phraseVerificationQuestions(text, List.of(
                            "issue=SCHEMA_SELECTION_UNCERTAIN; fieldPath=schemaId; guidance=Ask which of the routed approved physics models the user intends."))),
                    result.rawResponse(), result.assetSelection());
        }
        var verification = verify(text, routingDecision, result.document());
        if (!verification.passed()) {
            if (hasCapacityFinding(verification)) {
                result = ensureCapacityConsentQuestion(text, result, verification);
                verification = verify(text, routingDecision, result.document());
            }
            if (onlyPendingCapacityConsent(verification, result.document())) {
                return finish(text, routingDecision, result, verification);
            }
            ProviderExtractionResult baseline = result;
            var baselineVerification = verification;
            ProviderExtractionResult repaired = null;
            boolean keepBaseline = false;
            try {
                repaired = provider.extract(text, routingDecision, verification.findings());
            } catch (RuntimeException failure) {
                if (!hasCapacityFinding(baselineVerification)) throw failure;
                keepBaseline = true;
            }
            if (!keepBaseline && repaired.document().objects().size() < baseline.document().objects().size()) {
                keepBaseline = true;
            }
            if (!keepBaseline && routedEntityCountSatisfied(routingDecision, baseline.document())
                    && hasObjectsAmbiguity(repaired.document())) {
                keepBaseline = true;
            }
            if (keepBaseline) {
                result = baseline;
                verification = baselineVerification;
            } else {
                result = repaired;
                verification = verify(text, routingDecision, result.document());
            }
            if (hasCapacityFinding(verification)) {
                result = ensureCapacityConsentQuestion(text, result, verification);
                verification = verify(text, routingDecision, result.document());
            }
            if (!verification.passed()) {
                if (hasIssue(verification, "ENTITY_COUNT_MISMATCH")) {
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                            "AI extraction could not preserve the JEV-confirmed physical object count.");
                }
                result = new ProviderExtractionResult(
                        phraseVerifiedQuestions(text, routingDecision, result.document(), verification),
                        result.rawResponse(), result.assetSelection());
            }
        }
        return finish(text, routingDecision, result, verification);
    }

    private ExtractionResult finish(String text, SchemaRoutingDecision routingDecision,
            ProviderExtractionResult result, JevSchemaRoutingService.Verification verification) {
        SpecificationDocument document = result.document();
        boolean compatibilityPending = document.ambiguities().stream()
                .anyMatch(item -> !isRequiredInputPath(item.fieldPath()));
        if (compatibilityPending) {
            document = document.withoutAmbiguitiesMatching(item -> isRequiredInputPath(item.fieldPath()));
        }
        SchemaRoutingDecision finalRouting = routingDecision;
        com.fasterxml.jackson.databind.JsonNode assetSelection = null;
        boolean visualPrepared = false;
        if (verification.passed() && document.ambiguities().isEmpty()
                && assetSelections != null && routingDecision.assets() == null) {
            try {
                finalRouting = schemaRouting.routeAssets(text, document, List.of());
                ProviderExtractionResult visual = provider.bindVisualAssets(text,
                        objectMapper.valueToTree(document), finalRouting, List.of());
                document = visual.document();
                verification = schemaRouting.verify(text, finalRouting, document);
                visualPrepared = true;
                if (!verification.passed()) {
                    document = phraseVerifiedQuestions(text, finalRouting, document, verification);
                }
            } catch (RuntimeException assetFailure) {
                org.slf4j.LoggerFactory.getLogger(ExtractionCoordinator.class)
                        .warn("Visual preparation did not complete for a valid physics specification.", assetFailure);
            }
        }
        if (verification.passed() && document.ambiguities().isEmpty()
                && visualPrepared && assetSelections != null && finalRouting.assets() != null) {
            assetSelection = assetSelections.create(document, finalRouting.assets(), text);
        }
        return new ExtractionResult(
                document,
                provider.path(),
                ExtractionOutcome.API_SUCCESS,
                provider.providerName(),
                provider.modelVersion(),
                result.rawResponse(),
                null,
                finalRouting,
                assetSelection);
    }

    private boolean isRequiredInputPath(String fieldPath) {
        return fieldPath != null && (fieldPath.startsWith("quantities.")
                || fieldPath.startsWith("objects.") && fieldPath.contains(".quantities."));
    }

    private ProviderExtractionResult ensureCapacityConsentQuestion(String text,
            ProviderExtractionResult result, JevSchemaRoutingService.Verification verification) {
        List<String> capacityFindings = verification.findings().stream()
                .filter(this::isCapacityFinding).toList();
        if (capacityFindings.isEmpty()) return result;
        boolean validExisting = hasCapacityConsentQuestion(result.document());
        var cleaned = result.document().withoutAmbiguitiesMatching(item ->
                isCapacityRelatedAmbiguity(item) && (!isCanonicalCapacityAmbiguity(item) || !validExisting));
        if (validExisting) {
            return new ProviderExtractionResult(cleaned, result.rawResponse(), result.assetSelection());
        }
        var document = cleaned
                .withAmbiguities(provider.phraseVerificationQuestions(text, capacityFindings));
        return new ProviderExtractionResult(document, result.rawResponse(), result.assetSelection());
    }

    private boolean isCanonicalCapacityAmbiguity(
            com.example.backend.ai.extraction.model.AmbiguityItem item) {
        return item != null && item.code() != null && item.code().startsWith("jev.capacity.");
    }

    private boolean isCapacityRelatedAmbiguity(
            com.example.backend.ai.extraction.model.AmbiguityItem item) {
        if (item == null) return false;
        String code = item.code() == null ? "" : item.code().toLowerCase(java.util.Locale.ROOT);
        String fieldPath = item.fieldPath() == null ? "" : item.fieldPath().toLowerCase(java.util.Locale.ROOT);
        return "schemaid".equals(fieldPath) || "objects".equals(fieldPath)
                || code.contains("capacity") || fieldPath.contains("capacity");
    }

    private JevSchemaRoutingService.Verification verify(String text, SchemaRoutingDecision routing,
            SpecificationDocument document) {
        return schemaRouting.verify(text, routing, document);
    }

    private String findingFieldPath(String finding) {
        if (finding == null) return null;
        return java.util.Arrays.stream(finding.split(";"))
                .map(String::trim).filter(part -> part.startsWith("fieldPath="))
                .map(part -> part.substring("fieldPath=".length()).trim())
                .filter(path -> !path.isBlank()).findFirst().orElse(null);
    }

    private JevSchemaRoutingService.Verification withRouteCapacityFindings(
            JevSchemaRoutingService.Verification verification, List<String> routeFindings) {
        List<String> combined = new java.util.ArrayList<>(verification.findings());
        if (routeFindings != null) routeFindings.stream().filter(finding -> !combined.contains(finding))
                .forEach(combined::add);
        return new JevSchemaRoutingService.Verification(combined);
    }

    private SpecificationDocument phraseVerifiedQuestions(String text, SchemaRoutingDecision routing,
            SpecificationDocument source, JevSchemaRoutingService.Verification initial) {
        SpecificationDocument candidate = source;
        JevSchemaRoutingService.Verification verification = initial;
        for (int attempt = 0; attempt < 2; attempt++) {
            var invalidPaths = invalidQuestionPaths(verification);
            boolean capacityPending = hasCapacityFinding(verification);
            var actionableFindings = verification.findings().stream()
                    .filter(finding -> !finding.contains("issue=REDUNDANT_ENTITY_AMBIGUITY")
                            && !(capacityPending && "objects".equals(findingFieldPath(finding))))
                    .toList();
            candidate = candidate.withoutAmbiguitiesAt(invalidPaths);
            if (!actionableFindings.isEmpty()) {
                candidate = candidate.withAmbiguities(
                        provider.phraseVerificationQuestions(text, actionableFindings));
            }
            verification = withRouteCapacityFindings(verify(text, routing, candidate),
                    schemaRouting.capacityFindings(routing));
            if (invalidQuestionPaths(verification).isEmpty()) return candidate;
        }
        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                "AI could not produce a clarification without repeating facts already present in the problem.");
    }

    private java.util.Set<String> invalidQuestionPaths(JevSchemaRoutingService.Verification verification) {
        return verification.findings().stream()
                .filter(finding -> finding.contains("issue=AMBIGUITY_REPEATS_SOURCE")
                        || finding.contains("issue=REDUNDANT_ENTITY_AMBIGUITY"))
                .map(this::findingFieldPath).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
    }

    private boolean routedEntityCountSatisfied(SchemaRoutingDecision routing, SpecificationDocument document) {
        return routing.candidates().stream()
                .filter(candidate -> candidate.schemaId().equals(document.schemaId())
                        && candidate.schemaVersion().equals(document.schemaVersion()))
                .flatMap(candidate -> candidate.verificationEvidence().evidenceCodes().stream())
                .filter(signal -> signal.startsWith("JEV_ENTITY_COUNT:"))
                .map(signal -> signal.substring("JEV_ENTITY_COUNT:".length()))
                .filter(value -> !value.startsWith("MORE_THAN_"))
                .map(this::parseEntityCount)
                .filter(java.util.Objects::nonNull)
                .anyMatch(count -> count == document.objects().size());
    }

    private Integer parseEntityCount(String value) {
        try { return Integer.valueOf(value); }
        catch (NumberFormatException ignored) { return null; }
    }

    private boolean hasObjectsAmbiguity(SpecificationDocument document) {
        return document.ambiguities().stream().anyMatch(item -> "objects".equals(item.fieldPath()));
    }

    private boolean hasCapacityFinding(JevSchemaRoutingService.Verification verification) {
        return verification != null && verification.findings().stream().anyMatch(this::isCapacityFinding);
    }

    private boolean hasCapacityConsentQuestion(SpecificationDocument document) {
        return document != null && document.ambiguities().stream().anyMatch(item ->
                item != null && item.code() != null && item.code().startsWith("jev.capacity."));
    }

    private boolean onlyPendingCapacityConsent(JevSchemaRoutingService.Verification verification,
            SpecificationDocument document) {
        return hasCapacityConsentQuestion(document) && hasCapacityFinding(verification)
                && verification.findings().stream().allMatch(this::isCapacityFinding);
    }

    private boolean isCapacityFinding(String finding) {
        return finding != null && (finding.contains("VISUAL_CAPACITY_EXCEEDED")
                || finding.contains("JEV_CAPACITY_EXCEEDED"));
    }

    private boolean hasIssue(JevSchemaRoutingService.Verification verification, String issue) {
        return verification != null && verification.findings().stream().anyMatch(finding -> hasIssue(finding, issue));
    }

    private boolean hasIssue(String finding, String issue) {
        return finding != null && finding.contains("issue=" + issue);
    }

}
