package com.example.backend.service.problem;

import com.example.backend.service.account.CurrentUserService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.backend.dto.problem.ResolveAmbiguityRequest;
import com.example.backend.dto.problem.SpecificationResponse;
import com.example.backend.dto.problem.ValidationReadinessResponse;
import com.example.backend.entity.problem.AmbiguityCase;
import com.example.backend.entity.enums.AmbiguityStatus;
import com.example.backend.entity.enums.ConfirmationState;
import com.example.backend.entity.reviewer.ReviewerDecision;
import com.example.backend.entity.problem.Specification;
import com.example.backend.entity.enums.SubmissionStatus;
import com.example.backend.entity.account.User;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.problem.AmbiguityCaseRepository;
import com.example.backend.repository.reviewer.ReviewerDecisionRepository;
import com.example.backend.repository.problem.SpecificationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SpecificationService {

    private final SpecificationRepository specificationRepository;
    private final AmbiguityCaseRepository ambiguityRepository;
    private final ReviewerDecisionRepository decisionRepository;
    private final CurrentUserService currentUserService;
    private final ProblemResponseMapper mapper;
    private final AmbiguityResolutionApplier ambiguityResolutionApplier;
    private final SpecificationReadinessService readinessService;

    @Transactional(readOnly = true)
    public SpecificationResponse get(UUID id) {
        return mapper.toSpecification(requireOwnedSpecification(id));
    }

    @Transactional
    public SpecificationResponse resolve(UUID specificationId, UUID ambiguityId, ResolveAmbiguityRequest request) {
        if (request == null || !StringUtils.hasText(request.answer())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Ambiguity answer is required");
        }

        User actor = currentUserService.requireCurrentUser();
        Specification specification = specificationRepository.findByIdAndSubmissionOwner(specificationId, actor)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Specification not found"));
        AmbiguityCase ambiguity = ambiguityRepository.findByIdAndSpecification(ambiguityId, specification)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Ambiguity case not found"));
        if (ambiguity.getStatus() != AmbiguityStatus.OPEN) {
            throw new ApiException(HttpStatus.CONFLICT, "Ambiguity case is already resolved");
        }

        Instant now = Instant.now();
        try {
            ambiguityResolutionApplier.applyAll(specification, Map.of(ambiguity.getCode(), request.answer().trim()));
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "AI ambiguity confirmation failed; specification was not guessed locally");
        }
        readinessService.ensureRequiredAmbiguities(specification);
        ReviewerDecision decision = new ReviewerDecision();
        decision.setAmbiguityCase(ambiguity);
        decision.setActorId(actor.getId());
        decision.setActorRole(actor.getRole().getName());
        decision.setDecisionState(ambiguity.getStatus() == AmbiguityStatus.RESOLVED ? "CONFIRMED" : "NEEDS_CLARIFICATION");
        decision.setAnswer(request.answer().trim());
        decision.setComment(trimToNull(request.comment()));
        decision.setDecidedAt(now);
        decisionRepository.save(decision);

        specification.getSubmission().setStatus(specification.getConfirmationState() == ConfirmationState.UNRESOLVED
                ? SubmissionStatus.NEEDS_CONFIRMATION : SubmissionStatus.READY_FOR_VALIDATION);
        return mapper.toSpecification(specification);
    }

    @Transactional(readOnly = true)
    public ValidationReadinessResponse readiness(UUID id) {
        Specification specification = requireOwnedSpecification(id);
        List<String> blockers = readinessService.blockers(specification);
        return new ValidationReadinessResponse(specification.getId(), blockers.isEmpty(), List.copyOf(blockers));
    }

    private Specification requireOwnedSpecification(UUID id) {
        User owner = currentUserService.requireCurrentUser();
        return specificationRepository.findByIdAndSubmissionOwner(id, owner)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Specification not found"));
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
