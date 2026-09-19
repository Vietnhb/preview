package com.example.backend.service.support;

import com.example.backend.service.account.CurrentUserService;

import com.example.backend.entity.support.SupportItem;
import com.example.backend.entity.enums.SupportKind;
import com.example.backend.entity.enums.SupportStatus;
import com.example.backend.entity.enums.RoleName;
import com.example.backend.entity.account.User;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.support.SupportItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SupportService {
    private final SupportItemRepository repository;
    private final CurrentUserService currentUser;

    public record SupportView(UUID id, SupportKind kind, Integer senderId, String senderName, String senderEmail,
                              String subject, String content, SupportStatus status, String adminResponse,
                              Instant createdAt, Instant respondedAt) { }
    public record CreateRequest(String subject, String content) { }
    public record UpdateRequest(SupportStatus status, String response) { }

    @Transactional
    public SupportView create(SupportKind kind, CreateRequest request) {
        if (request == null || request.subject() == null || request.subject().isBlank()
                || request.content() == null || request.content().isBlank())
            throw new ApiException(HttpStatus.BAD_REQUEST, "Subject and content are required");
        User sender = currentUser.requireCurrentUser();
        SupportItem item = new SupportItem();
        item.setKind(kind); item.setSender(sender); item.setSubject(request.subject().trim());
        item.setContent(request.content().trim()); item.setStatus(SupportStatus.OPEN);
        return view(repository.save(item));
    }

    @Transactional(readOnly = true)
    public List<SupportView> mine() { return repository.findBySenderIdOrderByCreatedAtDesc(currentUser.requireCurrentUser().getId()).stream().map(this::view).toList(); }

    @Transactional(readOnly = true)
    public List<SupportView> adminList(SupportKind kind) { return repository.findTop200ByKindOrderByCreatedAtDesc(kind).stream().map(this::view).toList(); }

    @Transactional
    public SupportView update(UUID id, UpdateRequest request) {
        User admin = currentUser.requireCurrentUser();
        if (admin.getRole() == null || !RoleName.ADMIN.matches(admin.getRole().getName()))
            throw new ApiException(HttpStatus.FORBIDDEN, "Only admins can update support items");
        SupportItem item = repository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Support item not found"));
        SupportStatus status = request == null || request.status() == null ? item.getStatus() : request.status();
        item.setStatus(status);
        if (request != null && request.response() != null && !request.response().isBlank()) {
            item.setAdminResponse(request.response().trim()); item.setRespondedBy(admin); item.setRespondedAt(Instant.now());
            if (status == SupportStatus.OPEN) item.setStatus(SupportStatus.READ);
        }
        return view(repository.save(item));
    }

    private SupportView view(SupportItem item) {
        User sender = item.getSender();
        return new SupportView(item.getId(), item.getKind(), sender.getId(), sender.getFullName(), sender.getEmail(),
                item.getSubject(), item.getContent(), item.getStatus(), item.getAdminResponse(), item.getCreatedAt(), item.getRespondedAt());
    }
}
