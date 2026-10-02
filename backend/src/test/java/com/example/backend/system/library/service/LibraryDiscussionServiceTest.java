package com.example.backend.system.library.service;

import com.example.backend.exception.ApiException;
import com.example.backend.system.account.TestPermissions;
import com.example.backend.system.account.model.entity.Role;
import com.example.backend.system.account.model.entity.User;
import com.example.backend.system.account.service.AccountAccessService;
import com.example.backend.system.account.service.CurrentUserService;
import com.example.backend.system.library.model.entity.LibraryComment;
import com.example.backend.system.library.model.entity.LibraryItem;
import com.example.backend.system.library.model.enums.LibraryModerationStatus;
import com.example.backend.system.library.model.enums.Visibility;
import com.example.backend.system.library.repository.LibraryCommentRepository;
import com.example.backend.system.library.repository.LibraryItemRepository;
import com.example.backend.system.school.model.entity.School;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LibraryDiscussionServiceTest {
    private final LibraryItemRepository items = mock(LibraryItemRepository.class);
    private final LibraryCommentRepository comments = mock(LibraryCommentRepository.class);
    private final CurrentUserService current = mock(CurrentUserService.class);
    private final LibraryDiscussionService service = new LibraryDiscussionService(items, comments, current, TestPermissions.access());
    private final UUID schoolId = UUID.randomUUID();

    @Test
    void anonymousCanReadPublishedDiscussionWithoutInteractionOrAuthorEmail() {
        LibraryItem item = item(Visibility.PUBLIC);
        LibraryComment comment = comment(item, user(7, "STAFF", schoolId));
        comment.getAuthor().setAvatarUrl("https://example.com/avatar.jpg");
        read(item, List.of(comment), false);
        when(items.countLikes(item.getId())).thenReturn(3L);
        when(comments.countByItemId(item.getId())).thenReturn(8L);

        var result = service.discussion(item.getId(), 0, 20);

        assertEquals(3, result.likes());
        assertEquals(8, result.commentCount());
        assertFalse(result.liked());
        assertFalse(result.canInteract());
        assertEquals(2000, result.commentMaxLength());
        assertFalse(result.comments().getFirst().canDelete());
        assertEquals(7, result.comments().getFirst().authorId());
        assertEquals("https://example.com/avatar.jpg", result.comments().getFirst().avatarUrl());
        verify(items, never()).hasLiked(any(), any());
    }

    @ParameterizedTest
    @CsvSource({"PERSONAL,APPROVED,true", "SHARED,APPROVED,true", "PUBLIC,PENDING,true",
            "PUBLIC,REJECTED,true", "PUBLIC,REMOVED,false", "PUBLIC,APPROVED,false"})
    void anonymousCannotInspectUnpublishedPrivateOrSchoolDiscussions(String scope, String status, boolean active) {
        LibraryItem item = item(Visibility.valueOf(scope));
        item.setModerationStatus(LibraryModerationStatus.valueOf(status));
        item.setActive(active);
        when(items.findById(item.getId())).thenReturn(Optional.of(item));

        assertStatus(HttpStatus.NOT_FOUND, () -> service.discussion(item.getId(), 0, 20));
        verifyNoInteractions(comments);
        verify(items, never()).countLikes(any());
    }

    @Test
    void schoolDiscussionUsesExplicitScopeAndLegacyOwnerSchool() {
        User viewer = user(4, "STUDENT", schoolId);
        when(current.currentUserOrNull()).thenReturn(viewer);
        LibraryItem item = item(Visibility.SHARED);
        read(item, List.of(), false);
        assertTrue(service.discussion(item.getId(), 0, 20).canInteract());
        item.setSharedInstitutionId(" ");
        assertTrue(service.discussion(item.getId(), 0, 20).canInteract());
        item.setSharedInstitutionId(UUID.randomUUID().toString());
        assertStatus(HttpStatus.NOT_FOUND, () -> service.discussion(item.getId(), 0, 20));
        item.setSharedInstitutionId(null);
        item.getOwner().getSchool().setId(UUID.randomUUID());
        assertStatus(HttpStatus.NOT_FOUND, () -> service.discussion(item.getId(), 0, 20));
    }

    @Test
    void commentStoresTrimmedPlainTextAndReturnsIdentity() {
        User author = user(4, "STUDENT", schoolId);
        LibraryItem item = item(Visibility.PUBLIC);
        write(item, author);
        when(comments.save(any())).thenAnswer(call -> {
            LibraryComment saved = call.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setCreatedAt(Instant.parse("2026-10-01T07:00:00Z"));
            return saved;
        });

        var result = service.comment(item.getId(), "  <b>Giải thích lực?</b>  ");

        assertEquals("<b>Giải thích lực?</b>", result.body());
        assertEquals(author.getId(), result.authorId());
        assertTrue(result.canDelete());
        assertNotNull(result.id());
        assertNotNull(result.createdAt());
        verify(items).findByIdForUpdate(item.getId());
    }

    @Test
    void configuredLimitsRejectBlankOversizedCommentsAndUnboundedPagination() {
        when(current.requireCurrentUser()).thenReturn(user(4, "STUDENT", schoolId));
        ReflectionTestUtils.setField(service, "maxCommentCharacters", 12);
        ReflectionTestUtils.setField(service, "maxPageSize", 25);
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.comment(UUID.randomUUID(), null));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.comment(UUID.randomUUID(), " \n "));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.comment(UUID.randomUUID(), "x".repeat(13)));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.discussion(UUID.randomUUID(), -1, 20));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.discussion(UUID.randomUUID(), 0, 0));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.discussion(UUID.randomUUID(), 0, 26));
        verifyNoInteractions(items, comments);
    }

    @ParameterizedTest
    @CsvSource({"ADMIN,true,false", "STUDENT,false,false", "STUDENT,true,true", "LEGACY_ROLE,true,false"})
    void restrictedDisabledAndInitialPasswordAccountsCannotWrite(String role, boolean active, boolean mustChange) {
        User actor = user(4, role, schoolId);
        actor.setActive(active);
        actor.setMustChangePassword(mustChange);
        when(current.requireCurrentUser()).thenReturn(actor);
        assertStatus(HttpStatus.FORBIDDEN, () -> service.comment(UUID.randomUUID(), "Valid"));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.react(UUID.randomUUID(), true));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.removeComment(UUID.randomUUID(), UUID.randomUUID()));
        verifyNoInteractions(items, comments);
    }

    @Test
    void anonymousCannotWriteEvenWhenResourceIsPublic() {
        when(current.requireCurrentUser()).thenThrow(new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required"));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> service.comment(UUID.randomUUID(), "Valid"));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> service.react(UUID.randomUUID(), true));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> service.removeComment(UUID.randomUUID(), UUID.randomUUID()));
        verifyNoInteractions(items, comments);
    }

    @Test
    void interactionsCannotUseOtherSchoolContentOrWithdrawnResources() {
        LibraryItem item = item(Visibility.SHARED);
        write(item, user(4, "STUDENT", UUID.randomUUID()));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.react(item.getId(), true));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.comment(item.getId(), "Valid"));
        item.setVisibility(Visibility.PUBLIC);
        item.setActive(false);
        assertStatus(HttpStatus.NOT_FOUND, () -> service.react(item.getId(), true));
        assertStatus(HttpStatus.NOT_FOUND, () -> service.comment(item.getId(), "Valid"));
        verifyNoInteractions(comments);
        verify(items, never()).addLike(any(), any());
    }

    @ParameterizedTest
    @CsvSource({"PUBLIC,STUDENT,false,false,false", "PUBLIC,MANAGER,false,false,true",
            "PUBLIC,REVIEWER,false,true,true", "PUBLIC,REVIEWER,false,false,false",
            "PUBLIC,STAFF,true,false,false", "SHARED,STAFF,true,false,true",
            "SHARED,STAFF,false,false,false", "SHARED,SCHOOL,false,false,false"})
    void deletionRequiresCommentOwnerOrModeratorForThatPublicationScope(String scope, String role,
            boolean head, boolean reviewer, boolean allowed) {
        LibraryItem item = item(Visibility.valueOf(scope));
        User viewer = user(4, role, schoolId);
        if (head) TestPermissions.set(viewer, "DEPARTMENT_HEAD_PHYSICS");
        else if (reviewer) TestPermissions.set(viewer, "CONTENT_REVIEW");
        else TestPermissions.set(viewer, "TEACH");
        write(item, viewer);
        LibraryComment comment = comment(item, user(7, "STAFF", schoolId));
        when(comments.findByIdAndItemId(comment.getId(), item.getId())).thenReturn(Optional.of(comment));

        if (allowed) {
            service.removeComment(item.getId(), comment.getId());
            verify(comments).delete(comment);
        } else {
            assertStatus(HttpStatus.FORBIDDEN, () -> service.removeComment(item.getId(), comment.getId()));
            verify(comments, never()).delete(any());
        }
    }

    @Test
    void ownerCanDeleteOwnCommentAndIdsAreBoundToTheResource() {
        LibraryItem item = item(Visibility.PUBLIC);
        User viewer = user(4, "STUDENT", schoolId);
        write(item, viewer);
        LibraryComment comment = comment(item, viewer);
        when(comments.findByIdAndItemId(comment.getId(), item.getId())).thenReturn(Optional.of(comment));
        service.removeComment(item.getId(), comment.getId());
        verify(comments).delete(comment);
        assertStatus(HttpStatus.NOT_FOUND, () -> service.removeComment(item.getId(), UUID.randomUUID()));
    }

    @Test
    void pageMetadataAndDeletionCapabilityAreComputedForTheCurrentViewer() {
        LibraryItem item = item(Visibility.PUBLIC);
        User viewer = user(4, "STUDENT", schoolId);
        when(current.currentUserOrNull()).thenReturn(viewer);
        when(items.findById(item.getId())).thenReturn(Optional.of(item));
        when(items.hasLiked(item.getId(), viewer.getId())).thenReturn(true);
        var requested = PageRequest.of(2, 10);
        when(comments.findByItemIdOrderByCreatedAtDescIdDesc(item.getId(), requested))
                .thenReturn(new SliceImpl<>(List.of(comment(item, viewer), comment(item, user(7, "STAFF", schoolId))), requested, true));

        var result = service.discussion(item.getId(), 2, 10);

        assertEquals(2, result.page());
        assertTrue(result.hasMore());
        assertTrue(result.liked());
        assertTrue(result.comments().getFirst().canDelete());
        assertFalse(result.comments().getLast().canDelete());
    }

    @Test
    void reactionUsesDesiredStateInsteadOfTogglingAndReturnsCurrentCount() {
        LibraryItem item = item(Visibility.PUBLIC);
        User viewer = user(4, "STUDENT", schoolId);
        write(item, viewer);
        when(items.countLikes(item.getId())).thenReturn(1L, 1L, 0L, 0L);

        assertEquals(1L, service.react(item.getId(), true).likes());
        assertTrue(service.react(item.getId(), true).liked());
        assertEquals(0L, service.react(item.getId(), false).likes());
        assertFalse(service.react(item.getId(), false).liked());
        verify(items, times(2)).addLike(item.getId(), viewer.getId());
        verify(items, times(2)).removeLike(item.getId(), viewer.getId());
    }

    private void read(LibraryItem item, List<LibraryComment> result, boolean hasMore) {
        when(items.findById(item.getId())).thenReturn(Optional.of(item));
        var page = PageRequest.of(0, 20);
        when(comments.findByItemIdOrderByCreatedAtDescIdDesc(item.getId(), page)).thenReturn(new SliceImpl<>(result, page, hasMore));
    }

    private void write(LibraryItem item, User actor) {
        when(current.requireCurrentUser()).thenReturn(actor);
        when(items.findByIdForUpdate(item.getId())).thenReturn(Optional.of(item));
    }

    private User user(int id, String role, UUID school) {
        User user = new User();
        user.setId(id);
        user.setFullName("User " + id);
        Role accountRole = new Role();
        accountRole.setName(role);
        user.setRole(accountRole);
        if (school != null) {
            School membership = new School();
            membership.setId(school);
            user.setSchool(membership);
        }
        return user;
    }

    private LibraryItem item(Visibility visibility) {
        LibraryItem item = new LibraryItem();
        item.setId(UUID.randomUUID());
        item.setOwner(user(7, "STAFF", schoolId));
        item.setVisibility(visibility);
        item.setSharedInstitutionId(visibility == Visibility.SHARED ? schoolId.toString() : null);
        return item;
    }

    private LibraryComment comment(LibraryItem item, User author) {
        LibraryComment comment = new LibraryComment();
        comment.setId(UUID.randomUUID());
        comment.setItem(item);
        comment.setAuthor(author);
        comment.setBody("Bình luận");
        comment.setCreatedAt(Instant.now());
        return comment;
    }

    private void assertStatus(HttpStatus expected, org.junit.jupiter.api.function.Executable action) {
        assertEquals(expected, assertThrows(ApiException.class, action).getStatus());
    }
}
