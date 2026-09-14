package com.example.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.example.backend.dto.library.LibrarySaveRequest;
import com.example.backend.entity.LibraryItem;
import com.example.backend.entity.LibraryFolder;
import com.example.backend.entity.Lesson;
import com.example.backend.entity.Topic;
import com.example.backend.entity.ContentModule;
import com.example.backend.entity.GradeLevel;
import com.example.backend.entity.Simulation;
import com.example.backend.entity.SimulationStatus;
import com.example.backend.entity.Specification;
import com.example.backend.entity.User;
import com.example.backend.entity.Visibility;
import com.example.backend.repository.LibraryItemRepository;
import com.example.backend.repository.LibraryFolderRepository;
import com.example.backend.repository.SimulationRepository;
import com.example.backend.repository.LessonRepository;

class LibraryServiceTest {
    @Test
    void movesLegacyItemAndRejectsForeignOrInactiveItemsAndForeignFolders() {
        LibraryItemRepository library = mock(LibraryItemRepository.class);
        LibraryFolderRepository folders = mock(LibraryFolderRepository.class);
        CurrentUserService currentUsers = mock(CurrentUserService.class);
        LibraryService service = new LibraryService(library, folders, mock(SimulationRepository.class), mock(LessonRepository.class), currentUsers);
        User teacher = user(7);
        LibraryFolder destination = folder(teacher);
        LibraryItem item = new LibraryItem();
        item.setId(UUID.randomUUID());
        item.setOwner(teacher);
        item.setActive(true);
        item.setTitle("Legacy simulation");
        item.setSpecification(simulation(SimulationStatus.READY, "PASSED").getSpecification());
        when(currentUsers.requireCurrentUser()).thenReturn(teacher);
        when(library.findById(item.getId())).thenReturn(Optional.of(item));
        when(library.save(any(LibraryItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(folders.findByIdAndOwnerIdAndActiveTrue(destination.getId(), teacher.getId())).thenReturn(Optional.of(destination));

        var response = service.move(item.getId(), destination.getId());
        assertThat(response.folderId()).isEqualTo(destination.getId());
        assertThat(response.id()).isEqualTo(item.getId());
        assertThat(item.getTitle()).isEqualTo("Legacy simulation");
        assertThat(item.getLesson()).isNull();
        assertThatThrownBy(() -> service.move(item.getId(), UUID.randomUUID())).hasMessage("Library folder not found");
        item.setOwner(user(8));
        assertThatThrownBy(() -> service.move(item.getId(), destination.getId())).hasMessage("Library item not found");
        item.setOwner(teacher);
        item.setActive(false);
        assertThatThrownBy(() -> service.move(item.getId(), destination.getId())).hasMessage("Library item not found");
    }

    @Test
    void savesOwnedReadySimulationAndReusesItsLibraryRecord() {
        LibraryItemRepository library = mock(LibraryItemRepository.class);
        LibraryFolderRepository folders = mock(LibraryFolderRepository.class);
        SimulationRepository simulations = mock(SimulationRepository.class);
        LessonRepository lessons = mock(LessonRepository.class);
        CurrentUserService currentUsers = mock(CurrentUserService.class);
        LibraryService service = new LibraryService(library, folders, simulations, lessons, currentUsers);
        User teacher = user(7);
        Simulation simulation = simulation(SimulationStatus.READY, "PASSED");
        Lesson lesson = lesson("KINEMATICS");
        LibraryFolder folder = folder(teacher);
        LibraryItem existing = new LibraryItem(); existing.setId(UUID.randomUUID()); existing.setCreatedAt(Instant.now());
        when(currentUsers.requireCurrentUser()).thenReturn(teacher);
        when(simulations.findByIdAndOwnerId(simulation.getId(), teacher.getId())).thenReturn(Optional.of(simulation));
        when(folders.findByIdAndOwnerIdAndActiveTrue(folder.getId(), teacher.getId())).thenReturn(Optional.of(folder));
        when(lessons.findById(lesson.getId())).thenReturn(Optional.of(lesson));
        when(library.findBySimulationIdAndOwnerId(simulation.getId(), teacher.getId())).thenReturn(Optional.of(existing));
        when(library.save(any(LibraryItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.save(new LibrarySaveRequest(simulation.getId(), folder.getId(), lesson.getId(), "  Projectile practice  ", Visibility.PERSONAL));

        assertThat(response.id()).isEqualTo(existing.getId());
        assertThat(response.simulationId()).isEqualTo(simulation.getId());
        assertThat(existing.getTitle()).isEqualTo("Projectile practice");
        assertThat(existing.getOwner()).isSameAs(teacher);
        assertThat(existing.getFolder()).isSameAs(folder);
        assertThat(existing.isActive()).isTrue();
    }

    @Test
    void rejectsSimulationThatDidNotPassValidation() {
        LibraryItemRepository library = mock(LibraryItemRepository.class);
        LibraryFolderRepository folders = mock(LibraryFolderRepository.class);
        SimulationRepository simulations = mock(SimulationRepository.class);
        LessonRepository lessons = mock(LessonRepository.class);
        CurrentUserService currentUsers = mock(CurrentUserService.class);
        LibraryService service = new LibraryService(library, folders, simulations, lessons, currentUsers);
        User teacher = user(7);
        Simulation simulation = simulation(SimulationStatus.BLOCKED, "FAILED");
        when(currentUsers.requireCurrentUser()).thenReturn(teacher);
        when(simulations.findByIdAndOwnerId(simulation.getId(), teacher.getId())).thenReturn(Optional.of(simulation));

        assertThatThrownBy(() -> service.save(new LibrarySaveRequest(simulation.getId(), UUID.randomUUID(), UUID.randomUUID(), "Blocked", Visibility.PERSONAL)))
                .hasMessageContaining("validated simulations");
    }

    @Test
    void rejectsLessonFromAnotherPhysicsTopic() {
        LibraryItemRepository library = mock(LibraryItemRepository.class);
        LibraryFolderRepository folders = mock(LibraryFolderRepository.class);
        SimulationRepository simulations = mock(SimulationRepository.class);
        LessonRepository lessons = mock(LessonRepository.class);
        CurrentUserService currentUsers = mock(CurrentUserService.class);
        LibraryService service = new LibraryService(library, folders, simulations, lessons, currentUsers);
        User teacher = user(7); Simulation simulation = simulation(SimulationStatus.READY, "PASSED");
        Lesson lesson = lesson("CIRCUITS");
        LibraryFolder folder = folder(teacher);
        when(currentUsers.requireCurrentUser()).thenReturn(teacher);
        when(simulations.findByIdAndOwnerId(simulation.getId(), teacher.getId())).thenReturn(Optional.of(simulation));
        when(folders.findByIdAndOwnerIdAndActiveTrue(folder.getId(), teacher.getId())).thenReturn(Optional.of(folder));
        when(lessons.findById(lesson.getId())).thenReturn(Optional.of(lesson));

        assertThatThrownBy(() -> service.save(new LibrarySaveRequest(simulation.getId(), folder.getId(), lesson.getId(), "Wrong topic", Visibility.PERSONAL)))
                .hasMessageContaining("does not match");
    }

    @Test
    void rejectsFolderNotOwnedByCurrentTeacher() {
        LibraryItemRepository library = mock(LibraryItemRepository.class);
        LibraryFolderRepository folders = mock(LibraryFolderRepository.class);
        SimulationRepository simulations = mock(SimulationRepository.class);
        LessonRepository lessons = mock(LessonRepository.class);
        CurrentUserService currentUsers = mock(CurrentUserService.class);
        LibraryService service = new LibraryService(library, folders, simulations, lessons, currentUsers);
        User teacher = user(7);
        Simulation simulation = simulation(SimulationStatus.READY, "PASSED");
        UUID foreignFolderId = UUID.randomUUID();
        when(currentUsers.requireCurrentUser()).thenReturn(teacher);
        when(simulations.findByIdAndOwnerId(simulation.getId(), teacher.getId())).thenReturn(Optional.of(simulation));
        when(folders.findByIdAndOwnerIdAndActiveTrue(foreignFolderId, teacher.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(new LibrarySaveRequest(simulation.getId(), foreignFolderId,
                UUID.randomUUID(), "Foreign folder", Visibility.PERSONAL)))
                .hasMessageContaining("folder not found");
    }

    private static User user(int id) { User user = new User(); user.setId(id); return user; }

    private static LibraryFolder folder(User owner) {
        LibraryFolder folder = new LibraryFolder();
        folder.setId(UUID.randomUUID());
        folder.setOwner(owner);
        return folder;
    }

    private static Simulation simulation(SimulationStatus status, String validationStatus) {
        Specification specification = new Specification(); specification.setId(UUID.randomUUID());
        specification.setTopic("KINEMATICS"); specification.setValidationStatus(validationStatus);
        Simulation simulation = new Simulation(); simulation.setId(UUID.randomUUID()); simulation.setSpecification(specification); simulation.setStatus(status);
        return simulation;
    }

    private static Lesson lesson(String topicName) {
        Topic topic = new Topic(); topic.setName(topicName);
        ContentModule module = new ContentModule(); module.setTopic(topic);
        GradeLevel level = new GradeLevel(); level.setModule(module);
        Lesson lesson = new Lesson(); lesson.setId(UUID.randomUUID()); lesson.setLevel(level);
        return lesson;
    }
}
