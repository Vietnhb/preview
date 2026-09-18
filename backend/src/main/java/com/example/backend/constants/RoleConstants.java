package com.example.backend.constants;

/**
 * Role name constants for B2B system.
 * 
 * Platform roles (school_id = NULL):
 * - ADMIN: System administrator, manages all schools and platform settings
 * - CONTENT_REVIEWER: Reviews shared simulations, manages content quality
 * 
 * School roles (school_id = UUID):
 * - SCHOOL_MANAGER: Manages one school (1 per school), creates classes, assigns teachers
 * - TEACHER: Creates simulations, teaches classes, assigns work
 * - STUDENT: Takes classes, submits assignments
 */
public final class RoleConstants {
    
    // Platform roles
    public static final String ADMIN = "ADMIN";
    public static final String CONTENT_REVIEWER = "CONTENT_REVIEWER";
    
    // School roles
    public static final String SCHOOL_MANAGER = "SCHOOL_MANAGER";
    public static final String TEACHER = "TEACHER";
    public static final String STUDENT = "STUDENT";
    
    // Legacy role (for backward compatibility)
    public static final String GUEST = "GUEST";
    
    // Spring Security role prefix
    public static final String ROLE_PREFIX = "ROLE_";
    
    private RoleConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
    
    /**
     * Check if role is platform-level (school_id must be NULL).
     */
    public static boolean isPlatformRole(String roleName) {
        return ADMIN.equals(roleName) || CONTENT_REVIEWER.equals(roleName);
    }
    
    /**
     * Check if role is school-level (school_id must be NOT NULL).
     */
    public static boolean isSchoolRole(String roleName) {
        return SCHOOL_MANAGER.equals(roleName) || TEACHER.equals(roleName) || STUDENT.equals(roleName);
    }
    
    /**
     * Get role name with Spring Security prefix.
     */
    public static String withPrefix(String roleName) {
        return ROLE_PREFIX + roleName;
    }
}
