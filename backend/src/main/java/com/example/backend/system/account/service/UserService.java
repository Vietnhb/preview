package com.example.backend.system.account.service;

import com.example.backend.exception.ApiException;
import com.example.backend.security.PasswordPolicy;
import com.example.backend.system.account.dto.UserResponse;
import com.example.backend.system.account.model.entity.User;
import com.example.backend.system.account.model.enums.RoleName;
import com.example.backend.system.account.repository.UserRepository;
import com.example.backend.system.school.dto.LicenseStatusResponse;
import com.example.backend.system.school.dto.SchoolClassContracts.StudentOptionResponse;
import com.example.backend.system.school.dto.SchoolClassContracts;
import com.example.backend.system.school.service.LicenseCheckService;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final PasswordEncoder passwordEncoder;
    private final LicenseCheckService licenseCheckService;

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String email) {
        return toResponse(findCurrent(email));
    }

    @Transactional(readOnly = true)
    public LicenseStatusResponse license() {
        return licenseCheckService.status(currentUserService.requireCurrentUser());
    }

    @Transactional
    public UserResponse updateProfile(String email, String fullName, java.time.LocalDate dateOfBirth) {
        User user = findCurrent(email);
        user.setFullName(fullName.trim());
        user.setDateOfBirth(dateOfBirth);
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse changePassword(String email, String currentPassword, String newPassword) {
        User user = findCurrent(email);
        if (!PasswordPolicy.matches(currentPassword, user.getPassword(), passwordEncoder)) {
            throw ApiException.badRequest("Mật khẩu hiện tại không đúng");
        }
        PasswordPolicy.requireValid(newPassword);
        if (PasswordPolicy.matches(newPassword, user.getPassword(), passwordEncoder)) {
            throw ApiException.badRequest("Mật khẩu mới phải khác mật khẩu hiện tại");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateAvatar(String email, String avatarUrl) {
        User user = findCurrent(email);
        user.setAvatarUrl(avatarUrl);
        return toResponse(userRepository.save(user));
    }

    private User findCurrent(String email) {
        return userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng"));
    }

    private UserResponse toResponse(User user) {
        String role = user.getRole() == null ? "UNKNOWN" : user.getRole().getName();
        boolean billingRequired = RoleName.SCHOOL.matches(role)
                && (user.getSchool() == null || !user.getSchool().isLicenseActive());
        return UserResponse.from(user, billingRequired);
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private List<StudentOptionResponse> getActiveStudents() {
        return userRepository.findActiveByRoleName(RoleName.STUDENT.name()).stream()
                .map(user -> new StudentOptionResponse(user.getId(), user.getFullName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StudentOptionResponse> getAssignableStudents() {
        User requester = currentUserService.requireCurrentUser();
        if (requester.getRole() != null && RoleName.MANAGER.matches(requester.getRole().getName())) {
            return getActiveStudents();
        }
        return userRepository.findActiveStudentsAssignableByTeacher(requester.getId()).stream()
                .filter(student -> requester.getSchool() != null
                        && student.getSchool() != null
                        && requester.getSchool().getId().equals(student.getSchool().getId()))
                .map(student -> new StudentOptionResponse(student.getId(), student.getFullName()))
                .toList();
    }
}
