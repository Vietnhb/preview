package com.example.backend.system.library.service;

import com.example.backend.exception.ApiException;
import com.example.backend.system.account.model.entity.User;
import com.example.backend.system.account.service.CurrentUserService;
import com.example.backend.system.library.dto.LibraryFolders;
import com.example.backend.system.library.model.entity.LibraryFolder;
import com.example.backend.system.library.repository.LibraryFolderRepository;
import com.example.backend.system.library.repository.LibraryItemRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LibraryFolderService {
    private final LibraryFolderRepository folderRepository;
    private final LibraryItemRepository itemRepository;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public List<LibraryFolders.Response> mine() {
        User owner = currentUserService.requireCurrentUser();
        return folderRepository.findByOwnerIdAndActiveTrueOrderByNameAsc(owner.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public LibraryFolders.Response create(LibraryFolders.Request request) {
        User owner = currentUserService.requireCurrentUser();
        String name = normalizedName(request.name());
        String nameKey = nameKey(name);
        if (folderRepository.existsByOwnerIdAndActiveTrueAndNameKey(owner.getId(), nameKey)) {
            throw ApiException.conflict("Đã có thư mục thư viện trùng tên");
        }
        LibraryFolder folder = new LibraryFolder();
        folder.setOwner(owner);
        folder.setName(name);
        folder.setNameKey(nameKey);
        try {
            return toResponse(folderRepository.saveAndFlush(folder));
        } catch (DataIntegrityViolationException exception) {
            throw ApiException.conflict("Đã có thư mục thư viện trùng tên");
        }
    }

    @Transactional
    public LibraryFolders.Response rename(UUID id, LibraryFolders.Request request) {
        User owner = currentUserService.requireCurrentUser();
        LibraryFolder folder = requireOwned(id, owner);
        String name = normalizedName(request.name());
        String nameKey = nameKey(name);
        if (folderRepository.existsByOwnerIdAndActiveTrueAndNameKeyAndIdNot(owner.getId(), nameKey, id)) {
            throw ApiException.conflict("Đã có thư mục thư viện trùng tên");
        }
        folder.setName(name);
        folder.setNameKey(nameKey);
        try {
            return toResponse(folderRepository.saveAndFlush(folder));
        } catch (DataIntegrityViolationException exception) {
            throw ApiException.conflict("Đã có thư mục thư viện trùng tên");
        }
    }

    @Transactional
    public void remove(UUID id) {
        User owner = currentUserService.requireCurrentUser();
        LibraryFolder folder = requireOwned(id, owner);
        if (itemRepository.existsByFolderIdAndActiveTrue(folder.getId())) {
            throw ApiException.conflict("Vui lòng di chuyển hoặc xóa các mô phỏng trước khi xóa thư mục");
        }
        folder.setActive(false);
        folder.setNameKey(folder.getId().toString());
        folderRepository.save(folder);
    }

    private LibraryFolder requireOwned(UUID id, User owner) {
        return folderRepository.findByIdAndOwnerIdAndActiveTrue(id, owner.getId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy thư mục thư viện"));
    }

    private String normalizedName(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private String nameKey(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private LibraryFolders.Response toResponse(LibraryFolder folder) {
        return new LibraryFolders.Response(folder.getId(), folder.getName(),
                itemRepository.countByFolderIdAndActiveTrue(folder.getId()),
                folder.getCreatedAt(), folder.getUpdatedAt());
    }
}
