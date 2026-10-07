package com.example.backend.system.school.service;

import com.example.backend.exception.ApiException;
import com.example.backend.system.account.model.enums.RoleName;
import com.example.backend.system.account.repository.UserRepository;
import com.example.backend.system.account.service.CurrentUserService;
import com.example.backend.system.account.service.RoleValidationService;
import com.example.backend.system.school.dto.SchoolReportContracts.ClassRow;
import com.example.backend.system.school.dto.SchoolReportContracts.Summary;
import com.example.backend.system.school.dto.SchoolReportContracts.TokenRow;
import com.example.backend.system.school.dto.SchoolReportContracts;
import com.example.backend.system.school.model.entity.School;
import com.example.backend.system.school.repository.ClassEnrollmentRepository;
import com.example.backend.system.school.repository.ClassTeacherAssignmentRepository;
import com.example.backend.system.school.repository.SchoolClassRepository;
import com.example.backend.system.school.repository.SchoolRepository;
import com.example.backend.system.school.repository.TokenUsageAuditRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SchoolReportService {
    private final SchoolRepository schools;
    private final SchoolClassRepository classes;
    private final ClassEnrollmentRepository enrollments;
    private final ClassTeacherAssignmentRepository teacherAssignments;
    private final UserRepository users;
    private final CurrentUserService currentUser;
    private final RoleValidationService roles;
    private final TokenUsageAuditRepository tokenAudits;

    public Summary summary(UUID schoolId) {
        requireAccess(schoolId); School school = school(schoolId);
        long teachers = users.findBySchoolIdAndRoleName(schoolId, RoleName.STAFF.name()).size();
        long students = users.countActiveStudents(schoolId);
        long managers = users.findBySchoolIdAndRoleName(schoolId, RoleName.SCHOOL.name()).size();
        long enrolled = enrollments.countActiveStudentsBySchoolId(schoolId);
        return new Summary(schoolId, school.getName(), students, teachers, managers,
                classes.findBySchoolIdAndIsActiveTrue(schoolId).size(), enrolled,
                school.getUsedTokens() == null ? 0L : school.getUsedTokens(), school.getMonthlyTokenQuota(), school.getLicenseEnd());
    }

    public List<ClassRow> classes(UUID schoolId) {
        requireAccess(schoolId);
        return classes.findBySchoolIdAndIsActiveTrue(schoolId).stream().map(item -> new ClassRow(item.getId(), item.getName(), item.getGradeLevel(), item.getSchoolYear(),
                teacherAssignments.findByClassIdAndIsActiveTrue(item.getId()).size(), enrollments.findActiveStudentsByClassId(item.getId()).size())).toList();
    }

    public List<TokenRow> tokenAudit(UUID schoolId) {
        requireAccess(schoolId);
        return tokenAudits.findTop200BySchoolIdOrderByRecordedAtDesc(schoolId).stream()
                .map(row -> new TokenRow(row.getUser().getEmail(), row.getOperation(), row.getTokens(), row.getUsageMonth(), row.getRecordedAt())).toList();
    }

    private School school(UUID schoolId) { return schools.findById(schoolId).orElseThrow(() -> ApiException.notFound("Không tìm thấy trường")); }
    private void requireAccess(UUID schoolId) {
        if (!roles.canManageSchool(currentUser.requireCurrentUser(), schoolId)) {
            throw ApiException.forbidden("Bạn không có quyền truy cập trường này");
        }
    }

}
