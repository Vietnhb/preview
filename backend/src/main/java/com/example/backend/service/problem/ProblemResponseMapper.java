package com.example.backend.service.problem;

import org.springframework.stereotype.Component;

import com.example.backend.ai.extraction.prompt.CandidateContractProjection;
import com.example.backend.dto.problem.AmbiguityResponse;
import com.example.backend.dto.problem.ExtractionRunResponse;
import com.example.backend.dto.problem.ProblemResponse;
import com.example.backend.dto.problem.ProblemSummaryResponse;
import com.example.backend.dto.problem.SourceAssetResponse;
import com.example.backend.dto.problem.SpecificationResponse;
import com.example.backend.entity.problem.AmbiguityCase;
import com.example.backend.entity.problem.ExtractionRun;
import com.example.backend.entity.problem.ProblemSubmission;
import com.example.backend.entity.problem.SourceAsset;
import com.example.backend.entity.problem.Specification;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ProblemResponseMapper {

    private final SchemaDefinitionService schemaDefinitions;

    public ProblemSummaryResponse toSummary(ProblemSubmission problem) {
        return new ProblemSummaryResponse(
                problem.getId(),
                problem.getSourceMode(),
                problem.getStatus(),
                problem.getEditableText(),
                problem.getLesson() == null ? null : problem.getLesson().getId(),
                problem.getCurrentSpecification() == null ? null : problem.getCurrentSpecification().getId(),
                problem.getCreatedAt(),
                problem.getUpdatedAt());
    }

    public ProblemResponse toResponse(ProblemSubmission problem) {
        return new ProblemResponse(
                problem.getId(),
                problem.getOwner().getId(),
                problem.getLesson() == null ? null : problem.getLesson().getId(),
                problem.getSourceMode(),
                problem.getOriginalText(),
                problem.getEditableText(),
                problem.getStatus(),
                problem.getCurrentSpecification() == null ? null : toSpecification(problem.getCurrentSpecification()),
                problem.getSourceAssets().stream().map(this::toAsset).toList(),
                problem.getExtractionRuns().stream().map(this::toExtractionRun).toList(),
                problem.getCreatedAt(),
                problem.getUpdatedAt());
    }

    public SpecificationResponse toSpecification(Specification specification) {
        return new SpecificationResponse(
                specification.getId(), specification.getContractVersion(),
                specification.getSchemaVersion(),
                specification.getTopic(),
                specification.getConfidence(),
                specification.getObjects(),
                specification.getQuantities(),
                specification.getRelations(),
                specification.getEndCondition(),
                endConditionCapabilities(specification),
                specification.getAmbiguity(),
                specification.getConfirmationState(),
                specification.getAmbiguityCases().stream().map(this::toAmbiguity).toList(),
                specification.getSchemaId(),
                specification.getValidationStatus(),
                specification.getValidationResult(),
                specification.getCreatedAt());
    }

    private java.util.List<String> endConditionCapabilities(Specification specification) {
        if (specification.getSchemaId() == null || specification.getSchemaId().isBlank()) return java.util.List.of();
        try {
            return CandidateContractProjection.declaredEndConditionCapabilities(
                    schemaDefinitions.approvedDefinitionSnapshot(specification.getSchemaId()).definition());
        } catch (RuntimeException ignored) {
            // Keep legacy specification reads available when their historical
            // schema is no longer present in the active catalog.
            return java.util.List.of();
        }
    }

    private SourceAssetResponse toAsset(SourceAsset asset) {
        return new SourceAssetResponse(
                asset.getId(),
                asset.getOriginalFilename(),
                asset.getContentType(),
                asset.getContentLength(),
                asset.getOcrStatus(),
                asset.getOcrText(),
                asset.getOcrError());
    }

    private ExtractionRunResponse toExtractionRun(ExtractionRun run) {
        return new ExtractionRunResponse(
                run.getId(),
                run.getExtractionPath(),
                run.getProviderName(),
                run.getModelVersion(),
                run.getStatus(),
                run.getOutcome(),
                run.getErrorMessage(),
                run.getCreatedAt());
    }

    private AmbiguityResponse toAmbiguity(AmbiguityCase ambiguity) {
        return new AmbiguityResponse(
                ambiguity.getId(),
                ambiguity.getCode(),
                ambiguity.getFieldPath(),
                ambiguity.getQuestion(),
                ambiguity.getOptions(),
                ambiguity.getStatus(),
                ambiguity.getResolution(),
                ambiguity.getResolvedAt());
    }
}
