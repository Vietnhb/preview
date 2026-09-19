package com.example.backend.service.account;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.example.backend.entity.school.School;
import com.example.backend.entity.account.User;
import com.example.backend.entity.enums.RoleName;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.account.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Service for validating role-school consistency rules.
 * Enforces business logic:
 * - Platform roles (ADMIN, CONTENT_REVIEWER) must have school_id = NULL
 * - School roles (SCHOOL_MANAGER, TEACHER, STUDENT) must have school_id NOT NULL
 * - Only 1 SCHOOL_MANAGER per school
 */
@Service
@RequiredArgsConstructor
public class RoleValidationService {
    
    private final UserRepository userRepository;
    
    /**
     * Validate that role-school relationship is consistent.
     * Throws ApiException if validation fails.
     */
    public void validateRoleSchoolConsistency(String roleName, School school) {
        RoleName role = RoleName.from(roleName)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Unsupported role"));
        if (role.isPlatformRole()) {
            if (school != null) {
                throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    String.format("Platform role %s cannot have a school assigned", roleName)
                );
            }
        } else {
            if (school == null) {
                throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    String.format("School role %s must have a school assigned", roleName)
                );
            }
        }
    }
    
    /**
     * Validate that only 1 SCHOOL_MANAGER exists per school.
     * Throws ApiException if another active manager exists.
     * 
     * @param schoolId School to check
     * @param excludeUserId User ID to exclude from check (for updates)
     */
    public void validateSingleSchoolManager(UUID schoolId, Integer excludeUserId) {
        if (schoolId == null) {
            return;
        }
        
        long managerCount = userRepository.countBySchoolIdAndRoleNameAndActive(
            schoolId,
            RoleName.SCHOOL_MANAGER.name(),
            true
        );
        
        // If updating existing user, subtract 1 from count
        if (excludeUserId != null) {
            User existingUser = userRepository.findById(excludeUserId).orElse(null);
            if (existingUser != null 
                && existingUser.getSchool() != null 
                && existingUser.getSchool().getId().equals(schoolId)
                && existingUser.getRole() != null
                && RoleName.SCHOOL_MANAGER.matches(existingUser.getRole().getName())
                && Boolean.TRUE.equals(existingUser.getActive())) {
                managerCount--;
            }
        }
        
        if (managerCount > 0) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "This school already has an active SCHOOL_MANAGER. Only 1 manager per school is allowed."
            );
        }
    }
    
    /**
     * Check if user has permission to perform action on school.
     */
    public boolean canManageSchool(User user, UUID targetSchoolId) {
        if (user == null || !Boolean.TRUE.equals(user.getActive()) || targetSchoolId == null) {
            return false;
        }
        
        String roleName = user.getRole() != null ? user.getRole().getName() : null;
        
        // ADMIN can manage all schools
        if (RoleName.ADMIN.matches(roleName)) {
            return true;
        }
        
        // SCHOOL_MANAGER can only manage their own school
        if (RoleName.SCHOOL_MANAGER.matches(roleName)) {
            return user.getSchool() != null && user.getSchool().getId().equals(targetSchoolId);
        }
        
        return false;
    }
}
