/**
 * B2B Role System Types for PhysLive
 * 
 * Platform roles (school_id = null):
 * - ADMIN: System administrator, manages all schools
 * - CONTENT_REVIEWER: Reviews shared simulations, manages content quality
 * 
 * School roles (school_id = UUID):
 * - SCHOOL_MANAGER: Manages one school (1 per school max)
 * - TEACHER: Creates simulations, teaches classes, assigns work
 * - STUDENT: Takes classes, submits assignments
 */

export type RoleType = 
  | 'ADMIN' 
  | 'CONTENT_REVIEWER' 
  | 'SCHOOL_MANAGER' 
  | 'TEACHER' 
  | 'STUDENT';

export const PLATFORM_ROLES: RoleType[] = ['ADMIN', 'CONTENT_REVIEWER'];
export const SCHOOL_ROLES: RoleType[] = ['SCHOOL_MANAGER', 'TEACHER', 'STUDENT'];

/**
 * Role display names (Vietnamese)
 */
export const ROLE_LABELS: Record<RoleType, string> = {
  ADMIN: 'Quản trị viên hệ thống',
  CONTENT_REVIEWER: 'Kiểm duyệt nội dung',
  SCHOOL_MANAGER: 'Quản lý trường',
  TEACHER: 'Giáo viên',
  STUDENT: 'Học sinh'
};

/**
 * Check if role is platform-level
 */
export function isPlatformRole(role: string): boolean {
  return PLATFORM_ROLES.includes(role as RoleType);
}

/**
 * Check if role is school-level
 */
export function isSchoolRole(role: string): boolean {
  return SCHOOL_ROLES.includes(role as RoleType);
}

/**
 * Get role display name
 */
export function getRoleLabel(role: string): string {
  return ROLE_LABELS[role as RoleType] || role;
}
