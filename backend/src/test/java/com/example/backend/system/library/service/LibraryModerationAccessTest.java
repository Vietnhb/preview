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
import com.example.backend.system.library.repository.LibraryModerationAuditRepository;
import com.example.backend.system.problem.model.entity.Specification;
import com.example.backend.system.school.model.entity.School;
import com.example.backend.system.simulation.model.entity.Simulation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LibraryModerationAccessTest {
    private final LibraryItemRepository items = mock(LibraryItemRepository.class);
    private final LibraryModerationAuditRepository audits = mock(LibraryModerationAuditRepository.class);
    private final CurrentUserService current = mock(CurrentUserService.class);
    private final LibraryModerationService service = new LibraryModerationService(items, audits, current, TestPermissions.access());
    private final UUID schoolId = UUID.randomUUID();
    private User user(int id, String role, UUID schoolId) {
        User user = new User(); user.setId(id); Role accountRole = new Role(); accountRole.setName(role); user.setRole(accountRole);
        if (schoolId != null) { School school = new School(); school.setId(schoolId); user.setSchool(school); }
        return TestPermissions.defaults(user);
    }
    private User head() { User actor = user(1, "STAFF", schoolId); TestPermissions.set(actor, "TEACH", "DEPARTMENT_HEAD_PHYSICS"); return actor; }
    private LibraryItem item(Visibility visibility) {
        LibraryItem item = new LibraryItem(); item.setId(UUID.randomUUID()); item.setVisibility(visibility); item.setTitle("Simulation");
        item.setOwner(user(2, "STAFF", schoolId)); Specification specification = new Specification(); specification.setId(UUID.randomUUID());
        item.setSpecification(specification); item.setModerationStatus(LibraryModerationStatus.PENDING); return item;
    }
    private void persist(LibraryItem item) { when(items.findByIdForUpdate(item.getId())).thenReturn(Optional.of(item)); when(items.save(item)).thenReturn(item); }

    @Test void reviewOnlyReviewerApprovesPublicAndRecordsAudit() {
        User actor = user(1, "REVIEWER", null); TestPermissions.set(actor, "CONTENT_REVIEW");
        when(current.requireCurrentUser()).thenReturn(actor); LibraryItem item = item(Visibility.PUBLIC); persist(item);
        assertEquals(LibraryModerationStatus.APPROVED, service.moderate(item.getId(), LibraryModerationStatus.APPROVED, " Đúng ").moderationStatus());
        verify(audits).save(argThat(audit -> audit.getFromStatus() == LibraryModerationStatus.PENDING
            && audit.getToStatus() == LibraryModerationStatus.APPROVED && audit.getReviewer() == actor && "Đúng".equals(audit.getComment())));
        assertEquals(actor, item.getModeratedBy()); assertNotNull(item.getModeratedAt());
    }

    @Test void editorOnlyReviewerCannotInspectQueueOrMakeDecision() {
        User actor = user(1, "REVIEWER", null); TestPermissions.set(actor, "CONTENT_EDIT"); when(current.requireCurrentUser()).thenReturn(actor);
        assertThrows(ApiException.class, () -> service.queue(null)); assertThrows(ApiException.class, () -> service.moderate(UUID.randomUUID(), LibraryModerationStatus.APPROVED, null));
        verifyNoInteractions(items, audits);
    }

    @Test void publicReviewerCannotApproveSchoolContent() {
        when(current.requireCurrentUser()).thenReturn(user(1, "REVIEWER", null)); LibraryItem item = item(Visibility.SHARED);
        when(items.findByIdForUpdate(item.getId())).thenReturn(Optional.of(item));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class,
            () -> service.moderate(item.getId(), LibraryModerationStatus.APPROVED, null)).getStatus()); verifyNoInteractions(audits);
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {" "})
    void headCanReviewLegacySchoolScopeAndQueue(String legacyScope) {
        when(current.requireCurrentUser()).thenReturn(head()); LibraryItem item = item(Visibility.SHARED); item.setSharedInstitutionId(legacyScope); persist(item);
        when(items.findSchoolModerationItems(schoolId, LibraryModerationStatus.PENDING)).thenReturn(List.of(item));
        assertEquals(1, service.schoolQueue(schoolId, null).size());
        assertEquals(LibraryModerationStatus.APPROVED, service.moderateForSchool(schoolId, item.getId(), LibraryModerationStatus.APPROVED, null).moderationStatus());
    }

    @Test void headCannotModerateAnotherSchoolOrItemExplicitlyScopedElsewhere() {
        when(current.requireCurrentUser()).thenReturn(head());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> service.schoolQueue(UUID.randomUUID(), null)).getStatus());
        LibraryItem item = item(Visibility.SHARED); item.setSharedInstitutionId(UUID.randomUUID().toString());
        when(items.findByIdForUpdate(item.getId())).thenReturn(Optional.of(item));
        assertThrows(ApiException.class, () -> service.moderateForSchool(schoolId, item.getId(), LibraryModerationStatus.APPROVED, null));
        item.setSharedInstitutionId(null); item.getOwner().getSchool().setId(UUID.randomUUID());
        assertThrows(ApiException.class, () -> service.moderateForSchool(schoolId, item.getId(), LibraryModerationStatus.APPROVED, null));
        verifyNoInteractions(audits); verify(items, never()).save(any());
    }

    @Test void headCannotApproveOwnWorkFeatureSchoolContentOrApprovePublic() {
        User actor = head(); when(current.requireCurrentUser()).thenReturn(actor); LibraryItem item = item(Visibility.SHARED);
        when(items.findByIdForUpdate(item.getId())).thenReturn(Optional.of(item)); item.setOwner(actor);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class, () -> service.moderateForSchool(schoolId, item.getId(), LibraryModerationStatus.APPROVED, null)).getStatus());
        item.setOwner(user(2, "STAFF", schoolId));
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class, () -> service.moderateForSchool(schoolId, item.getId(), LibraryModerationStatus.FEATURED, null)).getStatus());
        item.setVisibility(Visibility.PUBLIC);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> service.moderateForSchool(schoolId, item.getId(), LibraryModerationStatus.APPROVED, null)).getStatus());
        verifyNoInteractions(audits);
    }

    @Test void schoolManagerAndRegularTeacherCannotModerateSchoolContent() {
        for (String role : List.of("SCHOOL", "STAFF")) {
            when(current.requireCurrentUser()).thenReturn(user(1, role, schoolId));
            assertThrows(ApiException.class, () -> service.schoolQueue(schoolId, null));
        }
        verifyNoInteractions(items, audits);
    }

    @Test void ownerWithdrawnContentCannotBeReactivatedByApproval() {
        when(current.requireCurrentUser()).thenReturn(user(1, "REVIEWER", null)); LibraryItem item = item(Visibility.PUBLIC); item.setActive(false);
        when(items.findByIdForUpdate(item.getId())).thenReturn(Optional.of(item));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class, () -> service.moderate(item.getId(), LibraryModerationStatus.APPROVED, null)).getStatus());
        verifyNoInteractions(audits); verify(items, never()).save(any()); assertFalse(item.isActive());
    }

    @Test void moderatorRemovedContentCanBeReconsideredWithAudit() {
        when(current.requireCurrentUser()).thenReturn(user(1, "REVIEWER", null)); LibraryItem item = item(Visibility.PUBLIC);
        item.setActive(false); item.setModerationStatus(LibraryModerationStatus.REMOVED); persist(item);
        service.moderate(item.getId(), LibraryModerationStatus.APPROVED, "Đã sửa"); assertTrue(item.isActive());
        verify(audits).save(argThat(audit -> audit.getFromStatus() == LibraryModerationStatus.REMOVED && audit.getToStatus() == LibraryModerationStatus.APPROVED));
    }
}
