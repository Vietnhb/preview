/**
 * School (B2B Customer) Types
 */

export interface School {
  id: string; // UUID
  name: string;
  shortName?: string | null;
  address?: string | null;
  provinceCity?: string | null;
  phoneNumber?: string | null;
  contactEmail?: string | null;
  
  // License management
  licenseStart: string | null; // ISO date
  licenseEnd: string | null; // ISO date
  active: boolean;
  
  // AI Quota
  monthlyTokenQuota: number | null;
  usedTokens: number;
  
  // Timestamps
  createdAt: string;
  updatedAt: string;
}

export interface SchoolCreateRequest {
  name: string;
  shortName?: string;
  address?: string;
  provinceCity?: string;
  phoneNumber?: string;
  contactEmail?: string;
  licenseStart: string; // ISO date
  licenseEnd: string; // ISO date
  monthlyTokenQuota?: number;
}

export interface SchoolUpdateRequest {
  name?: string;
  shortName?: string;
  address?: string;
  provinceCity?: string;
  phoneNumber?: string;
  contactEmail?: string;
  licenseStart?: string;
  licenseEnd?: string;
  monthlyTokenQuota?: number;
}

/**
 * School Class Types
 */
export interface SchoolClass {
  id: string; // UUID
  schoolId: string; // UUID
  name: string;
  gradeLevel: 10 | 11 | 12;
  schoolYear: string; // "2024-2025"
  subject?: string | null; // "Lý", "Hóa", etc.
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface SchoolClassCreateRequest {
  name: string;
  gradeLevel: 10 | 11 | 12;
  schoolYear: string;
  subject?: string;
  studentIds?: number[]; // Initial student enrollment
}

/**
 * Class Enrollment Types
 */
export type EnrollmentStatus = 'ACTIVE' | 'TRANSFERRED' | 'DROPPED' | 'COMPLETED';

export interface ClassEnrollment {
  id: string; // UUID
  classId: string;
  studentId: number;
  schoolYear: string;
  status: EnrollmentStatus;
  enrolledAt: string;
  updatedAt: string;
}

/**
 * Teacher Assignment Types
 */
export interface ClassTeacherAssignment {
  id: string; // UUID
  classId: string;
  teacherId: number;
  isActive: boolean;
  assignedAt: string;
}

/**
 * School Statistics (for SCHOOL_MANAGER dashboard)
 */
export interface SchoolStats {
  totalStudents: number;
  totalTeachers: number;
  totalClasses: number;
  activeSimulations: number;
  quotaUsed: number;
  quotaTotal: number;
  licenseExpiresIn: number; // days
}
