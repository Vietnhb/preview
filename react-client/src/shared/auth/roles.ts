/**
 * B2B Role System Types for PhysLive
 *
 * Platform roles (school_id = null):
 * - ADMIN: Views users and manages manager accounts
 * - MANAGER: System administrator, manages all schools
 * - REVIEWER: Edits reference content and/or reviews shared simulations (see permissions.ts)
 *
 * School roles (school_id = UUID):
 * - SCHOOL: Manages one school (1 per school max)
 * - STAFF: Teacher and/or department head of the school (see permissions.ts)
 * - STUDENT: Takes classes, submits assignments
 */

export type RoleType =
  | 'ADMIN'
  | 'MANAGER'
  | 'REVIEWER'
  | 'SCHOOL'
  | 'STAFF'
  | 'STUDENT';

export const ROLE_NAMES = {
  ADMIN: 'ADMIN',
  MANAGER: 'MANAGER',
  REVIEWER: 'REVIEWER',
  SCHOOL: 'SCHOOL',
  STAFF: 'STAFF',
  STUDENT: 'STUDENT',
} as const satisfies Record<RoleType, RoleType>;

export const PLATFORM_ROLES: RoleType[] = [ROLE_NAMES.ADMIN, ROLE_NAMES.MANAGER, ROLE_NAMES.REVIEWER];
export const SCHOOL_ROLES: RoleType[] = [ROLE_NAMES.SCHOOL, ROLE_NAMES.STAFF, ROLE_NAMES.STUDENT];

/** Roles that may create simulations and manage learning activities. */
export const LEARNING_MANAGER_ROLES = [ROLE_NAMES.STAFF, ROLE_NAMES.MANAGER] as const;

/** Roles that may review shared simulation content. */
export const CONTENT_REVIEW_ROLES = [ROLE_NAMES.REVIEWER, ROLE_NAMES.MANAGER] as const;

export function hasRole<T extends string>(role: string | null | undefined, roles: readonly T[]): role is T {
  return role != null && roles.includes(role as T);
}

export function isManagerRole(role: string | null | undefined): boolean {
  return role === ROLE_NAMES.MANAGER;
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
  ADMIN: 'Quản trị hệ thống',
  MANAGER: 'Vận hành',
  REVIEWER: 'Kiểm duyệt vật lý',
  SCHOOL: 'Quản lý trường',
  STAFF: 'Giáo viên',
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

/** Canonical database IDs; no legacy role aliases are accepted. */
export const ROLE_IDS = { ADMIN: 1, MANAGER: 2, REVIEWER: 3, SCHOOL: 4, STAFF: 5, STUDENT: 6 } as const satisfies Record<RoleType, number>;
export const APPLICATION_ROLES = [ROLE_NAMES.MANAGER, ROLE_NAMES.REVIEWER, ROLE_NAMES.SCHOOL, ROLE_NAMES.STAFF, ROLE_NAMES.STUDENT] as const;
export function isAdminRole(role: string | null | undefined): boolean { return role === ROLE_NAMES.ADMIN; }
export function canViewUsers(role: string | null | undefined): boolean { return isAdminRole(role) || isManagerRole(role); }
export function canEditUser(actor: string | null | undefined, target: string): boolean {
  return (isAdminRole(actor) && target === ROLE_NAMES.MANAGER)
    || (isManagerRole(actor) && target !== ROLE_NAMES.ADMIN)
    || (actor === ROLE_NAMES.SCHOOL && (target === ROLE_NAMES.STAFF || target === ROLE_NAMES.STUDENT));
}
export function assignableRoles(actor: string | null | undefined, currentRole?: string): RoleType[] {
  if (isAdminRole(actor)) return [ROLE_NAMES.MANAGER];
  if (isManagerRole(actor)) return [ROLE_NAMES.REVIEWER, ROLE_NAMES.SCHOOL, ROLE_NAMES.STAFF, ROLE_NAMES.STUDENT,
    ...(currentRole === ROLE_NAMES.MANAGER ? [ROLE_NAMES.MANAGER] : [])];
  if (actor === ROLE_NAMES.SCHOOL) return [ROLE_NAMES.STAFF, ROLE_NAMES.STUDENT];
  return [];
}

export function roleHome(role: string | null | undefined, billingRequired = false): string {
  switch (role) {
    case 'ADMIN': return '/admin';
    case 'MANAGER': return '/manager';
    case 'REVIEWER': return '/reviewer';
    case 'SCHOOL': return billingRequired ? '/school/billing' : '/school';
    case 'STAFF': return '/workspace';
    case 'STUDENT': return '/student';
    default: return '/';
  }
}