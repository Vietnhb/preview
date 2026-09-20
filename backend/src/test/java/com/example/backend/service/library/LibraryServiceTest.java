package com.example.backend.service.library;

import com.example.backend.entity.account.User;
import com.example.backend.entity.enums.LibraryModerationStatus;
import com.example.backend.entity.enums.Visibility;
import com.example.backend.entity.library.LibraryItem;
import com.example.backend.entity.problem.Specification;
import com.example.backend.entity.school.School;
import com.example.backend.repository.curriculum.LessonRepository;
import com.example.backend.repository.library.LibraryFolderRepository;
import com.example.backend.repository.library.LibraryItemRepository;
import com.example.backend.repository.simulation.SimulationRepository;
import com.example.backend.service.account.CurrentUserService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

class LibraryServiceTest {
    @Test
    void publicItemsAreVisibleAcrossSchoolsWhileSchoolSharesStayScoped() {
        LibraryItemRepository items = mock(LibraryItemRepository.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        LibraryService service = new LibraryService(items, mock(LibraryFolderRepository.class),
                mock(SimulationRepository.class), mock(LessonRepository.class), currentUser);
        School ownSchool = school("School A");
        School otherSchool = school("School B");
        User student = user(1, "Student", ownSchool);
        LibraryItem publicItem = item("Public simulation", Visibility.PUBLIC, otherSchool);
        LibraryItem sameSchoolItem = item("School simulation", Visibility.SHARED, ownSchool);
        when(currentUser.requireCurrentUser()).thenReturn(student);
        when(items.findSearchVisibleItems(eq(student.getId()), eq(student.getInstitutionId()),
                eq(Visibility.PERSONAL), eq(Visibility.PUBLIC),
                eq(Set.of(LibraryModerationStatus.APPROVED, LibraryModerationStatus.FEATURED)), isNull()))
                .thenReturn(List.of(publicItem, sameSchoolItem));

        var result = service.search(null);
        assertEquals(2, result.size());
        verify(items).findSearchVisibleItems(student.getId(), student.getInstitutionId(),
                Visibility.PERSONAL, Visibility.PUBLIC,
                Set.of(LibraryModerationStatus.APPROVED, LibraryModerationStatus.FEATURED), null);
        verify(items, never()).findAll();
        assertTrue(result.stream().anyMatch(row -> row.title().equals("Public simulation")
                && row.sharedByName().equals("Teacher") && row.schoolName().equals("School B")));
        assertTrue(result.stream().anyMatch(row -> row.title().equals("School simulation")));
    }

    @Test
    void communityContainsOnlyApprovedPublicItems() {
        LibraryItemRepository items = mock(LibraryItemRepository.class);
        LibraryService service = new LibraryService(items, mock(LibraryFolderRepository.class),
                mock(SimulationRepository.class), mock(LessonRepository.class), mock(CurrentUserService.class));
        School school = school("School A");
        LibraryItem approved = item("Public simulation", Visibility.PUBLIC, school);
        when(items.findCommunityItems(eq(Visibility.PUBLIC),
                eq(Set.of(LibraryModerationStatus.APPROVED, LibraryModerationStatus.FEATURED)), isNull()))
                .thenReturn(List.of(approved));

        var result = service.community(null);

        assertEquals(1, result.size());
        assertEquals("Public simulation", result.get(0).title());
        verify(items).findCommunityItems(Visibility.PUBLIC,
                Set.of(LibraryModerationStatus.APPROVED, LibraryModerationStatus.FEATURED), null);
        verify(items, never()).findAll();
    }

    @Test
    void topicFilteringIsDelegatedToRepositoryAndBlankTopicMeansNoFilter() {
        LibraryItemRepository items = mock(LibraryItemRepository.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        LibraryService service = new LibraryService(items, mock(LibraryFolderRepository.class),
                mock(SimulationRepository.class), mock(LessonRepository.class), currentUser);
        User student = user(1, "Student", school("School A"));
        when(currentUser.requireCurrentUser()).thenReturn(student);

        when(items.findSearchVisibleItems(eq(student.getId()), eq(student.getInstitutionId()),
                eq(Visibility.PERSONAL), eq(Visibility.PUBLIC),
                eq(Set.of(LibraryModerationStatus.APPROVED, LibraryModerationStatus.FEATURED)), isNull()))
                .thenReturn(List.of());
        when(items.findCommunityItems(eq(Visibility.PUBLIC),
                eq(Set.of(LibraryModerationStatus.APPROVED, LibraryModerationStatus.FEATURED)), eq("Optics")))
                .thenReturn(List.of());

        service.search("   ");
        service.community("Optics");

        verify(items).findSearchVisibleItems(student.getId(), student.getInstitutionId(),
                Visibility.PERSONAL, Visibility.PUBLIC,
                Set.of(LibraryModerationStatus.APPROVED, LibraryModerationStatus.FEATURED), null);
        verify(items).findCommunityItems(Visibility.PUBLIC,
                Set.of(LibraryModerationStatus.APPROVED, LibraryModerationStatus.FEATURED), "Optics");
        verify(items, never()).findAll();
    }

    private static School school(String name) {
        School school = new School(); school.setId(UUID.randomUUID()); school.setName(name); return school;
    }

    private static User user(int id, String name, School school) {
        User user = new User(); user.setId(id); user.setFullName(name); user.setSchool(school); return user;
    }

    private static LibraryItem item(String title, Visibility visibility, School school) {
        Specification specification = new Specification(); specification.setId(UUID.randomUUID());
        specification.setTopic("Physics"); specification.setValidationStatus("PASSED");
        LibraryItem item = new LibraryItem(); item.setId(UUID.randomUUID()); item.setTitle(title); item.setVisibility(visibility);
        item.setSharedInstitutionId(visibility == Visibility.SHARED ? school.getId().toString() : null);
        item.setModerationStatus(LibraryModerationStatus.APPROVED); item.setActive(true);
        item.setOwner(user(title.hashCode(), "Teacher", school)); item.setSpecification(specification);
        return item;
    }
}
