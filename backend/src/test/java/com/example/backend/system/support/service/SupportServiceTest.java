package com.example.backend.system.support.service;

import com.example.backend.exception.ApiException;
import com.example.backend.system.account.model.entity.Role;
import com.example.backend.system.account.model.entity.User;
import com.example.backend.system.account.model.enums.RoleName;
import com.example.backend.system.account.service.CurrentUserService;
import com.example.backend.system.support.dto.SupportContracts.UpdateSupportRequest;
import com.example.backend.system.support.dto.SupportContracts;
import com.example.backend.system.support.model.entity.SupportItem;
import com.example.backend.system.support.model.enums.SupportKind;
import com.example.backend.system.support.model.enums.SupportStatus;
import com.example.backend.system.support.repository.SupportItemRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SupportServiceTest {
    private final SupportItemRepository repository = mock(SupportItemRepository.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final SupportService service = new SupportService(repository, currentUser);

    private User user(RoleName role, int id) {
        Role entity = new Role(); entity.setName(role.name());
        User user = new User(); user.setId(id); user.setRole(entity); user.setFullName("Test user"); user.setEmail("user@example.test");
        return user;
    }

    @Test
    void nonManagersCannotReadPlatformSupportInboxThroughService() {
        for (RoleName role : RoleName.values()) {
            if (role == RoleName.MANAGER) continue;
            when(currentUser.requireCurrentUser()).thenReturn(user(role, 1));
            assertThrows(ApiException.class, () -> service.adminList(SupportKind.FEEDBACK), role.name());
        }
        verifyNoInteractions(repository);
    }

    @Test
    void ownMessagesRemainScopedToAuthenticatedSender() {
        User sender = user(RoleName.STUDENT, 12);
        when(currentUser.requireCurrentUser()).thenReturn(sender);
        SupportItem item = new SupportItem(); item.setId(UUID.randomUUID()); item.setSender(sender);
        item.setKind(SupportKind.MESSAGE); item.setSubject("Question"); item.setContent("Content"); item.setStatus(SupportStatus.OPEN);
        when(repository.findBySenderIdOrderByCreatedAtDesc(12)).thenReturn(List.of(item));
        var views = service.mine();
        assertEquals(1, views.size()); assertEquals(12, views.getFirst().senderId());
        assertEquals("Question", views.getFirst().subject());
        verify(repository).findBySenderIdOrderByCreatedAtDesc(12);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void managerResponseRetainsViewAndUpdatesReadStatus() {
        User manager = user(RoleName.MANAGER, 2), sender = user(RoleName.STUDENT, 12);
        when(currentUser.requireCurrentUser()).thenReturn(manager);
        SupportItem item = new SupportItem(); item.setId(UUID.randomUUID()); item.setSender(sender);
        item.setKind(SupportKind.FEEDBACK); item.setSubject("Feedback"); item.setContent("Original message"); item.setStatus(SupportStatus.OPEN);
        when(repository.findById(item.getId())).thenReturn(java.util.Optional.of(item));
        when(repository.save(item)).thenReturn(item);
        var response = service.update(item.getId(), new UpdateSupportRequest(null, "  Resolved  "));
        assertEquals(SupportStatus.READ, response.status()); assertEquals("Resolved", response.adminResponse());
        assertEquals("Original message", response.content()); assertNotNull(response.respondedAt());
        assertSame(manager, item.getRespondedBy());
    }
}
