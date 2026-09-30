package com.example.backend.service.account;

import com.example.backend.entity.account.User;
import com.example.backend.repository.account.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CurrentUserServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final CurrentUserService service = new CurrentUserService(users);

    @AfterEach void clearSecurityContext() { SecurityContextHolder.clearContext(); }

    @Test void absentAuthenticationDoesNotLookUpAnAccount() {
        SecurityContextHolder.clearContext();
        assertNull(service.currentUserOrNull());
        verifyNoInteractions(users);
    }

    @Test void anonymousPrincipalDoesNotLookUpAnAccount() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        assertNull(service.currentUserOrNull());
        verifyNoInteractions(users);
    }

    @Test void authenticatedPrincipalResolvesItsAccount() {
        User user = new User();
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "student@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
        when(users.findByEmail("student@example.com")).thenReturn(Optional.of(user));
        assertSame(user, service.currentUserOrNull());
    }
}
