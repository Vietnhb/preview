package com.example.backend.system.library.service;

import com.example.backend.exception.ApiException;
import com.example.backend.system.account.TestPermissions;
import com.example.backend.system.account.model.entity.Role;
import com.example.backend.system.account.model.entity.User;
import com.example.backend.system.account.service.AccountAccessService;
import com.example.backend.system.account.service.CurrentUserService;
import com.example.backend.system.library.model.entity.LibraryItem;
import com.example.backend.system.library.model.enums.LibraryModerationStatus;
import com.example.backend.system.library.model.enums.Visibility;
import com.example.backend.system.library.repository.LibraryItemRepository;
import com.example.backend.system.problem.model.entity.Specification;
import com.example.backend.system.school.model.entity.School;
import com.example.backend.system.simulation.model.entity.Simulation;
import com.example.backend.system.simulation.repository.SimulationRepository;
import com.example.backend.system.simulation.repository.SimulationRunRepository;
import com.example.backend.system.simulation.service.SimulationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ModerationSimulationPreviewTest {
    private final LibraryItemRepository library = mock(LibraryItemRepository.class);
    private final CurrentUserService current = mock(CurrentUserService.class);
    private final SimulationRunRepository runs = mock(SimulationRunRepository.class);
    private final SimulationService service = new SimulationService(mock(SimulationRepository.class), runs, library, current, new ObjectMapper(), TestPermissions.access());
    private User user(String role, UUID schoolId) {
        User user = new User(); user.setId(1); Role accountRole = new Role(); accountRole.setName(role); user.setRole(accountRole);
        if (schoolId != null) { School school = new School(); school.setId(schoolId); user.setSchool(school); }
        return TestPermissions.defaults(user);
    }
    private LibraryItem item(Visibility visibility, UUID schoolId) {
        Specification specification = new Specification(); specification.setId(UUID.randomUUID());
        Simulation simulation = new Simulation(); simulation.setId(UUID.randomUUID()); simulation.setSpecification(specification);
        LibraryItem item = new LibraryItem(); item.setSimulation(simulation); item.setVisibility(visibility);
        item.setOwner(user("STAFF", schoolId)); item.setModerationStatus(LibraryModerationStatus.PENDING); return item;
    }
    private void expectPublishedLookup(UUID simulationId, String schoolId) {
        verify(library).findVisiblePublishedSimulation(simulationId, Set.of(Visibility.PUBLIC, Visibility.SHARED), Visibility.PUBLIC,
            Set.of(LibraryModerationStatus.APPROVED, LibraryModerationStatus.FEATURED), schoolId);
    }

    @Test void reviewerWithReviewCapabilityPreviewsPendingPublicWithoutAnArbitraryPersonalClone() {
        User actor = user("REVIEWER", null); TestPermissions.set(actor, "CONTENT_REVIEW");
        LibraryItem item = item(Visibility.PUBLIC, UUID.randomUUID());
        when(current.currentUserOrNull()).thenReturn(actor);
        when(library.findBySimulationIdAndVisibilityOrderByCreatedAtDesc(item.getSimulation().getId(), Visibility.PUBLIC)).thenReturn(List.of(item));
        assertNotNull(service.getShared(item.getSimulation().getId()));
        verify(library, never()).findFirstBySimulationId(any());
        verify(runs).findFirstBySimulationIdOrderByCreatedAtDesc(item.getSimulation().getId());
    }

    @Test void editOnlyReviewerCannotPreviewPendingContent() {
        User actor = user("REVIEWER", null); TestPermissions.set(actor, "CONTENT_EDIT");
        when(current.currentUserOrNull()).thenReturn(actor); UUID id = UUID.randomUUID();
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> service.getShared(id)).getStatus());
        verify(library, never()).findBySimulationIdAndVisibilityOrderByCreatedAtDesc(any(), any());
        expectPublishedLookup(id, null); verifyNoInteractions(runs);
    }

    @Test void departmentHeadPreviewsLegacyBlankScopeOnlyWithinOwnSchool() {
        UUID school = UUID.randomUUID(); User actor = user("STAFF", school); TestPermissions.set(actor, "TEACH", "DEPARTMENT_HEAD_PHYSICS");
        LibraryItem own = item(Visibility.SHARED, school); own.setSharedInstitutionId(" ");
        when(current.currentUserOrNull()).thenReturn(actor);
        when(library.findBySimulationIdAndVisibilityOrderByCreatedAtDesc(own.getSimulation().getId(), Visibility.SHARED)).thenReturn(List.of(own));
        assertNotNull(service.getShared(own.getSimulation().getId()));
        own.getOwner().getSchool().setId(UUID.randomUUID());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> service.getShared(own.getSimulation().getId())).getStatus());
        expectPublishedLookup(own.getSimulation().getId(), actor.getInstitutionId());
    }

    @Test void headCannotPreviewContentExplicitlySharedToAnotherSchoolOrPublicPending() {
        UUID school = UUID.randomUUID(); User actor = user("STAFF", school); TestPermissions.set(actor, "TEACH", "DEPARTMENT_HEAD_PHYSICS");
        LibraryItem item = item(Visibility.SHARED, school); item.setSharedInstitutionId(UUID.randomUUID().toString());
        when(current.currentUserOrNull()).thenReturn(actor);
        when(library.findBySimulationIdAndVisibilityOrderByCreatedAtDesc(item.getSimulation().getId(), Visibility.SHARED)).thenReturn(List.of(item));
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> service.getShared(item.getSimulation().getId())).getStatus());
        verify(library, never()).findBySimulationIdAndVisibilityOrderByCreatedAtDesc(any(), eq(Visibility.PUBLIC));
        verifyNoInteractions(runs);
    }

    @Test void withdrawnPendingContentIsUnavailableButModeratorsCanInspectRemovedContent() {
        User actor = user("REVIEWER", null); LibraryItem item = item(Visibility.PUBLIC, null); item.setActive(false);
        when(current.currentUserOrNull()).thenReturn(actor);
        when(library.findBySimulationIdAndVisibilityOrderByCreatedAtDesc(item.getSimulation().getId(), Visibility.PUBLIC)).thenReturn(List.of(item));
        assertThrows(ApiException.class, () -> service.getShared(item.getSimulation().getId()));
        item.setModerationStatus(LibraryModerationStatus.REMOVED);
        assertNotNull(service.getShared(item.getSimulation().getId()));
    }

    @Test void anonymousViewerCanOpenOnlyPublishedPublicSimulations() {
        LibraryItem item = item(Visibility.PUBLIC, UUID.randomUUID());
        item.setModerationStatus(LibraryModerationStatus.APPROVED);
        UUID id = item.getSimulation().getId();
        when(library.findVisiblePublishedSimulation(id, Set.of(Visibility.PUBLIC), Visibility.PUBLIC,
                Set.of(LibraryModerationStatus.APPROVED, LibraryModerationStatus.FEATURED), null))
                .thenReturn(Optional.of(item));
        assertNotNull(service.getShared(id));
        verify(library, never()).findBySimulationIdAndVisibilityOrderByCreatedAtDesc(any(), any());
        verify(runs).findFirstBySimulationIdOrderByCreatedAtDesc(id);
    }

    @Test void anonymousViewerCannotPreviewPrivateOrUnpublishedSimulations() {
        UUID id = UUID.randomUUID();
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> service.getShared(id)).getStatus());
        verify(library).findVisiblePublishedSimulation(id, Set.of(Visibility.PUBLIC), Visibility.PUBLIC,
                Set.of(LibraryModerationStatus.APPROVED, LibraryModerationStatus.FEATURED), null);
        verify(library, never()).findBySimulationIdAndVisibilityOrderByCreatedAtDesc(any(), any());
        verifyNoInteractions(runs);
    }
}
