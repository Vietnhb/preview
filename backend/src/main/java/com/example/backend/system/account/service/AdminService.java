package com.example.backend.system.account.service;

import com.example.backend.exception.ApiException;
import com.example.backend.security.PasswordPolicy;
import com.example.backend.system.account.dto.CreateManagedUserRequest;
import com.example.backend.system.account.dto.UpdateManagedUserRequest;
import com.example.backend.system.account.dto.UserStatusResponse;
import com.example.backend.system.account.model.entity.Role;
import com.example.backend.system.account.model.entity.User;
import com.example.backend.system.account.model.enums.RoleName;
import com.example.backend.system.account.repository.RoleRepository;
import com.example.backend.system.account.repository.UserRepository;
import com.example.backend.system.school.model.entity.School;
import com.example.backend.system.school.repository.SchoolRepository;
import com.example.backend.system.school.service.LicenseCheckService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUserService;
    private final RoleValidationService roleValidationService;
    private final AccountAccessService accountAccessService;
    private final LicenseCheckService licenseCheckService;
    private final com.example.backend.system.school.repository.SchoolRepository schoolRepository;
    private final jakarta.persistence.EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<UserStatusResponse> users() {
        return userRepository.findAll().stream().map(this::toUser).toList();
    }

    @Transactional(readOnly = true)
    public List<UserStatusResponse> usersForSchool(java.util.UUID schoolId) {
        requireSchoolAccess(schoolId);
        return userRepository.findBySchoolId(schoolId).stream().map(this::toUser).toList();
    }

    @Transactional(readOnly = true)
    public void requireUserInSchool(java.util.UUID schoolId, Integer userId) {
        requireSchoolAccess(schoolId);
        userRepository.findById(userId)
                .filter(user -> user.getSchool() != null && schoolId.equals(user.getSchool().getId()))
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng trong trường"));
    }

    @Transactional
    public UserStatusResponse createUserForSchool(java.util.UUID schoolId, CreateManagedUserRequest request) {
        requireSchoolAccess(schoolId);
        return createUser(new CreateManagedUserRequest(request.email(), request.password(), request.fullName(),
                request.role(), schoolId.toString(), request.dateOfBirth(), request.avatarUrl(), request.permissions()));
    }

    @Transactional
    public UserStatusResponse updateUserForSchool(java.util.UUID schoolId, Integer userId, UpdateManagedUserRequest request) {
        requireUserInSchool(schoolId, userId);
        return updateUser(userId, new UpdateManagedUserRequest(request.fullName(), request.role(), schoolId.toString(),
                request.dateOfBirth(), request.avatarUrl(), request.permissions()));
    }

    @Transactional
    public UserStatusResponse resetPasswordForSchool(java.util.UUID schoolId, Integer userId, String password) {
        requireUserInSchool(schoolId, userId);
        return resetPassword(userId, password);
    }

    @Transactional
    public UserStatusResponse setActiveForSchool(java.util.UUID schoolId, Integer userId, boolean active) {
        requireUserInSchool(schoolId, userId);
        return setActive(userId, active);
    }

    @Transactional
    public UserStatusResponse createUser(CreateManagedUserRequest request) {
        if (userRepository.findFirstByEmailIgnoreCase(request.email().trim()).isPresent()) {
            throw ApiException.conflict("Email đã tồn tại");
        }
        Role role = roleRepository.findByName(request.role().trim().toUpperCase(java.util.Locale.ROOT))
                .orElseThrow(() -> ApiException.badRequest("Vai trò không được hỗ trợ"));
        var school = resolveSchool(request.institutionId());
        roleValidationService.validateRoleSchoolConsistency(role.getName(), school);
        User actor = currentUserService.requireCurrentUser();
        if (actor.getRole() != null && RoleName.ADMIN.matches(actor.getRole().getName())) {
            if (!RoleName.MANAGER.matches(role.getName()))
                throw ApiException.forbidden("Quản trị viên chỉ có thể tạo tài khoản quản lý");
        } else {
            if (RoleName.MANAGER.matches(role.getName()))
                throw ApiException.forbidden("Chỉ quản trị viên mới được tạo tài khoản quản lý");
            requireManage(role.getName(), school);
        }
        requireStudentSeat(role.getName(), school);
        validatePassword(request.password());
        User user = new User();
        user.setEmail(request.email().trim().toLowerCase(java.util.Locale.ROOT));
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setMustChangePassword(true);
        user.setFullName(request.fullName().trim());
        user.setRole(role);
        user.setSchool(school);
        user.setDateOfBirth(request.dateOfBirth());
        user.setAvatarUrl(normalizeAvatar(request.avatarUrl()));
        accountAccessService.applyPermissions(user, role.getName(), request.permissions(), actor);
        if (RoleName.SCHOOL.matches(role.getName()))
            roleValidationService.validateSingleSchoolManager(school.getId(), null);
        user.setActive(true);
        return toUser(userRepository.save(user));
    }

    @Transactional
    public UserStatusResponse setActive(Integer id, boolean active) {
        if (!active && currentUserService.requireCurrentUser().getId().equals(id)) {
            throw ApiException.conflict("Bạn không thể khóa tài khoản của chính mình");
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng"));
        requireManage(user.getRole().getName(), user.getSchool());
        if (active && Boolean.FALSE.equals(user.getActive())) requireStudentSeat(user.getRole().getName(), user.getSchool());
        if (active && RoleName.SCHOOL.matches(user.getRole().getName()))
            roleValidationService.validateSingleSchoolManager(user.getSchool().getId(), user.getId());
        user.setActive(active);
        user.setDeactivatedAt(active ? null : java.time.Instant.now());
        user.setDeactivatedBy(active ? null : currentUserService.requireCurrentUser().getId());
        return toUser(userRepository.save(user));
    }

    @Transactional
    public UserStatusResponse resetPassword(Integer id, String newPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng"));
        requireManage(user.getRole().getName(), user.getSchool());
        validatePassword(newPassword);
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(true);
        return toUser(userRepository.save(user));
    }

    private UserStatusResponse toUser(User user) {
        return new UserStatusResponse(user.getId(), user.getEmail(), user.getFullName(),
                user.getRole() == null ? "UNKNOWN" : user.getRole().getName(), !Boolean.FALSE.equals(user.getActive()),
                user.getInstitutionId(), user.getLastLogin(), user.getDateOfBirth(), user.getAvatarUrl(),
                user.getSchool() == null ? null : user.getSchool().getId(),
                user.getSchool() == null ? null : user.getSchool().getName(), user.isMustChangePassword(),
                user.permissionCodes());
    }

    @Transactional
    public UserStatusResponse updateUser(Integer id, UpdateManagedUserRequest request) {
        User user = userRepository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng"));
        requireManage(user.getRole().getName(), user.getSchool());
        Role role = roleRepository.findByName(request.role().trim().toUpperCase(java.util.Locale.ROOT))
                .orElseThrow(() -> ApiException.badRequest("Vai trò không được hỗ trợ"));
        if (RoleName.MANAGER.matches(role.getName()) && !RoleName.MANAGER.matches(user.getRole().getName())
                && !RoleName.ADMIN.matches(currentUserService.requireCurrentUser().getRole().getName()))
            throw ApiException.forbidden("Chỉ quản trị viên mới được tạo tài khoản quản lý");
        if (currentUserService.requireCurrentUser().getId().equals(id) && !role.getName().equals(user.getRole().getName()))
            throw ApiException.conflict("Bạn không thể thay đổi vai trò của chính mình");
        var school = resolveSchool(request.institutionId());
        roleValidationService.validateRoleSchoolConsistency(role.getName(), school);
        requireManage(role.getName(), school);
        boolean alreadyOccupiesSeat = RoleName.STUDENT.matches(user.getRole().getName()) && !Boolean.FALSE.equals(user.getActive())
                && user.getSchool() != null && school != null && user.getSchool().getId().equals(school.getId());
        if (!Boolean.FALSE.equals(user.getActive()) && !alreadyOccupiesSeat) requireStudentSeat(role.getName(), school);
        if (Boolean.TRUE.equals(user.getActive()) && RoleName.SCHOOL.matches(role.getName()))
            roleValidationService.validateSingleSchoolManager(school.getId(), user.getId());
        user.setFullName(request.fullName().trim()); user.setRole(role); user.setSchool(school);
        if (request.dateOfBirth() != null) user.setDateOfBirth(request.dateOfBirth());
        if (request.avatarUrl() != null) user.setAvatarUrl(normalizeAvatar(request.avatarUrl()));
        // Permissions of the former role are dropped; a new STAFF/REVIEWER gets the role defaults unless narrowed.
        accountAccessService.applyPermissions(user, role.getName(), request.permissions(), currentUserService.requireCurrentUser());
        return toUser(userRepository.save(user));
    }

    private void requireManage(String targetRole, com.example.backend.system.school.model.entity.School school) {
        User actor = currentUserService.requireCurrentUser();
        if (actor.getRole() != null && RoleName.ADMIN.matches(actor.getRole().getName())) {
            if (!RoleName.MANAGER.matches(targetRole))
                throw ApiException.forbidden("Quản trị viên chỉ có thể quản lý tài khoản quản lý");
            return;
        }
        if (RoleName.ADMIN.matches(targetRole))
            throw ApiException.forbidden("Không thể quản lý tài khoản quản trị viên tại đây");
        if (actor.getRole() != null && RoleName.MANAGER.matches(actor.getRole().getName())) return;
        if (school == null || !roleValidationService.canManageSchool(actor, school.getId())
                || !(RoleName.STAFF.matches(targetRole) || RoleName.STUDENT.matches(targetRole)))
            throw ApiException.forbidden("Bạn chỉ được quản lý giáo viên và học sinh trong trường của mình");
        licenseCheckService.requireWriteAccess(actor);
    }

    private static void validatePassword(String password) {
        PasswordPolicy.requireValid(password);
    }

    private static String normalizeAvatar(String avatarUrl) {
        return avatarUrl == null || avatarUrl.isBlank() ? null : avatarUrl.trim();
    }

    private void requireSchoolAccess(java.util.UUID schoolId) {
        User actor = currentUserService.requireCurrentUser();
        boolean departmentHead = accountAccessService.isDepartmentHead(actor) && actor.getSchool() != null
                && schoolId.equals(actor.getSchool().getId());
        if (!departmentHead && !roleValidationService.canManageSchool(actor, schoolId)) {
            throw ApiException.forbidden("Bạn không có quyền truy cập trường này");
        }
    }

    private void requireStudentSeat(String role, com.example.backend.system.school.model.entity.School school) {
        if (!RoleName.STUDENT.matches(role) || school == null) return;
        var locked = schoolRepository.findByIdForUpdate(school.getId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy trường"));
        entityManager.refresh(locked, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (locked.getStudentQuota() != null && userRepository.countActiveStudents(locked.getId()) >= locked.getStudentQuota())
            throw ApiException.conflict("Trường đã hết quota học sinh. Vui lòng nâng gói hoặc tạm khóa tài khoản không còn sử dụng.");
    }

    private com.example.backend.system.school.model.entity.School resolveSchool(String id) {
        if (id == null || id.isBlank()) return null;
        try {
            return schoolRepository.findById(java.util.UUID.fromString(id))
                    .filter(com.example.backend.system.school.model.entity.School::isActive)
                    .orElseThrow(() -> ApiException.badRequest("Vui lòng chọn trường đang hoạt động"));
        } catch (IllegalArgumentException ex) { throw ApiException.badRequest("Định danh trường không hợp lệ"); }
    }
}
