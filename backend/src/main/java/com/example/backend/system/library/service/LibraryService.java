package com.example.backend.system.library.service;

import com.example.backend.exception.ApiException;
import com.example.backend.system.account.model.entity.User;
import com.example.backend.system.account.service.CurrentUserService;
import com.example.backend.system.curriculum.model.entity.Lesson;
import com.example.backend.system.curriculum.repository.LessonRepository;
import com.example.backend.system.library.dto.LibraryItemResponse;
import com.example.backend.system.library.dto.LibraryRequests;
import com.example.backend.system.library.model.entity.LibraryFolder;
import com.example.backend.system.library.model.entity.LibraryItem;
import com.example.backend.system.library.model.enums.LibraryModerationStatus;
import com.example.backend.system.library.model.enums.Visibility;
import com.example.backend.system.library.repository.LibraryFolderRepository;
import com.example.backend.system.library.repository.LibraryItemRepository;
import com.example.backend.system.problem.model.entity.Specification;
import com.example.backend.system.simulation.model.entity.Simulation;
import com.example.backend.system.simulation.model.enums.SimulationStatus;
import com.example.backend.system.simulation.repository.SimulationRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LibraryService {
    private static final String LIBRARY_ITEM_NOT_FOUND = "Không tìm thấy mục thư viện";
    private static final Set<LibraryModerationStatus> PUBLISHED_STATUSES = Set.of(
            LibraryModerationStatus.APPROVED, LibraryModerationStatus.FEATURED);
    private final LibraryItemRepository libraryRepository;
    private final LibraryFolderRepository folderRepository;
    private final SimulationRepository simulationRepository;
    private final LessonRepository lessonRepository;
    private final CurrentUserService currentUserService;

    @Transactional
    public LibraryItemResponse save(LibraryRequests.Save request) {
        User user = currentUserService.requireCurrentUser();
        Simulation simulation = simulationRepository.findByIdAndOwnerId(request.simulationId(), user.getId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy mô phỏng"));
        Specification specification = simulation.getSpecification();
        if (simulation.getStatus() != SimulationStatus.READY || !"PASSED".equals(specification.getValidationStatus())) {
            throw ApiException.conflict("Chỉ có thể lưu mô phỏng đã kiểm định");
        }
        LibraryFolder folder = folderRepository.findByIdAndOwnerIdAndActiveTrue(request.folderId(), user.getId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy thư mục thư viện"));
        Lesson lesson = lessonRepository.findById(request.lessonId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy bài học"));
        String curriculumTopic = lesson.getLevel().getModule().getTopic().getName();
        if (specification.getTopic() == null || !specification.getTopic().equalsIgnoreCase(curriculumTopic)) {
            throw ApiException.conflict("Chủ đề bài học không khớp với chủ đề mô phỏng đã kiểm định");
        }
        LibraryItem item = libraryRepository.findBySimulationIdAndOwnerId(simulation.getId(), user.getId())
                .orElseGet(LibraryItem::new);
        Visibility nextVisibility = request.visibility() == null ? Visibility.PERSONAL : request.visibility();
        if (nextVisibility == Visibility.SHARED && (user.getInstitutionId() == null || user.getInstitutionId().isBlank()))
            throw ApiException.badRequest("Chia sẻ cấp trường cần tài khoản thuộc một trường.");
        boolean publishingPersonalItem = item.getId() != null && item.getVisibility() != nextVisibility
                && nextVisibility != Visibility.PERSONAL;
        item.setSimulation(simulation);
        item.setFolder(folder);
        item.setLesson(lesson);
        item.setSpecification(specification);
        item.setOwner(user);
        item.setTitle(request.title().trim());
        item.setVisibility(nextVisibility);
        item.setSharedInstitutionId(item.getVisibility() == Visibility.SHARED ? user.getInstitutionId() : null);
        if (item.getVisibility() != Visibility.PERSONAL && (item.getId() == null || publishingPersonalItem)) {
            item.setModerationStatus(LibraryModerationStatus.PENDING);
            item.setModerationComment(null);
            item.setModeratedAt(null);
            item.setModeratedBy(null);
        }
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
                .filter(item -> isPublishedFor(user, item))
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy mục trong thư viện chia sẻ"));
        LibraryFolder folder = folderRepository.findByIdAndOwnerIdAndActiveTrue(folderId, user.getId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy thư mục thư viện"));
        LibraryItem copy = new LibraryItem();
        copy.setSimulation(source.getSimulation()); copy.setFolder(folder); copy.setLesson(source.getLesson());
        copy.setSpecification(source.getSpecification()); copy.setOwner(user);
        copy.setTitle(title == null || title.isBlank() ? source.getTitle() + " (bản sao)" : title.trim());
        copy.setVisibility(Visibility.PERSONAL); copy.setSharedInstitutionId(null);
        copy.setModerationStatus(LibraryModerationStatus.APPROVED); copy.setActive(true);
        return toResponse(libraryRepository.save(copy));
    }

    @Transactional(readOnly = true)
    public List<LibraryItemResponse> search(String topic) {
        User user = currentUserService.requireCurrentUser();
        return libraryRepository.findSearchVisibleItems(user.getId(), user.getInstitutionId(),
                        Visibility.PERSONAL, Visibility.PUBLIC, PUBLISHED_STATUSES, normalizedTopic(topic))
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<LibraryItemResponse> community(String topic) {
        User user = currentUserService.currentUserOrNull();
        if (user == null) return libraryRepository.findCommunityItems(Visibility.PUBLIC, PUBLISHED_STATUSES, normalizedTopic(topic))
                .stream().filter(item -> isPublishedFor(null, item)).map(this::toResponse).toList();
        return libraryRepository.findSearchVisibleItems(user.getId(), user.getInstitutionId(),
                        Visibility.PERSONAL, Visibility.PUBLIC, PUBLISHED_STATUSES, normalizedTopic(topic))
                .stream()
                .filter(item -> isPublishedFor(user, item))
                .map(this::toResponse).toList();
    }

    static boolean sharedWithSchool(User user, LibraryItem item) {
        if (user == null) return false;
        String institutionId = user.getInstitutionId();
        if (institutionId == null || institutionId.isBlank()) return false;
        String scope = item.getSharedInstitutionId();
        if ((scope == null || scope.isBlank()) && item.getOwner() != null) {
            scope = item.getOwner().getInstitutionId();
        }
        return institutionId.equals(scope);
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
                .orElseThrow(() -> ApiException.notFound(LIBRARY_ITEM_NOT_FOUND));
        if (!item.getOwner().getId().equals(user.getId())) {
            throw ApiException.forbidden("Chỉ chủ sở hữu mới được xóa mục này");
        }
        item.setActive(false);
        libraryRepository.save(item);
    }

    @Transactional
    public LibraryItemResponse rename(java.util.UUID id, String title) {
        User user = currentUserService.requireCurrentUser();
        LibraryItem item = libraryRepository.findById(id)
                .filter(value -> value.isActive() && value.getOwner().getId().equals(user.getId()))
                .orElseThrow(() -> ApiException.notFound(LIBRARY_ITEM_NOT_FOUND));
        item.setTitle(title.trim());
        return toResponse(libraryRepository.save(item));
    }

    @Transactional
    public LibraryItemResponse move(java.util.UUID id, java.util.UUID folderId) {
        User user = currentUserService.requireCurrentUser();
        LibraryItem item = libraryRepository.findById(id)
                .filter(value -> value.isActive() && value.getOwner().getId().equals(user.getId()))
                .orElseThrow(() -> ApiException.notFound(LIBRARY_ITEM_NOT_FOUND));
        LibraryFolder folder = folderRepository.findByIdAndOwnerIdAndActiveTrue(folderId, user.getId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy thư mục thư viện"));
        item.setFolder(folder);
        return toResponse(libraryRepository.save(item));
    }

    /**
     * Changes who can see an item the caller owns. School sharing waits for the school's physics
     * department head; community sharing waits for a reviewer. Going back to personal needs no review.
     */
    @Transactional
    public LibraryItemResponse share(java.util.UUID id, Visibility visibility) {
        User user = currentUserService.requireCurrentUser();
        LibraryItem item = libraryRepository.findById(id)
                .filter(value -> value.isActive() && value.getOwner().getId().equals(user.getId()))
                .orElseThrow(() -> ApiException.notFound(LIBRARY_ITEM_NOT_FOUND));
        if (visibility != Visibility.PERSONAL) {
            Simulation simulation = item.getSimulation();
            if (simulation == null || simulation.getOwner() == null || !user.getId().equals(simulation.getOwner().getId()))
                throw ApiException.forbidden("Chỉ chia sẻ được mô phỏng do chính bạn tạo, không chia sẻ được bản sao lấy từ người khác.");
            if (simulation.getStatus() != SimulationStatus.READY || !"PASSED".equals(item.getSpecification().getValidationStatus()))
                throw ApiException.conflict("Chỉ chia sẻ được mô phỏng đã kiểm định.");
            if (visibility == Visibility.SHARED && (user.getInstitutionId() == null || user.getInstitutionId().isBlank()))
                throw ApiException.badRequest("Chia sẻ cấp trường cần tài khoản thuộc một trường.");
        }
        if (item.getVisibility() == visibility && item.getModerationStatus() != LibraryModerationStatus.REJECTED)
            return toResponse(item);
        item.setVisibility(visibility);
        item.setSharedInstitutionId(visibility == Visibility.SHARED ? user.getInstitutionId() : null);
        item.setModerationStatus(visibility == Visibility.PERSONAL ? LibraryModerationStatus.APPROVED : LibraryModerationStatus.PENDING);
        item.setModerationComment(null);
        item.setModeratedAt(null);
        item.setModeratedBy(null);
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

    static boolean isPublishedFor(User user, LibraryItem item) {
        return item != null && item.isActive() && PUBLISHED_STATUSES.contains(item.getModerationStatus())
                && visibleTo(user, item);
    }

    private static boolean visibleTo(User user, LibraryItem item) {
        if (item.getVisibility() == Visibility.PUBLIC) return true;
        return item.getVisibility() == Visibility.SHARED && sharedWithSchool(user, item);
    }

    private String normalizedTopic(String topic) {
        return topic == null || topic.isBlank() ? null : topic.trim();
    }
}
