/**
 * School (B2B customer) contracts shared by billing and school pages.
 */

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
  planChoices: { planCode: string; allowed: boolean; purpose: "UPGRADE" | "RENEWAL" | null; reason: string | null }[];
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