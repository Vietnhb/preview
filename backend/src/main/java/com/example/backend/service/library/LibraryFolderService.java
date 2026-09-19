package com.example.backend.service.library;

import com.example.backend.service.account.CurrentUserService;

import com.example.backend.dto.library.LibraryFolderRequest;
import com.example.backend.dto.library.LibraryFolderResponse;
import com.example.backend.entity.library.LibraryFolder;
import com.example.backend.entity.account.User;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.library.LibraryFolderRepository;
import com.example.backend.repository.library.LibraryItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LibraryFolderService {
    private final LibraryFolderRepository folderRepository;
    private final LibraryItemRepository itemRepository;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public List<LibraryFolderResponse> mine() {
        User owner = currentUserService.requireCurrentUser();
        return folderRepository.findByOwnerIdAndActiveTrueOrderByNameAsc(owner.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public LibraryFolderResponse create(LibraryFolderRequest request) {
        User owner = currentUserService.requireCurrentUser();
        String name = normalizedName(request.name());
        String nameKey = nameKey(name);
        if (folderRepository.existsByOwnerIdAndActiveTrueAndNameKey(owner.getId(), nameKey)) {
            throw new ApiException(HttpStatus.CONFLICT, "A library folder with this name already exists");
        }
        LibraryFolder folder = new LibraryFolder();
        folder.setOwner(owner);
        folder.setName(name);
        folder.setNameKey(nameKey);
        try {
            return toResponse(folderRepository.saveAndFlush(folder));
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "A library folder with this name already exists");
        }
    }

    @Transactional
    public LibraryFolderResponse rename(UUID id, LibraryFolderRequest request) {
        User owner = currentUserService.requireCurrentUser();
        LibraryFolder folder = requireOwned(id, owner);
        String name = normalizedName(request.name());
        String nameKey = nameKey(name);
        if (folderRepository.existsByOwnerIdAndActiveTrueAndNameKeyAndIdNot(owner.getId(), nameKey, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "A library folder with this name already exists");
        }
        folder.setName(name);
        folder.setNameKey(nameKey);
        try {
            return toResponse(folderRepository.saveAndFlush(folder));
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "A library folder with this name already exists");
        }
    }

    @Transactional
    public void remove(UUID id) {
        User owner = currentUserService.requireCurrentUser();
        LibraryFolder folder = requireOwned(id, owner);
        if (itemRepository.existsByFolderIdAndActiveTrue(folder.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "Move or remove simulations before deleting this folder");
        }
        folder.setActive(false);
        folder.setNameKey(folder.getId().toString());
        folderRepository.save(folder);
    }

    private LibraryFolder requireOwned(UUID id, User owner) {
        return folderRepository.findByIdAndOwnerIdAndActiveTrue(id, owner.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Library folder not found"));
    }

    private String normalizedName(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private String nameKey(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private LibraryFolderResponse toResponse(LibraryFolder folder) {
        return new LibraryFolderResponse(folder.getId(), folder.getName(),
                itemRepository.countByFolderIdAndActiveTrue(folder.getId()),
                folder.getCreatedAt(), folder.getUpdatedAt());
    }
}
