package com.example.backend.security;

import com.example.backend.entity.account.Role;
import com.example.backend.entity.account.User;
import com.example.backend.exception.ApiException;
import com.example.backend.service.account.AccountAccessService;
import com.example.backend.service.account.CurrentUserService;
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
        "true,false,POST,/api/simulations/id/approve,false", "false,true,POST,/api/simulations/id/approve,true",
        "true,false,PUT,/api/simulations/id/reject,false", "false,true,PUT,/api/simulations/id/reject,true",
        "true,false,POST,/api/evaluations,true", "false,true,GET,/api/evaluations/history,false",
        "false,true,GET,/api/schemas,true", "false,true,HEAD,/api/schemas,true",
        "false,true,POST,/api/schemas,false", "true,false,PUT,/api/schemas/topic,true"
    })
    void editorAndModeratorRoutesUseIndependentDatabaseCapabilities(boolean edit, boolean review, String method, String path, boolean allowed) {
        User user = new User(); Role role = new Role(); role.setName("REVIEWER"); user.setRole(role);
        user.setReviewerCanEdit(edit); user.setReviewerCanReview(review);
        var current = mock(CurrentUserService.class); when(current.requireCurrentUser()).thenReturn(user);
        var interceptor = new AccountCapabilityInterceptor(new AccountAccessService(), current);
        var request = new MockHttpServletRequest(method, path); request.setServletPath(path);
        if (allowed) assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        else assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class,
            () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).getStatus());
    }
}
