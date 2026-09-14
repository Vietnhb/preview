package com.example.backend.service;

import com.example.backend.dto.admin.CreateManagedUserRequest;
import com.example.backend.dto.admin.UserStatusResponse;
import com.example.backend.dto.admin.ValidationMetricsResponse;
import com.example.backend.dto.admin.TopicStatusResponse;
import com.example.backend.entity.Role;
import com.example.backend.entity.Topic;
import com.example.backend.entity.User;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.RoleRepository;
import com.example.backend.repository.TopicRepository;
import com.example.backend.repository.UserRepository;
import com.example.backend.repository.ValidationRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TopicRepository topicRepository;
    private final ValidationRunRepository validationRunRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUserService;
    private final com.example.backend.repository.SchoolRepository schoolRepository;

    @Transactional(readOnly = true)
    public List<UserStatusResponse> users() {
        return userRepository.findAll().stream().map(this::toUser).toList();
    }

    @Transactional
    public UserStatusResponse createUser(CreateManagedUserRequest request) {
        if (userRepository.findByEmail(request.email().trim().toLowerCase(java.util.Locale.ROOT)).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "Email already exists");
        }
        Role role = roleRepository.findByName(request.role().trim().toUpperCase())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Role is not supported"));
        validateSchool(request.institutionId());
        if (request.password().length() < 8) throw new ApiException(HttpStatus.BAD_REQUEST, "Password must have at least 8 characters");
        User user = new User();
        user.setEmail(request.email().trim().toLowerCase());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName().trim());
        user.setRole(role);
        user.setInstitutionId(request.institutionId());
        user.setActive(true);
        return toUser(userRepository.save(user));
    }

    @Transactional
    public UserStatusResponse setActive(Integer id, boolean active) {
        if (!active && currentUserService.requireCurrentUser().getId().equals(id)) {
            throw new ApiException(HttpStatus.CONFLICT, "You cannot suspend your own account");
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        user.setActive(active);
        return toUser(userRepository.save(user));
    }

    @Transactional
    public TopicStatusResponse toggleTopic(java.util.UUID id) {
        Topic topic = topicRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Topic not found"));
        topic.setEnabled(!topic.isEnabled());
        topicRepository.save(topic);
        return new TopicStatusResponse(topic.getId(), topic.getName(), topic.isEnabled());
    }

    @Transactional(readOnly = true)
    public ValidationMetricsResponse validationMetrics() {
        long total = validationRunRepository.count();
        long failed = validationRunRepository.countByPassedFalse();
        return new ValidationMetricsResponse(total, failed, total == 0 ? 0 : (double) failed / total);
    }

    private UserStatusResponse toUser(User user) {
        return new UserStatusResponse(user.getId(), user.getEmail(), user.getFullName(),
                user.getRole() == null ? "UNKNOWN" : user.getRole().getName(), !Boolean.FALSE.equals(user.getActive()), user.getInstitutionId());
    }

    public record UpdateUserRequest(@jakarta.validation.constraints.NotBlank String fullName,
            @jakarta.validation.constraints.NotBlank String role, String institutionId) { }

    @Transactional
    public UserStatusResponse updateUser(Integer id, UpdateUserRequest request) {
        User user = userRepository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        Role role = roleRepository.findByName(request.role().trim().toUpperCase(java.util.Locale.ROOT))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Role is not supported"));
        if (currentUserService.requireCurrentUser().getId().equals(id) && !role.getName().equals("ADMIN"))
            throw new ApiException(HttpStatus.CONFLICT, "You cannot remove your own admin role");
        validateSchool(request.institutionId());
        user.setFullName(request.fullName().trim()); user.setRole(role); user.setInstitutionId(request.institutionId());
        return toUser(userRepository.save(user));
    }

    private void validateSchool(String id) {
        if (id == null || id.isBlank()) return;
        try {
            if (!schoolRepository.findById(java.util.UUID.fromString(id)).map(com.example.backend.entity.School::isActive).orElse(false))
                throw new ApiException(HttpStatus.BAD_REQUEST, "Select an active school");
        } catch (IllegalArgumentException ex) { throw new ApiException(HttpStatus.BAD_REQUEST, "School ID is invalid"); }
    }
}
