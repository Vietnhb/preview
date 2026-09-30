package com.example.backend.service.account;

import com.example.backend.entity.account.User;
import com.example.backend.entity.enums.RoleName;
import com.example.backend.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Account capabilities supplement roles; school boundaries are checked by the calling service. */
@Service
public class AccountAccessService {
    public static final String TEACHER = "TEACHER";
    public static final String DEPARTMENT_HEAD = "DEPARTMENT_HEAD";

    public boolean canEditContext(User user) {
        return active(user) && (hasRole(user, RoleName.MANAGER)
                || hasRole(user, RoleName.REVIEWER) && Boolean.TRUE.equals(user.getReviewerCanEdit()));
    }

    public boolean canReviewPublic(User user) {
        return active(user) && (hasRole(user, RoleName.MANAGER)
                || hasRole(user, RoleName.REVIEWER) && Boolean.TRUE.equals(user.getReviewerCanReview()));
    }

    public boolean isDepartmentHead(User user) {
        return active(user) && hasRole(user, RoleName.STAFF) && DEPARTMENT_HEAD.equals(user.getStaffType());
    }

    public void applyPermissions(User user, String role, String staffType, Boolean edit, Boolean review) {
        if (RoleName.STAFF.matches(role)) {
            String kind = staffType == null ? (user.getStaffType() == null ? TEACHER : user.getStaffType()) : staffType;
            if (!TEACHER.equals(kind) && !DEPARTMENT_HEAD.equals(kind))
                throw new ApiException(HttpStatus.BAD_REQUEST, "Loại STAFF không hợp lệ.");
            user.setStaffType(kind);
        } else {
            if (staffType != null) throw new ApiException(HttpStatus.BAD_REQUEST, "Chỉ STAFF có loại giáo viên hoặc trưởng bộ môn.");
            user.setStaffType(null);
        }
        if (RoleName.REVIEWER.matches(role)) {
            boolean nextEdit = edit == null ? !Boolean.FALSE.equals(user.getReviewerCanEdit()) : edit;
            boolean nextReview = review == null ? !Boolean.FALSE.equals(user.getReviewerCanReview()) : review;
            if (!nextEdit && !nextReview)
                throw new ApiException(HttpStatus.BAD_REQUEST, "REVIEWER cần ít nhất một quyền chỉnh sửa hoặc kiểm duyệt.");
            user.setReviewerCanEdit(nextEdit);
            user.setReviewerCanReview(nextReview);
        } else {
            if (edit != null || review != null)
                throw new ApiException(HttpStatus.BAD_REQUEST, "Quyền REVIEWER chỉ áp dụng cho tài khoản REVIEWER.");
            user.setReviewerCanEdit(false);
            user.setReviewerCanReview(false);
        }
    }

    private boolean active(User user) { return user != null && Boolean.TRUE.equals(user.getActive()); }
    private boolean hasRole(User user, RoleName role) { return user.getRole() != null && role.matches(user.getRole().getName()); }
}
