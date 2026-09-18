package com.example.backend.repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.backend.entity.User;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    List<User> findBySchoolId(UUID schoolId);

    Optional<User> findByEmail(String email);

    @Query("select u from User u join u.role r where lower(r.name) = lower(:roleName) and (u.active = true or u.active is null) order by u.fullName")
    List<User> findActiveByRoleName(@Param("roleName") String roleName);

    /**
     * Count users by school, role, and active status.
     * Used to enforce "1 SCHOOL_MANAGER per school" constraint.
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

    /**
     * Find all active users in a school.
     */
    @Query("SELECT u FROM User u WHERE u.school.id = :schoolId AND (u.active = true OR u.active IS NULL)")
    List<User> findActiveBySchoolId(@Param("schoolId") UUID schoolId);
}
