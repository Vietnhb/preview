package com.example.backend.service.library;

import com.example.backend.service.account.CurrentUserService;

import com.example.backend.dto.library.LibraryItemResponse;
import com.example.backend.dto.library.LibrarySaveRequest;
import com.example.backend.entity.library.LibraryItem;
import com.example.backend.entity.library.LibraryFolder;
import com.example.backend.entity.problem.Specification;
import com.example.backend.entity.simulation.Simulation;
import com.example.backend.entity.enums.SimulationStatus;
import com.example.backend.entity.curriculum.Lesson;
import com.example.backend.entity.account.User;
import com.example.backend.entity.enums.Visibility;
import com.example.backend.entity.enums.LibraryModerationStatus;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.library.LibraryItemRepository;
import com.example.backend.repository.library.LibraryFolderRepository;
import com.example.backend.repository.simulation.SimulationRepository;
import com.example.backend.repository.curriculum.LessonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LibraryService {
    private static final String LIBRARY_ITEM_NOT_FOUND = "Library item not found";
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
        boolean publishingPersonalItem = item.getId() != null && item.getVisibility() == Visibility.PERSONAL
                && request.visibility() != null && request.visibility() != Visibility.PERSONAL;
        item.setSimulation(simulation);
        item.setFolder(folder);
        item.setLesson(lesson);
        item.setSpecification(specification);
        item.setOwner(user);
        item.setTitle(request.title().trim());
        item.setVisibility(request.visibility() == null ? Visibility.PERSONAL : request.visibility());
        item.setSharedInstitutionId(item.getVisibility() == Visibility.SHARED ? user.getInstitutionId() : null);
        if (item.getVisibility() != Visibility.PERSONAL && (item.getId() == null || publishingPersonalItem))
            item.setModerationStatus(LibraryModerationStatus.PENDING);
        if (item.getVisibility() == Visibility.PERSONAL) item.setModerationStatus(LibraryModerationStatus.APPROVED);
        if (item.getVisibility() != Visibility.PERSONAL && item.getModerationStatus() == LibraryModerationStatus.REJECTED)
            item.setModerationStatus(LibraryModerationStatus.PENDING);
        item.setActive(true);
        return toResponse(libraryRepository.save(item));
    }

    @Transactional
    public LibraryItemResponse cloneShared(UUID id, UUID folderId, String title) {
        User user = currentUserService.requireCurrentUser();
        LibraryItem source = libraryRepository.findById(id)
                .filter(item -> item.isActive() && item.getVisibility() != Visibility.PERSONAL
                        && (item.getModerationStatus() == LibraryModerationStatus.APPROVED || item.getModerationStatus() == LibraryModerationStatus.FEATURED)
                        && visibleTo(user, item))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Shared library item not found"));
        LibraryFolder folder = folderRepository.findByIdAndOwnerIdAndActiveTrue(folderId, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Library folder not found"));
        LibraryItem copy = new LibraryItem();
        copy.setSimulation(source.getSimulation()); copy.setFolder(folder); copy.setLesson(source.getLesson());
        copy.setSpecification(source.getSpecification()); copy.setOwner(user);
        copy.setTitle(title == null || title.isBlank() ? source.getTitle() + " (bÃƒÂ¡Ã‚ÂºÃ‚Â£n sao)" : title.trim());
        copy.setVisibility(Visibility.PERSONAL); copy.setSharedInstitutionId(null);
        copy.setModerationStatus(LibraryModerationStatus.APPROVED); copy.setActive(true);
        return toResponse(libraryRepository.save(copy));
    }

    @Transactional(readOnly = true)
    public List<LibraryItemResponse> search(String topic) {
        User user = currentUserService.requireCurrentUser();
        return libraryRepository.findAll().stream()
                .filter(item -> item.isActive() && (item.getOwner().getId().equals(user.getId())
                        || (item.getVisibility() != Visibility.PERSONAL && visibleTo(user, item)
                        && (item.getModerationStatus() == LibraryModerationStatus.APPROVED || item.getModerationStatus() == LibraryModerationStatus.FEATURED))))
                .filter(item -> topic == null || topic.isBlank() || (item.getSpecification().getTopic() != null
                        && item.getSpecification().getTopic().equalsIgnoreCase(topic)))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LibraryItemResponse> community(String topic) {
        return libraryRepository.findAll().stream()
                .filter(item -> item.isActive() && item.getVisibility() == Visibility.PUBLIC)
                .filter(item -> item.getModerationStatus() == LibraryModerationStatus.APPROVED
                        || item.getModerationStatus() == LibraryModerationStatus.FEATURED)
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
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, LIBRARY_ITEM_NOT_FOUND));
        if (!item.getOwner().getId().equals(user.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the owner can remove this item");
        }
        item.setActive(false);
        libraryRepository.save(item);
    }

    @Transactional
    public LibraryItemResponse rename(java.util.UUID id, String title) {
        User user = currentUserService.requireCurrentUser();
        LibraryItem item = libraryRepository.findById(id)
                .filter(value -> value.isActive() && value.getOwner().getId().equals(user.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, LIBRARY_ITEM_NOT_FOUND));
        item.setTitle(title.trim());
        return toResponse(libraryRepository.save(item));
    }

    @Transactional
    public LibraryItemResponse move(java.util.UUID id, java.util.UUID folderId) {
        User user = currentUserService.requireCurrentUser();
        LibraryItem item = libraryRepository.findById(id)
                .filter(value -> value.isActive() && value.getOwner().getId().equals(user.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, LIBRARY_ITEM_NOT_FOUND));
        LibraryFolder folder = folderRepository.findByIdAndOwnerIdAndActiveTrue(folderId, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Library folder not found"));
        item.setFolder(folder);
        return toResponse(libraryRepository.save(item));
    }

    private LibraryItemResponse toResponse(LibraryItem item) {
        return new LibraryItemResponse(item.getId(), item.getSimulation() == null ? null : item.getSimulation().getId(),
                item.getFolder() == null ? null : item.getFolder().getId(),
                item.getLesson() == null ? null : item.getLesson().getId(),
                item.getSpecification().getId(), item.getTitle(),
                item.getSpecification().getTopic(), item.getSpecification().getValidationStatus(),
                item.getVisibility(), item.getCreatedAt(), item.getModerationStatus(), item.getModerationComment(),
                item.getOwner() == null ? null : item.getOwner().getId(),
                item.getOwner() == null ? null : item.getOwner().getFullName(),
                item.getOwner() == null || item.getOwner().getSchool() == null ? null : item.getOwner().getSchool().getId(),
                item.getOwner() == null || item.getOwner().getSchool() == null ? null : item.getOwner().getSchool().getName());
    }

    private boolean visibleTo(User user, LibraryItem item) {
        if (item.getVisibility() == Visibility.PUBLIC) return true;
        String scope = item.getSharedInstitutionId();
        return scope == null || scope.isBlank() || scope.equals(user.getInstitutionId());
    }
}
