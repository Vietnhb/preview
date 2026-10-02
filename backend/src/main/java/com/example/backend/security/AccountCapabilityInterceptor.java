package com.example.backend.security;

import com.example.backend.exception.ApiException;
import com.example.backend.system.account.service.AccountAccessService;
import com.example.backend.system.account.service.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Central enforcement of account permissions (user_permissions) on top of the role gates:
 * editor and public moderator routes for REVIEWER, and teaching routes for STAFF.
 */
@Component
@RequiredArgsConstructor
public class AccountCapabilityInterceptor implements HandlerInterceptor {
    /** Routes this interceptor must be registered for. */
    public static final String[] PATHS = {"/api/reviewer/**", "/api/evaluations/**", "/api/schemas/**",
        "/api/simulation/**", "/api/problems/**", "/api/specifications/**", "/api/exports/**",
        "/api/assignments", "/api/assignments/**", "/api/user/students", "/api/library", "/api/library/**"};
    private static final List<String> READ_METHODS = List.of("GET", "HEAD", "OPTIONS");
    private static final Pattern COMMUNITY_INTERACTION = Pattern.compile("^/api/library/[^/]+/(comments(/[^/]+)?|reaction)$");

    private final AccountAccessService access;
    private final CurrentUserService currentUser;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getServletPath();
        boolean read = READ_METHODS.contains(request.getMethod());
        boolean review = path.startsWith("/api/reviewer/library");
        boolean editor = path.startsWith("/api/reviewer/") && !review
                || path.startsWith("/api/evaluations")
                || path.startsWith("/api/schemas") && !read;
        if (review && !access.canReviewPublic(currentUser.requireCurrentUser())
                || editor && !access.canEditContext(currentUser.requireCurrentUser())
                // Anonymous community reads stay public; only STAFF accounts without TEACH are stopped here.
                || teaching(path, read) && access.isStaffWithoutTeaching(currentUser.currentUserOrNull()))
            throw ApiException.forbidden("Tài khoản không được cấp quyền cho thao tác này.");
        return true;
    }

    /** Simulation authoring, personal library management, class assignments and grading. */
    private static boolean teaching(String path, boolean read) {
        if (path.startsWith("/api/simulation/") || path.startsWith("/api/problems") || path.startsWith("/api/specifications")
                || path.startsWith("/api/exports") || path.startsWith("/api/assignments") || path.equals("/api/user/students")
                || path.startsWith("/api/library/folders") || path.equals("/api/library/mine")) return true;
        return path.startsWith("/api/library") && !read && !COMMUNITY_INTERACTION.matcher(path).matches();
    }
}
