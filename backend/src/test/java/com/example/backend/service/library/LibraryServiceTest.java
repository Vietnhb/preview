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
        School ownSchool = school("TrÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Âng A");
        School otherSchool = school("TrÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Âng B");
        User student = user(1, "HÃƒÂ¡Ã‚Â»Ã‚Âc sinh", ownSchool);
        LibraryItem publicItem = item("MÃƒÆ’Ã‚Â´ phÃƒÂ¡Ã‚Â»Ã‚Âng cÃƒÆ’Ã‚Â´ng khai", Visibility.PUBLIC, otherSchool);
        LibraryItem sameSchoolItem = item("MÃƒÆ’Ã‚Â´ phÃƒÂ¡Ã‚Â»Ã‚Âng cÃƒÂ¡Ã‚Â»Ã‚Â§a trÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Âng", Visibility.SHARED, ownSchool);
        LibraryItem hiddenItem = item("MÃƒÆ’Ã‚Â´ phÃƒÂ¡Ã‚Â»Ã‚Âng trÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Âng khÃƒÆ’Ã‚Â¡c", Visibility.SHARED, otherSchool);
        when(currentUser.requireCurrentUser()).thenReturn(student);
        when(items.findAll()).thenReturn(List.of(publicItem, sameSchoolItem, hiddenItem));

        var result = service.search(null);
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(row -> row.title().equals("MÃƒÆ’Ã‚Â´ phÃƒÂ¡Ã‚Â»Ã‚Âng cÃƒÆ’Ã‚Â´ng khai")
                && row.sharedByName().equals("GiÃƒÆ’Ã‚Â¡o viÃƒÆ’Ã‚Âªn") && row.schoolName().equals("TrÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Âng B")));
        assertTrue(result.stream().anyMatch(row -> row.title().equals("MÃƒÆ’Ã‚Â´ phÃƒÂ¡Ã‚Â»Ã‚Âng cÃƒÂ¡Ã‚Â»Ã‚Â§a trÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Âng")));
    }

    @Test
    void communityContainsOnlyApprovedPublicItems() {
        LibraryItemRepository items = mock(LibraryItemRepository.class);
        LibraryService service = new LibraryService(items, mock(LibraryFolderRepository.class),
                mock(SimulationRepository.class), mock(LessonRepository.class), mock(CurrentUserService.class));
        School school = school("TrÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Âng A");
        LibraryItem approved = item("CÃƒÆ’Ã‚Â´ng khai", Visibility.PUBLIC, school);
        LibraryItem pending = item("Ãƒâ€žÃ‚Âang duyÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡t", Visibility.PUBLIC, school);
        pending.setModerationStatus(LibraryModerationStatus.PENDING);
        LibraryItem internal = item("NÃƒÂ¡Ã‚Â»Ã¢â€žÂ¢i bÃƒÂ¡Ã‚Â»Ã¢â€žÂ¢", Visibility.SHARED, school);
        when(items.findAll()).thenReturn(List.of(approved, pending, internal));

        var result = service.community(null);

        assertEquals(1, result.size());
        assertEquals("CÃƒÆ’Ã‚Â´ng khai", result.get(0).title());
    }

    private static School school(String name) {
        School school = new School(); school.setId(UUID.randomUUID()); school.setName(name); return school;
    }

    private static User user(int id, String name, School school) {
        User user = new User(); user.setId(id); user.setFullName(name); user.setSchool(school); return user;
    }

    private static LibraryItem item(String title, Visibility visibility, School school) {
        Specification specification = new Specification(); specification.setId(UUID.randomUUID());
        specification.setTopic("Ãƒâ€žÃ‚ÂÃƒÂ¡Ã‚Â»Ã¢â€žÂ¢ng hÃƒÂ¡Ã‚Â»Ã‚Âc"); specification.setValidationStatus("PASSED");
        LibraryItem item = new LibraryItem(); item.setId(UUID.randomUUID()); item.setTitle(title); item.setVisibility(visibility);
        item.setSharedInstitutionId(visibility == Visibility.SHARED ? school.getId().toString() : null);
        item.setModerationStatus(LibraryModerationStatus.APPROVED); item.setActive(true);
        item.setOwner(user(title.hashCode(), "GiÃƒÆ’Ã‚Â¡o viÃƒÆ’Ã‚Âªn", school)); item.setSpecification(specification);
        return item;
    }
}
