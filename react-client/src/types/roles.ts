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

export const ROLE_NAMES = {
  ADMIN: 'ADMIN',
  CONTENT_REVIEWER: 'CONTENT_REVIEWER',
  SCHOOL_MANAGER: 'SCHOOL_MANAGER',
  TEACHER: 'TEACHER',
  STUDENT: 'STUDENT',
} as const satisfies Record<RoleType, RoleType>;

export const PLATFORM_ROLES: RoleType[] = [ROLE_NAMES.ADMIN, ROLE_NAMES.CONTENT_REVIEWER];
export const SCHOOL_ROLES: RoleType[] = [ROLE_NAMES.SCHOOL_MANAGER, ROLE_NAMES.TEACHER, ROLE_NAMES.STUDENT];

/** Roles that may create simulations and manage learning activities. */
export const LEARNING_MANAGER_ROLES = [ROLE_NAMES.TEACHER, ROLE_NAMES.ADMIN] as const;

/** Roles that may review shared simulation content. */
export const CONTENT_REVIEW_ROLES = [ROLE_NAMES.CONTENT_REVIEWER, ROLE_NAMES.ADMIN] as const;

export function hasRole<T extends string>(role: string | null | undefined, roles: readonly T[]): role is T {
  return role != null && roles.includes(role as T);
}

export function isAdminRole(role: string | null | undefined): boolean {
  return role === ROLE_NAMES.ADMIN;
}

export function isStudentRole(role: string | null | undefined): boolean {
  return role === ROLE_NAMES.STUDENT;
}

export function canManageLearning(role: string | null | undefined): boolean {
  return hasRole(role, LEARNING_MANAGER_ROLES);
}

export function canReviewContent(role: string | null | undefined): boolean {
  return hasRole(role, CONTENT_REVIEW_ROLES);
}

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
