import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import LearningIcon from "../../../shared/ui/LearningIcon";
import { Avatar, Badge, Button as ThemeButton, Callout, Dialog, DropdownMenu, IconButton, Select, Table, Text, TextField, type BadgeProps } from "@radix-ui/themes";
import { DotsHorizontalIcon } from "@radix-ui/react-icons";
import { adminSchools, type ManagedSchool } from "../../school/api/schoolApi";
import { adminUsers, createManagedUser, setManagedUserActive, updateManagedUser } from "../api/userApi";
import { useSessionStore } from "../../../shared/auth/sessionStore";
import { isAdminRole, canEditUser, assignableRoles, getRoleLabel } from "../../../shared/auth/roles";
import type { User } from "../../../shared/auth/types";
import { apiMessage } from "../../../shared/lib/apiError";
import { AccountPermissionFields, AccountProfileFields, ResetManagedPasswordDialog } from "./ManagedAccountFields";
import { accountDetails, accountDetailsPayload, blankAccountDetails, missingPermission, type AccountDetails } from "../model/accountDetailsModel";
import { permissionSummary, permissionsForRole } from "../../../shared/auth/permissions";
import SchoolBulkImport from "../../school/dataio/components/SchoolBulkImport";
import { PageHeader, Panel, Loading, ErrorNotice, Button, FormField, Stat } from "../../../shared/ui/ManagementUI";

function roleColor(role: string): NonNullable<BadgeProps["color"]> {
  switch (role) {
    case "ADMIN": return "indigo";
    case "MANAGER": return "violet";
    case "REVIEWER": return "amber";
    case "SCHOOL": return "cyan";
    case "STAFF": return "orange";
    case "STUDENT": return "blue";
    default: return "gray";
  }
}

function UserActionMenu({ item, open, disabled, onToggleMenu, onToggle, onEdit, onResetPassword, onClose }: Readonly<{
  item: User;
  open: boolean;
  disabled: boolean;
  onToggleMenu: () => void;
  onToggle: () => void;
  onEdit: () => void;
  onResetPassword: () => void;
  onClose: () => void;
}>) {
  return <DropdownMenu.Root open={open} onOpenChange={nextOpen => { if (nextOpen) onToggleMenu(); else onClose(); }}>
    <DropdownMenu.Trigger>
      <IconButton type="button" variant="ghost" color="gray" disabled={disabled} aria-label={`Thao tác với ${item.email}`}><DotsHorizontalIcon width="20" height="20" /></IconButton>
    </DropdownMenu.Trigger>
    <DropdownMenu.Content align="end" sideOffset={6} collisionPadding={8}>
      <DropdownMenu.Label>Tài khoản</DropdownMenu.Label>
      <DropdownMenu.Separator />
      <DropdownMenu.Item onSelect={() => { onEdit(); onClose(); }}>Sửa hồ sơ</DropdownMenu.Item>
      <DropdownMenu.Item onSelect={() => { onResetPassword(); onClose(); }}>Đặt lại mật khẩu</DropdownMenu.Item>
      <DropdownMenu.Item color={item.active === false ? "cyan" : "red"} onSelect={() => { onToggle(); onClose(); }}>{item.active === false ? "Kích hoạt lại" : "Tạm khóa"}</DropdownMenu.Item>
    </DropdownMenu.Content>
  </DropdownMenu.Root>;
}

type UserForm = AccountDetails & { email: string; password: string; fullName: string; role: string; institutionId: string };

const blankUser: UserForm = { email: "", password: "", fullName: "", role: "STUDENT", institutionId: "", ...blankAccountDetails };

export function UsersView({ schoolId }: Readonly<{ schoolId?: string }> = {}) {
  const actorRole = useSessionStore(state => state.user?.role);
  const adminOnly = isAdminRole(actorRole);
  const newUser = () => ({ ...blankUser, role: adminOnly ? "MANAGER" : "STUDENT", institutionId: schoolId ?? "" });
  const [availableSchools, setAvailableSchools] = useState<ManagedSchool[]>([]);
  const [items, setItems] = useState<User[]>([]); const [query, setQuery] = useState(""); const [form, setForm] = useState<UserForm>(blankUser); const [editing, setEditing] = useState<number | null>(null); const [showForm, setShowForm] = useState(false); const [loading, setLoading] = useState(true); const [busy, setBusy] = useState(false); const [error, setError] = useState("");
  const [openActions, setOpenActions] = useState<number | null>(null);
  const [resetUser, setResetUser] = useState<User | null>(null);
  const [notice, setNotice] = useState("");
  const busyRef = useRef(false);
  const load = useCallback(async () => { setLoading(true); try { const [users, schools] = await Promise.all([adminUsers(schoolId), (schoolId || adminOnly) ? Promise.resolve([]) : adminSchools()]); setItems(users); setAvailableSchools(schools); setError(""); } catch (e) { setError(apiMessage(e, "Không thể tải danh sách người dùng.")); } finally { setLoading(false); } }, [schoolId, adminOnly]);
  useEffect(() => { void load(); }, [load]);
  const schoolScoped = Boolean(schoolId);
  const scopedItems = useMemo(() => schoolScoped ? items.filter(item => item.role === "STAFF" || item.role === "STUDENT") : items, [items, schoolScoped]);
  const filtered = useMemo(() => { const q = query.trim().toLowerCase(); return scopedItems.filter(item => !q || item.email.toLowerCase().includes(q) || item.fullName.toLowerCase().includes(q)); }, [scopedItems, query]);
  const updateDetails = (details: Partial<AccountDetails>) => setForm(current => ({ ...current, ...details }));
  const submit = async (event: FormEvent) => {
    event.preventDefault(); if (busyRef.current) return;
    if (missingPermission(form, form.role)) { setError("Cần chọn ít nhất một quyền cho tài khoản này."); return; }
    busyRef.current = true; setBusy(true); setError(""); setNotice("");
    try {
      const payload = { fullName: form.fullName.trim(), role: form.role, institutionId: form.institutionId || undefined, ...accountDetailsPayload(form, form.role) };
      if (editing === null) await createManagedUser({ ...payload, email: form.email.trim(), password: form.password }, schoolId);
      else await updateManagedUser(editing, payload, schoolId);
      setShowForm(false); setEditing(null); setForm(newUser()); await load();
      setNotice("Đã lưu tài khoản.");
    } catch (e) { setError(apiMessage(e, "Không thể lưu tài khoản.")); }
    finally { busyRef.current = false; setBusy(false); }
  };
  const edit = (item: User) => { setError(""); setNotice(""); setEditing(item.id); setForm({ email: item.email, password: "", fullName: item.fullName, role: item.role, institutionId: item.schoolId ?? item.institutionId ?? "", ...accountDetails(item) }); setShowForm(true); };
  const toggle = async (item: User) => { if (busyRef.current) return; busyRef.current = true; setBusy(true); setError(""); setNotice(""); try { const updated = await setManagedUserActive(item.id, item.active === false, schoolId); setItems(current => current.map(user => user.id === updated.id ? updated : user)); } catch (e) { setError(apiMessage(e, "Không thể cập nhật trạng thái tài khoản.")); } finally { busyRef.current = false; setBusy(false); } };
  return <div className="admin-content">
    <PageHeader title="Tài khoản" description="" action={<div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>{schoolId && (actorRole === "SCHOOL" || actorRole === "MANAGER") && <SchoolBulkImport schoolId={schoolId} kind="USERS" disabled={busy} onImported={load} />}<Button primary disabled={busy} onClick={() => { setError(""); setNotice(""); setEditing(null); setForm(newUser()); setShowForm(true); }}><LearningIcon name="plus" />Tạo tài khoản</Button></div>} />
    <div className="admin-toolbar"><TextField.Root type="search" size="3" aria-label="Tìm theo email hoặc họ tên" placeholder={schoolScoped ? "Tìm giáo viên, học sinh…" : "Tìm họ tên hoặc email…"} value={query} onChange={event => setQuery(event.target.value)} style={{ width: "min(100%, 420px)" }}><TextField.Slot><LearningIcon name="search" /></TextField.Slot></TextField.Root><IconButton type="button" variant="surface" size="3" aria-label="Làm mới danh sách tài khoản" onClick={() => void load()} disabled={loading}><LearningIcon name="refresh" /></IconButton></div>
    {error && <ErrorNotice error={error} onRetry={() => void load()} />}
    {notice && <Callout.Root color="green" role="status"><Callout.Text>{notice}</Callout.Text></Callout.Root>}
    <div className="admin-stats-grid">
      <Stat label="Tổng tài khoản" value={scopedItems.length} icon="users" tone="blue" />
      <Stat label="Giáo viên" value={scopedItems.filter(item => item.role === "STAFF").length} icon="book" tone="orange" />
      <Stat label="Học sinh" value={scopedItems.filter(item => item.role === "STUDENT").length} icon="users" tone="green" />
      <Stat label="Tạm khóa" value={scopedItems.filter(item => item.active === false).length} icon="close" tone="red" />
    </div>
    <Dialog.Root open={showForm} onOpenChange={open => { if (!busyRef.current) setShowForm(open); }}><Dialog.Content maxWidth="620px" aria-describedby={undefined} onEscapeKeyDown={event => { if (busyRef.current) event.preventDefault(); }} onInteractOutside={event => { if (busyRef.current) event.preventDefault(); }}>
      <Dialog.Title>{editing === null ? "Tạo tài khoản" : "Cập nhật tài khoản"}</Dialog.Title>
      {error && <Callout.Root color="red" size="1" mb="4" role="alert"><Callout.Text>{error}</Callout.Text></Callout.Root>}
      <form className="admin-form-grid" onSubmit={submit}>
        <FormField label="Email" htmlFor="managed-user-email"><TextField.Root id="managed-user-email" name="email" size="3" type="email" value={form.email} disabled={busy || editing !== null} required onChange={event => setForm({ ...form, email: event.target.value })} /></FormField>
        <FormField label="Họ và tên" htmlFor="managed-user-name"><TextField.Root id="managed-user-name" name="fullName" size="3" value={form.fullName} disabled={busy} maxLength={120} required onChange={event => setForm({ ...form, fullName: event.target.value })} /></FormField>
        <AccountProfileFields idPrefix="managed-user" value={form} name={form.fullName} disabled={busy} onChange={updateDetails} />
        <FormField label="Vai trò" htmlFor="managed-user-role"><Select.Root size="3" name="role" value={form.role} disabled={busy} onValueChange={role => setForm({ ...form, role, institutionId: ["ADMIN", "MANAGER", "REVIEWER"].includes(role) ? "" : form.institutionId })}><Select.Trigger id="managed-user-role" /><Select.Content>{assignableRoles(actorRole, items.find(item => item.id === editing)?.role).map(role => <Select.Item key={role} value={role}>{getRoleLabel(role)}</Select.Item>)}</Select.Content></Select.Root></FormField>
        <AccountPermissionFields idPrefix="managed-user" role={form.role} value={form} disabled={busy} onChange={updateDetails} />
        {!schoolId && ["STAFF", "STUDENT", "SCHOOL"].includes(form.role) && <FormField label="Trường" htmlFor="managed-user-school"><Select.Root size="3" name="institutionId" required value={form.institutionId} onValueChange={institutionId => setForm({ ...form, institutionId })}><Select.Trigger id="managed-user-school" placeholder="Chọn trường" /><Select.Content>{availableSchools.filter(item => item.active).map(item => <Select.Item key={item.id} value={item.id}>{item.name}</Select.Item>)}</Select.Content></Select.Root></FormField>}
        {editing === null && <FormField label="Mật khẩu ban đầu" htmlFor="managed-user-password"><TextField.Root id="managed-user-password" name="password" size="3" type="password" autoComplete="new-password" minLength={8} maxLength={120} disabled={busy} required value={form.password} onChange={event => setForm({ ...form, password: event.target.value })} /></FormField>}
        {editing === null && <Text size="2" color="gray" style={{ gridColumn: "1 / -1" }}>Tài khoản phải đổi mật khẩu khi đăng nhập lần đầu.</Text>}
        <div className="admin-form-actions"><Button disabled={busy} onClick={() => setShowForm(false)}>Hủy</Button><ThemeButton type="submit" size="3" disabled={busy || missingPermission(form, form.role)} loading={busy}>{busy ? "Đang lưu…" : "Lưu tài khoản"}</ThemeButton></div>
      </form>
    </Dialog.Content></Dialog.Root>
    <Panel title={`Danh sách tài khoản · ${filtered.length}`} className="admin-users-panel">{loading ? <Loading text="Đang tải danh sách tài khoản…" /> : <Table.Root className="admin-user-table" size="2" variant="ghost"><Table.Header><Table.Row><Table.ColumnHeaderCell>Họ tên / Email</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Vai trò</Table.ColumnHeaderCell>{!schoolScoped && <Table.ColumnHeaderCell>Trường</Table.ColumnHeaderCell>}<Table.ColumnHeaderCell>Đăng nhập gần nhất</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Trạng thái</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Thao tác</Table.ColumnHeaderCell></Table.Row></Table.Header><Table.Body>{filtered.length === 0 ? <Table.Row><Table.Cell colSpan={schoolScoped ? 5 : 6} className="admin-empty-table">Không tìm thấy tài khoản phù hợp.</Table.Cell></Table.Row> : filtered.map(item => <Table.Row key={item.id}>
      <Table.Cell><div className="admin-table-user"><Avatar size="3" radius="full" src={item.avatarUrl || undefined} color={roleColor(item.role)} fallback={(item.fullName || item.email).slice(0, 1).toUpperCase()} /><span><Text as="div" size="3" weight="medium">{item.fullName || "Chưa có họ tên"}</Text><Text as="div" color="gray" size="2">{item.email}</Text>{item.dateOfBirth && <Text as="div" color="gray" size="1">{new Date(`${item.dateOfBirth}T00:00:00`).toLocaleDateString("vi-VN")}</Text>}</span></div></Table.Cell>
      <Table.Cell><div style={{ display: "grid", gap: 6, justifyItems: "start" }}><Badge color={roleColor(item.role)} size="2" title={item.role}>{getRoleLabel(item.role)}</Badge>{permissionsForRole(item.role).length > 0 && <Text size="1" color="gray">{permissionSummary(item)}</Text>}</div></Table.Cell>
      {!schoolScoped && <Table.Cell>{availableSchools.find(school => school.id === item.institutionId)?.name || "—"}</Table.Cell>}
      <Table.Cell><Text size="2" color="gray">{item.lastLogin ? new Date(item.lastLogin).toLocaleString("vi-VN") : "Chưa đăng nhập"}</Text></Table.Cell>
      <Table.Cell><div style={{ display: "grid", gap: 6, justifyItems: "start" }}><Badge color={item.active === false ? "amber" : "cyan"} size="2">{item.active === false ? "Tạm khóa" : "Hoạt động"}</Badge>{item.mustChangePassword && <Text size="1" color="gray">Cần đổi mật khẩu</Text>}</div></Table.Cell>
      <Table.Cell>{canEditUser(actorRole, item.role) && <UserActionMenu item={item} open={openActions === item.id} disabled={busy} onToggleMenu={() => setOpenActions(openActions === item.id ? null : item.id)} onToggle={() => void toggle(item)} onEdit={() => edit(item)} onResetPassword={() => setResetUser(item)} onClose={() => setOpenActions(null)} />}</Table.Cell>
    </Table.Row>)}</Table.Body></Table.Root>}</Panel>
    <ResetManagedPasswordDialog user={resetUser} schoolId={schoolId} onClose={() => setResetUser(null)} onUpdated={updated => { setItems(current => current.map(user => user.id === updated.id ? updated : user)); setNotice(`Đã đặt lại mật khẩu cho ${updated.email}.`); }} />
  </div>;
}
