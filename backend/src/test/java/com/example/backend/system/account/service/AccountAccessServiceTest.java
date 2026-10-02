package com.example.backend.system.account.service;

import com.example.backend.exception.ApiException;
import com.example.backend.system.account.TestPermissions;
import com.example.backend.system.account.model.entity.Role;
import com.example.backend.system.account.model.entity.User;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class AccountAccessServiceTest {
    private final AccountAccessService access = TestPermissions.access();
    private User user(String role) {
        User user = new User(); user.setId(5); Role accountRole = new Role(); accountRole.setName(role); user.setRole(accountRole); return user;
    }
    private User grantor() { User user = user("MANAGER"); user.setId(1); return user; }

    @ParameterizedTest @CsvSource({"true,false", "false,true", "true,true"})
    void reviewerPermissionsAreIndependent(boolean edit, boolean review) {
        List<String> codes = new ArrayList<>();
        if (edit) codes.add("CONTENT_EDIT");
        if (review) codes.add("CONTENT_REVIEW");
        User reviewer = user("REVIEWER"); access.applyPermissions(reviewer, "REVIEWER", codes, grantor());
        assertEquals(edit, access.canEditContext(reviewer)); assertEquals(review, access.canReviewPublic(reviewer));
        assertFalse(access.isDepartmentHead(reviewer)); assertEquals(codes, reviewer.permissionCodes());
    }

    @Test void staffAndReviewerNeedAtLeastOnePermissionAndPermissionsBelongToTheirRole() {
        assertThrows(ApiException.class, () -> access.applyPermissions(user("REVIEWER"), "REVIEWER", List.of(), grantor()));
        assertThrows(ApiException.class, () -> access.applyPermissions(user("STAFF"), "STAFF", List.of(), grantor()));
        assertThrows(ApiException.class, () -> access.applyPermissions(user("MANAGER"), "MANAGER", List.of("CONTENT_REVIEW"), grantor()));
        assertThrows(ApiException.class, () -> access.applyPermissions(user("STUDENT"), "STUDENT", List.of("TEACH"), grantor()));
        assertThrows(ApiException.class, () -> access.applyPermissions(user("STAFF"), "STAFF", List.of("CONTENT_EDIT"), grantor()));
        assertThrows(ApiException.class, () -> access.applyPermissions(user("REVIEWER"), "REVIEWER", List.of("DEPARTMENT_HEAD_PHYSICS"), grantor()));
        assertThrows(ApiException.class, () -> access.applyPermissions(user("STAFF"), "STAFF", List.of("TEACHER"), grantor()));
    }

    @Test void staffCanBeTeacherDepartmentHeadOrBothWithoutPlatformRights() {
        User staff = user("STAFF"); access.applyPermissions(staff, "STAFF", null, grantor());
        assertEquals(List.of("TEACH"), staff.permissionCodes()); assertTrue(access.canTeach(staff)); assertFalse(access.isDepartmentHead(staff));
        access.applyPermissions(staff, "STAFF", List.of("TEACH", "DEPARTMENT_HEAD_PHYSICS"), grantor());
        assertTrue(access.canTeach(staff)); assertTrue(access.isDepartmentHead(staff));
        assertFalse(access.canEditContext(staff)); assertFalse(access.canReviewPublic(staff));
        access.applyPermissions(staff, "STAFF", List.of("DEPARTMENT_HEAD_PHYSICS"), grantor());
        assertFalse(access.canTeach(staff)); assertTrue(access.isStaffWithoutTeaching(staff)); assertTrue(access.isDepartmentHead(staff));
        assertEquals("STAFF", staff.getRole().getName());
    }

    @Test void grantsRecordTheGrantorAndUnchangedRequestsKeepExistingGrants() {
        User reviewer = user("REVIEWER"); access.applyPermissions(reviewer, "REVIEWER", null, grantor());
        assertEquals(List.of("CONTENT_EDIT", "CONTENT_REVIEW"), reviewer.permissionCodes());
        assertTrue(reviewer.getPermissions().stream().allMatch(grant -> Integer.valueOf(1).equals(grant.getGrantedBy()) && grant.getGrantedAt() != null));
        User other = user("MANAGER"); other.setId(2);
        access.applyPermissions(reviewer, "REVIEWER", null, other);
        assertTrue(reviewer.getPermissions().stream().allMatch(grant -> Integer.valueOf(1).equals(grant.getGrantedBy())));
        access.applyPermissions(reviewer, "REVIEWER", List.of("CONTENT_REVIEW"), other);
        assertEquals(List.of("CONTENT_REVIEW"), reviewer.permissionCodes());
    }

    @Test void changingRoleDropsPermissionsOfTheFormerRole() {
        User account = TestPermissions.set(user("STAFF"), "TEACH", "DEPARTMENT_HEAD_PHYSICS");
        access.applyPermissions(account, "STUDENT", null, grantor());
        assertTrue(account.getPermissions().isEmpty());
        access.applyPermissions(account, "REVIEWER", null, grantor());
        assertEquals(List.of("CONTENT_EDIT", "CONTENT_REVIEW"), account.permissionCodes());
    }

    @ParameterizedTest @CsvSource({"MANAGER,true", "ADMIN,false", "SCHOOL,false", "STUDENT,false"})
    void onlyManagerReceivesBothPlatformCapabilitiesByRole(String role, boolean allowed) {
        User account = user(role); assertEquals(allowed, access.canEditContext(account)); assertEquals(allowed, access.canReviewPublic(account));
    }

    @Test void lockedAndLegacyNullActiveAccountsHaveNoCapabilities() {
        User reviewer = TestPermissions.defaults(user("REVIEWER")); reviewer.setActive(false);
        assertFalse(access.canEditContext(reviewer)); assertFalse(access.canReviewPublic(reviewer));
        reviewer.setActive(null); assertFalse(access.canEditContext(reviewer)); assertFalse(access.canReviewPublic(reviewer));
        User head = TestPermissions.set(user("STAFF"), "TEACH", "DEPARTMENT_HEAD_PHYSICS"); head.setActive(null);
        assertFalse(access.isDepartmentHead(head)); assertFalse(access.canTeach(head)); assertFalse(access.canReviewPublic(null));
    }
}
