package com.example.backend.config.web;

import com.example.backend.service.realtime.RealtimeEventService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.mockito.Mockito.*;

class RealtimeMutationFilterTest {
    private final RealtimeEventService events = mock(RealtimeEventService.class);
    private final RealtimeMutationFilter filter = new RealtimeMutationFilter(Optional.of(events));

    @Test
    void publishesEverySuccessfulMutationMethodAndVnpayIpn() throws Exception {
        for (String method : new String[] { "POST", "PUT", "PATCH", "DELETE" }) {
            execute(method, "/api/domain/resource", 200, "browser-1");
        }
        execute("POST", "/api/auth/login", 200, null);
        execute("GET", "/api/auth/payments/vnpay/ipn", 200, null);

        verify(events, times(4)).publish("/api/domain/resource", "browser-1");
        verify(events).publish("/api/auth/login", null);
        verify(events).publish("/api/auth/payments/vnpay/ipn", null);
    }

    @Test
    void ignoresReadsFailuresAndReadOnlyPosts() throws Exception {
        execute("GET", "/api/domain/resource", 200, null);
        execute("POST", "/api/domain/resource", 409, null);
        execute("POST", "/api/school/billing/quote", 200, null);

        verifyNoInteractions(events);
    }

    private void execute(String method, String path, int status, String clientId) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        if (clientId != null) request.addHeader(RealtimeMutationFilter.CLIENT_ID_HEADER, clientId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (ignoredRequest, servletResponse) -> ((MockHttpServletResponse) servletResponse).setStatus(status);
        filter.doFilter(request, response, chain);
    }
}
