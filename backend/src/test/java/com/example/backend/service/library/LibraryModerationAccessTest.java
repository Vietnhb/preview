package com.example.backend.service.library;

import com.example.backend.entity.account.Role;
import com.example.backend.entity.account.User;
import com.example.backend.entity.enums.LibraryModerationStatus;
import com.example.backend.entity.enums.Visibility;
import com.example.backend.entity.library.LibraryItem;
import com.example.backend.entity.library.LibraryModerationAudit;
import com.example.backend.entity.problem.Specification;
import com.example.backend.entity.school.School;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.library.LibraryItemRepository;
import com.example.backend.repository.library.LibraryModerationAuditRepository;
import com.example.backend.service.account.AccountAccessService;
import com.example.backend.service.account.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LibraryModerationAccessTest {
    private final LibraryItemRepository items = mock(LibraryItemRepository.class);
    private final LibraryModerationAuditRepository audits = mock(LibraryModerationAuditRepository.class);
    private final CurrentUserService current = mock(CurrentUserService.class);
    private final LibraryModerationService service = new LibraryModerationService(items, audits, current, new AccountAccessService());
    private final UUID schoolId = UUID.randomUUID();
    private User user(int id, String role, UUID schoolId) {
        User user = new User(); user.setId(id); Role accountRole = new Role(); accountRole.setName(role); user.setRole(accountRole);
        if (schoolId != null) { School school = new School(); school.setId(schoolId); user.setSchool(school); }
        return user;
    }
    private User head() { User actor = user(1, "STAFF", schoolId); actor.setStaffType("DEPARTMENT_HEAD"); return actor; }
    private LibraryItem item(Visibility visibility) {
        LibraryItem item = new LibraryItem(); item.setId(UUID.randomUUID()); item.setVisibility(visibility); item.setTitle("Simulation");
        item.setOwner(user(2, "STAFF", schoolId)); Specification specification = new Specification(); specification.setId(UUID.randomUUID());
        item.setSpecification(specification); item.setModerationStatus(LibraryModerationStatus.PENDING); return item;
    }
    private void persist(LibraryItem item) { when(items.findByIdForUpdate(item.getId())).thenReturn(Optional.of(item)); when(items.save(item)).thenReturn(item); }

    @Test void reviewOnlyReviewerApprovesPublicAndRecordsAudit() {
        User actor = user(1, "REVIEWER", null); actor.setReviewerCanEdit(false); actor.setReviewerCanReview(true);
        when(current.requireCurrentUser()).thenReturn(actor); LibraryItem item = item(Visibility.PUBLIC); persist(item);
        assertEquals(LibraryModerationStatus.APPROVED, service.moderate(item.getId(), LibraryModerationStatus.APPROVED, " Đúng ").moderationStatus());
        verify(audits).save(argThat(audit -> audit.getFromStatus() == LibraryModerationStatus.PENDING
            && audit.getToStatus() == LibraryModerationStatus.APPROVED && audit.getReviewer() == actor && "Đúng".equals(audit.getComment())));
        assertEquals(actor, item.getModeratedBy()); assertNotNull(item.getModeratedAt());
    }

    @Test void editorOnlyReviewerCannotInspectQueueOrMakeDecision() {
        User actor = user(1, "REVIEWER", null); actor.setReviewerCanEdit(true); actor.setReviewerCanReview(false); when(current.requireCurrentUser()).thenReturn(actor);
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
