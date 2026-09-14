package com.example.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.example.backend.dto.assignment.CreateAssignmentRequest;
import com.example.backend.entity.Assignment;
import com.example.backend.entity.LibraryItem;
import com.example.backend.entity.Role;
import com.example.backend.entity.Simulation;
import com.example.backend.entity.SimulationStatus;
import com.example.backend.entity.Specification;
import com.example.backend.entity.User;
import com.example.backend.repository.AssignmentRepository;
import com.example.backend.repository.AssignmentSubmissionRepository;
import com.example.backend.repository.LibraryItemRepository;
import com.example.backend.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

class AssignmentServiceTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void createsAssignmentOnlyFromTeachersActiveLibraryItem() {
        AssignmentRepository assignments = mock(AssignmentRepository.class);
        AssignmentSubmissionRepository submissions = mock(AssignmentSubmissionRepository.class);
        LibraryItemRepository library = mock(LibraryItemRepository.class);
        UserRepository users = mock(UserRepository.class);
        CurrentUserService currentUsers = mock(CurrentUserService.class);
        AssignmentService service = new AssignmentService(assignments, submissions, library, users, currentUsers);
        User teacher = user(5, "TEACHER"); User student = user(9, "STUDENT");
        LibraryItem item = libraryItem();
        when(currentUsers.requireCurrentUser()).thenReturn(teacher);
        when(library.findByIdAndOwnerIdAndActiveTrue(item.getId(), teacher.getId())).thenReturn(Optional.of(item));
        when(users.findById(student.getId())).thenReturn(Optional.of(student));
        when(assignments.save(any(Assignment.class))).thenAnswer(invocation -> { Assignment value = invocation.getArgument(0); value.setId(UUID.randomUUID()); return value; });
        var request = new CreateAssignmentRequest(item.getId(), "Momentum", null, json.createObjectNode().put("prompt", "Predict velocity"), new LinkedHashSet<>(Set.of(student.getId())), null);

        var response = service.create(request);

        assertThat(response.libraryItemId()).isEqualTo(item.getId());
        assertThat(response.specificationId()).isEqualTo(item.getSpecification().getId());
        assertThat(response.studentIds()).containsExactly(student.getId());
    }

    @Test
    void rejectsSpecificationThatWasNotSavedToTeachersLibrary() {
        AssignmentRepository assignments = mock(AssignmentRepository.class);
        AssignmentSubmissionRepository submissions = mock(AssignmentSubmissionRepository.class);
        LibraryItemRepository library = mock(LibraryItemRepository.class);
        UserRepository users = mock(UserRepository.class);
        CurrentUserService currentUsers = mock(CurrentUserService.class);
        AssignmentService service = new AssignmentService(assignments, submissions, library, users, currentUsers);
        User teacher = user(5, "TEACHER"); UUID libraryId = UUID.randomUUID();
        when(currentUsers.requireCurrentUser()).thenReturn(teacher);
        when(library.findByIdAndOwnerIdAndActiveTrue(libraryId, teacher.getId())).thenReturn(Optional.empty());
        var request = new CreateAssignmentRequest(libraryId, "Unsafe", null, json.createObjectNode(), Set.of(9), null);

        assertThatThrownBy(() -> service.create(request)).hasMessageContaining("Personal library item not found");
    }

    private static LibraryItem libraryItem() {
        Specification specification = new Specification(); specification.setId(UUID.randomUUID()); specification.setValidationStatus("PASSED");
        Simulation simulation = new Simulation(); simulation.setId(UUID.randomUUID()); simulation.setStatus(SimulationStatus.READY);
        LibraryItem item = new LibraryItem(); item.setId(UUID.randomUUID()); item.setSpecification(specification); item.setSimulation(simulation); item.setActive(true);
        return item;
    }

    private static User user(int id, String roleName) {
        Role role = new Role(); role.setName(roleName);
        User user = new User(); user.setId(id); user.setRole(role); return user;
    }
}
