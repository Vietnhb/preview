package com.example.backend.service;

import com.example.backend.dto.problem.ResolveAmbiguityRequest;
import com.example.backend.dto.problem.SpecificationResponse;
import com.example.backend.dto.reviewer.ReviewerAmbiguityResponse;
import com.example.backend.entity.AmbiguityCase;
import com.example.backend.enums.AmbiguityStatus;
import com.example.backend.enums.ConfirmationState;
import com.example.backend.entity.ReviewerDecision;
import com.example.backend.enums.SubmissionStatus;
import com.example.backend.entity.User;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.AmbiguityCaseRepository;
import com.example.backend.repository.ReviewerDecisionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewerService {
    private final AmbiguityCaseRepository ambiguityRepository;
    private final ReviewerDecisionRepository decisionRepository;
    private final CurrentUserService currentUserService;
    private final ProblemResponseMapper mapper;
    private final AmbiguityResolutionApplier ambiguityResolutionApplier;
    private final SpecificationReadinessService readinessService;

    @Transactional(readOnly = true)
    public List<ReviewerAmbiguityResponse> openAmbiguities() {
        return ambiguityRepository.findByStatus(AmbiguityStatus.OPEN).stream()
                .map(item -> new ReviewerAmbiguityResponse(item.getId(), item.getSpecification().getId(), item.getCode(),
                        item.getFieldPath(), item.getQuestion(), item.getOptions(), item.getStatus(),
                        item.getSpecification().getSubmission().getEditableText() == null
                            ? item.getSpecification().getSubmission().getOriginalText() : item.getSpecification().getSubmission().getEditableText(),
                        item.getSpecification().getTopic(), item.getSpecification().getQuantities(), item.getSpecification().getRelations()))
                .toList();
    }

    @Transactional
    public SpecificationResponse resolve(java.util.UUID ambiguityId, ResolveAmbiguityRequest request) {
        if (request == null || !StringUtils.hasText(request.answer())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Resolution answer is required");
        }
        User actor = currentUserService.requireCurrentUser();
        AmbiguityCase ambiguity = ambiguityRepository.findById(ambiguityId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Ambiguity case not found"));
        if (ambiguity.getStatus() != AmbiguityStatus.OPEN) {
            throw new ApiException(HttpStatus.CONFLICT, "Ambiguity case is already resolved");
        }
        Instant now = Instant.now();
        try {
            ambiguityResolutionApplier.applyAll(ambiguity.getSpecification(), java.util.Map.of(ambiguity.getCode(), request.answer().trim()));
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not apply the answer to the specification; please retry");
        }
        readinessService.ensureRequiredAmbiguities(ambiguity.getSpecification());
        ReviewerDecision decision = new ReviewerDecision();
        decision.setAmbiguityCase(ambiguity);
        decision.setActorId(actor.getId());
        decision.setActorRole(actor.getRole() == null ? "CONTENT_REVIEWER" : actor.getRole().getName());
        decision.setDecisionState(ambiguity.getStatus() == AmbiguityStatus.RESOLVED ? "ADJUDICATED" : "NEEDS_CLARIFICATION");
        decision.setAnswer(request.answer().trim());
        decision.setComment(request.comment());
        decision.setDecidedAt(now);
        decisionRepository.save(decision);
        var specification = ambiguity.getSpecification();
        boolean hasOpen = specification.getAmbiguityCases().stream().anyMatch(item -> item.getStatus() == AmbiguityStatus.OPEN);
        if (!hasOpen) {
            specification.setConfirmationState(ConfirmationState.CONFIRMED);
            specification.getSubmission().setStatus(SubmissionStatus.READY_FOR_VALIDATION);
        }
        return mapper.toSpecification(specification);
    }
}
