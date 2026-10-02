package com.example.backend.system.assignment.service;

import com.example.backend.exception.ApiException;
import com.example.backend.system.account.model.entity.User;
import com.example.backend.system.account.service.AccountAccessService;
import com.example.backend.system.account.service.CurrentUserService;
import com.example.backend.system.assignment.dto.AssignmentContracts.SchoolAssignment;
import com.example.backend.system.assignment.dto.AssignmentContracts;
import com.example.backend.system.assignment.repository.AssignmentRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SchoolAssignmentService {
    private final AssignmentRepository assignments;
    private final CurrentUserService currentUser;
    private final AccountAccessService access;

    @Transactional(readOnly = true)
    public Page<SchoolAssignment> list(UUID schoolId, int page, int size) {
        User actor = currentUser.requireCurrentUser();
        if (!access.isDepartmentHead(actor) || actor.getSchool() == null || !schoolId.equals(actor.getSchool().getId()))
            throw ApiException.forbidden("Chỉ Trưởng bộ môn được xem phân công bài tập trong trường mình.");
        if (page < 0 || size < 1 || size > 100)
            throw ApiException.badRequest("Phân trang không hợp lệ.");
        return assignments.findSchoolAssignments(schoolId, PageRequest.of(page, size, Sort.by("createdAt").descending()))
                .map(item -> new SchoolAssignment(item.getId(), item.getTitle(),
                        item.getSchoolClass() == null ? null : item.getSchoolClass().getId(),
                        item.getSchoolClass() == null ? null : item.getSchoolClass().getName(),
                        item.getSchoolClass() == null ? null : item.getSchoolClass().getSchoolYear(),
                        item.getTeacher().getId(), item.getTeacher().getFullName(), item.getAssignedStudentIds().size(),
                        item.getStatus().name(), item.getAssignedAt(), item.getDueAt()));
    }
}
