package com.example.backend.ai.extraction;

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
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI extraction provider is not configured");
        }
        SchemaRoutingDecision routingDecision = schemaRouting.route(text);
        requireSupportedSchema(routingDecision);
        List<String> routeFindings = "JEV_CAPACITY_EXCEEDED".equals(routingDecision.reasonCode())
                ? schemaRouting.capacityFindings(routingDecision) : List.of();
        ProviderExtractionResult result = routeFindings.isEmpty()
                ? provider.extract(text, routingDecision)
                : provider.extract(text, routingDecision, routeFindings);
        var verification = schemaRouting.verify(routingDecision, result.document());
        if (!verification.passed()) {
            if (hasCapacityFinding(verification)) {
                result = ensureCapacityConsentQuestion(text, result, verification);
                verification = schemaRouting.verify(routingDecision, result.document());
            }
            if (onlyPendingCapacityConsent(verification, result.document())) {
                return finish(routingDecision, result);
            }
            if (hasIssue(verification, "ENTITY_COUNT_MISMATCH")) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "AI extraction could not preserve the JEV-confirmed physical object count.");
            }
            result = new ProviderExtractionResult(
                    phraseVerifiedQuestions(text, routingDecision, result.document(), verification),
                    result.rawResponse());
        }
        return finish(routingDecision, result);
    }

    private ExtractionResult finish(SchemaRoutingDecision routingDecision, ProviderExtractionResult result) {
        SpecificationDocument document = result.document();
        boolean compatibilityPending = document.ambiguities().stream()
                .anyMatch(item -> !isRequiredInputPath(item.fieldPath()));
        if (compatibilityPending) {
            document = document.withoutAmbiguitiesMatching(item -> isRequiredInputPath(item.fieldPath()));
        }
        return new ExtractionResult(
                document,
                provider.path(),
                ExtractionOutcome.API_SUCCESS,
                provider.providerName(),
                provider.modelVersion(),
                result.rawResponse(),
                null,
                routingDecision);
    }

    static void requireSupportedSchema(SchemaRoutingDecision routingDecision) {
        if (routingDecision.status() == SchemaRoutingDecision.Status.AMBIGUOUS
                && !"JEV_CAPACITY_EXCEEDED".equals(routingDecision.reasonCode())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Xin lỗi, PhysLive chưa hỗ trợ mô tả này vì chưa xác định được schema vật lý phù hợp.");
        }
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
            return new ProviderExtractionResult(cleaned, result.rawResponse());
        }
        var document = cleaned
                .withAmbiguities(provider.phraseVerificationQuestions(text, capacityFindings));
        return new ProviderExtractionResult(document, result.rawResponse());
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
        verification = withRouteCapacityFindings(schemaRouting.verify(routing, candidate),
                schemaRouting.capacityFindings(routing));
        if (!invalidQuestionPaths(verification).isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "AI could not produce a clarification without repeating facts already present in the problem.");
        }
        return candidate;
    }

    private java.util.Set<String> invalidQuestionPaths(JevSchemaRoutingService.Verification verification) {
        return verification.findings().stream()
                .filter(finding -> finding.contains("issue=AMBIGUITY_REPEATS_SOURCE")
                        || finding.contains("issue=REDUNDANT_ENTITY_AMBIGUITY"))
                .map(this::findingFieldPath).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
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
