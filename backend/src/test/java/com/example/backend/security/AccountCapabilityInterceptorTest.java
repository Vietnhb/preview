package com.example.backend.security;

import com.example.backend.exception.ApiException;
import com.example.backend.system.account.model.entity.Permission;
import com.example.backend.system.account.TestPermissions;
import com.example.backend.system.account.model.entity.Role;
import com.example.backend.system.account.model.entity.User;
import com.example.backend.system.account.service.AccountAccessService;
import com.example.backend.system.account.service.CurrentUserService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountCapabilityInterceptorTest {
    @ParameterizedTest @CsvSource({
        "true,false,GET,/api/reviewer/solvers,true", "false,true,GET,/api/reviewer/solvers,false",
        "true,false,GET,/api/reviewer/library,false", "false,true,GET,/api/reviewer/library,true",
        "true,false,PUT,/api/reviewer/library/id,false", "false,true,PUT,/api/reviewer/library/id,true",
        "true,false,GET,/api/reviewer/library/id/history,false", "false,true,GET,/api/reviewer/library/id/history,true",
        "true,false,GET,/api/reviewer/library/page,false", "false,true,GET,/api/reviewer/library/page,true",
        "true,false,POST,/api/evaluations,true", "false,true,GET,/api/evaluations/history,false",
        "false,true,GET,/api/schemas,true", "false,true,HEAD,/api/schemas,true",
        "false,true,POST,/api/schemas,false", "true,false,PUT,/api/schemas/topic,true"
    })
    void editorAndModeratorRoutesUseIndependentDatabaseCapabilities(boolean edit, boolean review, String method, String path, boolean allowed) {
        User user = new User(); Role role = new Role(); role.setName("REVIEWER"); user.setRole(role);
        if (edit) user.grantPermission(new Permission("CONTENT_EDIT"), null);
        if (review) user.grantPermission(new Permission("CONTENT_REVIEW"), null);
        var current = mock(CurrentUserService.class); when(current.requireCurrentUser()).thenReturn(user);
        var interceptor = new AccountCapabilityInterceptor(TestPermissions.access(), current);
        var request = new MockHttpServletRequest(method, path); request.setServletPath(path);
        if (allowed) assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        else assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class,
            () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).getStatus());
    }

    @ParameterizedTest @CsvSource({
        "TEACH,POST,/api/simulation/generate,true", "DEPARTMENT_HEAD_PHYSICS,POST,/api/simulation/generate,false",
        "DEPARTMENT_HEAD_PHYSICS,GET,/api/assignments/mine/teacher,false", "TEACH,GET,/api/assignments/mine/teacher,true",
        "DEPARTMENT_HEAD_PHYSICS,POST,/api/assignments,false", "DEPARTMENT_HEAD_PHYSICS,GET,/api/user/students,false",
        "DEPARTMENT_HEAD_PHYSICS,POST,/api/library,false", "DEPARTMENT_HEAD_PHYSICS,GET,/api/library/mine,false",
        "DEPARTMENT_HEAD_PHYSICS,GET,/api/library/folders,false", "DEPARTMENT_HEAD_PHYSICS,DELETE,/api/library/id,false",
        "DEPARTMENT_HEAD_PHYSICS,GET,/api/library/community,true", "DEPARTMENT_HEAD_PHYSICS,GET,/api/library/id/discussion,true",
        "DEPARTMENT_HEAD_PHYSICS,POST,/api/library/id/comments,true", "DEPARTMENT_HEAD_PHYSICS,DELETE,/api/library/id/comments/c1,true",
        "DEPARTMENT_HEAD_PHYSICS,PUT,/api/library/id/reaction,true", "DEPARTMENT_HEAD_PHYSICS,GET,/api/problems,false",
        "TEACH,POST,/api/library,true", "TEACH,GET,/api/exports/id/pdf,true", "DEPARTMENT_HEAD_PHYSICS,GET,/api/exports/id/pdf,false"
    })
    void teachingRoutesRequireTheTeachPermissionForStaff(String permission, String method, String path, boolean allowed) {
        User user = new User(); Role role = new Role(); role.setName("STAFF"); user.setRole(role);
        user.grantPermission(new Permission(permission), null);
        var current = mock(CurrentUserService.class); when(current.currentUserOrNull()).thenReturn(user);
        var interceptor = new AccountCapabilityInterceptor(TestPermissions.access(), current);
        var request = new MockHttpServletRequest(method, path); request.setServletPath(path);
        if (allowed) assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        else assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class,
            () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).getStatus());
    }

    @ParameterizedTest @CsvSource({"STUDENT,POST,/api/assignments/id/submit", "STUDENT,GET,/api/library", ",GET,/api/library/community"})
    void otherRolesAndAnonymousReadersAreNotAffectedByTheTeachingGate(String roleName, String method, String path) {
        User user = null;
        if (roleName != null) { user = new User(); Role role = new Role(); role.setName(roleName); user.setRole(role); }
        var current = mock(CurrentUserService.class); when(current.currentUserOrNull()).thenReturn(user);
        var interceptor = new AccountCapabilityInterceptor(TestPermissions.access(), current);
        var request = new MockHttpServletRequest(method, path); request.setServletPath(path);
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }
}
