package com.example.backend.system.account.repository;

import com.example.backend.system.account.model.entity.User;
import com.example.backend.system.school.model.entity.ClassEnrollment;
import com.example.backend.system.school.model.entity.ClassTeacherAssignment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findFirstByEmailIgnoreCase(String email);
    List<User> findBySchoolId(UUID schoolId);

    @Query("select count(u) from User u where u.school.id = :schoolId and u.role.name = 'STUDENT' and (u.active = true or u.active is null)")
    long countActiveStudents(@Param("schoolId") UUID schoolId);

    Optional<User> findByEmail(String email);

    @Query("select u from User u join u.role r where lower(r.name) = lower(:roleName) and (u.active = true or u.active is null) order by u.fullName")
    List<User> findActiveByRoleName(@Param("roleName") String roleName);

    @Query("select distinct u from User u join u.role r join ClassEnrollment e on e.student.id = u.id join ClassTeacherAssignment a on a.schoolClass.id = e.schoolClass.id where lower(r.name) = 'student' and a.teacher.id = :teacherId and a.isActive = true and e.status = 'ACTIVE' and (u.active = true or u.active is null) order by u.fullName")
    List<User> findActiveStudentsAssignableByTeacher(@Param("teacherId") Integer teacherId);

    /**
     * Count users by school, role, and active status.
     * Used to enforce "1 SCHOOL per school" constraint.
     */
    @Query("SELECT COUNT(u) FROM User u JOIN u.role r WHERE u.school.id = :schoolId AND r.name = :roleName AND u.active = :active")
    long countBySchoolIdAndRoleNameAndActive(
        @Param("schoolId") UUID schoolId,
        @Param("roleName") String roleName,
        @Param("active") Boolean active
    );

    /**
     * Find all users in a school by role.
     */
    @Query("SELECT u FROM User u JOIN u.role r WHERE u.school.id = :schoolId AND r.name = :roleName AND (u.active = true OR u.active IS NULL)")
    List<User> findBySchoolIdAndRoleName(@Param("schoolId") UUID schoolId, @Param("roleName") String roleName);

}
