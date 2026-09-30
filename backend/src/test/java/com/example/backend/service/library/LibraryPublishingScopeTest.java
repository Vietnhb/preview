package com.example.backend.service.library;

import com.example.backend.dto.library.LibrarySaveRequest;
import com.example.backend.entity.account.User;
import com.example.backend.entity.curriculum.ContentModule;
import com.example.backend.entity.curriculum.GradeLevel;
import com.example.backend.entity.curriculum.Lesson;
import com.example.backend.entity.curriculum.Topic;
import com.example.backend.entity.enums.LibraryModerationStatus;
import com.example.backend.entity.enums.SimulationStatus;
import com.example.backend.entity.enums.Visibility;
import com.example.backend.entity.library.LibraryFolder;
import com.example.backend.entity.library.LibraryItem;
import com.example.backend.entity.problem.Specification;
import com.example.backend.entity.school.School;
import com.example.backend.entity.simulation.Simulation;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.curriculum.LessonRepository;
import com.example.backend.repository.library.LibraryFolderRepository;
import com.example.backend.repository.library.LibraryItemRepository;
import com.example.backend.repository.simulation.SimulationRepository;
import com.example.backend.service.account.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LibraryPublishingScopeTest {
    private final LibraryItemRepository items = mock(LibraryItemRepository.class);
    private final LibraryFolderRepository folders = mock(LibraryFolderRepository.class);
    private final SimulationRepository simulations = mock(SimulationRepository.class);
    private final LessonRepository lessons = mock(LessonRepository.class);
    private final CurrentUserService current = mock(CurrentUserService.class);
    private final LibraryService service = new LibraryService(items, folders, simulations, lessons, current);
    private final UUID simulationId = UUID.randomUUID(), folderId = UUID.randomUUID(), lessonId = UUID.randomUUID();

    private LibraryItem fixture() {
        User owner = new User(); owner.setId(7); School school = new School(); school.setId(UUID.randomUUID()); owner.setSchool(school);
        when(current.requireCurrentUser()).thenReturn(owner);
        Topic topic = new Topic(); topic.setName("Vật lý"); ContentModule module = new ContentModule(); module.setTopic(topic);
        GradeLevel grade = new GradeLevel(); grade.setModule(module); Lesson lesson = new Lesson(); lesson.setLevel(grade); lesson.setId(lessonId);
        when(lessons.findById(lessonId)).thenReturn(Optional.of(lesson));
        Specification specification = new Specification(); specification.setId(UUID.randomUUID()); specification.setValidationStatus("PASSED"); specification.setTopic("Vật lý");
        Simulation simulation = new Simulation(); simulation.setId(simulationId); simulation.setStatus(SimulationStatus.READY); simulation.setSpecification(specification);
        when(simulations.findByIdAndOwnerId(simulationId, owner.getId())).thenReturn(Optional.of(simulation));
        LibraryFolder folder = new LibraryFolder(); folder.setId(folderId);
        when(folders.findByIdAndOwnerIdAndActiveTrue(folderId, owner.getId())).thenReturn(Optional.of(folder));
        LibraryItem item = new LibraryItem(); item.setId(UUID.randomUUID()); item.setOwner(owner); item.setSpecification(specification);
        item.setVisibility(Visibility.SHARED); item.setModerationStatus(LibraryModerationStatus.APPROVED);
        item.setModeratedAt(Instant.now()); item.setModeratedBy(new User()); item.setModerationComment("School approved");
        when(items.findBySimulationIdAndOwnerId(simulationId, owner.getId())).thenReturn(Optional.of(item));
        when(items.save(item)).thenReturn(item); return item;
    }

    @Test void approvedSchoolContentNeedsIndependentPublicReviewWhenScopeChanges() {
        LibraryItem item = fixture();
        var response = service.save(new LibrarySaveRequest(simulationId, folderId, lessonId, "Published title", Visibility.PUBLIC));
        assertEquals(Visibility.PUBLIC, response.visibility()); assertEquals(LibraryModerationStatus.PENDING, response.moderationStatus());
        assertNull(item.getSharedInstitutionId()); assertNull(item.getModeratedAt()); assertNull(item.getModeratedBy()); assertNull(item.getModerationComment());
        assertTrue(item.isActive());
    }

    @Test void publicToSchoolPublicationAlsoRequiresSchoolModeration() {
        LibraryItem item = fixture(); item.setVisibility(Visibility.PUBLIC); item.setModerationStatus(LibraryModerationStatus.FEATURED);
        service.save(new LibrarySaveRequest(simulationId, folderId, lessonId, "School title", Visibility.SHARED));
        assertEquals(LibraryModerationStatus.PENDING, item.getModerationStatus());
        assertEquals(item.getOwner().getInstitutionId(), item.getSharedInstitutionId()); assertNull(item.getModeratedBy());
    }

    @Test void platformAccountCannotCreateUnscopedSchoolSharing() {
        LibraryItem item = fixture(); item.getOwner().setSchool(null);
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class,
            () -> service.save(new LibrarySaveRequest(simulationId, folderId, lessonId, "Title", Visibility.SHARED))).getStatus());
        verify(items, never()).save(any());
    }
}
