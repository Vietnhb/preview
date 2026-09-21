package com.example.backend.service.reviewer;

import com.example.backend.service.account.CurrentUserService;
import com.example.backend.service.problem.AmbiguityResolutionApplier;
import com.example.backend.service.problem.ProblemResponseMapper;
import com.example.backend.service.problem.SpecificationReadinessService;

import com.example.backend.dto.problem.ResolveAmbiguityRequest;
import com.example.backend.dto.problem.SpecificationResponse;
import com.example.backend.dto.reviewer.ReviewerAmbiguityResponse;
import com.example.backend.entity.problem.AmbiguityCase;
import com.example.backend.entity.enums.AmbiguityStatus;
import com.example.backend.entity.enums.ConfirmationState;
import com.example.backend.entity.reviewer.ReviewerDecision;
import com.example.backend.entity.enums.SubmissionStatus;
import com.example.backend.entity.enums.RoleName;
import com.example.backend.entity.account.User;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.problem.AmbiguityCaseRepository;
import com.example.backend.repository.reviewer.ReviewerDecisionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
@RequiredArgsConstructor
public class ReviewerService {
    private final AmbiguityCaseRepository ambiguityRepository;
    private final ReviewerDecisionRepository decisionRepository;
    private final CurrentUserService currentUserService;
    private final ProblemResponseMapper mapper;
    private final AmbiguityResolutionApplier ambiguityResolutionApplier;
    private final SpecificationReadinessService readinessService;

    public record AmbiguityPage(List<ReviewerAmbiguityResponse> items, int page, int size,
                                long totalElements, int totalPages) { }

    @Transactional(readOnly = true)
    public List<ReviewerAmbiguityResponse> openAmbiguities() {
        User actor = currentUserService.requireCurrentUser();
        Instant now = Instant.now();
        return ambiguityRepository.findQueue(AmbiguityStatus.OPEN, now, actor.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AmbiguityPage openAmbiguitiesPage(String topic, Pageable pageable) {
        User actor = currentUserService.requireCurrentUser();
        Page<AmbiguityCase> result = ambiguityRepository.findQueuePage(AmbiguityStatus.OPEN, Instant.now(), actor.getId(),
                StringUtils.hasText(topic) ? topic.trim() : null, pageable);
        return new AmbiguityPage(result.getContent().stream().map(this::toResponse).toList(), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public ReviewerAmbiguityResponse claim(UUID ambiguityId) {
        User actor = currentUserService.requireCurrentUser();
        AmbiguityCase ambiguity = ambiguityRepository.findByIdForUpdate(ambiguityId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Ambiguity case not found"));
        Instant now = Instant.now();
        if (ambiguity.getStatus() != AmbiguityStatus.OPEN) {
            throw new ApiException(HttpStatus.CONFLICT, "Ambiguity case is no longer open");
        }
        if (ambiguity.getClaimedBy() != null && !ambiguity.getClaimedBy().equals(actor.getId())
                && ambiguity.getClaimExpiresAt() != null && ambiguity.getClaimExpiresAt().isAfter(now)) {
            throw new ApiException(HttpStatus.CONFLICT, "Ambiguity case is claimed by another reviewer");
        }
        ambiguity.setClaimedBy(actor.getId());
        ambiguity.setClaimedAt(now);
        ambiguity.setClaimExpiresAt(now.plusSeconds(30 * 60));
        return toResponse(ambiguity);
    }

    @Transactional
    public void release(UUID ambiguityId) {
        User actor = currentUserService.requireCurrentUser();
        AmbiguityCase ambiguity = ambiguityRepository.findByIdForUpdate(ambiguityId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Ambiguity case not found"));
        if (ambiguity.getClaimedBy() != null && !ambiguity.getClaimedBy().equals(actor.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "Only the claiming reviewer can release this case");
        }
        ambiguity.setClaimedBy(null);
        ambiguity.setClaimedAt(null);
        ambiguity.setClaimExpiresAt(null);
    }

    @Transactional
    public SpecificationResponse resolve(java.util.UUID ambiguityId, ResolveAmbiguityRequest request) {
        if (request == null || !StringUtils.hasText(request.answer())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Resolution answer is required");
        }
        User actor = currentUserService.requireCurrentUser();
        AmbiguityCase ambiguity = ambiguityRepository.findByIdForUpdate(ambiguityId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Ambiguity case not found"));
        if (ambiguity.getStatus() != AmbiguityStatus.OPEN) {
            throw new ApiException(HttpStatus.CONFLICT, "Ambiguity case is already resolved");
        }
        Instant now = Instant.now();
        if (ambiguity.getClaimedBy() != null && !ambiguity.getClaimedBy().equals(actor.getId())
                && ambiguity.getClaimExpiresAt() != null && ambiguity.getClaimExpiresAt().isAfter(now)) {
            throw new ApiException(HttpStatus.CONFLICT, "Ambiguity case is claimed by another reviewer");
        }
        ambiguity.setClaimedBy(actor.getId());
        ambiguity.setClaimedAt(now);
        ambiguity.setClaimExpiresAt(now.plusSeconds(30 * 60));
        try {
            ambiguityResolutionApplier.applyAll(ambiguity.getSpecification(), java.util.Map.of(ambiguity.getCode(), request.answer().trim()));
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not apply the answer to the specification; please retry");
        }
        readinessService.ensureRequiredAmbiguities(ambiguity.getSpecification());
        ReviewerDecision decision = new ReviewerDecision();
        decision.setAmbiguityCase(ambiguity);
        decision.setActorId(actor.getId());
        if (actor.getRole() == null || RoleName.from(actor.getRole().getName()).isEmpty()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Reviewer role is not configured");
        }
        decision.setActorRole(actor.getRole().getName());
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
        ambiguity.setClaimedBy(null);
        ambiguity.setClaimedAt(null);
        ambiguity.setClaimExpiresAt(null);
        return mapper.toSpecification(specification);
    }

    private ReviewerAmbiguityResponse toResponse(AmbiguityCase item) {
        String problemText = item.getSpecification().getSubmission().getEditableText() == null
                ? item.getSpecification().getSubmission().getOriginalText()
                : item.getSpecification().getSubmission().getEditableText();
        return new ReviewerAmbiguityResponse(item.getId(), item.getSpecification().getId(), item.getCode(),
                item.getFieldPath(), item.getQuestion(), item.getOptions(), item.getStatus(), problemText,
                item.getSpecification().getTopic(), item.getSpecification().getQuantities(),
                item.getSpecification().getRelations(), item.getClaimedBy(), item.getClaimedAt(), item.getClaimExpiresAt());
    }
}
