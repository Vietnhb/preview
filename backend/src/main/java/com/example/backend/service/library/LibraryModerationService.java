package com.example.backend.service.library;

import com.example.backend.service.account.CurrentUserService;
import com.example.backend.service.account.AccountAccessService;
import com.example.backend.entity.account.User;

import com.example.backend.dto.library.LibraryItemResponse;
import com.example.backend.entity.library.LibraryItem;
import com.example.backend.entity.library.LibraryModerationAudit;
import com.example.backend.entity.enums.LibraryModerationStatus;
import com.example.backend.entity.enums.Visibility;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.library.LibraryItemRepository;
import com.example.backend.repository.library.LibraryModerationAuditRepository;
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
public class LibraryModerationService {
    public record ModerationAuditView(UUID id, UUID itemId, String reviewerEmail, LibraryModerationStatus fromStatus,
                                      LibraryModerationStatus toStatus, String comment, Instant decidedAt) { }
    private final LibraryItemRepository items;
    private final LibraryModerationAuditRepository audits;
    private final CurrentUserService currentUser;
    private final AccountAccessService access;

    @Transactional(readOnly = true)
    public List<LibraryItemResponse> queue(LibraryModerationStatus status) {
        requirePublicReviewer();
        return items.findByVisibilityInAndModerationStatusOrderByCreatedAtAsc(java.util.Set.of(Visibility.PUBLIC),
                        status == null ? LibraryModerationStatus.PENDING : status)
                .stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public PageView page(LibraryModerationStatus status, Pageable pageable) {
        requirePublicReviewer();
        Page<LibraryItem> result = items.findByVisibilityInAndModerationStatusOrderByCreatedAtAsc(
                java.util.Set.of(Visibility.PUBLIC),
                status == null ? LibraryModerationStatus.PENDING : status, pageable);
        return new PageView(result.getContent().stream().map(this::response).toList(), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    public record PageView(List<LibraryItemResponse> items, int page, int size, long totalElements, int totalPages) { }

    @Transactional
    public LibraryItemResponse moderate(UUID id, LibraryModerationStatus status, String comment) {
        var actor = requirePublicReviewer();
        LibraryItem item = items.findByIdForUpdate(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Shared library item not found"));
        if (item.getVisibility() != Visibility.PUBLIC)
            throw new ApiException(HttpStatus.FORBIDDEN, "REVIEWER chỉ duyệt nội dung công khai toàn hệ thống.");
        return decide(actor, item, status, comment);
    }

    @Transactional(readOnly = true)
    public List<LibraryItemResponse> schoolQueue(UUID schoolId, LibraryModerationStatus status) {
        requireSchoolReviewer(schoolId);
        return items.findSchoolModerationItems(schoolId, status == null ? LibraryModerationStatus.PENDING : status)
                .stream().filter(item -> schoolId.toString().equals(item.getSharedInstitutionId())
                        || !StringUtils.hasText(item.getSharedInstitutionId())).map(this::response).toList();
    }

    @Transactional
    public LibraryItemResponse moderateForSchool(UUID schoolId, UUID id, LibraryModerationStatus status, String comment) {
        User actor = requireSchoolReviewer(schoolId);
        LibraryItem item = items.findByIdForUpdate(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy tài nguyên của trường."));
        if (item.getVisibility() != Visibility.SHARED || item.getOwner() == null || item.getOwner().getSchool() == null
                || !schoolId.equals(item.getOwner().getSchool().getId())
                || StringUtils.hasText(item.getSharedInstitutionId()) && !schoolId.toString().equals(item.getSharedInstitutionId()))
            throw new ApiException(HttpStatus.FORBIDDEN, "Chỉ được duyệt nội dung chia sẻ trong trường của mình.");
        if (actor.getId().equals(item.getOwner().getId()))
            throw new ApiException(HttpStatus.CONFLICT, "Không thể tự duyệt mô phỏng của mình.");
        if (status == LibraryModerationStatus.FEATURED)
            throw new ApiException(HttpStatus.BAD_REQUEST, "Nội dung cấp trường không được đưa lên trang chủ.");
        return decide(actor, item, status, comment);
    }

    private User requirePublicReviewer() {
        User actor = currentUser.requireCurrentUser();
        if (!access.canReviewPublic(actor)) throw new ApiException(HttpStatus.FORBIDDEN, "Không có quyền kiểm duyệt công khai.");
        return actor;
    }

    private User requireSchoolReviewer(UUID schoolId) {
        User actor = currentUser.requireCurrentUser();
        if (!access.isDepartmentHead(actor) || actor.getSchool() == null || !schoolId.equals(actor.getSchool().getId()))
            throw new ApiException(HttpStatus.FORBIDDEN, "Chỉ Trưởng bộ môn của trường được duyệt nội dung nội bộ.");
        return actor;
    }

    private LibraryItemResponse decide(User actor, LibraryItem item, LibraryModerationStatus status, String comment) {
        if (!item.isActive() && item.getModerationStatus() != LibraryModerationStatus.REMOVED)
            throw new ApiException(HttpStatus.CONFLICT, "Tài nguyên đã được chủ sở hữu gỡ khỏi thư viện.");
        if (status == null || status == LibraryModerationStatus.PENDING)
            throw new ApiException(HttpStatus.BAD_REQUEST, "A final moderation status is required");
        if ((status == LibraryModerationStatus.REJECTED || status == LibraryModerationStatus.REMOVED)
                && !StringUtils.hasText(comment)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A reason is required when rejecting or removing content");
        }
        LibraryModerationAudit audit = new LibraryModerationAudit(); audit.setLibraryItem(item); audit.setReviewer(actor);
        audit.setFromStatus(item.getModerationStatus()); audit.setToStatus(status); audit.setComment(comment == null ? null : comment.trim()); audits.save(audit);
        item.setModerationStatus(status);
        item.setModerationComment(comment == null ? null : comment.trim());
        item.setModeratedAt(Instant.now()); item.setModeratedBy(actor);
        item.setActive(status != LibraryModerationStatus.REMOVED);
        return response(items.save(item));
    }

    @Transactional(readOnly = true)
    public List<ModerationAuditView> history(UUID id) {
        requirePublicReviewer();
        items.findById(id).filter(item -> item.getVisibility() == Visibility.PUBLIC)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Public item not found"));
        return audits.findTop100ByLibraryItemIdOrderByDecidedAtDesc(id).stream().map(item -> new ModerationAuditView(item.getId(), item.getLibraryItem().getId(), item.getReviewer().getEmail(), item.getFromStatus(), item.getToStatus(), item.getComment(), item.getDecidedAt())).toList();
    }

    private LibraryItemResponse response(LibraryItem item) {
        return new LibraryItemResponse(item.getId(), item.getSimulation() == null ? null : item.getSimulation().getId(),
                item.getFolder() == null ? null : item.getFolder().getId(), item.getLesson() == null ? null : item.getLesson().getId(),
                item.getSpecification().getId(), item.getTitle(), item.getSpecification().getTopic(), item.getSpecification().getValidationStatus(),
                item.getVisibility(), item.getCreatedAt(), item.getModerationStatus(), item.getModerationComment(),
                item.getOwner() == null ? null : item.getOwner().getId(),
                item.getOwner() == null ? null : item.getOwner().getFullName(),
                item.getOwner() == null || item.getOwner().getSchool() == null ? null : item.getOwner().getSchool().getId(),
                item.getOwner() == null || item.getOwner().getSchool() == null ? null : item.getOwner().getSchool().getName());
    }
}
