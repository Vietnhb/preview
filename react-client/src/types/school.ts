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

export interface SchoolBilling {
  planCode: string | null;
  nextPlanCode: string | null;
  licenseStart: string | null;
  licenseEnd: string | null;
  studentQuota: number | null;
  studentsUsed: number;
  monthlyTokenQuota: number | null;
  tokensUsed: number;
  payments: { id: string; planCode: string; purpose: string; amountVnd: number; status: string; createdAt: string; paidAt: string | null }[];
}
export interface PlanQuote {
  planCode: string;
  purpose: "UPGRADE" | "RENEWAL";
  amountVnd: number;
  licenseStart: string;
  licenseEnd: string;
}

export interface SchoolClassSummary {
  id: string;
  name: string;
  gradeLevel: number;
  schoolYear: string;
  subject: string | null;
  active: boolean;
  teacherCount: number;
  studentCount: number;
}
export interface SchoolClassDetail extends SchoolClassSummary {
  teachers: { id: number; fullName: string; email: string }[];
  students: { id: number; fullName: string; email: string }[];
}
export interface SchoolClassRequest { name: string; gradeLevel: number; schoolYear: string; subject: string; }
