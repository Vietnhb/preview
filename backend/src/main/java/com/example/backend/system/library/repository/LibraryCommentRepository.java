package com.example.backend.system.library.repository;

import com.example.backend.system.library.model.entity.LibraryComment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LibraryCommentRepository extends JpaRepository<LibraryComment, UUID> {
    @EntityGraph(attributePaths = "author")
    Slice<LibraryComment> findByItemIdOrderByCreatedAtDescIdDesc(UUID itemId, Pageable pageable);

    Optional<LibraryComment> findByIdAndItemId(UUID id, UUID itemId);

    long countByItemId(UUID itemId);
}
