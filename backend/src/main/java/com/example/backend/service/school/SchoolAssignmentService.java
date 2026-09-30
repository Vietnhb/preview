package com.example.backend.service.school;

import com.example.backend.entity.account.User;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.assignment.AssignmentRepository;
import com.example.backend.service.account.AccountAccessService;
import com.example.backend.service.account.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SchoolAssignmentService {
    private final AssignmentRepository assignments;
    private final CurrentUserService currentUser;
    private final AccountAccessService access;

    public record SchoolAssignment(UUID id, String title, UUID classId, String className, String schoolYear,
                                   Integer teacherId, String teacherName, int studentCount,
                                   String status, Instant assignedAt, Instant dueAt) { }

    @Transactional(readOnly = true)
    public Page<SchoolAssignment> list(UUID schoolId, int page, int size) {
        User actor = currentUser.requireCurrentUser();
        if (!access.isDepartmentHead(actor) || actor.getSchool() == null || !schoolId.equals(actor.getSchool().getId()))
            throw new ApiException(HttpStatus.FORBIDDEN, "Chỉ Trưởng bộ môn được xem phân công bài tập trong trường mình.");
        if (page < 0 || size < 1 || size > 100)
            throw new ApiException(HttpStatus.BAD_REQUEST, "Phân trang không hợp lệ.");
        return assignments.findSchoolAssignments(schoolId, PageRequest.of(page, size, Sort.by("createdAt").descending()))
                .map(item -> new SchoolAssignment(item.getId(), item.getTitle(),
                        item.getSchoolClass() == null ? null : item.getSchoolClass().getId(),
                        item.getSchoolClass() == null ? null : item.getSchoolClass().getName(),
                        item.getSchoolClass() == null ? null : item.getSchoolClass().getSchoolYear(),
                        item.getTeacher().getId(), item.getTeacher().getFullName(), item.getAssignedStudentIds().size(),
                        item.getStatus().name(), item.getAssignedAt(), item.getDueAt()));
    }
}
