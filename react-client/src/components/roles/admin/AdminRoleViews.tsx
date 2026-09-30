import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent, type ReactNode } from "react";
import LearningIcon from "../../common/LearningIcon";
import { Avatar, Badge, Button as ThemeButton, Callout, Card, Checkbox, Dialog, DropdownMenu, Heading, IconButton, Progress, Select, Spinner, Table, Text, TextField, type BadgeProps } from "@radix-ui/themes";
import { DotsHorizontalIcon } from "@radix-ui/react-icons";
import { motion, useReducedMotion } from "motion/react";
import {
  adminCurriculum, adminSchools, adminUsers, createCurriculumNode, createManagedUser,
  createSchool, setManagedUserActive, toggleCurriculum, updateManagedUser, updateSchool,
  validationMetrics, validationRuns, adminPlans, saveManagedPlan, type ManagedPlan, type CurriculumTree, type ManagedSchool, type ValidationRun,
} from "../../../api/adminApi";
import { usePhysliveStore } from "../../../store/usePhysliveStore";
import { isAdminRole, canEditUser, assignableRoles, getRoleLabel } from "../../../types/roles";
import type { User } from "../../../types/physlive";
import { apiMessage, curriculumParentOptions, curriculumPath } from "./adminUtils";
import { AccountPermissionFields, AccountProfileFields, ResetManagedPasswordDialog } from "./ManagedAccountFields";
import { accountDetails, accountDetailsPayload, blankAccountDetails, type AccountDetails } from "./accountDetailsModel";
import SchoolBulkImport from "../../../features/school-import/SchoolBulkImport";

type IconName = "grid" | "users" | "book" | "activity" | "shield" | "refresh" | "plus" | "check" | "search" | "close";
import type { CurriculumKind } from "./adminUtils";

function PageHeader({ title, action }: Readonly<{ title: string; description: string; action?: ReactNode }>) {
  return <header className="admin-content-header"><div><Heading as="h1" size="7" className="admin-content-title">{title}</Heading></div>{action}</header>;
}

function Panel({ title, description, children, action, className }: Readonly<{ title: string; description?: string; children: ReactNode; action?: ReactNode; className?: string }>) {
  return <Card asChild size="3" className={`admin-panel ${className ?? ""}`}><section><div className="admin-panel-heading"><div><Heading as="h2" size="4">{title}</Heading>{description && <Text as="p" color="gray" size="2" className="admin-panel-description">{description}</Text>}</div>{action}</div>{children}</section></Card>;
}

function Loading({ text = "Đang tải dữ liệu…" }: Readonly<{ text?: string }>) { return <div className="admin-loading compact" role="status"><Spinner size="3" /><Text as="p" color="gray">{text}</Text></div>; }
function ErrorNotice({ error, onRetry }: Readonly<{ error: string; onRetry?: () => void }>) { return <Callout.Root color="red" className="admin-error-banner" role="alert"><Callout.Text>{error}</Callout.Text>{onRetry && <ThemeButton type="button" variant="ghost" color="red" onClick={onRetry}>Thử lại</ThemeButton>}</Callout.Root>; }
function Button({ children, onClick, primary = false, disabled = false, type = "button" }: Readonly<{ children: ReactNode; onClick?: () => void; primary?: boolean; disabled?: boolean; type?: "button" | "submit" }>) { return <ThemeButton type={type} variant={primary ? "solid" : "surface"} size="2" onClick={onClick} disabled={disabled}>{children}</ThemeButton>; }
function FormField({ label, htmlFor, children }: Readonly<{ label: string; htmlFor: string; children: ReactNode }>) { return <div style={{ display: "grid", gap: 8 }}><Text as="label" size="2" weight="medium" htmlFor={htmlFor}>{label}</Text>{children}</div>; }

export function OverviewView() {
  const [users, setUsers] = useState<User[]>([]);
  const [metrics, setMetrics] = useState<{ total: number; failed: number; failureRate: number } | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const load = async () => { setLoading(true); try { const [nextUsers, nextMetrics] = await Promise.all([adminUsers(), validationMetrics()]); setUsers(nextUsers); setMetrics(nextMetrics); setError(""); } catch (e) { setError(apiMessage(e, "Không thể tải tổng quan.")); } finally { setLoading(false); } };
  useEffect(() => { void load(); }, []);
  const passed = metrics ? metrics.total - metrics.failed : 0;
  const rate = metrics?.total ? (passed / metrics.total) * 100 : 0;
  if (loading) return <div className="admin-content"><Loading /></div>;
  return <div className="admin-content"><PageHeader title="Tổng quan" description="Theo dõi người dùng và chất lượng mô phỏng trong PhysLive." action={<Button onClick={() => void load()}><LearningIcon name="refresh" /> Làm mới</Button>} />{error && <ErrorNotice error={error} onRetry={() => void load()} />}
    <div className="admin-stats-grid"><Stat label="Tổng người dùng" value={users.length} icon="users" tone="blue" /><Stat label="Đang hoạt động" value={users.filter(item => item.active !== false).length} icon="activity" tone="green" /><Stat label="Giáo viên" value={users.filter(item => item.role === "STAFF").length} icon="book" tone="orange" /><Stat label="Kiểm định đạt" value={`${rate.toFixed(1)}%`} icon="shield" tone="purple" /></div>
    <div className="admin-dashboard-grid"><Panel title="Kết quả kiểm định"><Heading as="h3" size="8" color="indigo" className="admin-health-value">{rate.toFixed(1)}%</Heading><Progress value={Math.max(0, Math.min(100, rate))} color="indigo" aria-label="Tỷ lệ kiểm định đạt" /><div className="admin-health-meta"><Badge color="cyan" size="2">{passed} lượt đạt</Badge><Badge color="amber" size="2">{metrics?.failed ?? 0} lượt lỗi</Badge></div></Panel><Panel title="Phân bổ tài khoản"><div className="admin-breakdown-list"><div><Text>Giáo viên</Text><Badge color="amber" size="2">{users.filter(item => item.role === "STAFF").length}</Badge></div><div><Text>Học sinh</Text><Badge color="cyan" size="2">{users.filter(item => item.role === "STUDENT").length}</Badge></div><div><Text>Người kiểm duyệt</Text><Badge color="indigo" size="2">{users.filter(item => item.role === "REVIEWER").length}</Badge></div></div></Panel></div>
  </div>;
}

function Stat({ label, value, icon, tone, note }: Readonly<{ label: string; value: string | number; icon: IconName; tone: string; note?: string }>) {
  const reducedMotion = useReducedMotion();
  return <Card asChild size="3" className="admin-stat-card"><motion.article initial={reducedMotion ? false : { opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} whileHover={reducedMotion ? undefined : { y: -3 }} transition={{ duration: .18 }}><div className={`admin-stat-icon ${tone}`}><LearningIcon name={icon} /></div><Text as="p" color="gray" size="2">{label}</Text><Heading as="h2" size="8">{value}</Heading>{note && <Text as="p" color="gray" size="1">{note}</Text>}</motion.article></Card>;
}

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
  const actorRole = usePhysliveStore(state => state.user?.role);
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
    if (form.role === "REVIEWER" && !form.reviewerCanEdit && !form.reviewerCanReview) { setError("Reviewer cần ít nhất một quyền."); return; }
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
        <div className="admin-form-actions"><Button disabled={busy} onClick={() => setShowForm(false)}>Hủy</Button><ThemeButton type="submit" size="3" disabled={busy || (form.role === "REVIEWER" && !form.reviewerCanEdit && !form.reviewerCanReview)} loading={busy}>{busy ? "Đang lưu…" : "Lưu tài khoản"}</ThemeButton></div>
      </form>
    </Dialog.Content></Dialog.Root>
    <Panel title={`Danh sách tài khoản · ${filtered.length}`} className="admin-users-panel">{loading ? <Loading text="Đang tải danh sách tài khoản…" /> : <Table.Root className="admin-user-table" size="2" variant="ghost"><Table.Header><Table.Row><Table.ColumnHeaderCell>Họ tên / Email</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Vai trò</Table.ColumnHeaderCell>{!schoolScoped && <Table.ColumnHeaderCell>Trường</Table.ColumnHeaderCell>}<Table.ColumnHeaderCell>Đăng nhập gần nhất</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Trạng thái</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Thao tác</Table.ColumnHeaderCell></Table.Row></Table.Header><Table.Body>{filtered.length === 0 ? <Table.Row><Table.Cell colSpan={schoolScoped ? 5 : 6} className="admin-empty-table">Không tìm thấy tài khoản phù hợp.</Table.Cell></Table.Row> : filtered.map(item => <Table.Row key={item.id}>
      <Table.Cell><div className="admin-table-user"><Avatar size="3" radius="full" src={item.avatarUrl || undefined} color={roleColor(item.role)} fallback={(item.fullName || item.email).slice(0, 1).toUpperCase()} /><span><Text as="div" size="3" weight="medium">{item.fullName || "Chưa có họ tên"}</Text><Text as="div" color="gray" size="2">{item.email}</Text>{item.dateOfBirth && <Text as="div" color="gray" size="1">{new Date(`${item.dateOfBirth}T00:00:00`).toLocaleDateString("vi-VN")}</Text>}</span></div></Table.Cell>
      <Table.Cell><div style={{ display: "grid", gap: 6, justifyItems: "start" }}><Badge color={roleColor(item.role)} size="2" title={getRoleLabel(item.role)}>{item.role}</Badge>{item.role === "STAFF" && <Text size="1" color="gray">{item.staffType === "DEPARTMENT_HEAD" ? "Trưởng bộ môn" : "Giáo viên"}</Text>}{item.role === "REVIEWER" && <Text size="1" color="gray">{[item.reviewerCanEdit && "Chỉnh sửa", item.reviewerCanReview && "Kiểm duyệt"].filter(Boolean).join(" · ") || "Chưa cấp quyền"}</Text>}</div></Table.Cell>
      {!schoolScoped && <Table.Cell>{availableSchools.find(school => school.id === item.institutionId)?.name || "—"}</Table.Cell>}
      <Table.Cell><Text size="2" color="gray">{item.lastLogin ? new Date(item.lastLogin).toLocaleString("vi-VN") : "Chưa đăng nhập"}</Text></Table.Cell>
      <Table.Cell><div style={{ display: "grid", gap: 6, justifyItems: "start" }}><Badge color={item.active === false ? "amber" : "cyan"} size="2">{item.active === false ? "Tạm khóa" : "Hoạt động"}</Badge>{item.mustChangePassword && <Text size="1" color="gray">Cần đổi mật khẩu</Text>}</div></Table.Cell>
      <Table.Cell>{canEditUser(actorRole, item.role) && <UserActionMenu item={item} open={openActions === item.id} disabled={busy} onToggleMenu={() => setOpenActions(openActions === item.id ? null : item.id)} onToggle={() => void toggle(item)} onEdit={() => edit(item)} onResetPassword={() => setResetUser(item)} onClose={() => setOpenActions(null)} />}</Table.Cell>
    </Table.Row>)}</Table.Body></Table.Root>}</Panel>
    <ResetManagedPasswordDialog user={resetUser} schoolId={schoolId} onClose={() => setResetUser(null)} onUpdated={updated => { setItems(current => current.map(user => user.id === updated.id ? updated : user)); setNotice(`Đã đặt lại mật khẩu cho ${updated.email}.`); }} />
  </div>;
}
export function SchoolManagerView({ schoolId }: Readonly<{ schoolId: string }>) {
  return <UsersView schoolId={schoolId} />;
}

type SchoolForm = { code: string; name: string; address: string; active: boolean; licenseStart: string; licenseEnd: string; monthlyTokenQuota: string };
const blankSchool: SchoolForm = { code: "", name: "", address: "", active: true, licenseStart: "", licenseEnd: "", monthlyTokenQuota: "0" };

export function SchoolsView() {
  const [items, setItems] = useState<ManagedSchool[]>([]); const [form, setForm] = useState<SchoolForm>(blankSchool); const [editing, setEditing] = useState<string | null>(null); const [showForm, setShowForm] = useState(false); const [loading, setLoading] = useState(true); const [busy, setBusy] = useState(false); const [error, setError] = useState("");
  const load = async () => { setLoading(true); try { setItems(await adminSchools()); setError(""); } catch (e) { setError(apiMessage(e, "Không thể tải danh sách trường học.")); } finally { setLoading(false); } }; useEffect(() => { void load(); }, []);
  const submit = async (event: FormEvent) => { event.preventDefault(); setBusy(true); try { const payload = { ...form, licenseStart: form.licenseStart || null, licenseEnd: form.licenseEnd || null, monthlyTokenQuota: form.monthlyTokenQuota === "" ? null : Number(form.monthlyTokenQuota) }; if (editing) { await updateSchool(editing, payload); } else { await createSchool(payload); } setShowForm(false); setEditing(null); setForm(blankSchool); await load(); } catch (e) { setError(apiMessage(e, "Không thể lưu trường học.")); } finally { setBusy(false); } };
  return <div className="admin-content">
    <PageHeader title="Trường học" description="" action={<Button primary onClick={() => { setEditing(null); setForm(blankSchool); setShowForm(true); }}><LearningIcon name="plus" />Thêm trường</Button>} />
    {error && <ErrorNotice error={error} onRetry={() => void load()} />}
    <Dialog.Root open={showForm} onOpenChange={setShowForm}><Dialog.Content maxWidth="620px" aria-describedby={undefined}><Dialog.Title>{editing ? "Cập nhật trường" : "Thêm trường"}</Dialog.Title>
      {error && <Callout.Root color="red" size="1" mb="4" role="alert"><Callout.Text>{error}</Callout.Text></Callout.Root>}
      <form className="admin-form-grid" onSubmit={submit}>
        <FormField label="Mã trường" htmlFor="managed-school-code"><TextField.Root id="managed-school-code" size="3" required value={form.code} disabled={Boolean(editing)} onChange={event => setForm({ ...form, code: event.target.value })} /></FormField>
        <FormField label="Tên trường" htmlFor="managed-school-name"><TextField.Root id="managed-school-name" size="3" required value={form.name} onChange={event => setForm({ ...form, name: event.target.value })} /></FormField>
        <FormField label="Địa chỉ" htmlFor="managed-school-address"><TextField.Root id="managed-school-address" size="3" value={form.address} onChange={event => setForm({ ...form, address: event.target.value })} /></FormField>
        <FormField label="Ngày bắt đầu license" htmlFor="managed-school-start"><TextField.Root id="managed-school-start" size="3" type="date" value={form.licenseStart} required={Boolean(form.licenseEnd)} onChange={event => setForm({ ...form, licenseStart: event.target.value })} /></FormField>
        <FormField label="Ngày hết hạn license" htmlFor="managed-school-end"><TextField.Root id="managed-school-end" size="3" type="date" min={form.licenseStart} value={form.licenseEnd} required={Boolean(form.licenseStart)} onChange={event => setForm({ ...form, licenseEnd: event.target.value })} /></FormField>
        <FormField label="Token AI/tháng (trống = không giới hạn)" htmlFor="managed-school-quota"><TextField.Root id="managed-school-quota" size="3" type="number" min="0" step="1" value={form.monthlyTokenQuota} onChange={event => setForm({ ...form, monthlyTokenQuota: event.target.value })} /></FormField>
        <Text as="label" size="2" className="admin-checkbox"><Checkbox checked={form.active} onCheckedChange={checked => setForm({ ...form, active: checked === true })} />Đang hoạt động</Text>
        <div className="admin-form-actions"><Button onClick={() => setShowForm(false)}>Hủy</Button><ThemeButton type="submit" size="3" loading={busy} disabled={busy}>{busy ? "Đang lưu…" : "Lưu trường"}</ThemeButton></div>
      </form>
    </Dialog.Content></Dialog.Root>
    {loading ? <Loading /> : <Panel title={`${items.length} trường học`}><Table.Root size="2" variant="ghost"><Table.Header><Table.Row><Table.ColumnHeaderCell>Mã</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Tên trường</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Địa chỉ</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Trạng thái</Table.ColumnHeaderCell><Table.ColumnHeaderCell /></Table.Row></Table.Header><Table.Body>{items.map(item => <Table.Row key={item.id}><Table.Cell><Text weight="medium">{item.code}</Text></Table.Cell><Table.Cell>{item.name}</Table.Cell><Table.Cell>{item.address || "—"}</Table.Cell><Table.Cell><Badge color={item.active ? "cyan" : "gray"} size="2">{item.active ? "Hoạt động" : "Đã tắt"}</Badge></Table.Cell><Table.Cell><Button onClick={() => { setEditing(item.id); setForm({ code: item.code, name: item.name, address: item.address ?? "", active: item.active, licenseStart: item.licenseStart ?? "", licenseEnd: item.licenseEnd ?? "", monthlyTokenQuota: item.monthlyTokenQuota == null ? "" : String(item.monthlyTokenQuota) }); setShowForm(true); }}>Sửa</Button></Table.Cell></Table.Row>)}</Table.Body></Table.Root></Panel>}
  </div>;
}
export function PlansView() {
  const [items, setItems] = useState<ManagedPlan[]>([]);
  const [form, setForm] = useState<ManagedPlan | null>(null);
  const [editing, setEditing] = useState(false);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const load = async () => { setLoading(true); try { setItems(await adminPlans()); setError(""); } catch (err) { setError(apiMessage(err, "Không thể tải danh mục gói.")); } finally { setLoading(false); } };
  useEffect(() => { void load(); }, []);
  const submit = async (event: FormEvent) => {
    event.preventDefault(); if (!form || busy) return; setBusy(true); setError("");
    try { await saveManagedPlan(form, editing); setForm(null); await load(); }
    catch (err) { setError(apiMessage(err, "Không thể lưu gói.")); } finally { setBusy(false); }
  };
  return <div className="admin-content"><PageHeader title="Danh mục gói" description="Giá và quota công khai của PhysLive." action={<Button primary onClick={() => { setEditing(false); setForm({ code: "", name: "", description: "", annualPriceVnd: 0, studentQuota: 0, monthlyTokenQuota: 0, active: false }); }}>Thêm gói</Button>} />
    {error && <ErrorNotice error={error} onRetry={() => void load()} />}
    {form && <Panel title={editing ? "Cập nhật gói" : "Thêm gói"} description="Thay đổi áp dụng cho báo giá mới. Các giao dịch đã tạo giữ số tiền và quota đã chốt."><form className="admin-form-grid" onSubmit={submit}>
      <FormField label="Mã gói" htmlFor="managed-plan-code"><TextField.Root id="managed-plan-code" size="3" required maxLength={40} pattern={"[A-Z0-9_\\-]+"} readOnly={editing} value={form.code} onChange={e => setForm({ ...form, code: e.target.value.toUpperCase() })} /></FormField>
      <FormField label="Tên gói" htmlFor="managed-plan-name"><TextField.Root id="managed-plan-name" size="3" required maxLength={255} value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} /></FormField>
      <FormField label="Mô tả" htmlFor="managed-plan-description"><TextField.Root id="managed-plan-description" size="3" required maxLength={255} value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} /></FormField>
      <FormField label="Giá / năm (VND)" htmlFor="managed-plan-price"><TextField.Root id="managed-plan-price" size="3" type="number" required min="1" step="1" value={form.annualPriceVnd || ""} onChange={e => setForm({ ...form, annualPriceVnd: Number(e.target.value) })} /></FormField>
      <FormField label="Quota học sinh" htmlFor="managed-plan-students"><TextField.Root id="managed-plan-students" size="3" type="number" required min="1" step="1" value={form.studentQuota || ""} onChange={e => setForm({ ...form, studentQuota: Number(e.target.value) })} /></FormField>
      <FormField label="Token AI / tháng (trống = không giới hạn)" htmlFor="managed-plan-tokens"><TextField.Root id="managed-plan-tokens" size="3" type="number" min="0" step="1" value={form.monthlyTokenQuota ?? ""} onChange={e => setForm({ ...form, monthlyTokenQuota: e.target.value === "" ? null : Number(e.target.value) })} /></FormField>
      <Text as="label" className="admin-checkbox"><Checkbox checked={form.active} onCheckedChange={checked => setForm({ ...form, active: checked === true })} /> Cho phép đăng ký</Text>
      <div className="admin-form-actions"><Button disabled={busy} onClick={() => setForm(null)}>Hủy</Button><Button primary type="submit" disabled={busy}>{busy ? "Đang lưu…" : "Lưu gói"}</Button></div>
    </form></Panel>}
    {loading ? <Loading /> : <Panel title="Gói đăng ký"><div className="admin-table-scroll"><Table.Root size="2" variant="ghost" className="admin-table"><Table.Header><Table.Row><Table.ColumnHeaderCell>Mã</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Tên</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Giá / năm</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Học sinh</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Token / tháng</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Trạng thái</Table.ColumnHeaderCell><Table.ColumnHeaderCell /></Table.Row></Table.Header><Table.Body>{items.map(plan => <Table.Row key={plan.code}><Table.Cell>{plan.code}</Table.Cell><Table.Cell>{plan.name}</Table.Cell><Table.Cell>{plan.annualPriceVnd.toLocaleString("vi-VN")} ₫</Table.Cell><Table.Cell>{plan.studentQuota.toLocaleString("vi-VN")}</Table.Cell><Table.Cell>{plan.monthlyTokenQuota == null ? "Không giới hạn" : plan.monthlyTokenQuota.toLocaleString("vi-VN")}</Table.Cell><Table.Cell><Badge color={plan.active ? "cyan" : "gray"} size="2">{plan.active ? "Đang bán" : "Ẩn"}</Badge></Table.Cell><Table.Cell><Button onClick={() => { setEditing(true); setForm({ ...plan }); }}>Sửa</Button></Table.Cell></Table.Row>)}</Table.Body></Table.Root></div></Panel>}
  </div>;
}

export function CurriculumView() {
  const [tree, setTree] = useState<CurriculumTree | null>(null); const [loading, setLoading] = useState(true); const [busy, setBusy] = useState(false); const [error, setError] = useState(""); const [name, setName] = useState(""); const [kind, setKind] = useState<"topic" | "module" | "level" | "lesson">("topic"); const [parentId, setParentId] = useState("");
  const load = async () => { setLoading(true); try { setTree(await adminCurriculum()); setError(""); } catch (e) { setError(apiMessage(e, "Không thể tải chương trình học.")); } finally { setLoading(false); } }; useEffect(() => { void load(); }, []);
  const create = async (event: FormEvent) => { event.preventDefault(); if (!name.trim() || (kind !== "topic" && !parentId)) { return; } setBusy(true); try { setTree(await createCurriculumNode(curriculumPath(kind, parentId), { name: name.trim() })); setName(""); setParentId(""); } catch (e) { setError(apiMessage(e, "Không thể tạo mục chương trình.")); } finally { setBusy(false); } };
  const toggle = async (type: "topic" | "module" | "level" | "lesson", id: string) => { setBusy(true); try { setTree(await toggleCurriculum(type, id)); } catch (e) { setError(apiMessage(e, "Không thể cập nhật chương trình.")); } finally { setBusy(false); } };
  const parentOptions = curriculumParentOptions(kind, tree);
  return <div className="admin-content"><PageHeader title="Chương trình học" description="" />{error && <ErrorNotice error={error} onRetry={() => void load()} />}{loading ? <Loading /> : <>
    <Panel title="Thêm nội dung"><form className="admin-form-grid admin-form-inline" onSubmit={create}>
      <FormField label="Loại" htmlFor="managed-curriculum-kind"><Select.Root size="3" value={kind} onValueChange={value => { setKind(value as typeof kind); setParentId(""); }}><Select.Trigger id="managed-curriculum-kind" /><Select.Content><Select.Item value="topic">Chủ đề</Select.Item><Select.Item value="module">Module</Select.Item><Select.Item value="level">Level</Select.Item><Select.Item value="lesson">Lesson</Select.Item></Select.Content></Select.Root></FormField>
      {kind !== "topic" && <FormField label="Nằm trong" htmlFor="managed-curriculum-parent"><Select.Root size="3" name="parentId" required value={parentId} onValueChange={setParentId}><Select.Trigger id="managed-curriculum-parent" placeholder="Chọn mục cha" /><Select.Content>{parentOptions.map(item => <Select.Item key={item.id} value={item.id}>{item.label}</Select.Item>)}</Select.Content></Select.Root></FormField>}
      <FormField label="Tên" htmlFor="managed-curriculum-name"><TextField.Root id="managed-curriculum-name" size="3" required value={name} onChange={event => setName(event.target.value)} placeholder="Tên mục mới" /></FormField>
      <div className="admin-form-actions"><Button type="submit" primary disabled={busy}>Thêm</Button></div>
    </form></Panel><Panel title="Cấu trúc hiện tại"><CurriculumTreeRows tree={tree} onToggle={toggle} /></Panel>
  </>}</div>;
}
function CurriculumTreeRows({ tree, onToggle }: Readonly<{ tree: CurriculumTree | null; onToggle: (type: CurriculumKind, id: string) => void }>) {
  return <div className="admin-tree">{tree?.topics.map(topic => <CurriculumTopicRow key={topic.id} topic={topic} onToggle={onToggle} />)}</div>;
}

function CurriculumTopicRow({ topic, onToggle }: Readonly<{ topic: CurriculumTree["topics"][number]; onToggle: (type: CurriculumKind, id: string) => void }>) {
  return <div className="admin-tree-topic"><TreeRow label={topic.name} active={topic.enabled} onToggle={() => onToggle("topic", topic.id)} />{topic.modules.map(module => <CurriculumModuleRow key={module.id} module={module} onToggle={onToggle} />)}</div>;
}

function CurriculumModuleRow({ module, onToggle }: Readonly<{ module: CurriculumTree["topics"][number]["modules"][number]; onToggle: (type: CurriculumKind, id: string) => void }>) {
  return <div className="admin-tree-child"><TreeRow label={module.name} active={module.active} onToggle={() => onToggle("module", module.id)} />{module.levels.map(level => <CurriculumLevelRow key={level.id} level={level} onToggle={onToggle} />)}</div>;
}

function CurriculumLevelRow({ level, onToggle }: Readonly<{ level: CurriculumTree["topics"][number]["modules"][number]["levels"][number]; onToggle: (type: CurriculumKind, id: string) => void }>) {
  return <div className="admin-tree-grandchild"><TreeRow label={level.name} active={level.active} onToggle={() => onToggle("level", level.id)} />{level.lessons.map(lesson => <div className="admin-tree-lesson" key={lesson.id}><TreeRow label={lesson.name} active={lesson.active} onToggle={() => onToggle("lesson", lesson.id)} /></div>)}</div>;
}

function TreeRow({ label, active, onToggle }: Readonly<{ label: string; active: boolean; onToggle: () => void }>) { return <div className="admin-tree-row"><Text>{label}</Text><ThemeButton type="button" variant="soft" color={active ? "indigo" : "gray"} size="1" onClick={onToggle}>{active ? "Đang bật" : "Đã tắt"}</ThemeButton></div>; }

export function ValidationView() {
  const [items, setItems] = useState<ValidationRun[]>([]); const [loading, setLoading] = useState(true); const [error, setError] = useState(""); const load = async () => { setLoading(true); try { setItems(await validationRuns()); setError(""); } catch (e) { setError(apiMessage(e, "Không thể tải các lần kiểm định.")); } finally { setLoading(false); } }; useEffect(() => { void load(); }, []);
  return <div className="admin-content"><PageHeader title="Kiểm định mô phỏng" description="Theo dõi các lần kiểm tra thực tế của solver và reference solver." action={<Button onClick={() => void load()}><LearningIcon name="refresh" /> Làm mới</Button>} />{error && <ErrorNotice error={error} onRetry={() => void load()} />}{loading ? <Loading /> : <Panel title={`${items.length} lần chạy`}><div className="admin-table-scroll"><Table.Root size="2" variant="ghost" className="admin-table"><Table.Header><Table.Row><Table.ColumnHeaderCell>Thời gian</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Chủ đề</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Schema</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Bộ giải</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Trạng thái</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Lỗi</Table.ColumnHeaderCell></Table.Row></Table.Header><Table.Body>{items.length === 0 ? <Table.Row><Table.Cell colSpan={6} className="admin-empty-table">Chưa có lần kiểm định.</Table.Cell></Table.Row> : items.map(item => <Table.Row key={item.id}><Table.Cell>{new Date(item.createdAt).toLocaleString("vi-VN")}</Table.Cell><Table.Cell>{item.topic}</Table.Cell><Table.Cell>{item.schemaId} <small>{item.schemaVersion}</small></Table.Cell><Table.Cell>{item.solverVersion}</Table.Cell><Table.Cell><Badge color={item.passed ? "cyan" : "red"} size="2">{item.passed ? "Đạt" : "Không đạt"}</Badge></Table.Cell><Table.Cell className="admin-error-cell">{item.errorMessage || "—"}</Table.Cell></Table.Row>)}</Table.Body></Table.Root></div></Panel>}</div>;
}
