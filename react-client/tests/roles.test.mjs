import test from 'node:test';
import assert from 'node:assert/strict';
import { roleHome, ROLE_NAMES, ROLE_IDS, APPLICATION_ROLES, canManageLearning, canReviewContent,
  canViewUsers, canEditUser, assignableRoles, isPlatformRole, isSchoolRole } from '../src/types/roles.ts';

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
