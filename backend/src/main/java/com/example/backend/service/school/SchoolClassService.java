package com.example.backend.service.school;

import com.example.backend.dto.school.SchoolClassRequest;
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
    public ClassDetail create(UUID schoolId, SchoolClassRequest request) {
        requireWriteAccess(schoolId);
        String name = clean(request.name());
        String year = clean(request.schoolYear());
        if (classes.existsBySchoolIdAndNameIgnoreCaseAndSchoolYear(schoolId, name, year))
            throw new ApiException(HttpStatus.CONFLICT, "Lớp đã tồn tại trong năm học này.");
        School school = school(schoolId);
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setSchool(school); schoolClass.setName(name); schoolClass.setGradeLevel(request.gradeLevel());
        schoolClass.setSchoolYear(year); schoolClass.setSubject(blankToNull(request.subject())); schoolClass.setIsActive(true);
        return get(schoolId, classes.save(schoolClass).getId());
    }

    @Transactional
    public ClassDetail update(UUID schoolId, UUID classId, SchoolClassRequest request) {
        requireWriteAccess(schoolId);
        SchoolClass schoolClass = activeClassInSchool(schoolId, classId);
        String name = clean(request.name()); String year = clean(request.schoolYear());
        if ((!schoolClass.getName().equalsIgnoreCase(name) || !schoolClass.getSchoolYear().equals(year))
                && classes.existsBySchoolIdAndNameIgnoreCaseAndSchoolYear(schoolId, name, year))
            throw new ApiException(HttpStatus.CONFLICT, "Lớp đã tồn tại trong năm học này.");
        if (!schoolClass.getSchoolYear().equals(year) && !enrollments.findByClassId(classId).isEmpty())
            throw new ApiException(HttpStatus.CONFLICT, "Không thể đổi năm học khi lớp đã có enrollment.");
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
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy phân công giáo viên."));
        assignment.setIsActive(false); teacherAssignments.save(assignment);
    }

    @Transactional
    public Enrollment enrollStudent(UUID schoolId, UUID classId, Integer studentId) {
        requireWriteAccess(schoolId);
        School school = schools.findByIdForUpdate(schoolId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy trường."));
        SchoolClass schoolClass = activeClassInSchool(schoolId, classId);
        User student = userInSchool(schoolId, studentId, RoleName.STUDENT.name());
        Optional<ClassEnrollment> activeEnrollment = enrollments.findActiveEnrollment(studentId, schoolClass.getSchoolYear());
        activeEnrollment.ifPresent(existing -> {
            if (!existing.getSchoolClass().getId().equals(classId))
                throw new ApiException(HttpStatus.CONFLICT, "Học sinh đã thuộc một lớp khác trong năm học này.");
        });
        if (activeEnrollment.isEmpty() && school.getStudentQuota() != null
                && enrollments.countActiveStudentsBySchoolId(schoolId) >= school.getStudentQuota())
            throw new ApiException(HttpStatus.CONFLICT, "Trường đã đạt quota học sinh của gói hiện tại.");
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
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Học sinh chưa ở trong lớp này."));
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
        SchoolClass schoolClass = classes.findById(classId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy lớp."));
        if (schoolClass.getSchool() == null || !schoolId.equals(schoolClass.getSchool().getId())) throw new ApiException(HttpStatus.FORBIDDEN, "Lớp không thuộc trường này.");
        return schoolClass;
    }

    private SchoolClass activeClassInSchool(UUID schoolId, UUID classId) {
        SchoolClass schoolClass = classInSchool(schoolId, classId);
        if (!Boolean.TRUE.equals(schoolClass.getIsActive()))
            throw new ApiException(HttpStatus.CONFLICT, "Lớp đã được tắt và không thể cập nhật.");
        return schoolClass;
    }

    private School school(UUID schoolId) { return schools.findById(schoolId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy trường.")); }

    private User userInSchool(UUID schoolId, Integer id, String role) {
        User user = users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng."));
        if (user.getSchool() == null || !schoolId.equals(user.getSchool().getId()) || user.getRole() == null || !role.equals(user.getRole().getName()) || !Boolean.TRUE.equals(user.getActive()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Người dùng không hợp lệ trong trường này.");
        return user;
    }

    private static Person person(User user) { return new Person(user.getId(), user.getFullName(), user.getEmail()); }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static String blankToNull(String value) { String cleaned = clean(value); return cleaned.isBlank() ? null : cleaned; }
}
