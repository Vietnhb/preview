package com.example.backend.system.problem.mapper;

import com.example.backend.system.problem.dto.AmbiguityContracts;
import com.example.backend.system.problem.dto.ExtractionRunResponse;
import com.example.backend.system.problem.dto.ProblemResponse;
import com.example.backend.system.problem.dto.ProblemSummaryResponse;
import com.example.backend.system.problem.dto.SourceAssetResponse;
import com.example.backend.system.problem.dto.SpecificationResponse;
import com.example.backend.system.problem.model.entity.AmbiguityCase;
import com.example.backend.system.problem.model.entity.ExtractionRun;
import com.example.backend.system.problem.model.entity.ProblemSubmission;
import com.example.backend.system.problem.model.entity.SourceAsset;
import com.example.backend.system.problem.model.entity.Specification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProblemResponseMapper {

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
                specification.getAmbiguityCases().stream().sorted(specification.questionOrder()).map(this::toAmbiguity).toList(),
                specification.getSchemaId(),
                specification.getValidationStatus(),
                specification.getValidationResult(),
                specification.getCreatedAt());
    }

    private java.util.List<String> endConditionCapabilities(Specification specification) {
        return java.util.List.of();
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

    private AmbiguityContracts.Item toAmbiguity(AmbiguityCase ambiguity) {
        return new AmbiguityContracts.Item(
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
