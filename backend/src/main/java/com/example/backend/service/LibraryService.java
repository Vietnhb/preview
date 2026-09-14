package com.example.backend.service;

import com.example.backend.dto.library.LibraryItemResponse;
import com.example.backend.dto.library.LibrarySaveRequest;
import com.example.backend.entity.LibraryItem;
import com.example.backend.entity.LibraryFolder;
import com.example.backend.entity.Specification;
import com.example.backend.entity.Simulation;
import com.example.backend.entity.SimulationStatus;
import com.example.backend.entity.Lesson;
import com.example.backend.entity.User;
import com.example.backend.entity.Visibility;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.LibraryItemRepository;
import com.example.backend.repository.LibraryFolderRepository;
import com.example.backend.repository.SimulationRepository;
import com.example.backend.repository.LessonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LibraryService {
    private final LibraryItemRepository libraryRepository;
    private final LibraryFolderRepository folderRepository;
    private final SimulationRepository simulationRepository;
    private final LessonRepository lessonRepository;
    private final CurrentUserService currentUserService;

    @Transactional
    public LibraryItemResponse save(LibrarySaveRequest request) {
        User user = currentUserService.requireCurrentUser();
        Simulation simulation = simulationRepository.findByIdAndOwnerId(request.simulationId(), user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Simulation not found"));
        Specification specification = simulation.getSpecification();
        if (simulation.getStatus() != SimulationStatus.READY || !"PASSED".equals(specification.getValidationStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "Only validated simulations can be saved");
        }
        LibraryFolder folder = folderRepository.findByIdAndOwnerIdAndActiveTrue(request.folderId(), user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Library folder not found"));
        Lesson lesson = lessonRepository.findById(request.lessonId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Lesson not found"));
        String curriculumTopic = lesson.getLevel().getModule().getTopic().getName();
        if (specification.getTopic() == null || !specification.getTopic().equalsIgnoreCase(curriculumTopic)) {
            throw new ApiException(HttpStatus.CONFLICT, "Lesson topic does not match the validated simulation topic");
        }
        LibraryItem item = libraryRepository.findBySimulationIdAndOwnerId(simulation.getId(), user.getId())
                .orElseGet(LibraryItem::new);
        item.setSimulation(simulation);
        item.setFolder(folder);
        item.setLesson(lesson);
        item.setSpecification(specification);
        item.setOwner(user);
        item.setTitle(request.title().trim());
        item.setVisibility(request.visibility() == null ? Visibility.PERSONAL : request.visibility());
        item.setActive(true);
        return toResponse(libraryRepository.save(item));
    }

    @Transactional(readOnly = true)
    public List<LibraryItemResponse> search(String topic) {
        User user = currentUserService.requireCurrentUser();
        return libraryRepository.findAll().stream()
                .filter(item -> item.isActive() && (item.getOwner().getId().equals(user.getId())
                        || item.getVisibility() == Visibility.SHARED))
                .filter(item -> topic == null || topic.isBlank() || (item.getSpecification().getTopic() != null
                        && item.getSpecification().getTopic().equalsIgnoreCase(topic)))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LibraryItemResponse> mine() {
        User user = currentUserService.requireCurrentUser();
        return libraryRepository.findByOwnerIdAndActiveTrueOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void remove(java.util.UUID id) {
        User user = currentUserService.requireCurrentUser();
        LibraryItem item = libraryRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Library item not found"));
        if (!item.getOwner().getId().equals(user.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the owner can remove this item");
        }
        item.setActive(false);
        libraryRepository.save(item);
    }

    private LibraryItemResponse toResponse(LibraryItem item) {
        return new LibraryItemResponse(item.getId(), item.getSimulation() == null ? null : item.getSimulation().getId(),
                item.getFolder() == null ? null : item.getFolder().getId(),
                item.getLesson() == null ? null : item.getLesson().getId(),
                item.getSpecification().getId(), item.getTitle(),
                item.getSpecification().getTopic(), item.getSpecification().getValidationStatus(),
                item.getVisibility(), item.getCreatedAt());
    }
}
