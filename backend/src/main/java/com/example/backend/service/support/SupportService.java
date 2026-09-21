package com.example.backend.service.support;

import com.example.backend.dto.support.CreateSupportRequest;
import com.example.backend.dto.support.UpdateSupportRequest;
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

    @Transactional
    public SupportView create(SupportKind kind, CreateSupportRequest request) {
        if (kind == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Support kind is required");
        }
        if (request == null || request.subject() == null || request.subject().isBlank()
                || request.content() == null || request.content().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Subject and content are required");
        }
        if (request.subject().trim().length() > 180 || request.content().trim().length() > 10000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Subject or content is too long");
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
        if (kind == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Support kind is required");
        }
        return repository.findTop200ByKindOrderByCreatedAtDesc(kind).stream()
                .map(this::view)
                .toList();
    }

    @Transactional
    public SupportView update(UUID id, UpdateSupportRequest request) {
        User admin = currentUser.requireCurrentUser();
        if (admin.getRole() == null || !RoleName.ADMIN.matches(admin.getRole().getName()))
            throw new ApiException(HttpStatus.FORBIDDEN, "Only admins can update support items");
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Support update is required");
        }
        SupportItem item = repository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Support item not found"));
        SupportStatus status = request.status() == null ? item.getStatus() : request.status();
        String response = request.response() == null ? null : request.response().trim();
        if (response != null && response.length() > 10000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Admin response is too long");
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
}
