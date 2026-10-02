package com.example.backend.system.account.service;

import com.example.backend.exception.ApiException;
import com.example.backend.system.account.model.entity.Permission;
import com.example.backend.system.account.model.entity.User;
import com.example.backend.system.account.model.enums.PermissionCode;
import com.example.backend.system.account.model.enums.RoleName;
import com.example.backend.system.account.repository.PermissionRepository;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Account permissions supplement roles (users N-N permissions through user_permissions).
 * School boundaries are checked by the calling service.
 */
@Service
@RequiredArgsConstructor
public class AccountAccessService {
    private final PermissionRepository permissions;

    public boolean canEditContext(User user) {
        return active(user) && (hasRole(user, RoleName.MANAGER)
                || hasRole(user, RoleName.REVIEWER) && user.hasPermission(PermissionCode.CONTENT_EDIT));
    }

    public boolean canReviewPublic(User user) {
        return active(user) && (hasRole(user, RoleName.MANAGER)
                || hasRole(user, RoleName.REVIEWER) && user.hasPermission(PermissionCode.CONTENT_REVIEW));
    }

    public boolean isDepartmentHead(User user) {
        return active(user) && hasRole(user, RoleName.STAFF) && user.hasPermission(PermissionCode.DEPARTMENT_HEAD_PHYSICS);
    }

    /** Teaching: simulations, personal library, class assignments and grading. */
    public boolean canTeach(User user) {
        return active(user) && (hasRole(user, RoleName.MANAGER)
                || hasRole(user, RoleName.STAFF) && user.hasPermission(PermissionCode.TEACH));
    }

    /** A STAFF account that holds only DEPARTMENT_HEAD_PHYSICS has no teaching workspace. */
    public boolean isStaffWithoutTeaching(User user) {
        return user != null && hasRole(user, RoleName.STAFF) && !user.hasPermission(PermissionCode.TEACH);
    }

    /**
     * Replace the account's permissions with the requested set.
     * A null request keeps the permissions that are still valid for the role, or grants the role defaults.
     *
     * @param grantedBy the SCHOOL or MANAGER account performing the change; recorded on new grants
     */
    public void applyPermissions(User user, String roleName, Collection<String> requested, User grantedBy) {
        RoleName role = RoleName.from(roleName).orElseThrow(() -> ApiException.badRequest("Unsupported role"));
        List<PermissionCode> allowed = PermissionCode.forRole(role);
        if (allowed.isEmpty()) {
            if (requested != null && !requested.isEmpty())
                throw ApiException.badRequest("Vai trò " + role.name() + " không có quyền riêng để gắn.");
            user.getPermissions().clear();
            return;
        }
        Set<PermissionCode> target = EnumSet.noneOf(PermissionCode.class);
        if (requested == null) {
            allowed.stream().filter(user::hasPermission).forEach(target::add);
            if (target.isEmpty()) target.addAll(PermissionCode.defaultsFor(role));
        } else {
            for (String value : requested) {
                PermissionCode code = PermissionCode.from(value)
                        .orElseThrow(() -> ApiException.badRequest("Quyền không hợp lệ: " + value));
                if (code.role() != role)
                    throw ApiException.badRequest("Quyền " + code.name() + " chỉ áp dụng cho tài khoản " + code.role().name() + ".");
                target.add(code);
            }
            if (target.isEmpty())
                throw ApiException.badRequest(role == RoleName.STAFF
                        ? "STAFF cần ít nhất một quyền: giáo viên hoặc tổ trưởng bộ môn."
                        : "REVIEWER cần ít nhất một quyền chỉnh sửa hoặc kiểm duyệt.");
        }
        user.getPermissions().removeIf(grant -> PermissionCode.from(grant.code()).filter(target::contains).isEmpty());
        List<String> missing = target.stream().filter(code -> !user.hasPermission(code)).map(Enum::name).toList();
        if (missing.isEmpty()) return;
        List<Permission> catalog = permissions.findByCodeIn(missing);
        if (catalog.size() != missing.size())
            throw new IllegalStateException("Permission catalog is incomplete; apply migration V41");
        Integer grantor = grantedBy == null ? null : grantedBy.getId();
        catalog.forEach(permission -> user.grantPermission(permission, grantor));
    }

    private boolean active(User user) { return user != null && Boolean.TRUE.equals(user.getActive()); }
    private boolean hasRole(User user, RoleName role) { return user.getRole() != null && role.matches(user.getRole().getName()); }
}
