package com.example.backend.system.reviewer.service;

import com.example.backend.exception.ApiException;
import com.example.backend.system.account.model.entity.User;
import com.example.backend.system.account.model.enums.RoleName;
import com.example.backend.system.account.service.CurrentUserService;
import com.example.backend.system.problem.dto.AmbiguityContracts;
import com.example.backend.system.problem.dto.SpecificationResponse;
import com.example.backend.system.problem.mapper.ProblemResponseMapper;
import com.example.backend.system.problem.model.entity.AmbiguityCase;
import com.example.backend.system.problem.model.enums.AmbiguityStatus;
import com.example.backend.system.problem.model.enums.ConfirmationState;
import com.example.backend.system.problem.model.enums.SubmissionStatus;
import com.example.backend.system.problem.repository.AmbiguityCaseRepository;
import com.example.backend.system.problem.service.AmbiguityResolutionApplier;
import com.example.backend.system.problem.service.SpecificationReadinessService;
import com.example.backend.system.reviewer.model.entity.ReviewerDecision;
import com.example.backend.system.reviewer.repository.ReviewerDecisionRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
    public List<AmbiguityContracts.Review> openAmbiguities() {
        User actor = currentUserService.requireCurrentUser();
        Instant now = Instant.now();
        return ambiguityRepository.findQueue(AmbiguityStatus.OPEN, now, actor.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AmbiguityContracts.Page openAmbiguitiesPage(String topic, Pageable pageable) {
        User actor = currentUserService.requireCurrentUser();
        Page<AmbiguityCase> result = ambiguityRepository.findQueuePage(AmbiguityStatus.OPEN, Instant.now(), actor.getId(),
                StringUtils.hasText(topic) ? topic.trim() : null, pageable);
        return new AmbiguityContracts.Page(result.getContent().stream().map(this::toResponse).toList(), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public AmbiguityContracts.Review claim(UUID ambiguityId) {
        User actor = currentUserService.requireCurrentUser();
        AmbiguityCase ambiguity = ambiguityRepository.findByIdForUpdate(ambiguityId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy câu hỏi cần xác minh."));
        Instant now = Instant.now();
        if (ambiguity.getStatus() != AmbiguityStatus.OPEN) {
            throw ApiException.conflict("Câu hỏi này đã được xử lý.");
        }
        if (ambiguity.getClaimedBy() != null && !ambiguity.getClaimedBy().equals(actor.getId())
                && ambiguity.getClaimExpiresAt() != null && ambiguity.getClaimExpiresAt().isAfter(now)) {
            throw ApiException.conflict("Một chuyên gia khác đang xử lý câu hỏi này.");
        }
        ambiguity.setClaimedBy(actor.getId());
        ambiguity.setClaimedAt(now);
        ambiguity.setClaimExpiresAt(now.plusSeconds(30L * 60L));
        return toResponse(ambiguity);
    }

    @Transactional
    public void release(UUID ambiguityId) {
        User actor = currentUserService.requireCurrentUser();
        AmbiguityCase ambiguity = ambiguityRepository.findByIdForUpdate(ambiguityId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy câu hỏi cần xác minh."));
        if (ambiguity.getClaimedBy() != null && !ambiguity.getClaimedBy().equals(actor.getId())) {
            throw ApiException.conflict("Chỉ người đang giữ câu hỏi mới trả lại được.");
        }
        ambiguity.setClaimedBy(null);
        ambiguity.setClaimedAt(null);
        ambiguity.setClaimExpiresAt(null);
    }

    @Transactional
    public SpecificationResponse resolve(java.util.UUID ambiguityId, AmbiguityContracts.Answer request) {
        if (request == null || !StringUtils.hasText(request.answer())) {
            throw ApiException.badRequest("Vui lòng nhập câu trả lời.");
        }
        User actor = currentUserService.requireCurrentUser();
        AmbiguityCase ambiguity = ambiguityRepository.findByIdForUpdate(ambiguityId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy câu hỏi cần xác minh."));
        if (ambiguity.getStatus() != AmbiguityStatus.OPEN) {
            throw ApiException.conflict("Câu hỏi này đã được trả lời.");
        }
        Instant now = Instant.now();
        if (ambiguity.getClaimedBy() != null && !ambiguity.getClaimedBy().equals(actor.getId())
                && ambiguity.getClaimExpiresAt() != null && ambiguity.getClaimExpiresAt().isAfter(now)) {
            throw ApiException.conflict("Một chuyên gia khác đang xử lý câu hỏi này.");
        }
        ambiguity.setClaimedBy(actor.getId());
        ambiguity.setClaimedAt(now);
        ambiguity.setClaimExpiresAt(now.plusSeconds(30L * 60L));
        try {
            ambiguityResolutionApplier.applyAll(ambiguity.getSpecification(), java.util.Map.of(ambiguity.getCode(), request.answer().trim()));
        } catch (RuntimeException exception) {
            throw ApiException.upstream("Không áp dụng được câu trả lời vào đề bài. Hãy ghi rõ giá trị kèm đơn vị rồi thử lại.");
        }
        readinessService.ensureRequiredAmbiguities(ambiguity.getSpecification());
        ReviewerDecision decision = new ReviewerDecision();
        decision.setAmbiguityCase(ambiguity);
        decision.setActorId(actor.getId());
        if (actor.getRole() == null || RoleName.from(actor.getRole().getName()).isEmpty()) {
            throw ApiException.forbidden("Tài khoản chưa được cấu hình vai trò kiểm duyệt.");
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

    private AmbiguityContracts.Review toResponse(AmbiguityCase item) {
        String problemText = item.getSpecification().getSubmission().getEditableText() == null
                ? item.getSpecification().getSubmission().getOriginalText()
                : item.getSpecification().getSubmission().getEditableText();
        return new AmbiguityContracts.Review(item.getId(), item.getSpecification().getId(), item.getCode(),
                item.getFieldPath(), item.getQuestion(), item.getOptions(), item.getStatus(), problemText,
                item.getSpecification().getTopic(), item.getSpecification().getQuantities(),
                item.getSpecification().getRelations(), item.getClaimedBy(), item.getClaimedAt(), item.getClaimExpiresAt());
    }
}
