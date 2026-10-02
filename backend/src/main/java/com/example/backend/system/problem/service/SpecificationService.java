package com.example.backend.system.problem.service;

import com.example.backend.exception.ApiException;
import com.example.backend.system.account.model.entity.User;
import com.example.backend.system.account.service.CurrentUserService;
import com.example.backend.system.problem.dto.AmbiguityContracts;
import com.example.backend.system.problem.dto.SpecificationResponse;
import com.example.backend.system.problem.dto.ValidationReadinessResponse;
import com.example.backend.system.problem.mapper.ProblemResponseMapper;
import com.example.backend.system.problem.model.entity.AmbiguityCase;
import com.example.backend.system.problem.model.entity.Specification;
import com.example.backend.system.problem.model.enums.AmbiguityStatus;
import com.example.backend.system.problem.model.enums.ConfirmationState;
import com.example.backend.system.problem.model.enums.SubmissionStatus;
import com.example.backend.system.problem.repository.AmbiguityCaseRepository;
import com.example.backend.system.problem.repository.SpecificationRepository;
import com.example.backend.system.reviewer.model.entity.ReviewerDecision;
import com.example.backend.system.reviewer.repository.ReviewerDecisionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
    public SpecificationResponse resolve(UUID specificationId, UUID ambiguityId, AmbiguityContracts.Answer request) {
        if (request == null || !StringUtils.hasText(request.answer())) {
            throw ApiException.badRequest("Ambiguity answer is required");
        }

        User actor = currentUserService.requireCurrentUser();
        Specification specification = specificationRepository.findByIdAndSubmissionOwner(specificationId, actor)
                .orElseThrow(() -> ApiException.notFound("Specification not found"));
        AmbiguityCase ambiguity = ambiguityRepository.findByIdAndSpecification(ambiguityId, specification)
                .orElseThrow(() -> ApiException.notFound("Ambiguity case not found"));
        if (ambiguity.getStatus() != AmbiguityStatus.OPEN) {
            throw ApiException.conflict("Ambiguity case is already resolved");
        }

        Instant now = Instant.now();
        try {
            ambiguityResolutionApplier.applyAll(specification, Map.of(ambiguity.getCode(), request.answer().trim()));
        } catch (RuntimeException exception) {
            throw ApiException.upstream("AI ambiguity confirmation failed; specification was not guessed locally");
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
                .orElseThrow(() -> ApiException.notFound("Specification not found"));
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
