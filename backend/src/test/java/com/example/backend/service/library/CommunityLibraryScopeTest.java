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

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommunityLibraryScopeTest {
    private static final Set<LibraryModerationStatus> PUBLISHED = Set.of(
            LibraryModerationStatus.APPROVED, LibraryModerationStatus.FEATURED);
    private final LibraryItemRepository items = mock(LibraryItemRepository.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final LibraryService service = new LibraryService(items, mock(LibraryFolderRepository.class),
            mock(SimulationRepository.class), mock(LessonRepository.class), currentUser);

    @Test
    void communityIncludesApprovedSystemAndSameSchoolOnly() {
        User viewer = user(1, UUID.randomUUID());
        User otherSchool = user(2, UUID.randomUUID());
        LibraryItem system = item("System", Visibility.PUBLIC, otherSchool);
        LibraryItem school = item("School", Visibility.SHARED, viewer);
        LibraryItem other = item("Other school", Visibility.SHARED, otherSchool);
        LibraryItem personal = item("Personal", Visibility.PERSONAL, viewer);
        LibraryItem pending = item("Pending", Visibility.PUBLIC, viewer);
        pending.setModerationStatus(LibraryModerationStatus.PENDING);
        LibraryItem inactive = item("Inactive", Visibility.PUBLIC, viewer);
        inactive.setActive(false);
        query(viewer, "Vật lý", List.of(system, school, other, personal, pending, inactive));

        assertEquals(List.of("System", "School"), service.community("Vật lý").stream().map(value -> value.title()).toList());
        verify(items).findSearchVisibleItems(viewer.getId(), viewer.getInstitutionId(),
                Visibility.PERSONAL, Visibility.PUBLIC, PUBLISHED, "Vật lý");
    }

    @Test
    void legacySchoolSharingRequiresTheOwnerToBeInTheViewersSchool() {
        User viewer = user(1, UUID.randomUUID());
        LibraryItem ownSchool = item("Own school", Visibility.SHARED, user(2, viewer.getSchool().getId()));
        ownSchool.setSharedInstitutionId(null);
        LibraryItem otherSchool = item("Other school", Visibility.SHARED, user(3, UUID.randomUUID()));
        otherSchool.setSharedInstitutionId(null);
        LibraryItem unscoped = item("Unscoped", Visibility.SHARED, user(4, null));
        unscoped.setSharedInstitutionId("");
        query(viewer, null, List.of(ownSchool, otherSchool, unscoped));

        assertEquals(List.of("Own school"), service.community("  ").stream().map(value -> value.title()).toList());
    }

    @Test
    void platformViewerReceivesSystemResourcesWithoutSchoolSharing() {
        User viewer = user(1, null);
        LibraryItem system = item("System", Visibility.PUBLIC, viewer);
        LibraryItem school = item("School", Visibility.SHARED, user(2, UUID.randomUUID()));
        LibraryItem legacy = item("Legacy", Visibility.SHARED, viewer);
        query(viewer, null, List.of(system, school, legacy));

        assertEquals(List.of("System"), service.community(null).stream().map(value -> value.title()).toList());
    }

    private void query(User viewer, String topic, List<LibraryItem> results) {
        when(currentUser.requireCurrentUser()).thenReturn(viewer);
        when(items.findSearchVisibleItems(viewer.getId(), viewer.getInstitutionId(),
                Visibility.PERSONAL, Visibility.PUBLIC, PUBLISHED, topic)).thenReturn(results);
    }

    private User user(int id, UUID schoolId) {
        User user = new User();
        user.setId(id);
        user.setFullName("User " + id);
        if (schoolId != null) {
            School school = new School();
            school.setId(schoolId);
            school.setName("School " + schoolId);
            user.setSchool(school);
        }
        return user;
    }

    private LibraryItem item(String title, Visibility visibility, User owner) {
        LibraryItem item = new LibraryItem();
        item.setId(UUID.randomUUID());
        item.setTitle(title);
        item.setOwner(owner);
        item.setVisibility(visibility);
        item.setSharedInstitutionId(visibility == Visibility.SHARED ? owner.getInstitutionId() : null);
        item.setModerationStatus(LibraryModerationStatus.APPROVED);
        item.setCreatedAt(Instant.now());
        Specification specification = new Specification();
        specification.setId(UUID.randomUUID());
        specification.setTopic("Vật lý");
        specification.setValidationStatus("PASSED");
        item.setSpecification(specification);
        return item;
    }
}
