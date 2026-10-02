import type { User } from "../../../shared/auth/types";
import { DEFAULT_PERMISSIONS, grantedPermissions, permissionsForRole, type PermissionCode } from "../../../shared/auth/permissions";

export type AccountDetails = {
  dateOfBirth: string;
  avatarUrl: string;
  /** Selection for every role that has permissions; only those of the chosen role are sent. */
  permissions: PermissionCode[];
};

const allDefaults = (): PermissionCode[] => Object.values(DEFAULT_PERMISSIONS).flat();

export const blankAccountDetails: AccountDetails = { dateOfBirth: "", avatarUrl: "", permissions: allDefaults() };

export function accountDetails(user: User): AccountDetails {
  // Keep the account's own grants and offer the defaults of the other role in case the role is changed.
  const own = grantedPermissions(user);
  const others = allDefaults().filter(code => !permissionsForRole(user.role).includes(code));
  return { dateOfBirth: user.dateOfBirth ?? "", avatarUrl: user.avatarUrl ?? "", permissions: [...own, ...others] };
}

/** Permissions selected for the given role, in catalog order. */
export function selectedPermissions(details: AccountDetails, role: string): PermissionCode[] {
  return permissionsForRole(role).filter(code => details.permissions.includes(code));
}

/** True when the role needs a permission and none is selected. */
export function missingPermission(details: AccountDetails, role: string): boolean {
  return permissionsForRole(role).length > 0 && selectedPermissions(details, role).length === 0;
}

export function accountDetailsPayload(details: AccountDetails, role: string) {
  return {
    dateOfBirth: details.dateOfBirth || undefined,
    avatarUrl: details.avatarUrl.trim(),
    ...(permissionsForRole(role).length > 0 ? { permissions: selectedPermissions(details, role) } : {}),
  };
}
