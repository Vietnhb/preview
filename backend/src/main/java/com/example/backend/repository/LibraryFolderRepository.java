package com.example.backend.repository;

import com.example.backend.entity.LibraryFolder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LibraryFolderRepository extends JpaRepository<LibraryFolder, UUID> {
    List<LibraryFolder> findByOwnerIdAndActiveTrueOrderByNameAsc(Integer ownerId);

    Optional<LibraryFolder> findByIdAndOwnerIdAndActiveTrue(UUID id, Integer ownerId);

    boolean existsByOwnerIdAndActiveTrueAndNameKey(Integer ownerId, String nameKey);

    boolean existsByOwnerIdAndActiveTrueAndNameKeyAndIdNot(Integer ownerId, String nameKey, UUID id);
}
