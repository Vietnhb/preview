package com.example.backend.system.support.service;

import com.example.backend.exception.ApiException;
import com.example.backend.system.account.model.entity.User;
import com.example.backend.system.account.model.enums.RoleName;
import com.example.backend.system.account.service.CurrentUserService;
import com.example.backend.system.support.dto.SupportContracts.CreateSupportRequest;
import com.example.backend.system.support.dto.SupportContracts.SupportView;
import com.example.backend.system.support.dto.SupportContracts.UpdateSupportRequest;
import com.example.backend.system.support.dto.SupportContracts;
import com.example.backend.system.support.model.entity.SupportItem;
import com.example.backend.system.support.model.enums.SupportKind;
import com.example.backend.system.support.model.enums.SupportStatus;
import com.example.backend.system.support.repository.SupportItemRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SupportService {
    private final SupportItemRepository repository;
    private final CurrentUserService currentUser;

    @Transactional
    public SupportView create(SupportKind kind, CreateSupportRequest request) {
        if (kind == null) {
            throw ApiException.badRequest("Vui lòng chọn loại yêu cầu hỗ trợ");
        }
        if (request == null || request.subject() == null || request.subject().isBlank()
                || request.content() == null || request.content().isBlank()) {
            throw ApiException.badRequest("Vui lòng nhập tiêu đề và nội dung");
        }
        if (request.subject().trim().length() > 180 || request.content().trim().length() > 10000) {
            throw ApiException.badRequest("Tiêu đề hoặc nội dung quá dài");
        }
        User sender = currentUser.requireCurrentUser();
        SupportItem item = new SupportItem();
        item.setKind(kind);
        item.setSender(sender);
        item.setSubject(request.subject().trim());
        item.setContent(request.content().trim());
        item.setStatus(SupportStatus.OPEN);
        return view(repository.save(item));
    }

    @Transactional
    public SupportView createComplaint(SupportContracts.CreateComplaintRequest request) {
        User sender = currentUser.requireCurrentUser();
        if (!hasRole(sender, RoleName.STAFF) && !hasRole(sender, RoleName.MANAGER))
            throw ApiException.forbidden("Chỉ giáo viên mới gửi được khiếu nại về mô phỏng.");
        SupportView created = create(SupportKind.COMPLAINT, new CreateSupportRequest(request.subject(), request.content()));
        if (request.simulationId() == null) return created;
        SupportItem item = repository.findById(created.id()).orElseThrow(() -> ApiException.notFound("Không tìm thấy yêu cầu hỗ trợ"));
        item.setSimulationId(request.simulationId());
        return view(repository.save(item));
    }
    @Transactional(readOnly = true)
    public List<SupportView> complaintsForReview() {
        requireReviewer();
        return repository.findTop200ByKindOrderByCreatedAtDesc(SupportKind.COMPLAINT).stream().map(this::view).toList();
    }
    @Transactional
    public SupportView resolveComplaint(UUID id, UpdateSupportRequest request) {
        User reviewer = requireReviewer();
        SupportItem item = repository.findById(id).filter(value -> value.getKind() == SupportKind.COMPLAINT)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy khiếu nại."));
        if (item.getSender().getId().equals(reviewer.getId()))
            throw ApiException.conflict("Không thể tự giải quyết khiếu nại của chính mình.");
        String response = request == null || request.response() == null ? "" : request.response().trim();
        SupportStatus status = request == null || request.status() == null ? item.getStatus() : request.status();
        if (status == SupportStatus.RESOLVED && response.isBlank() && (item.getAdminResponse() == null || item.getAdminResponse().isBlank()))
            throw ApiException.badRequest("Cần ghi kết quả xử lý trước khi đánh dấu đã giải quyết.");
        if (!response.isBlank()) {
            item.setAdminResponse(response);
            item.setRespondedBy(reviewer);
            item.setRespondedAt(Instant.now());
            if (status == SupportStatus.OPEN) status = SupportStatus.READ;
        }
        item.setStatus(status);
        return view(repository.save(item));
    }
    @Transactional(readOnly = true)
    public List<SupportView> mine() {
        return repository.findBySenderIdOrderByCreatedAtDesc(currentUser.requireCurrentUser().getId()).stream()
                .map(this::view)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SupportView> adminList(SupportKind kind) {
        requireManager();
        if (kind == null) {
            throw ApiException.badRequest("Vui lòng chọn loại yêu cầu hỗ trợ");
        }
        return repository.findTop200ByKindOrderByCreatedAtDesc(kind).stream()
                .map(this::view)
                .toList();
    }

    @Transactional
    public SupportView update(UUID id, UpdateSupportRequest request) {
        User admin = requireManager();
        if (request == null) {
            throw ApiException.badRequest("Vui lòng nhập nội dung cập nhật yêu cầu hỗ trợ");
        }
        SupportItem item = repository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy yêu cầu hỗ trợ"));
        SupportStatus status = request.status() == null ? item.getStatus() : request.status();
        String response = request.response() == null ? null : request.response().trim();
        if (response != null && response.length() > 10000) {
            throw ApiException.badRequest("Phản hồi của quản trị viên quá dài");
        }
        if (response != null && !response.isBlank()) {
            item.setAdminResponse(response);
            item.setRespondedBy(admin);
            item.setRespondedAt(Instant.now());
            if (status == SupportStatus.OPEN) {
                status = SupportStatus.READ;
            }
        }
        item.setStatus(status);
        return view(repository.save(item));
    }

    private SupportView view(SupportItem item) {
        User sender = item.getSender();
        return new SupportView(item.getId(), item.getKind(), sender.getId(), sender.getFullName(), sender.getEmail(),
                item.getSubject(), item.getContent(), item.getStatus(), item.getAdminResponse(), item.getCreatedAt(),
                item.getRespondedAt(), item.getSimulationId(),
                item.getRespondedBy() == null ? null : item.getRespondedBy().getFullName());
    }

    private User requireReviewer() {
        User user = currentUser.requireCurrentUser();
        if (!hasRole(user, RoleName.REVIEWER) && !hasRole(user, RoleName.MANAGER))
            throw ApiException.forbidden("Chỉ người kiểm duyệt mới xử lý được khiếu nại mô phỏng.");
        return user;
    }
    private static boolean hasRole(User user, RoleName role) {
        return user.getRole() != null && role.matches(user.getRole().getName());
    }
    private User requireManager() {
        User user = currentUser.requireCurrentUser();
        if (user.getRole() == null || !RoleName.MANAGER.matches(user.getRole().getName()))
            throw ApiException.forbidden("Chỉ người quản lý mới được quản lý yêu cầu hỗ trợ");
        return user;
    }
}
