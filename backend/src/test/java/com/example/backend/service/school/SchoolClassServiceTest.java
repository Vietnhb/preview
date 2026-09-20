package com.example.backend.service.school;

import com.example.backend.entity.account.Role;
import com.example.backend.entity.account.User;
import com.example.backend.entity.school.ClassEnrollment;
import com.example.backend.entity.school.ClassTeacherAssignment;
import com.example.backend.entity.school.School;
import com.example.backend.entity.school.SchoolClass;
import com.example.backend.repository.account.UserRepository;
import com.example.backend.repository.school.ClassEnrollmentRepository;
import com.example.backend.repository.school.ClassTeacherAssignmentRepository;
import com.example.backend.repository.school.SchoolClassRepository;
import com.example.backend.repository.school.SchoolRepository;
import com.example.backend.service.account.CurrentUserService;
import com.example.backend.service.account.RoleValidationService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SchoolClassServiceTest {
    @Test
    void studentSeesRealClassTeacherSchoolAndClassmateCount() {
        ClassEnrollmentRepository enrollments = mock(ClassEnrollmentRepository.class);
        ClassTeacherAssignmentRepository teachers = mock(ClassTeacherAssignmentRepository.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        SchoolClassService service = new SchoolClassService(mock(SchoolClassRepository.class), enrollments, teachers,
                mock(SchoolRepository.class), mock(UserRepository.class), currentUser,
                mock(RoleValidationService.class), mock(LicenseCheckService.class));

        Role studentRole = new Role(); studentRole.setName("STUDENT");
        User student = new User(); student.setId(7); student.setRole(studentRole);
        School school = new School(); school.setId(UUID.randomUUID()); school.setName("THPT NguyÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¦n TrÃƒÆ’Ã‚Â£i");
        SchoolClass schoolClass = new SchoolClass(); schoolClass.setId(UUID.randomUUID()); schoolClass.setSchool(school);
        schoolClass.setName("12A1"); schoolClass.setGradeLevel(12); schoolClass.setSchoolYear("2026-2027");
        schoolClass.setSubject("VÃƒÂ¡Ã‚ÂºÃ‚Â­t lÃƒÆ’Ã‚Â½"); schoolClass.setIsActive(true);
        ClassEnrollment own = new ClassEnrollment(); own.setStudent(student); own.setSchoolClass(schoolClass);
        User teacher = new User(); teacher.setId(9); teacher.setFullName("NguyÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¦n Minh An"); teacher.setEmail("an@school.edu");
        ClassTeacherAssignment teacherAssignment = new ClassTeacherAssignment(); teacherAssignment.setTeacher(teacher);

        when(currentUser.requireCurrentUser()).thenReturn(student);
        when(enrollments.findActiveEnrollmentsByStudentId(7)).thenReturn(List.of(own));
        when(enrollments.findActiveStudentsByClassId(schoolClass.getId())).thenReturn(List.of(own, new ClassEnrollment(), new ClassEnrollment()));
        when(teachers.findByClassIdAndIsActiveTrue(schoolClass.getId())).thenReturn(List.of(teacherAssignment));

        var result = service.mineForStudent().getFirst();
        assertEquals("12A1", result.name());
        assertEquals("THPT NguyÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¦n TrÃƒÆ’Ã‚Â£i", result.schoolName());
        assertEquals("NguyÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¦n Minh An", result.teachers().getFirst().fullName());
        assertEquals(2, result.classmateCount());
    }
}
