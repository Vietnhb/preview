package com.example.backend.security;

import com.example.backend.exception.ApiException;
import com.example.backend.service.account.AccountAccessService;
import com.example.backend.service.account.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Central enforcement for editor and public moderator routes, including read access. */
@Component
@RequiredArgsConstructor
public class AccountCapabilityInterceptor implements HandlerInterceptor {
    private final AccountAccessService access;
    private final CurrentUserService currentUser;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getServletPath();
        boolean review = path.startsWith("/api/reviewer/library")
                || path.matches("/api/simulations/[^/]+/(approve|reject)");
        boolean editor = path.startsWith("/api/reviewer/") && !review
                || path.startsWith("/api/evaluations")
                || path.startsWith("/api/schemas") && !java.util.List.of("GET", "HEAD", "OPTIONS").contains(request.getMethod());
        if (review && !access.canReviewPublic(currentUser.requireCurrentUser())
                || editor && !access.canEditContext(currentUser.requireCurrentUser()))
            throw new ApiException(HttpStatus.FORBIDDEN, "Tài khoản không được cấp quyền cho thao tác này.");
        return true;
    }
}
