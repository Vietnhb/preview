package com.example.backend.service.school;

import com.example.backend.service.account.CurrentUserService;
import com.example.backend.service.account.RoleValidationService;

import com.example.backend.entity.school.ClassEnrollment;
import com.example.backend.entity.school.ClassTeacherAssignment;
import com.example.backend.entity.school.School;
import com.example.backend.entity.school.SchoolClass;
import com.example.backend.entity.account.User;
import com.example.backend.entity.enums.RoleName;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.school.ClassEnrollmentRepository;
import com.example.backend.repository.school.ClassTeacherAssignmentRepository;
import com.example.backend.repository.school.SchoolClassRepository;
import com.example.backend.repository.school.SchoolRepository;
import com.example.backend.repository.account.UserRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Class, teacher assignment and student enrollment operations scoped to one school. */
@Service
@RequiredArgsConstructor
public class SchoolClassService {
    private final SchoolClassRepository classes;
    private final ClassEnrollmentRepository enrollments;
    private final ClassTeacherAssignmentRepository teacherAssignments;
    private final SchoolRepository schools;
    private final UserRepository users;
    private final CurrentUserService currentUser;
    private final RoleValidationService roleValidation;
    private final LicenseCheckService licenseCheck;

    public record ClassRequest(@NotBlank @Size(max = 100) String name,
                               @NotNull @Min(10) @Max(12) Integer gradeLevel,
                               @NotBlank @Size(max = 20) @Pattern(regexp = "\\d{4}-\\d{4}") String schoolYear,
                               @Size(max = 50) String subject) { }
    public record Person(Integer id, String fullName, String email) { }
    public record ClassSummary(UUID id, String name, Integer gradeLevel, String schoolYear, String subject,
                               boolean active, long teacherCount, long studentCount) { }
    public record ClassDetail(UUID id, String name, Integer gradeLevel, String schoolYear, String subject,
                              boolean active, List<Person> teachers, List<Person> students) { }
    public record TeacherAssignment(UUID id, Integer teacherId, String teacherName, boolean active) { }
    public record Enrollment(UUID id, Integer studentId, String studentName, String schoolYear, String status) { }
    public record StudentClassTeacher(Integer id, String fullName) { }
    public record StudentClassSummary(UUID id, String name, Integer gradeLevel, String schoolYear, String subject,
                                      UUID schoolId, String schoolName, List<StudentClassTeacher> teachers, long classmateCount) { }

    @Transactional(readOnly = true)
    public List<StudentClassSummary> mineForStudent() {
        User student = currentUser.requireCurrentUser();
        if (student.getRole() == null || !RoleName.STUDENT.matches(student.getRole().getName()))
            throw new ApiException(HttpStatus.FORBIDDEN, "Only students can view their classes");
        return enrollments.findActiveEnrollmentsByStudentId(student.getId()).stream()
                .filter(item -> item.getSchoolClass() != null && Boolean.TRUE.equals(item.getSchoolClass().getIsActive()))
                .map(item -> {
                    SchoolClass schoolClass = item.getSchoolClass();
                    List<StudentClassTeacher> teachers = teacherAssignments.findByClassIdAndIsActiveTrue(schoolClass.getId()).stream()
                            .map(assignment -> new StudentClassTeacher(assignment.getTeacher().getId(), assignment.getTeacher().getFullName())).toList();
                    long classmates = Math.max(0, enrollments.findActiveStudentsByClassId(schoolClass.getId()).size() - 1L);
                    return new StudentClassSummary(schoolClass.getId(), schoolClass.getName(), schoolClass.getGradeLevel(),
                            schoolClass.getSchoolYear(), schoolClass.getSubject(), schoolClass.getSchool().getId(),
                            schoolClass.getSchool().getName(), teachers, classmates);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ClassSummary> list(UUID schoolId) {
        requireSchoolAccess(schoolId);
        return classes.findBySchoolIdAndIsActiveTrue(schoolId).stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public ClassDetail get(UUID schoolId, UUID classId) {
        requireSchoolAccess(schoolId);
        SchoolClass schoolClass = activeClassInSchool(schoolId, classId);
        List<Person> teachers = teacherAssignments.findByClassIdAndIsActiveTrue(classId).stream()
                .map(item -> person(item.getTeacher())).toList();
        List<Person> students = enrollments.findActiveStudentsByClassId(classId).stream()
                .map(item -> person(item.getStudent())).toList();
        return new ClassDetail(schoolClass.getId(), schoolClass.getName(), schoolClass.getGradeLevel(),
                schoolClass.getSchoolYear(), schoolClass.getSubject(), Boolean.TRUE.equals(schoolClass.getIsActive()), teachers, students);
    }

    @Transactional
    public ClassDetail create(UUID schoolId, ClassRequest request) {
        requireWriteAccess(schoolId);
        String name = clean(request.name());
        String year = clean(request.schoolYear());
        if (classes.existsBySchoolIdAndNameIgnoreCaseAndSchoolYear(schoolId, name, year))
            throw new ApiException(HttpStatus.CONFLICT, "LÃƒÂ¡Ã‚Â»Ã¢â‚¬Âºp Ãƒâ€žÃ¢â‚¬ËœÃƒÆ’Ã‚Â£ tÃƒÂ¡Ã‚Â»Ã¢â‚¬Å“n tÃƒÂ¡Ã‚ÂºÃ‚Â¡i trong nÃƒâ€žÃ†â€™m hÃƒÂ¡Ã‚Â»Ã‚Âc nÃƒÆ’Ã‚Â y.");
        School school = school(schoolId);
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setSchool(school); schoolClass.setName(name); schoolClass.setGradeLevel(request.gradeLevel());
        schoolClass.setSchoolYear(year); schoolClass.setSubject(blankToNull(request.subject())); schoolClass.setIsActive(true);
        return get(schoolId, classes.save(schoolClass).getId());
    }

    @Transactional
    public ClassDetail update(UUID schoolId, UUID classId, ClassRequest request) {
        requireWriteAccess(schoolId);
        SchoolClass schoolClass = activeClassInSchool(schoolId, classId);
        String name = clean(request.name()); String year = clean(request.schoolYear());
        if ((!schoolClass.getName().equalsIgnoreCase(name) || !schoolClass.getSchoolYear().equals(year))
                && classes.existsBySchoolIdAndNameIgnoreCaseAndSchoolYear(schoolId, name, year))
            throw new ApiException(HttpStatus.CONFLICT, "LÃƒÂ¡Ã‚Â»Ã¢â‚¬Âºp Ãƒâ€žÃ¢â‚¬ËœÃƒÆ’Ã‚Â£ tÃƒÂ¡Ã‚Â»Ã¢â‚¬Å“n tÃƒÂ¡Ã‚ÂºÃ‚Â¡i trong nÃƒâ€žÃ†â€™m hÃƒÂ¡Ã‚Â»Ã‚Âc nÃƒÆ’Ã‚Â y.");
        if (!schoolClass.getSchoolYear().equals(year) && !enrollments.findByClassId(classId).isEmpty())
            throw new ApiException(HttpStatus.CONFLICT, "KhÃƒÆ’Ã‚Â´ng thÃƒÂ¡Ã‚Â»Ã†â€™ Ãƒâ€žÃ¢â‚¬ËœÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¢i nÃƒâ€žÃ†â€™m hÃƒÂ¡Ã‚Â»Ã‚Âc khi lÃƒÂ¡Ã‚Â»Ã¢â‚¬Âºp Ãƒâ€žÃ¢â‚¬ËœÃƒÆ’Ã‚Â£ cÃƒÆ’Ã‚Â³ enrollment.");
        schoolClass.setName(name); schoolClass.setGradeLevel(request.gradeLevel()); schoolClass.setSchoolYear(year);
        schoolClass.setSubject(blankToNull(request.subject()));
        return get(schoolId, classes.save(schoolClass).getId());
    }

    @Transactional
    public void archive(UUID schoolId, UUID classId) {
        requireWriteAccess(schoolId);
        SchoolClass schoolClass = activeClassInSchool(schoolId, classId);
        schoolClass.setIsActive(false); classes.save(schoolClass);
        teacherAssignments.findByClassIdAndIsActiveTrue(classId).forEach(item -> { item.setIsActive(false); teacherAssignments.save(item); });
        enrollments.findActiveStudentsByClassId(classId).forEach(item -> { item.setStatus(ClassEnrollment.EnrollmentStatus.COMPLETED); enrollments.save(item); });
    }

    @Transactional
    public TeacherAssignment assignTeacher(UUID schoolId, UUID classId, Integer teacherId) {
        requireWriteAccess(schoolId); SchoolClass schoolClass = activeClassInSchool(schoolId, classId);
        User teacher = userInSchool(schoolId, teacherId, RoleName.TEACHER.name());
        ClassTeacherAssignment assignment = teacherAssignments.findBySchoolClassIdAndTeacherId(classId, teacherId).orElseGet(ClassTeacherAssignment::new);
        assignment.setSchoolClass(schoolClass); assignment.setTeacher(teacher); assignment.setIsActive(true);
        assignment = teacherAssignments.save(assignment);
        return new TeacherAssignment(assignment.getId(), teacher.getId(), teacher.getFullName(), true);
    }

    @Transactional
    public void unassignTeacher(UUID schoolId, UUID classId, Integer teacherId) {
        requireWriteAccess(schoolId); activeClassInSchool(schoolId, classId);
        ClassTeacherAssignment assignment = teacherAssignments.findBySchoolClassIdAndTeacherId(classId, teacherId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "KhÃƒÆ’Ã‚Â´ng tÃƒÆ’Ã‚Â¬m thÃƒÂ¡Ã‚ÂºÃ‚Â¥y phÃƒÆ’Ã‚Â¢n cÃƒÆ’Ã‚Â´ng giÃƒÆ’Ã‚Â¡o viÃƒÆ’Ã‚Âªn."));
        assignment.setIsActive(false); teacherAssignments.save(assignment);
    }

    @Transactional
    public Enrollment enrollStudent(UUID schoolId, UUID classId, Integer studentId) {
        requireWriteAccess(schoolId);
        School school = schools.findByIdForUpdate(schoolId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "KhÃƒÆ’Ã‚Â´ng tÃƒÆ’Ã‚Â¬m thÃƒÂ¡Ã‚ÂºÃ‚Â¥y trÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Âng."));
        SchoolClass schoolClass = activeClassInSchool(schoolId, classId);
        User student = userInSchool(schoolId, studentId, RoleName.STUDENT.name());
        Optional<ClassEnrollment> activeEnrollment = enrollments.findActiveEnrollment(studentId, schoolClass.getSchoolYear());
        activeEnrollment.ifPresent(existing -> {
            if (!existing.getSchoolClass().getId().equals(classId))
                throw new ApiException(HttpStatus.CONFLICT, "HÃƒÂ¡Ã‚Â»Ã‚Âc sinh Ãƒâ€žÃ¢â‚¬ËœÃƒÆ’Ã‚Â£ thuÃƒÂ¡Ã‚Â»Ã¢â€žÂ¢c mÃƒÂ¡Ã‚Â»Ã¢â€žÂ¢t lÃƒÂ¡Ã‚Â»Ã¢â‚¬Âºp khÃƒÆ’Ã‚Â¡c trong nÃƒâ€žÃ†â€™m hÃƒÂ¡Ã‚Â»Ã‚Âc nÃƒÆ’Ã‚Â y.");
        });
        if (activeEnrollment.isEmpty() && school.getStudentQuota() != null
                && enrollments.countActiveStudentsBySchoolId(schoolId) >= school.getStudentQuota())
            throw new ApiException(HttpStatus.CONFLICT, "TrÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Âng Ãƒâ€žÃ¢â‚¬ËœÃƒÆ’Ã‚Â£ Ãƒâ€žÃ¢â‚¬ËœÃƒÂ¡Ã‚ÂºÃ‚Â¡t quota hÃƒÂ¡Ã‚Â»Ã‚Âc sinh cÃƒÂ¡Ã‚Â»Ã‚Â§a gÃƒÆ’Ã‚Â³i hiÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡n tÃƒÂ¡Ã‚ÂºÃ‚Â¡i.");
        ClassEnrollment enrollment = enrollments.findBySchoolClassIdAndStudentIdAndStatus(classId, studentId, ClassEnrollment.EnrollmentStatus.ACTIVE)
                .orElseGet(ClassEnrollment::new);
        enrollment.setSchoolClass(schoolClass); enrollment.setStudent(student); enrollment.setSchoolYear(schoolClass.getSchoolYear());
        enrollment.setStatus(ClassEnrollment.EnrollmentStatus.ACTIVE); enrollment = enrollments.save(enrollment);
        return new Enrollment(enrollment.getId(), student.getId(), student.getFullName(), enrollment.getSchoolYear(), enrollment.getStatus().name());
    }

    @Transactional
    public Enrollment transferStudent(UUID schoolId, UUID targetClassId, Integer studentId) {
        requireWriteAccess(schoolId); SchoolClass target = activeClassInSchool(schoolId, targetClassId);
        User student = userInSchool(schoolId, studentId, RoleName.STUDENT.name());
        enrollments.findActiveEnrollment(studentId, target.getSchoolYear()).ifPresent(existing -> {
            if (!existing.getSchoolClass().getId().equals(targetClassId)) {
                existing.setStatus(ClassEnrollment.EnrollmentStatus.TRANSFERRED); enrollments.save(existing);
            }
        });
        return enrollStudent(schoolId, targetClassId, student.getId());
    }

    @Transactional
    public void removeStudent(UUID schoolId, UUID classId, Integer studentId) {
        requireWriteAccess(schoolId); activeClassInSchool(schoolId, classId);
        ClassEnrollment enrollment = enrollments.findBySchoolClassIdAndStudentIdAndStatus(classId, studentId, ClassEnrollment.EnrollmentStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "HÃƒÂ¡Ã‚Â»Ã‚Âc sinh chÃƒâ€ Ã‚Â°a ÃƒÂ¡Ã‚Â»Ã…Â¸ trong lÃƒÂ¡Ã‚Â»Ã¢â‚¬Âºp nÃƒÆ’Ã‚Â y."));
        enrollment.setStatus(ClassEnrollment.EnrollmentStatus.DROPPED); enrollments.save(enrollment);
    }

    private ClassSummary summary(SchoolClass schoolClass) {
        return new ClassSummary(schoolClass.getId(), schoolClass.getName(), schoolClass.getGradeLevel(), schoolClass.getSchoolYear(),
                schoolClass.getSubject(), Boolean.TRUE.equals(schoolClass.getIsActive()),
                teacherAssignments.findByClassIdAndIsActiveTrue(schoolClass.getId()).size(),
                enrollments.findActiveStudentsByClassId(schoolClass.getId()).size());
    }

    private User requireWriteAccess(UUID schoolId) {
        User actor = requireSchoolAccess(schoolId);
        if (!RoleName.ADMIN.matches(actor.getRole() == null ? null : actor.getRole().getName())) licenseCheck.requireWriteAccess(actor);
        return actor;
    }

    private User requireSchoolAccess(UUID schoolId) {
        User actor = currentUser.requireCurrentUser();
        if (!roleValidation.canManageSchool(actor, schoolId)) throw new ApiException(HttpStatus.FORBIDDEN, "School access denied");
        return actor;
    }

    private SchoolClass classInSchool(UUID schoolId, UUID classId) {
        SchoolClass schoolClass = classes.findById(classId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "KhÃƒÆ’Ã‚Â´ng tÃƒÆ’Ã‚Â¬m thÃƒÂ¡Ã‚ÂºÃ‚Â¥y lÃƒÂ¡Ã‚Â»Ã¢â‚¬Âºp."));
        if (schoolClass.getSchool() == null || !schoolId.equals(schoolClass.getSchool().getId())) throw new ApiException(HttpStatus.FORBIDDEN, "LÃƒÂ¡Ã‚Â»Ã¢â‚¬Âºp khÃƒÆ’Ã‚Â´ng thuÃƒÂ¡Ã‚Â»Ã¢â€žÂ¢c trÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Âng nÃƒÆ’Ã‚Â y.");
        return schoolClass;
    }

    private SchoolClass activeClassInSchool(UUID schoolId, UUID classId) {
        SchoolClass schoolClass = classInSchool(schoolId, classId);
        if (!Boolean.TRUE.equals(schoolClass.getIsActive()))
            throw new ApiException(HttpStatus.CONFLICT, "LÃƒÂ¡Ã‚Â»Ã¢â‚¬Âºp Ãƒâ€žÃ¢â‚¬ËœÃƒÆ’Ã‚Â£ Ãƒâ€žÃ¢â‚¬ËœÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Â£c tÃƒÂ¡Ã‚ÂºÃ‚Â¯t vÃƒÆ’Ã‚Â  khÃƒÆ’Ã‚Â´ng thÃƒÂ¡Ã‚Â»Ã†â€™ cÃƒÂ¡Ã‚ÂºÃ‚Â­p nhÃƒÂ¡Ã‚ÂºÃ‚Â­t.");
        return schoolClass;
    }

    private School school(UUID schoolId) { return schools.findById(schoolId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "KhÃƒÆ’Ã‚Â´ng tÃƒÆ’Ã‚Â¬m thÃƒÂ¡Ã‚ÂºÃ‚Â¥y trÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Âng.")); }

    private User userInSchool(UUID schoolId, Integer id, String role) {
        User user = users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "KhÃƒÆ’Ã‚Â´ng tÃƒÆ’Ã‚Â¬m thÃƒÂ¡Ã‚ÂºÃ‚Â¥y ngÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Âi dÃƒÆ’Ã‚Â¹ng."));
        if (user.getSchool() == null || !schoolId.equals(user.getSchool().getId()) || user.getRole() == null || !role.equals(user.getRole().getName()) || !Boolean.TRUE.equals(user.getActive()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "NgÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Âi dÃƒÆ’Ã‚Â¹ng khÃƒÆ’Ã‚Â´ng hÃƒÂ¡Ã‚Â»Ã‚Â£p lÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡ trong trÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Âng nÃƒÆ’Ã‚Â y.");
        return user;
    }

    private static Person person(User user) { return new Person(user.getId(), user.getFullName(), user.getEmail()); }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static String blankToNull(String value) { String cleaned = clean(value); return cleaned.isBlank() ? null : cleaned; }
}
