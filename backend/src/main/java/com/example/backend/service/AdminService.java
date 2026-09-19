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
    private final RoleValidationService roleValidationService;
    private final LicenseCheckService licenseCheckService;
    private final com.example.backend.repository.SchoolRepository schoolRepository;
    private final jakarta.persistence.EntityManager entityManager;

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
        var school = resolveSchool(request.institutionId());
        roleValidationService.validateRoleSchoolConsistency(role.getName(), school);
        requireManage(role.getName(), school);
        requireStudentSeat(role.getName(), school);
        if (request.password().length() < 8) throw new ApiException(HttpStatus.BAD_REQUEST, "Password must have at least 8 characters");
        User user = new User();
        user.setEmail(request.email().trim().toLowerCase());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName().trim());
        user.setRole(role);
        user.setSchool(school);
        if ("SCHOOL_MANAGER".equals(role.getName()))
            roleValidationService.validateSingleSchoolManager(school.getId(), null);
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
        requireManage(user.getRole().getName(), user.getSchool());
        if (active && Boolean.FALSE.equals(user.getActive())) requireStudentSeat(user.getRole().getName(), user.getSchool());
        if (active && "SCHOOL_MANAGER".equals(user.getRole().getName()))
            roleValidationService.validateSingleSchoolManager(user.getSchool().getId(), user.getId());
        user.setActive(active);
        user.setDeactivatedAt(active ? null : java.time.Instant.now());
        user.setDeactivatedBy(active ? null : currentUserService.requireCurrentUser().getId());
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
                user.getRole() == null ? "UNKNOWN" : user.getRole().getName(), !Boolean.FALSE.equals(user.getActive()),
                user.getInstitutionId(), user.getLastLogin(), user.getDateOfBirth());
    }

    public record UpdateUserRequest(@jakarta.validation.constraints.NotBlank String fullName,
            @jakarta.validation.constraints.NotBlank String role, String institutionId) { }

    @Transactional
    public UserStatusResponse updateUser(Integer id, UpdateUserRequest request) {
        User user = userRepository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        requireManage(user.getRole().getName(), user.getSchool());
        Role role = roleRepository.findByName(request.role().trim().toUpperCase(java.util.Locale.ROOT))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Role is not supported"));
        if (currentUserService.requireCurrentUser().getId().equals(id) && !role.getName().equals("ADMIN"))
            throw new ApiException(HttpStatus.CONFLICT, "You cannot remove your own admin role");
        var school = resolveSchool(request.institutionId());
        roleValidationService.validateRoleSchoolConsistency(role.getName(), school);
        requireManage(role.getName(), school);
        boolean alreadyOccupiesSeat = "STUDENT".equals(user.getRole().getName()) && !Boolean.FALSE.equals(user.getActive())
                && user.getSchool() != null && school != null && user.getSchool().getId().equals(school.getId());
        if (!Boolean.FALSE.equals(user.getActive()) && !alreadyOccupiesSeat) requireStudentSeat(role.getName(), school);
        if (Boolean.TRUE.equals(user.getActive()) && "SCHOOL_MANAGER".equals(role.getName()))
            roleValidationService.validateSingleSchoolManager(school.getId(), user.getId());
        user.setFullName(request.fullName().trim()); user.setRole(role); user.setSchool(school);
        return toUser(userRepository.save(user));
    }

    private void requireManage(String targetRole, com.example.backend.entity.School school) {
        User actor = currentUserService.requireCurrentUser();
        if (actor.getRole() != null && "ADMIN".equals(actor.getRole().getName())) return;
        if (school == null || !roleValidationService.canManageSchool(actor, school.getId())
                || !("TEACHER".equals(targetRole) || "STUDENT".equals(targetRole)))
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only manage teachers and students in your school");
        licenseCheckService.requireWriteAccess(actor);
    }

    private void requireStudentSeat(String role, com.example.backend.entity.School school) {
        if (!"STUDENT".equals(role) || school == null) return;
        var locked = schoolRepository.findByIdForUpdate(school.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "School not found"));
        entityManager.refresh(locked, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (locked.getStudentQuota() != null && userRepository.countActiveStudents(locked.getId()) >= locked.getStudentQuota())
            throw new ApiException(HttpStatus.CONFLICT, "Trường đã hết quota học sinh. Vui lòng nâng gói hoặc tạm khóa tài khoản không còn sử dụng.");
    }

    private com.example.backend.entity.School resolveSchool(String id) {
        if (id == null || id.isBlank()) return null;
        try {
            return schoolRepository.findById(java.util.UUID.fromString(id))
                    .filter(com.example.backend.entity.School::isActive)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Select an active school"));
        } catch (IllegalArgumentException ex) { throw new ApiException(HttpStatus.BAD_REQUEST, "School ID is invalid"); }
    }
}
