package com.example.backend.service;

import com.example.backend.dto.library.LibraryFolderRequest;
import com.example.backend.entity.LibraryFolder;
import com.example.backend.entity.User;
import com.example.backend.repository.LibraryFolderRepository;
import com.example.backend.repository.LibraryItemRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LibraryFolderServiceTest {
    @Test
    void createsOwnedFolderWithNormalizedName() {
        LibraryFolderRepository folders = mock(LibraryFolderRepository.class);
        LibraryItemRepository items = mock(LibraryItemRepository.class);
        CurrentUserService currentUsers = mock(CurrentUserService.class);
        LibraryFolderService service = new LibraryFolderService(folders, items, currentUsers);
        User teacher = user(9);
        when(currentUsers.requireCurrentUser()).thenReturn(teacher);
        when(folders.saveAndFlush(any(LibraryFolder.class))).thenAnswer(invocation -> {
            LibraryFolder folder = invocation.getArgument(0);
            folder.setId(UUID.randomUUID());
            folder.setCreatedAt(Instant.now());
            folder.setUpdatedAt(Instant.now());
            return folder;
        });

        var response = service.create(new LibraryFolderRequest("  Chuyển động   lớp 10 "));

        assertThat(response.name()).isEqualTo("Chuyển động lớp 10");
        assertThat(response.id()).isNotNull();
    }

    @Test
    void refusesToDeleteNonEmptyFolder() {
        LibraryFolderRepository folders = mock(LibraryFolderRepository.class);
        LibraryItemRepository items = mock(LibraryItemRepository.class);
        CurrentUserService currentUsers = mock(CurrentUserService.class);
        LibraryFolderService service = new LibraryFolderService(folders, items, currentUsers);
        User teacher = user(9);
        LibraryFolder folder = folder(teacher);
        when(currentUsers.requireCurrentUser()).thenReturn(teacher);
        when(folders.findByIdAndOwnerIdAndActiveTrue(folder.getId(), teacher.getId())).thenReturn(Optional.of(folder));
        when(items.existsByFolderIdAndActiveTrue(folder.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.remove(folder.getId()))
                .hasMessageContaining("before deleting");
    }

    @Test
    void softDeletesEmptyOwnedFolder() {
        LibraryFolderRepository folders = mock(LibraryFolderRepository.class);
        LibraryItemRepository items = mock(LibraryItemRepository.class);
        CurrentUserService currentUsers = mock(CurrentUserService.class);
        LibraryFolderService service = new LibraryFolderService(folders, items, currentUsers);
        User teacher = user(9);
        LibraryFolder folder = folder(teacher);
        when(currentUsers.requireCurrentUser()).thenReturn(teacher);
        when(folders.findByIdAndOwnerIdAndActiveTrue(folder.getId(), teacher.getId())).thenReturn(Optional.of(folder));

        service.remove(folder.getId());

        assertThat(folder.isActive()).isFalse();
        verify(folders).save(folder);
    }

    private static User user(int id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private static LibraryFolder folder(User owner) {
        LibraryFolder folder = new LibraryFolder();
        folder.setId(UUID.randomUUID());
        folder.setOwner(owner);
        folder.setName("Cơ học");
        folder.setActive(true);
        return folder;
    }
}
