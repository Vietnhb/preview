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
            throw ApiException.badRequest("Support kind is required");
        }
        if (request == null || request.subject() == null || request.subject().isBlank()
                || request.content() == null || request.content().isBlank()) {
            throw ApiException.badRequest("Subject and content are required");
        }
        if (request.subject().trim().length() > 180 || request.content().trim().length() > 10000) {
            throw ApiException.badRequest("Subject or content is too long");
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
            throw ApiException.badRequest("Support kind is required");
        }
        return repository.findTop200ByKindOrderByCreatedAtDesc(kind).stream()
                .map(this::view)
                .toList();
    }

    @Transactional
    public SupportView update(UUID id, UpdateSupportRequest request) {
        User admin = requireManager();
        if (request == null) {
            throw ApiException.badRequest("Support update is required");
        }
        SupportItem item = repository.findById(id).orElseThrow(() -> ApiException.notFound("Support item not found"));
        SupportStatus status = request.status() == null ? item.getStatus() : request.status();
        String response = request.response() == null ? null : request.response().trim();
        if (response != null && response.length() > 10000) {
            throw ApiException.badRequest("Admin response is too long");
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
                item.getRespondedAt());
    }

    private User requireManager() {
        User user = currentUser.requireCurrentUser();
        if (user.getRole() == null || !RoleName.MANAGER.matches(user.getRole().getName()))
            throw ApiException.forbidden("Only managers can manage support items");
        return user;
    }
}
