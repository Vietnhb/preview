/**
 * Central type exports for PhysLive B2B system
 */

// Auth & User
export type { User, LoginResponse } from './auth';
export type { User as UserProfile } from './user';

// Roles
export type { RoleType } from './roles';
export { 
  PLATFORM_ROLES, 
  SCHOOL_ROLES, 
  ROLE_LABELS,
  isPlatformRole,
  isSchoolRole,
  getRoleLabel 
} from './roles';

// School & Classes
export type {
  School,
  SchoolCreateRequest,
  SchoolUpdateRequest,
  SchoolClass,
  SchoolClassCreateRequest,
  EnrollmentStatus,
  ClassEnrollment,
  ClassTeacherAssignment,
  SchoolStats
} from './school';

// Physics simulation types (from physlive.ts)
export type * from './physlive';
