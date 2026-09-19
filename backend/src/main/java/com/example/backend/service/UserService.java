package com.example.backend.service;

import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.example.backend.dto.UserResponse;
import com.example.backend.dto.StudentOptionResponse;
import com.example.backend.entity.User;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.UserRepository;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class UserService {
    UserRepository userRepository;
    CurrentUserService currentUserService;

    public UserResponse getCurrentUser(String email) {
        User user = userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Khong Thay User"));
        return toResponse(user);
    }

    public List<UserResponse> getAllUser() {
        return userRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public UserResponse updateProfile(String email, String fullName, java.time.LocalDate dateOfBirth) {
        User user = findCurrent(email);
        user.setFullName(fullName.trim());
        user.setDateOfBirth(dateOfBirth);
        return toResponse(userRepository.save(user));
    }

    public void changePassword(String email, String currentPassword, String newPassword, org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
        User user = findCurrent(email);
        if (!matchesPassword(currentPassword, user.getPassword(), passwordEncoder)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Mật khẩu hiện tại không đúng");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public UserResponse updateAvatar(String email, String avatarUrl) {
        User user = findCurrent(email);
        user.setAvatarUrl(avatarUrl);
        return toResponse(userRepository.save(user));
    }

    private User findCurrent(String email) {
        return userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"));
    }

    private UserResponse toResponse(User user) {
        String role = user.getRole() == null ? "UNKNOWN" : user.getRole().getName();
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), role, user.getDateOfBirth(), user.getAvatarUrl(), user.getSchool() == null ? null : user.getSchool().getId());
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean matchesPassword(String rawPassword, String storedPassword,
            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
        if (rawPassword == null || storedPassword == null) return false;
        return storedPassword.startsWith("$2")
                ? passwordEncoder.matches(rawPassword, storedPassword)
                : rawPassword.equals(storedPassword);
    }

    public List<StudentOptionResponse> getActiveStudents() {
        return userRepository.findActiveByRoleName("STUDENT").stream()
                .map(user -> new StudentOptionResponse(user.getId(), user.getFullName()))
                .toList();
    }

    public List<StudentOptionResponse> getAssignableStudents() {
        User requester = currentUserService.requireCurrentUser();
        if (requester.getRole() != null && "ADMIN".equalsIgnoreCase(requester.getRole().getName())) {
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
