/**
 * Account permissions (users N-N permissions through user_permissions).
 *
 * The role is the account type; permissions say what an individual STAFF or REVIEWER account may do:
 * - STAFF: TEACH and/or DEPARTMENT_HEAD_PHYSICS, granted by the school.
 * - REVIEWER: CONTENT_EDIT and/or CONTENT_REVIEW, granted by a manager.
 * An account of either role holds at least one permission; other roles hold none.
 */
import { roleHome } from './roles.ts';

export type PermissionCode = 'TEACH' | 'DEPARTMENT_HEAD_PHYSICS' | 'CONTENT_EDIT' | 'CONTENT_REVIEW';

type PermissionHolder = { role: string; permissions?: readonly string[] | null; billingRequired?: boolean };

export const PERMISSION_LABELS: Record<PermissionCode, string> = {
  TEACH: 'Giáo viên',
  DEPARTMENT_HEAD_PHYSICS: 'Tổ trưởng bộ môn Vật Lý',
  CONTENT_EDIT: 'Biên soạn',
  CONTENT_REVIEW: 'Kiểm duyệt',
};

export const PERMISSION_HINTS: Record<PermissionCode, string> = {
  TEACH: 'Tạo mô phỏng, quản lý thư viện cá nhân, giao bài và chấm bài cho lớp được phân công',
  DEPARTMENT_HEAD_PHYSICS: 'Phân công giáo viên và học sinh, xem bài giao toàn trường, duyệt nội dung chia sẻ nội bộ',
  CONTENT_EDIT: 'Chỉnh sửa ngữ cảnh và dữ liệu chuẩn',
  CONTENT_REVIEW: 'Kiểm duyệt nội dung cộng đồng',
};

/** Catalog order matches the database. */
export const ROLE_PERMISSIONS: Record<string, readonly PermissionCode[]> = {
  STAFF: ['TEACH', 'DEPARTMENT_HEAD_PHYSICS'],
  REVIEWER: ['CONTENT_EDIT', 'CONTENT_REVIEW'],
};

/** What a new account receives when the grantor does not choose. */
export const DEFAULT_PERMISSIONS: Record<string, readonly PermissionCode[]> = {
  STAFF: ['TEACH'],
  REVIEWER: ['CONTENT_EDIT', 'CONTENT_REVIEW'],
};

export function permissionsForRole(role: string | null | undefined): readonly PermissionCode[] {
  return (role && ROLE_PERMISSIONS[role]) || [];
}

/** The account's permissions that are valid for its role, in catalog order. */
export function grantedPermissions(user: PermissionHolder | null | undefined): PermissionCode[] {
  if (!user) return [];
  return permissionsForRole(user.role).filter(code => user.permissions?.includes(code) === true);
}

export function hasPermission(user: PermissionHolder | null | undefined, code: PermissionCode): boolean {
  return grantedPermissions(user).includes(code);
}

export function permissionSummary(user: PermissionHolder | null | undefined): string {
  return grantedPermissions(user).map(code => PERMISSION_LABELS[code]).join(' · ') || 'Chưa cấp quyền';
}

/** Simulations, personal library, class assignments and grading. */
export function canTeach(user: PermissionHolder | null | undefined): boolean {
  return user?.role === 'MANAGER' || hasPermission(user, 'TEACH');
}

export function isDepartmentHead(user: PermissionHolder | null | undefined): boolean {
  return hasPermission(user, 'DEPARTMENT_HEAD_PHYSICS');
}

/** Landing page of an account; a STAFF account without TEACH works in the department workspace. */
export function userHome(user: PermissionHolder | null | undefined): string {
  if (!user) return '/';
  if (user.role === 'STAFF' && !canTeach(user)) return isDepartmentHead(user) ? '/department' : '/profile';
  return roleHome(user.role, user.billingRequired === true);
}
