package com.example.backend.service;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.entity.User;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * User management with soft delete functionality.
 * 
 * Business rules:
 * - Soft delete: set active=false, record who/when/why
 * - Hard delete: not allowed (data retention policy)
 * - Deactivated users: cannot login, data preserved
 */
@Service
@RequiredArgsConstructor
public class UserManagementService {
    
    private final UserRepository userRepository;
    private final LicenseCheckService licenseCheckService;
    private final AdminService adminService;
    private final CurrentUserService currentUserService;
    
    /**
     * Deactivate user (soft delete).
     * Only ADMIN or SCHOOL_MANAGER (for their school) can deactivate.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHOOL_MANAGER')")
    @Transactional
    public void deactivateUser(Integer userId, Integer deactivatedByUserId, String reason) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        
        if (Boolean.FALSE.equals(user.getActive())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "User is already deactivated");
        }
        
        if (!java.util.Objects.equals(currentUserService.requireCurrentUser().getId(), deactivatedByUserId))
            throw new ApiException(HttpStatus.FORBIDDEN, "Invalid acting user");
        adminService.setActive(userId, false);
        user.setActive(false);
        user.setDeactivatedBy(deactivatedByUserId);
        user.setDeactivationReason(reason);
        user.setDeactivatedAt(Instant.now());
        
        userRepository.save(user);
    }
    
    /**
     * Reactivate user.
     * Only ADMIN or SCHOOL_MANAGER (for their school) can reactivate.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHOOL_MANAGER')")
    @Transactional
    public void reactivateUser(Integer userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        
        if (Boolean.TRUE.equals(user.getActive())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "User is already active");
        }
        
        adminService.setActive(userId, true);
        user.setActive(true);
        user.setDeactivatedBy(null);
        user.setDeactivationReason(null);
        user.setDeactivatedAt(null);
        
        userRepository.save(user);
    }
    
    /**
     * Check if user can create simulation (license + quota check).
     * 
     * Rules:
     * - ADMIN: can create
     * - CONTENT_REVIEWER: CANNOT create (only review)
     * - TEACHER: can create (if license active + quota available)
     * - Others: cannot create
     */
    public boolean canCreateSimulation(User user) {
        if (user == null || !Boolean.TRUE.equals(user.getActive()) || user.getRole() == null) {
            return false;
        }
        
        String roleName = user.getRole().getName();
        
        // ADMIN can always create
        if ("ADMIN".equals(roleName)) {
            return true;
        }
        
        // CONTENT_REVIEWER CANNOT create (only review)
        if ("CONTENT_REVIEWER".equals(roleName)) {
            return false;
        }
        
        // TEACHER can create (with license + quota checks)
        if ("TEACHER".equals(roleName)) {
            if (user.getSchool() == null) {
                return false; // Teacher must belong to school
            }
            
            // Check license expiry (grace mode blocks writes)
            if (!licenseCheckService.canPerformWriteOperations(user)) {
                return false;
            }
            
            // Check quota
            return user.getSchool().hasQuotaRemaining();
        }
        
        // Others (SCHOOL_MANAGER, STUDENT) cannot create
        return false;
    }
    
    /**
     * Check if user can create assignment (license check only).
     * 
     * Rules:
     * - ADMIN: can create
     * - CONTENT_REVIEWER: CANNOT create
     * - TEACHER: can create (if license active)
     * - Others: cannot create
     */
    public boolean canCreateAssignment(User user) {
        if (user == null || !Boolean.TRUE.equals(user.getActive()) || user.getRole() == null) {
            return false;
        }
        
        String roleName = user.getRole().getName();
        
        // ADMIN can always create
        if ("ADMIN".equals(roleName)) {
            return true;
        }
        
        // CONTENT_REVIEWER CANNOT create
        if ("CONTENT_REVIEWER".equals(roleName)) {
            return false;
        }
        
        // TEACHER can create (with license check)
        if ("TEACHER".equals(roleName)) {
            if (user.getSchool() == null) {
                return false;
            }
            
            // Check license expiry (grace mode blocks writes)
            return licenseCheckService.canPerformWriteOperations(user);
        }
        
        // Others cannot create
        return false;
    }
}
