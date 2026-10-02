package com.example.backend.system.library.service;

import com.example.backend.exception.ApiException;
import com.example.backend.system.account.model.entity.User;
import com.example.backend.system.account.service.AccountAccessService;
import com.example.backend.system.account.service.CurrentUserService;
import com.example.backend.system.library.dto.LibraryItemResponse;
import com.example.backend.system.library.dto.LibraryModeration;
import com.example.backend.system.library.model.entity.LibraryItem;
import com.example.backend.system.library.model.entity.LibraryModerationAudit;
import com.example.backend.system.library.model.enums.LibraryModerationStatus;
import com.example.backend.system.library.model.enums.Visibility;
import com.example.backend.system.library.repository.LibraryItemRepository;
import com.example.backend.system.library.repository.LibraryModerationAuditRepository;
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
public class LibraryModerationService {
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
    public LibraryModeration.Page page(LibraryModerationStatus status, Pageable pageable) {
        requirePublicReviewer();
        Page<LibraryItem> result = items.findByVisibilityInAndModerationStatusOrderByCreatedAtAsc(
                java.util.Set.of(Visibility.PUBLIC),
                status == null ? LibraryModerationStatus.PENDING : status, pageable);
        return new LibraryModeration.Page(result.getContent().stream().map(this::response).toList(), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public LibraryItemResponse moderate(UUID id, LibraryModerationStatus status, String comment) {
        var actor = requirePublicReviewer();
        LibraryItem item = items.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy mô phỏng cần duyệt."));
        if (item.getVisibility() != Visibility.PUBLIC)
            throw ApiException.forbidden("REVIEWER chỉ duyệt nội dung công khai toàn hệ thống.");
        if (item.getOwner() != null && actor.getId().equals(item.getOwner().getId()))
            throw ApiException.conflict("Không thể tự duyệt mô phỏng của mình.");
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
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy tài nguyên của trường."));
        if (item.getVisibility() != Visibility.SHARED || item.getOwner() == null || item.getOwner().getSchool() == null
                || !schoolId.equals(item.getOwner().getSchool().getId())
                || StringUtils.hasText(item.getSharedInstitutionId()) && !schoolId.toString().equals(item.getSharedInstitutionId()))
            throw ApiException.forbidden("Chỉ được duyệt nội dung chia sẻ trong trường của mình.");
        if (actor.getId().equals(item.getOwner().getId()))
            throw ApiException.conflict("Không thể tự duyệt mô phỏng của mình.");
        if (status == LibraryModerationStatus.FEATURED)
            throw ApiException.badRequest("Nội dung cấp trường không được đưa lên trang chủ.");
        return decide(actor, item, status, comment);
    }

    private User requirePublicReviewer() {
        User actor = currentUser.requireCurrentUser();
        if (!access.canReviewPublic(actor)) throw ApiException.forbidden("Không có quyền kiểm duyệt công khai.");
        return actor;
    }

    private User requireSchoolReviewer(UUID schoolId) {
        User actor = currentUser.requireCurrentUser();
        if (!access.isDepartmentHead(actor) || actor.getSchool() == null || !schoolId.equals(actor.getSchool().getId()))
            throw ApiException.forbidden("Chỉ Trưởng bộ môn của trường được duyệt nội dung nội bộ.");
        return actor;
    }

    private LibraryItemResponse decide(User actor, LibraryItem item, LibraryModerationStatus status, String comment) {
        if (!item.isActive() && item.getModerationStatus() != LibraryModerationStatus.REMOVED)
            throw ApiException.conflict("Tài nguyên đã được chủ sở hữu gỡ khỏi thư viện.");
        if (status == null || status == LibraryModerationStatus.PENDING)
            throw ApiException.badRequest("Vui lòng chọn quyết định kiểm duyệt.");
        if ((status == LibraryModerationStatus.REJECTED || status == LibraryModerationStatus.REMOVED)
                && !StringUtils.hasText(comment)) {
            throw ApiException.badRequest("Cần nhập lý do khi từ chối hoặc gỡ nội dung.");
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
    public List<LibraryModeration.Audit> history(UUID id) {
        requirePublicReviewer();
        items.findById(id).filter(item -> item.getVisibility() == Visibility.PUBLIC)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy mô phỏng công khai."));
        return audits.findTop100ByLibraryItemIdOrderByDecidedAtDesc(id).stream().map(item -> new LibraryModeration.Audit(item.getId(), item.getLibraryItem().getId(), item.getReviewer().getEmail(), item.getFromStatus(), item.getToStatus(), item.getComment(), item.getDecidedAt())).toList();
    }

    private LibraryItemResponse response(LibraryItem item) {
        return LibraryItemResponse.from(item);
    }
}
