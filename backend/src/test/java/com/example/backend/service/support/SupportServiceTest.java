package com.example.backend.service.support;

import com.example.backend.dto.support.CreateSupportRequest;
import com.example.backend.dto.support.UpdateSupportRequest;
import com.example.backend.entity.account.Role;
import com.example.backend.entity.account.User;
import com.example.backend.entity.enums.SupportKind;
import com.example.backend.entity.enums.SupportStatus;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.support.SupportItemRepository;
import com.example.backend.service.account.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class SupportServiceTest {
    private final SupportItemRepository repository = mock(SupportItemRepository.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final SupportService service = new SupportService(repository, currentUser);
    private User sender;

    @BeforeEach
    void setUp() {
        sender = new User();
        sender.setId(7);
        sender.setEmail("sender@example.test");
        sender.setFullName("Sender");
        when(currentUser.requireCurrentUser()).thenReturn(sender);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createTrimsPayloadAndStartsOpen() {
        var view = service.create(SupportKind.FEEDBACK,
                new CreateSupportRequest("  Subject  ", "  Content  "));

        assertEquals("Subject", view.subject());
        assertEquals("Content", view.content());
        assertEquals(SupportStatus.OPEN, view.status());
    }

    @Test
    void createRejectsSubjectLongerThanDatabaseColumn() {
        String subject = "x".repeat(181);

        assertThrows(ApiException.class, () -> service.create(SupportKind.MESSAGE,
                new CreateSupportRequest(subject, "Content")));
        verify(repository, never()).save(any());
    }

    @Test
    void adminResponseMovesOpenItemToReadByDefault() {
        Role role = new Role();
        role.setName("ADMIN");
        sender.setRole(role);
        var item = new com.example.backend.entity.support.SupportItem();
        item.setId(UUID.randomUUID());
        item.setKind(SupportKind.MESSAGE);
        item.setSender(sender);
        item.setSubject("Question");
        item.setContent("Content");
        item.setStatus(SupportStatus.OPEN);
        when(repository.findById(item.getId())).thenReturn(Optional.of(item));

        var view = service.update(item.getId(), new UpdateSupportRequest(null, "Resolved guidance"));

        assertEquals(SupportStatus.READ, view.status());
        assertEquals("Resolved guidance", view.adminResponse());
    }
}
