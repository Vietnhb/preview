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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LibraryServiceTest {
    @Test
    void publicItemsAreVisibleAcrossSchoolsWhileSchoolSharesStayScoped() {
        LibraryItemRepository items = mock(LibraryItemRepository.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        LibraryService service = new LibraryService(items, mock(LibraryFolderRepository.class),
                mock(SimulationRepository.class), mock(LessonRepository.class), currentUser);
        School ownSchool = school("Trường A");
        School otherSchool = school("Trường B");
        User student = user(1, "Học sinh", ownSchool);
        LibraryItem publicItem = item("Mô phỏng công khai", Visibility.PUBLIC, otherSchool);
        LibraryItem sameSchoolItem = item("Mô phỏng của trường", Visibility.SHARED, ownSchool);
        LibraryItem hiddenItem = item("Mô phỏng trường khác", Visibility.SHARED, otherSchool);
        when(currentUser.requireCurrentUser()).thenReturn(student);
        when(items.findAll()).thenReturn(List.of(publicItem, sameSchoolItem, hiddenItem));

        var result = service.search(null);
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(row -> row.title().equals("Mô phỏng công khai")
                && row.sharedByName().equals("Giáo viên") && row.schoolName().equals("Trường B")));
        assertTrue(result.stream().anyMatch(row -> row.title().equals("Mô phỏng của trường")));
    }

    @Test
    void communityContainsOnlyApprovedPublicItems() {
        LibraryItemRepository items = mock(LibraryItemRepository.class);
        LibraryService service = new LibraryService(items, mock(LibraryFolderRepository.class),
                mock(SimulationRepository.class), mock(LessonRepository.class), mock(CurrentUserService.class));
        School school = school("Trường A");
        LibraryItem approved = item("Công khai", Visibility.PUBLIC, school);
        LibraryItem pending = item("Đang duyệt", Visibility.PUBLIC, school);
        pending.setModerationStatus(LibraryModerationStatus.PENDING);
        LibraryItem internal = item("Nội bộ", Visibility.SHARED, school);
        when(items.findAll()).thenReturn(List.of(approved, pending, internal));

        var result = service.community(null);

        assertEquals(1, result.size());
        assertEquals("Công khai", result.get(0).title());
    }

    private static School school(String name) {
        School school = new School(); school.setId(UUID.randomUUID()); school.setName(name); return school;
    }

    private static User user(int id, String name, School school) {
        User user = new User(); user.setId(id); user.setFullName(name); user.setSchool(school); return user;
    }

    private static LibraryItem item(String title, Visibility visibility, School school) {
        Specification specification = new Specification(); specification.setId(UUID.randomUUID());
        specification.setTopic("Động học"); specification.setValidationStatus("PASSED");
        LibraryItem item = new LibraryItem(); item.setId(UUID.randomUUID()); item.setTitle(title); item.setVisibility(visibility);
        item.setSharedInstitutionId(visibility == Visibility.SHARED ? school.getId().toString() : null);
        item.setModerationStatus(LibraryModerationStatus.APPROVED); item.setActive(true);
        item.setOwner(user(title.hashCode(), "Giáo viên", school)); item.setSpecification(specification);
        return item;
    }
}
