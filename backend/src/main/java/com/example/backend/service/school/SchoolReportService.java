package com.example.backend.service.school;

import com.example.backend.service.account.CurrentUserService;
import com.example.backend.service.account.RoleValidationService;

import com.example.backend.entity.school.School;
import com.example.backend.entity.enums.RoleName;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.school.ClassEnrollmentRepository;
import com.example.backend.repository.school.ClassTeacherAssignmentRepository;
import com.example.backend.repository.school.SchoolClassRepository;
import com.example.backend.repository.school.SchoolRepository;
import com.example.backend.repository.account.UserRepository;
import com.example.backend.repository.audit.TokenUsageAuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SchoolReportService {
    private final SchoolRepository schools;
    private final SchoolClassRepository classes;
    private final ClassEnrollmentRepository enrollments;
    private final ClassTeacherAssignmentRepository teacherAssignments;
    private final UserRepository users;
    private final CurrentUserService currentUser;
    private final RoleValidationService roles;
    private final TokenUsageAuditRepository tokenAudits;

    public record Summary(UUID schoolId, String schoolName, long students, long teachers, long managers,
                          long activeClasses, long enrolledStudents, long usedTokens, Integer tokenQuota,
                          LocalDate licenseEnd) { }
    public record ClassRow(UUID id, String name, Integer gradeLevel, String schoolYear,
                           long teachers, long students) { }
    public record TokenRow(String userEmail, String operation, long tokens, LocalDate usageMonth, java.time.Instant recordedAt) { }

    public Summary summary(UUID schoolId) {
        School school = school(schoolId); requireAccess(schoolId);
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

    private School school(UUID schoolId) { return schools.findById(schoolId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "School not found")); }
    private void requireAccess(UUID schoolId) {
        if (!roles.canManageSchool(currentUser.requireCurrentUser(), schoolId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "School access denied");
        }
    }

}
