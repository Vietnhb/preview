package com.example.backend.service;

import com.example.backend.dto.session.SaveSessionRequest;
import com.example.backend.dto.session.SessionResponse;
import com.example.backend.entity.Session;
import com.example.backend.entity.Specification;
import com.example.backend.entity.User;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.SessionRepository;
import com.example.backend.repository.SpecificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SessionService {
    private final SessionRepository sessionRepository;
    private final SpecificationRepository specificationRepository;
    private final CurrentUserService currentUserService;

    @Transactional
    public SessionResponse save(SaveSessionRequest request) {
        User teacher = currentUserService.requireCurrentUser();
        Specification specification = specificationRepository.findByIdAndSubmissionOwner(request.specificationId(), teacher)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Specification not found"));
        if (!"PASSED".equals(specification.getValidationStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "Only validated specifications can be recorded");
        }
        Session session = new Session();
        session.setSpecification(specification);
        session.setTeacher(teacher);
        session.setTitle(request.title().trim());
        session.setInitialSpecification(request.initialSpecification());
        session.setParameterTimeline(request.parameterTimeline());
        session.setDurationSeconds(Math.max(0, request.durationSeconds()));
        return toResponse(sessionRepository.save(session));
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> mine() {
        User teacher = currentUserService.requireCurrentUser();
        return sessionRepository.findByTeacherIdOrderByCreatedAtDesc(teacher.getId()).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public SessionResponse get(java.util.UUID id) {
        User teacher = currentUserService.requireCurrentUser();
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Session not found"));
        if (!session.getTeacher().getId().equals(teacher.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Session is not available to this user");
        }
        return toResponse(session);
    }

    private SessionResponse toResponse(Session item) {
        return new SessionResponse(item.getId(), item.getSpecification().getId(), item.getTitle(),
                item.getInitialSpecification(), item.getParameterTimeline(), item.getDurationSeconds(), item.getCreatedAt());
    }
}
