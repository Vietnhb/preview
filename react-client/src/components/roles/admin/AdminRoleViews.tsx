import { useCallback, useEffect, useMemo, useState, type FormEvent, type ReactNode } from "react";
import LearningIcon from "../../common/LearningIcon";
import * as DropdownMenu from "@radix-ui/react-dropdown-menu";
import {
  adminCurriculum, adminSchools, adminUsers, createCurriculumNode, createManagedUser,
  createSchool, setManagedUserActive, toggleCurriculum, updateManagedUser, updateSchool,
  validationMetrics, validationRuns, adminPlans, saveManagedPlan, type ManagedPlan, type CurriculumTree, type ManagedSchool, type ValidationRun,
} from "../../../api/adminApi";
import type { User } from "../../../types/physlive";
import { apiMessage, curriculumParentOptions, curriculumPath, roleLabels } from "./adminUtils";

type IconName = "grid" | "users" | "book" | "activity" | "shield" | "refresh" | "plus" | "check" | "search" | "close";
import type { CurriculumKind } from "./adminUtils";

function PageHeader({ title, description, action }: Readonly<{ title: string; description: string; action?: ReactNode }>) {
  return <header className="admin-content-header"><div><p className="admin-eyebrow">PhysLive Admin</p><h1 className="admin-content-title">{title}</h1><p className="admin-content-subtitle">{description}</p></div>{action}</header>;
}

function Panel({ title, description, children, action, className }: Readonly<{ title: string; description?: string; children: ReactNode; action?: ReactNode; className?: string }>) {
  return <section className={`admin-panel ${className ?? ""}`}><div className="admin-panel-heading"><div><h2>{title}</h2>{description && <p className="admin-panel-description">{description}</p>}</div>{action}</div>{children}</section>;
}

function Loading({ text = "Đang tải dữ liệu..." }: Readonly<{ text?: string }>) { return <div className="admin-loading compact"><span className="admin-loading-spinner" /><p>{text}</p></div>; }
function ErrorNotice({ error, onRetry }: Readonly<{ error: string; onRetry?: () => void }>) { return <div className="admin-error-banner" role="alert">{error}{onRetry && <button type="button" className="admin-inline-button" onClick={onRetry}>Thử lại</button>}</div>; }
function Button({ children, onClick, primary = false, disabled = false, type = "button" }: Readonly<{ children: ReactNode; onClick?: () => void; primary?: boolean; disabled?: boolean; type?: "button" | "submit" }>) { return <button type={type} className={primary ? "admin-primary-button" : "admin-secondary-button"} onClick={onClick} disabled={disabled}>{children}</button>; }

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
    <div className="admin-stats-grid"><Stat label="Tổng người dùng" value={users.length} icon="users" tone="blue" /><Stat label="Đang hoạt động" value={users.filter(item => item.active !== false).length} icon="activity" tone="green" /><Stat label="Giáo viên" value={users.filter(item => item.role === "TEACHER").length} icon="book" tone="orange" /><Stat label="Validation đạt" value={`${rate.toFixed(1)}%`} icon="shield" tone="purple" /></div>
    <div className="admin-dashboard-grid"><Panel title="Sức khỏe validation" description="Dữ liệu lấy trực tiếp từ các validation run."><div className="admin-health-value">{rate.toFixed(1)}%</div><div className="admin-health-track"><span style={{ width: `${Math.max(0, Math.min(100, rate))}%` }} /></div><div className="admin-health-meta"><span>{passed} lượt đạt</span><span>{metrics?.failed ?? 0} lượt lỗi</span></div></Panel><Panel title="Phân bổ tài khoản" description="Trạng thái hiện tại của hệ thống."><div className="admin-breakdown-list"><div><span>Giáo viên</span><strong>{users.filter(item => item.role === "TEACHER").length}</strong></div><div><span>Học sinh</span><strong>{users.filter(item => item.role === "STUDENT").length}</strong></div><div><span>Reviewer</span><strong>{users.filter(item => item.role === "REVIEWER").length}</strong></div></div></Panel></div>
  </div>;
}

function Stat({ label, value, icon, tone, note }: Readonly<{ label: string; value: string | number; icon: IconName; tone: string; note?: string }>) { return <article className="admin-stat-card"><div className={`admin-stat-icon ${tone}`}><LearningIcon name={icon} /></div><p>{label}</p><strong>{value}</strong>{note && <small>{note}</small>}</article>; }

function UserActionMenu({ item, open, onToggleMenu, onToggle, onEdit, onClose, schoolScoped = false }: Readonly<{
  item: User;
  open: boolean;
  onToggleMenu: () => void;
  onToggle: () => void;
  onEdit: () => void;
  onClose: () => void;
  schoolScoped?: boolean;
}>) {
  return <DropdownMenu.Root open={open} onOpenChange={nextOpen => { if (nextOpen !== open) onToggleMenu(); }}>
    <DropdownMenu.Trigger asChild>
      <button type="button" className="admin-action-trigger" aria-label={`Actions for ${item.email}`}><svg className="learn-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="1" /><circle cx="19" cy="12" r="1" /><circle cx="5" cy="12" r="1" /></svg></button>
    </DropdownMenu.Trigger>
    <DropdownMenu.Portal>
      <DropdownMenu.Content align="end" sideOffset={4} collisionPadding={8} className="admin-shell admin-action-overlay-root">
      <div className="admin-action-popover">
      <div className="admin-action-title">{schoolScoped ? "Tài khoản" : "Actions"}</div>
      <div className="admin-action-separator" />
      <button type="button" className="admin-action-item" role="menuitem" onClick={() => { onEdit(); onClose(); }}><LearningIcon name="edit" />{schoolScoped ? "Sửa hồ sơ" : "Edit Profile"}</button>
      <button type="button" className="admin-action-item warning" role="menuitem" onClick={() => { onToggle(); onClose(); }}><LearningIcon name="ban" />{schoolScoped ? (item.active === false ? "Kích hoạt lại" : "Tạm khóa") : (item.active === false ? "Unban User" : "Ban User")}</button>
      {!schoolScoped && <>
        <div className="admin-action-separator" />
        <button type="button" className="admin-action-item danger" role="menuitem" disabled={item.active === false} onClick={() => { onToggle(); onClose(); }}><LearningIcon name="trash" />Delete User</button>
      </>}
      </div>
      </DropdownMenu.Content>
    </DropdownMenu.Portal>
  </DropdownMenu.Root>;
}

type UserForm = { email: string; password: string; fullName: string; role: string; institutionId: string };
const blankUser: UserForm = { email: "", password: "", fullName: "", role: "STUDENT", institutionId: "" };
export function UsersView({ schoolId }: Readonly<{ schoolId?: string }> = {}) {
  const [availableSchools, setAvailableSchools] = useState<ManagedSchool[]>([]);
  const [items, setItems] = useState<User[]>([]); const [query, setQuery] = useState(""); const [form, setForm] = useState<UserForm>(blankUser); const [editing, setEditing] = useState<number | null>(null); const [showForm, setShowForm] = useState(false); const [loading, setLoading] = useState(true); const [busy, setBusy] = useState(false); const [error, setError] = useState("");
  const [openActions, setOpenActions] = useState<number | null>(null);
  const load = useCallback(async () => { setLoading(true); try { const [users, schools] = await Promise.all([adminUsers(schoolId), schoolId ? Promise.resolve([]) : adminSchools()]); setItems(users); setAvailableSchools(schools); setError(""); } catch (e) { setError(apiMessage(e, "Không thể tải danh sách người dùng.")); } finally { setLoading(false); } }, [schoolId]);
  useEffect(() => { void load(); }, [load]);
  const schoolScoped = Boolean(schoolId);
  const scopedItems = useMemo(() => schoolScoped ? items.filter(item => item.role === "TEACHER" || item.role === "STUDENT") : items, [items, schoolScoped]);
  const filtered = useMemo(() => { const q = query.trim().toLowerCase(); return scopedItems.filter(item => !q || item.email.toLowerCase().includes(q) || item.fullName.toLowerCase().includes(q)); }, [scopedItems, query]);
  const submit = async (event: FormEvent) => { event.preventDefault(); setBusy(true); setError(""); try { if (editing === null) { await createManagedUser({ ...form, institutionId: form.institutionId || undefined }, schoolId); } else { await updateManagedUser(editing, { fullName: form.fullName, role: form.role, institutionId: form.institutionId || undefined }, schoolId); } setShowForm(false); setEditing(null); setForm(blankUser); await load(); } catch (e) { setError(apiMessage(e, "Không thể lưu tài khoản.")); } finally { setBusy(false); } };
  const edit = (item: User) => { setEditing(item.id); setForm({ email: item.email, password: "", fullName: item.fullName, role: item.role, institutionId: item.institutionId ?? "" }); setShowForm(true); };
  const toggle = async (item: User) => { setBusy(true); setError(""); try { await setManagedUserActive(item.id, item.active === false, schoolId); await load(); } catch (e) { setError(apiMessage(e, "Không thể cập nhật trạng thái tài khoản.")); } finally { setBusy(false); } };
  const schoolUsers = items.filter(item => ["SCHOOL_MANAGER", "TEACHER", "STUDENT"].includes(item.role));
  const isOnline = (item: User) => item.active !== false && Boolean(item.lastLogin) && Date.now() - new Date(item.lastLogin as string).getTime() < 5 * 60 * 1000;
  return <div className="admin-content">
    <div className="admin-toolbar"><label className="admin-search-field"><LearningIcon name="search" /><span className="sr-only">Tìm theo email hoặc họ tên</span><input type="search" placeholder={schoolScoped ? "Tìm giáo viên, học sinh…" : "Search users by email or name..."} value={query} onChange={event => setQuery(event.target.value)} /></label><button type="button" className="admin-icon-button" aria-label="Làm mới danh sách tài khoản" onClick={() => void load()} disabled={loading}><LearningIcon name="refresh" /></button><button type="button" className="admin-icon-button" aria-label="Tạo tài khoản" title="Tạo tài khoản" onClick={() => { setEditing(null); setForm(blankUser); setShowForm(true); }}><LearningIcon name="plus" /></button></div>{error && <ErrorNotice error={error} onRetry={() => void load()} />}
    <div className="admin-stats-grid admin-user-stats">{schoolScoped ? <>
      <Stat label="Tổng tài khoản" value={scopedItems.length} icon="users" tone="blue" />
      <Stat label="Giáo viên" value={scopedItems.filter(item => item.role === "TEACHER").length} icon="book" tone="orange" />
      <Stat label="Học sinh" value={scopedItems.filter(item => item.role === "STUDENT").length} icon="users" tone="green" />
      <Stat label="Đang hoạt động" value={scopedItems.filter(item => item.active !== false).length} icon="activity" tone="purple" />
      <Stat label="Tạm khóa" value={scopedItems.filter(item => item.active === false).length} icon="close" tone="red" />
    </> : <>
      <Stat label="Total Users" value={items.length} icon="users" tone="blue" />
      <Stat label="Admins" value={items.filter(item => item.role === "ADMIN").length} icon="shield" tone="purple" />
      <Stat label="Regular Users" value={schoolUsers.length} icon="book" tone="orange" />
      <Stat label="Banned Users" value={items.filter(item => item.active === false).length} icon="close" tone="red" />
      <Stat label="Online Now" value={items.filter(isOnline).length} icon="activity" tone="green" note="Last 5 minutes" />
    </>}</div>
    {showForm && <Panel title={editing === null ? "Tạo tài khoản" : "Cập nhật tài khoản"} description="Thông tin quyền được kiểm tra lại ở backend."><form className="admin-form-grid" onSubmit={submit}><label>Email<input type="email" value={form.email} disabled={editing !== null} required onChange={e => setForm({ ...form, email: e.target.value })} /></label><label>Họ và tên<input value={form.fullName} required onChange={e => setForm({ ...form, fullName: e.target.value })} /></label><label>Vai trò<select value={form.role} onChange={e => setForm({ ...form, role: e.target.value, institutionId: ["ADMIN", "REVIEWER"].includes(e.target.value) ? "" : form.institutionId })}><option value="STUDENT">Học sinh</option><option value="TEACHER">Giáo viên</option>{!schoolId && <><option value="REVIEWER">Reviewer</option><option value="SCHOOL_MANAGER">Quản lý trường</option><option value="ADMIN">Admin</option></>}</select></label>{!schoolId && ["TEACHER", "STUDENT", "SCHOOL_MANAGER"].includes(form.role) && <label>Trường<select required value={form.institutionId} onChange={e => setForm({ ...form, institutionId: e.target.value })}><option value="">Chọn trường</option>{availableSchools.filter(item => item.active).map(item => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>}{editing === null && <label>Mật khẩu<input type="password" minLength={8} required value={form.password} onChange={e => setForm({ ...form, password: e.target.value })} /></label>}<div className="admin-form-actions"><Button onClick={() => setShowForm(false)}>Hủy</Button><Button type="submit" primary disabled={busy}>{busy ? "Đang lưu..." : "Lưu tài khoản"}</Button></div></form></Panel>}
    <Panel title={schoolScoped ? "Tài khoản trường" : "Users"} description={schoolScoped ? "Danh sách giáo viên và học sinh thuộc trường." : "All accounts managed by PhysLive."} className="admin-users-panel">{loading ? <Loading text="Đang tải danh sách tài khoản…" /> : <div className="admin-table-scroll"><table className="admin-table admin-user-table"><thead><tr><th>Email</th><th>{schoolScoped ? "Họ và tên" : "Full Name"}</th><th>{schoolScoped ? "Vai trò" : "Role"}</th>{!schoolScoped && <><th>Online</th><th>Last Online</th></>}<th>{schoolScoped ? "Trạng thái" : "Status"}</th>{schoolScoped && <th>Lần đăng nhập gần nhất</th>}<th>{schoolScoped ? "Ngày sinh" : "Date of Birth"}</th>{!schoolScoped && <th>Created At</th>}<th>{schoolScoped ? "Thao tác" : "Actions"}</th></tr></thead><tbody>{filtered.length === 0 ? <tr><td colSpan={schoolScoped ? 7 : 9} className="admin-empty-table">{schoolScoped ? "Không tìm thấy tài khoản phù hợp." : "No users found."}</td></tr> : filtered.map(item => <tr key={item.id}><td><strong className="admin-user-email">{item.email}</strong></td><td><div className="admin-table-user"><span className="admin-user-avatar">{(item.fullName || item.email).slice(0, 1).toUpperCase()}</span><span><strong>{item.fullName || "Chưa có họ tên"}</strong></span></div></td><td><span className={`admin-role-badge ${item.role.toLowerCase()}`}>{roleLabels[item.role] ?? item.role}</span></td>{!schoolScoped && <><td><span className={`admin-status ${item.active === false ? "inactive" : "active"}`}><i />{item.active === false ? "Offline" : "Active"}</span></td><td><span className="admin-muted-label">—</span></td></>}<td><span className={`admin-status ${item.active === false ? "inactive" : "active"}`}><i />{schoolScoped ? (item.active === false ? "Tạm khóa" : "Hoạt động") : (item.active === false ? "Banned" : "Active")}</span></td>{schoolScoped && <td>{item.lastLogin ? new Date(item.lastLogin).toLocaleString("vi-VN") : "Chưa đăng nhập"}</td>}<td>{item.dateOfBirth || "—"}</td>{!schoolScoped && <td><span className="admin-muted-label">—</span></td>}<td><UserActionMenu item={item} open={openActions === item.id} onToggleMenu={() => setOpenActions(openActions === item.id ? null : item.id)} onToggle={() => void toggle(item)} onEdit={() => edit(item)} onClose={() => setOpenActions(null)} schoolScoped={schoolScoped} /></td></tr>)}</tbody></table></div>}</Panel>
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
  return <div className="admin-content"><PageHeader title="Trường học" description="Quản lý danh sách trường để gắn tài khoản và phạm vi lớp học." action={<Button primary onClick={() => { setEditing(null); setForm(blankSchool); setShowForm(true); }}><LearningIcon name="plus" /> Thêm trường</Button>} />{error && <ErrorNotice error={error} onRetry={() => void load()} />}{showForm && <Panel title={editing ? "Cập nhật trường" : "Thêm trường"}><form className="admin-form-grid" onSubmit={submit}><label>Mã trường<input required value={form.code} disabled={Boolean(editing)} onChange={e => setForm({ ...form, code: e.target.value })} /></label><label>Tên trường<input required value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} /></label><label>Địa chỉ<input value={form.address} onChange={e => setForm({ ...form, address: e.target.value })} /></label><label>Ngày bắt đầu license<input type="date" value={form.licenseStart} required={Boolean(form.licenseEnd)} onChange={e => setForm({ ...form, licenseStart: e.target.value })} /></label><label>Ngày hết hạn license<input type="date" min={form.licenseStart} value={form.licenseEnd} required={Boolean(form.licenseStart)} onChange={e => setForm({ ...form, licenseEnd: e.target.value })} /></label><label>Token AI/tháng (để trống nếu không giới hạn)<input type="number" min="0" step="1" value={form.monthlyTokenQuota} onChange={e => setForm({ ...form, monthlyTokenQuota: e.target.value })} /></label><label className="admin-checkbox"><input type="checkbox" checked={form.active} onChange={e => setForm({ ...form, active: e.target.checked })} /> Đang hoạt động</label><div className="admin-form-actions"><Button onClick={() => setShowForm(false)}>Hủy</Button><Button type="submit" primary disabled={busy}>{busy ? "Đang lưu..." : "Lưu trường"}</Button></div></form></Panel>}{loading ? <Loading /> : <Panel title={`${items.length} trường học`}><div className="admin-table-scroll"><table className="admin-table"><thead><tr><th>Mã</th><th>Tên trường</th><th>Địa chỉ</th><th>Trạng thái</th><th /></tr></thead><tbody>{items.map(item => <tr key={item.id}><td><strong>{item.code}</strong></td><td>{item.name}</td><td>{item.address || "—"}</td><td><span className={`admin-status ${item.active ? "active" : "inactive"}`}><i />{item.active ? "Hoạt động" : "Đã tắt"}</span></td><td><Button onClick={() => { setEditing(item.id); setForm({ code: item.code, name: item.name, address: item.address ?? "", active: item.active, licenseStart: item.licenseStart ?? "", licenseEnd: item.licenseEnd ?? "", monthlyTokenQuota: item.monthlyTokenQuota == null ? "" : String(item.monthlyTokenQuota) }); setShowForm(true); }}>Sửa</Button></td></tr>)}</tbody></table></div></Panel>}</div>;
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
      <label>Mã gói<input required maxLength={40} pattern="[A-Z0-9_-]+" readOnly={editing} value={form.code} onChange={e => setForm({ ...form, code: e.target.value.toUpperCase() })} /></label>
      <label>Tên gói<input required maxLength={255} value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} /></label>
      <label>Mô tả<input required maxLength={255} value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} /></label>
      <label>Giá / năm (VND)<input type="number" required min="1" step="1" value={form.annualPriceVnd || ""} onChange={e => setForm({ ...form, annualPriceVnd: Number(e.target.value) })} /></label>
      <label>Quota học sinh<input type="number" required min="1" step="1" value={form.studentQuota || ""} onChange={e => setForm({ ...form, studentQuota: Number(e.target.value) })} /></label>
      <label>Token AI / tháng (trống = không giới hạn)<input type="number" min="0" step="1" value={form.monthlyTokenQuota ?? ""} onChange={e => setForm({ ...form, monthlyTokenQuota: e.target.value === "" ? null : Number(e.target.value) })} /></label>
      <label className="admin-checkbox"><input type="checkbox" checked={form.active} onChange={e => setForm({ ...form, active: e.target.checked })} /> Cho phép đăng ký</label>
      <div className="admin-form-actions"><Button disabled={busy} onClick={() => setForm(null)}>Hủy</Button><Button primary type="submit" disabled={busy}>{busy ? "Đang lưu…" : "Lưu gói"}</Button></div>
    </form></Panel>}
    {loading ? <Loading /> : <Panel title="Gói đăng ký"><div className="admin-table-scroll"><table className="admin-table"><thead><tr><th>Mã</th><th>Tên</th><th>Giá / năm</th><th>Học sinh</th><th>Token / tháng</th><th>Trạng thái</th><th /></tr></thead><tbody>{items.map(plan => <tr key={plan.code}><td>{plan.code}</td><td>{plan.name}</td><td>{plan.annualPriceVnd.toLocaleString("vi-VN")} ₫</td><td>{plan.studentQuota.toLocaleString("vi-VN")}</td><td>{plan.monthlyTokenQuota == null ? "Không giới hạn" : plan.monthlyTokenQuota.toLocaleString("vi-VN")}</td><td>{plan.active ? "Đang bán" : "Ẩn"}</td><td><Button onClick={() => { setEditing(true); setForm({ ...plan }); }}>Sửa</Button></td></tr>)}</tbody></table></div></Panel>}
  </div>;
}

export function CurriculumView() {
  const [tree, setTree] = useState<CurriculumTree | null>(null); const [loading, setLoading] = useState(true); const [busy, setBusy] = useState(false); const [error, setError] = useState(""); const [name, setName] = useState(""); const [kind, setKind] = useState<"topic" | "module" | "level" | "lesson">("topic"); const [parentId, setParentId] = useState("");
  const load = async () => { setLoading(true); try { setTree(await adminCurriculum()); setError(""); } catch (e) { setError(apiMessage(e, "Không thể tải chương trình học.")); } finally { setLoading(false); } }; useEffect(() => { void load(); }, []);
  const create = async (event: FormEvent) => { event.preventDefault(); if (!name.trim() || (kind !== "topic" && !parentId)) { return; } setBusy(true); try { setTree(await createCurriculumNode(curriculumPath(kind, parentId), { name: name.trim() })); setName(""); setParentId(""); } catch (e) { setError(apiMessage(e, "Không thể tạo mục chương trình.")); } finally { setBusy(false); } };
  const toggle = async (type: "topic" | "module" | "level" | "lesson", id: string) => { setBusy(true); try { setTree(await toggleCurriculum(type, id)); } catch (e) { setError(apiMessage(e, "Không thể cập nhật chương trình.")); } finally { setBusy(false); } };
  const parentOptions = curriculumParentOptions(kind, tree);
  return <div className="admin-content"><PageHeader title="Chương trình học" description="Bật/tắt và bổ sung chủ đề, module, level, lesson cho Kinematics, Dynamics và Circuits." />{error && <ErrorNotice error={error} onRetry={() => void load()} />}{loading ? <Loading /> : <><Panel title="Thêm nội dung"><form className="admin-form-grid admin-form-inline" onSubmit={create}><label>Loại<select value={kind} onChange={e => { setKind(e.target.value as typeof kind); setParentId(""); }}><option value="topic">Chủ đề</option><option value="module">Module</option><option value="level">Level</option><option value="lesson">Lesson</option></select></label>{kind !== "topic" && <label>Nằm trong<select required value={parentId} onChange={e => setParentId(e.target.value)}><option value="">Chọn mục cha</option>{parentOptions.map(item => <option key={item.id} value={item.id}>{item.label}</option>)}</select></label>}<label>Tên<input required value={name} onChange={e => setName(e.target.value)} placeholder="Tên mục mới" /></label><div className="admin-form-actions"><Button type="submit" primary disabled={busy}>Thêm</Button></div></form></Panel><Panel title="Cấu trúc hiện tại" description="Trạng thái thay đổi trực tiếp qua API quản trị."><CurriculumTreeRows tree={tree} onToggle={toggle} /></Panel></>}</div>;
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

function TreeRow({ label, active, onToggle }: Readonly<{ label: string; active: boolean; onToggle: () => void }>) { return <div className="admin-tree-row"><span>{label}</span><button type="button" className={`admin-tree-status ${active ? "active" : "inactive"}`} onClick={onToggle}>{active ? "Đang bật" : "Đã tắt"}</button></div>; }

export function ValidationView() {
  const [items, setItems] = useState<ValidationRun[]>([]); const [loading, setLoading] = useState(true); const [error, setError] = useState(""); const load = async () => { setLoading(true); try { setItems(await validationRuns()); setError(""); } catch (e) { setError(apiMessage(e, "Không thể tải validation runs.")); } finally { setLoading(false); } }; useEffect(() => { void load(); }, []);
  return <div className="admin-content"><PageHeader title="Validation runs" description="Theo dõi các lần kiểm tra thực tế của solver và reference solver." action={<Button onClick={() => void load()}><LearningIcon name="refresh" /> Làm mới</Button>} />{error && <ErrorNotice error={error} onRetry={() => void load()} />}{loading ? <Loading /> : <Panel title={`${items.length} lần chạy`}><div className="admin-table-scroll"><table className="admin-table"><thead><tr><th>Thời gian</th><th>Topic</th><th>Schema</th><th>Solver</th><th>Trạng thái</th><th>Lỗi</th></tr></thead><tbody>{items.length === 0 ? <tr><td colSpan={6} className="admin-empty-table">Chưa có validation run.</td></tr> : items.map(item => <tr key={item.id}><td>{new Date(item.createdAt).toLocaleString("vi-VN")}</td><td>{item.topic}</td><td>{item.schemaId} <small>{item.schemaVersion}</small></td><td>{item.solverVersion}</td><td><span className={`admin-status ${item.passed ? "active" : "inactive"}`}><i />{item.passed ? "PASS" : "FAIL"}</span></td><td className="admin-error-cell">{item.errorMessage || "—"}</td></tr>)}</tbody></table></div></Panel>}</div>;
}




