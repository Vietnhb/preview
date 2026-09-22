package com.example.backend.config.web;

import java.io.IOException;
import java.util.Set;
import java.util.Optional;

import com.example.backend.service.realtime.RealtimeEventService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class RealtimeMutationFilter extends OncePerRequestFilter {
    public static final String CLIENT_ID_HEADER = "X-PhysLive-Client";
    private static final Set<String> MUTATION_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private final Optional<RealtimeEventService> realtime;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(request, response);
        if (response.getStatus() >= 400 || !changesData(request)) return;
        realtime.ifPresent(events -> events.publish(request.getRequestURI(), request.getHeader(CLIENT_ID_HEADER)));
    }

    private boolean changesData(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/") || path.startsWith("/api/realtime/")) return false;
        if (path.endsWith("/billing/quote")) return false;
        return MUTATION_METHODS.contains(request.getMethod())
            || path.equals("/api/auth/payments/vnpay/ipn");
    }
}
