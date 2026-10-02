package com.example.backend.system.library.repository;

import com.example.backend.system.library.model.entity.LibraryFolder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LibraryFolderRepository extends JpaRepository<LibraryFolder, UUID> {
    List<LibraryFolder> findByOwnerIdAndActiveTrueOrderByNameAsc(Integer ownerId);

    Optional<LibraryFolder> findByIdAndOwnerIdAndActiveTrue(UUID id, Integer ownerId);

    boolean existsByOwnerIdAndActiveTrueAndNameKey(Integer ownerId, String nameKey);

    boolean existsByOwnerIdAndActiveTrueAndNameKeyAndIdNot(Integer ownerId, String nameKey, UUID id);
}
