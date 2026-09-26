package com.example.backend.service.school;

import com.example.backend.service.account.CurrentUserService;
import com.example.backend.service.account.RoleValidationService;
import com.example.backend.service.admin.AdminService;

import com.example.backend.dto.admin.CreateManagedUserRequest;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    private final LicenseCheckService license;
    private final AdminService admin;
    private final TokenUsageAuditRepository tokenAudits;

    public record Summary(UUID schoolId, String schoolName, long students, long teachers, long managers,
                          long activeClasses, long enrolledStudents, long usedTokens, Integer tokenQuota,
                          LocalDate licenseEnd) { }
    public record ClassRow(UUID id, String name, Integer gradeLevel, String schoolYear,
                           long teachers, long students) { }
    public record ImportRow(int row, String email, String status, String message) { }
    public record ImportResult(int total, int imported, int failed, List<ImportRow> rows) { }
    public record TokenRow(String userEmail, String operation, long tokens, LocalDate usageMonth, java.time.Instant recordedAt) { }

    public Summary summary(UUID schoolId) {
        School school = school(schoolId); requireAccess(schoolId);
        long teachers = users.findBySchoolIdAndRoleName(schoolId, RoleName.TEACHER.name()).size();
        long students = users.countActiveStudents(schoolId);
        long managers = users.findBySchoolIdAndRoleName(schoolId, RoleName.SCHOOL_MANAGER.name()).size();
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

    public ImportResult importUsers(UUID schoolId, MultipartFile file) {
        requireAccess(schoolId); license.requireWriteAccess(currentUser.requireCurrentUser());
        if (file == null || file.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "CSV file is required");
        List<ImportRow> rows = new ArrayList<>();
        int imported = 0;
        int rowNumber = 1;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) throw new ApiException(HttpStatus.BAD_REQUEST, "CSV file is empty");
            List<String> columns = parse(header).stream().map(value -> value.trim().toLowerCase(Locale.ROOT)).toList();
            for (String required : List.of("email", "fullname", "role", "password"))
                if (!columns.contains(required)) throw new ApiException(HttpStatus.BAD_REQUEST, "CSV thiếu cột " + required);
            String line;
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                if (line.isBlank()) continue;
                List<String> values = parse(line);
                String email = value(columns, values, "email");
                ImportRow importedRow = importRow(columns, values, email, rowNumber, schoolId);
                rows.add(importedRow);
                if ("IMPORTED".equals(importedRow.status())) imported++;
            }
        } catch (IOException ex) { throw new ApiException(HttpStatus.BAD_REQUEST, "Không thể đọc CSV"); }
        int failed = (int) rows.stream().filter(row -> row.status().equals("FAILED")).count();
        return new ImportResult(rows.size(), imported, failed, rows);
    }

    private ImportRow importRow(List<String> columns, List<String> values, String email,
                                int rowNumber, UUID schoolId) {
        try {
            String role = value(columns, values, "role").toUpperCase(Locale.ROOT);
            if (!RoleName.TEACHER.matches(role) && !RoleName.STUDENT.matches(role)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Role phải là TEACHER hoặc STUDENT");
            }
            admin.createUser(new CreateManagedUserRequest(email, value(columns, values, "password"),
                    value(columns, values, "fullname"), role, schoolId.toString()));
            return new ImportRow(rowNumber, email, "IMPORTED", "Tạo tài khoản thành công");
        } catch (RuntimeException ex) {
            return new ImportRow(rowNumber, email, "FAILED",
                    ex.getMessage() == null ? "Dòng không hợp lệ" : ex.getMessage());
        }
    }

    private School school(UUID schoolId) { return schools.findById(schoolId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "School not found")); }
    private void requireAccess(UUID schoolId) {
        if (!roles.canManageSchool(currentUser.requireCurrentUser(), schoolId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "School access denied");
        }
    }
    private static String value(List<String> columns, List<String> values, String key) {
        int index = columns.indexOf(key);
        if (index < 0 || index >= values.size() || values.get(index).isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Thiếu dữ liệu " + key);
        }
        return values.get(index).trim();
    }
    private static List<String> parse(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        int index = 0;
        while (index < line.length()) {
            char character = line.charAt(index);
            if (character == '"' && quoted && index + 1 < line.length()
                    && line.charAt(index + 1) == '"') {
                current.append('"');
                index += 2;
                continue;
            }
            if (character == '"') {
                quoted = !quoted;
            } else if (character == ',' && !quoted) {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(character);
            }
            index++;
        }
        result.add(current.toString());
        return result;
    }
}
