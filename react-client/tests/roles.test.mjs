import test from 'node:test';
import assert from 'node:assert/strict';
import { roleHome, ROLE_NAMES, ROLE_IDS, APPLICATION_ROLES, canManageLearning, canReviewContent,
  canViewUsers, canEditUser, assignableRoles, isPlatformRole, isSchoolRole } from '../src/shared/auth/roles.ts';

test('canonical roles and IDs match the database contract', () => {
  assert.deepEqual(ROLE_IDS, { ADMIN: 1, MANAGER: 2, REVIEWER: 3, SCHOOL: 4, STAFF: 5, STUDENT: 6 });
  assert.deepEqual(Object.keys(ROLE_NAMES), Object.keys(ROLE_IDS));
  for (const role of Object.keys(ROLE_NAMES)) assert.notEqual(isPlatformRole(role), isSchoolRole(role));
  for (const role of ['TEACHER', 'SCHOOL_MANAGER', 'CONTENT_REVIEWER']) {
    assert.equal(isPlatformRole(role), false);
    assert.equal(isSchoolRole(role), false);
    assert.equal(canManageLearning(role), false);
    assert.equal(canReviewContent(role), false);
    assert.deepEqual(assignableRoles(role), []);
  }
});

test('restricted admin views users and creates managers without operational permissions', () => {
  assert.equal(canViewUsers('ADMIN'), true);
  assert.deepEqual(assignableRoles('ADMIN'), ['MANAGER']);
  assert.equal(canManageLearning('ADMIN'), false);
  assert.equal(canReviewContent('ADMIN'), false);
  assert.equal(APPLICATION_ROLES.includes('ADMIN'), false);
  for (const role of Object.keys(ROLE_NAMES)) assert.equal(canEditUser('ADMIN', role), role === 'MANAGER');
});

test('manager inherits platform operations but cannot create admin or promote a user to manager', () => {
  assert.equal(canViewUsers('MANAGER'), true);
  assert.equal(canManageLearning('MANAGER'), true);
  assert.equal(canReviewContent('MANAGER'), true);
  assert.equal(canEditUser('MANAGER', 'ADMIN'), false);
  assert.equal(assignableRoles('MANAGER').includes('MANAGER'), false);
  assert.equal(assignableRoles('MANAGER').includes('ADMIN'), false);
  assert.equal(assignableRoles('MANAGER', 'MANAGER').includes('MANAGER'), true);
});

test('school manages staff and students; staff teaches; reviewer reviews', () => {
  assert.deepEqual(assignableRoles('SCHOOL'), ['STAFF', 'STUDENT']);
  assert.equal(canEditUser('SCHOOL', 'MANAGER'), false);
  assert.equal(canEditUser('SCHOOL', 'STAFF'), true);
  assert.equal(canManageLearning('STAFF'), true);
  assert.equal(canManageLearning('STUDENT'), false);
  assert.equal(canReviewContent('REVIEWER'), true);
  assert.equal(canReviewContent('STAFF'), false);
});

test('each role lands in its own workspace', () => {
  assert.equal(roleHome('ADMIN'), '/admin/users');
  assert.equal(roleHome('MANAGER'), '/manager');
  assert.equal(roleHome('REVIEWER'), '/reviewer');
  assert.equal(roleHome('SCHOOL'), '/school');
  assert.equal(roleHome('SCHOOL', true), '/school/billing');
  assert.equal(roleHome('STAFF'), '/workspace');
  assert.equal(roleHome('STUDENT'), '/assignments');
  assert.equal(roleHome(null), '/');
});

test('permissions are granted per account and belong to one role', async () => {
  const { ROLE_PERMISSIONS, DEFAULT_PERMISSIONS, grantedPermissions, hasPermission, permissionSummary, canTeach, isDepartmentHead, userHome } =
    await import('../src/shared/auth/permissions.ts');
  assert.deepEqual(ROLE_PERMISSIONS, { STAFF: ['TEACH', 'DEPARTMENT_HEAD_PHYSICS'], REVIEWER: ['CONTENT_EDIT', 'CONTENT_REVIEW'] });
  assert.deepEqual(DEFAULT_PERMISSIONS, { STAFF: ['TEACH'], REVIEWER: ['CONTENT_EDIT', 'CONTENT_REVIEW'] });

  // A staff account can be a teacher, a department head, or both.
  const both = { role: 'STAFF', permissions: ['DEPARTMENT_HEAD_PHYSICS', 'TEACH'] };
  assert.deepEqual(grantedPermissions(both), ['TEACH', 'DEPARTMENT_HEAD_PHYSICS']);
  assert.equal(canTeach(both), true);
  assert.equal(isDepartmentHead(both), true);
  assert.equal(permissionSummary(both), 'Giáo viên · Tổ trưởng bộ môn Vật Lý');
  assert.equal(userHome(both), '/workspace');
  const headOnly = { role: 'STAFF', permissions: ['DEPARTMENT_HEAD_PHYSICS'] };
  assert.equal(canTeach(headOnly), false);
  assert.equal(userHome(headOnly), '/department');
  assert.equal(isDepartmentHead({ role: 'STAFF', permissions: ['TEACH'] }), false);

  // Reviewer permissions are independent.
  assert.equal(hasPermission({ role: 'REVIEWER', permissions: ['CONTENT_EDIT'] }, 'CONTENT_REVIEW'), false);
  assert.equal(hasPermission({ role: 'REVIEWER', permissions: ['CONTENT_EDIT'] }, 'CONTENT_EDIT'), true);

  // A permission of another role, a missing list or a missing account grants nothing.
  assert.equal(hasPermission({ role: 'STUDENT', permissions: ['TEACH'] }, 'TEACH'), false);
  assert.equal(hasPermission({ role: 'STAFF', permissions: ['CONTENT_REVIEW'] }, 'CONTENT_REVIEW'), false);
  assert.deepEqual(grantedPermissions({ role: 'STAFF' }), []);
  assert.equal(permissionSummary({ role: 'REVIEWER', permissions: [] }), 'Chưa cấp quyền');
  assert.equal(canTeach(null), false);
  assert.equal(canTeach({ role: 'MANAGER' }), true);
  assert.equal(userHome(null), '/');
  assert.equal(userHome({ role: 'SCHOOL', billingRequired: true }), '/school/billing');
});
