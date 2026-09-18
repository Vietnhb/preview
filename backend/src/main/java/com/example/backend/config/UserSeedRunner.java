package com.example.backend.config;

import java.util.Optional;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.entity.Role;
import com.example.backend.entity.School;
import com.example.backend.entity.User;
import com.example.backend.repository.RoleRepository;
import com.example.backend.repository.SchoolRepository;
import com.example.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Order(15)
@RequiredArgsConstructor
@Slf4j
public class UserSeedRunner implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final SchoolRepository schoolRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        ensureSchool();
        ensureUser("admin@physlive.com", "Quản trị viên Hệ thống", "admin123456", "ADMIN");
        ensureUser("reviewer@physlive.com", "Chuyên gia Thẩm định Vật lý", "reviewer123456", "CONTENT_REVIEWER");
        ensureUser("teacher@physlive.com", "Giáo viên Vật lý", "password123", "TEACHER");
        ensureUser("student@physlive.com", "Nguyễn Văn An (Học sinh)", "student123456", "STUDENT");
        ensureUser("student2@physlive.com", "Trần Thị Bình (Học sinh)", "student123456", "STUDENT");
        log.info("PhysLive standard demo users verified across all roles.");
    }

    private School ensureSchool() {
        var existingSchool = schoolRepository.findByCode("AMS-HN");
        if (existingSchool.isEmpty()) {
            School school = new School();
            school.setCode("AMS-HN");
            school.setName("THPT Chuyên Hà Nội - Amsterdam");
            school.setAddress("1 Hoàng Minh Giám, Cầu Giấy, Hà Nội");
            school.setActive(true);
            return schoolRepository.save(school);
        }
        return existingSchool.get();
    }

    private void ensureUser(String email, String fullName, String rawPassword, String roleName) {
        Role role = roleRepository.findByName(roleName).orElse(null);
        if (role == null) {
            role = new Role();
            role.setName(roleName);
            role = roleRepository.save(role);
        }

        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isEmpty()) {
            User user = new User();
            user.setEmail(email);
            user.setFullName(fullName);
            user.setPassword(passwordEncoder.encode(rawPassword));
            user.setRole(role);
            if (com.example.backend.constants.RoleConstants.isSchoolRole(roleName)) user.setSchool(ensureSchool());
            user.setActive(true);
            userRepository.save(user);
        }
        // Existing accounts are deliberately left untouched. In particular, a
        // suspended account must not be silently reactivated on application start,
        // and an administrator's role must not be overwritten by demo seed data.
    }
}
