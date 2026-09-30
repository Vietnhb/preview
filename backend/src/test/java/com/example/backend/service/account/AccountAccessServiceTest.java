package com.example.backend.service.account;

import com.example.backend.entity.account.Role;
import com.example.backend.entity.account.User;
import com.example.backend.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class AccountAccessServiceTest {
    private final AccountAccessService access = new AccountAccessService();
    private User user(String role) {
        User user = new User(); Role accountRole = new Role(); accountRole.setName(role); user.setRole(accountRole); return user;
    }

    @ParameterizedTest @CsvSource({"true,false", "false,true", "true,true"})
    void reviewerCapabilitiesAreIndependent(boolean edit, boolean review) {
        User reviewer = user("REVIEWER"); access.applyPermissions(reviewer, "REVIEWER", null, edit, review);
        assertEquals(edit, access.canEditContext(reviewer)); assertEquals(review, access.canReviewPublic(reviewer));
        assertFalse(access.isDepartmentHead(reviewer)); assertNull(reviewer.getStaffType());
    }

    @Test void reviewerRequiresAtLeastOneCapabilityAndOnlyReviewerCanHaveThoseFields() {
        assertThrows(ApiException.class, () -> access.applyPermissions(user("REVIEWER"), "REVIEWER", null, false, false));
        assertThrows(ApiException.class, () -> access.applyPermissions(user("MANAGER"), "MANAGER", null, false, true));
        assertThrows(ApiException.class, () -> access.applyPermissions(user("STUDENT"), "STUDENT", "TEACHER", null, null));
    }

    @Test void teacherAndDepartmentHeadStayStaffAndHaveNoPublicModerationOrContextEditRights() {
        User teacher = user("STAFF"); access.applyPermissions(teacher, "STAFF", null, null, null);
        assertEquals("TEACHER", teacher.getStaffType()); assertFalse(access.isDepartmentHead(teacher));
        access.applyPermissions(teacher, "STAFF", "DEPARTMENT_HEAD", null, null);
        assertTrue(access.isDepartmentHead(teacher)); assertFalse(access.canEditContext(teacher));
        assertFalse(access.canReviewPublic(teacher)); assertEquals("STAFF", teacher.getRole().getName());
        assertThrows(ApiException.class, () -> access.applyPermissions(teacher, "STAFF", "TEACHER_LEGACY", null, null));
    }

    @ParameterizedTest @CsvSource({"MANAGER,true", "ADMIN,false", "SCHOOL,false", "STUDENT,false"})
    void onlyManagerReceivesBothPlatformCapabilitiesByRole(String role, boolean allowed) {
        User account = user(role); assertEquals(allowed, access.canEditContext(account)); assertEquals(allowed, access.canReviewPublic(account));
    }

    @Test void lockedAndLegacyNullActiveAccountsHaveNoCapabilities() {
        User reviewer = user("REVIEWER"); reviewer.setActive(false);
        assertFalse(access.canEditContext(reviewer)); assertFalse(access.canReviewPublic(reviewer));
        reviewer.setActive(null); assertFalse(access.canEditContext(reviewer)); assertFalse(access.canReviewPublic(reviewer));
        User head = user("STAFF"); head.setStaffType("DEPARTMENT_HEAD"); head.setActive(null);
        assertFalse(access.isDepartmentHead(head)); assertFalse(access.canReviewPublic(null));
    }
}
