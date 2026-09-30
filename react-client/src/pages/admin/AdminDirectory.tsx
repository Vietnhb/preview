import { useEffect, useRef, useState, type FormEvent } from "react";
import { Avatar, Badge, Button, Callout, Card, Dialog, Flex, Heading, IconButton, Select, Table, Text, TextField } from "@radix-ui/themes";
import { ArrowRightIcon, CheckCircledIcon, Cross2Icon, LockClosedIcon, MagnifyingGlassIcon, PersonIcon, PlusIcon, ReloadIcon } from "@radix-ui/react-icons";
import { AnimatePresence, motion } from "motion/react";
import { Link, useNavigate } from "react-router-dom";
import { adminUsers, createManagedUser, setManagedUserActive, updateManagedUser } from "../../api/adminApi";
import { apiMessage } from "../../components/roles/admin/adminUtils";
import { AccountProfileFields, ResetManagedPasswordDialog } from "../../components/roles/admin/ManagedAccountFields";
import { accountDetails, accountDetailsPayload, blankAccountDetails, type AccountDetails } from "../../components/roles/admin/accountDetailsModel";
import LearningIcon from "../../components/common/LearningIcon";
import { usePhysliveStore } from "../../store/usePhysliveStore";
import { ROLE_NAMES, getRoleLabel } from "../../types/roles";
import type { User } from "../../types/physlive";
import { clearToken } from "../../utils/token";
import "../../styles/admin-directory.css";

const initialForm = { fullName: "", email: "", password: "", ...blankAccountDetails };
const formatDate = (date?: string | null) => date ? new Date(date).toLocaleString("vi-VN") : "Chưa có thông tin";
const roleColors: Record<string, "iris" | "indigo" | "amber" | "cyan" | "green" | "gray"> = { ADMIN: "iris", MANAGER: "indigo", REVIEWER: "amber", SCHOOL: "cyan", STAFF: "green", STUDENT: "gray" };

export default function AdminDirectory() {
  const currentUser = usePhysliveStore(state => state.user);
  const navigate = useNavigate();
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [query, setQuery] = useState("");
  const [role, setRole] = useState("");
  const [status, setStatus] = useState("");
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [creating, setCreating] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [resetUser, setResetUser] = useState<User | null>(null);
  const [form, setForm] = useState(initialForm);
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);
  const [formError, setFormError] = useState("");
  const selected = users.find(item => item.id === selectedId);

  const load = async () => {
    setLoading(true); setError("");
    try { setUsers(await adminUsers()); }
    catch (err) { setError(apiMessage(err, "Không thể tải danh sách tài khoản. Vui lòng thử lại.")); }
    finally { setLoading(false); }
  };
  useEffect(() => { void load(); }, []);
  const filtered = users.filter(item => (!role || item.role === role)
    && (!status || (status === "active" ? item.active !== false : item.active === false))
    && `${item.fullName} ${item.email} ${item.id}`.toLowerCase().includes(query.trim().toLowerCase()));
  const startCreate = () => { if (savingRef.current) return; setSelectedId(null); setEditingId(null); setForm(initialForm); setFormError(""); setSuccess(""); setCreating(true); };
  const startEdit = (user: User) => {
    if (user.role !== "MANAGER" || savingRef.current) return;
    setEditingId(user.id); setForm({ fullName: user.fullName, email: user.email, password: "", ...accountDetails(user) });
    setFormError(""); setSuccess(""); setCreating(true);
  };
  const updateDetails = (details: Partial<AccountDetails>) => setForm(current => ({ ...current, ...details }));
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (savingRef.current) return;
    savingRef.current = true;
    setSaving(true); setFormError("");
    try {
      const payload = { fullName: form.fullName.trim(), role: "MANAGER", ...accountDetailsPayload(form, "MANAGER") };
      const updated = editingId === null
        ? await createManagedUser({ ...payload, email: form.email.trim(), password: form.password })
        : await updateManagedUser(editingId, payload);
      setUsers(items => editingId === null ? [...items, updated] : items.map(user => user.id === updated.id ? updated : user));
      setCreating(false); setForm(initialForm); setSelectedId(updated.id); setEditingId(null);
      setSuccess(`Đã ${editingId === null ? "tạo" : "cập nhật"} tài khoản MANAGER cho ${updated.fullName}.`);
    } catch (err) { setFormError(apiMessage(err, "Không thể lưu tài khoản MANAGER.")); }
    finally { savingRef.current = false; setSaving(false); }
  };
  const toggleManager = async (user: User) => {
    if (user.role !== "MANAGER" || savingRef.current) return;
    savingRef.current = true; setSaving(true); setError(""); setSuccess("");
    try { const updated = await setManagedUserActive(user.id, user.active === false); setUsers(items => items.map(item => item.id === updated.id ? updated : item)); setSuccess(`Đã ${updated.active === false ? "tạm khóa" : "kích hoạt"} ${updated.email}.`); }
    catch (err) { setError(apiMessage(err, "Không thể cập nhật trạng thái MANAGER.")); }
    finally { savingRef.current = false; setSaving(false); }
  };
  const logout = () => { clearToken(); usePhysliveStore.getState().setUser(null); navigate("/login", { replace: true }); };

  return <div className="directory-shell">
    <aside className="directory-sidebar">
      <Link className="directory-brand" to="/admin/users"><img src="/favicon.ico" alt="" /><div>PhysLive<small>ADMIN</small></div></Link>
      <p className="directory-nav-label">Quản trị</p>
      <Link className="directory-nav-active" to="/admin/users"><LearningIcon name="users" />Tài khoản người dùng</Link>
      <Button variant="ghost" className="directory-nav-create" onClick={startCreate}><PlusIcon />Tạo MANAGER</Button>
      <div className="directory-self"><Avatar size="2" src={currentUser?.avatarUrl || undefined} fallback={currentUser?.fullName?.slice(0, 1) || "A"} /><div><strong>{currentUser?.fullName}</strong><small>ADMIN</small></div><IconButton variant="ghost" color="gray" aria-label="Đăng xuất" onClick={logout}><LearningIcon name="logout" /></IconButton></div>
    </aside>
    <main className="directory-main">
      <header className="directory-topbar"><span>Quản trị <span>/</span> Tài khoản</span><Button asChild variant="ghost" color="gray"><Link to="/profile"><PersonIcon />Hồ sơ cá nhân</Link></Button></header>
      <div className="directory-content">
        <div className="directory-heading"><Heading as="h1" size="7">Tài khoản người dùng</Heading>
          <Dialog.Root open={creating} onOpenChange={next => { if (!saving) setCreating(next); }}>
            <Dialog.Trigger><Button size="3" onClick={startCreate}><PlusIcon />Tạo MANAGER</Button></Dialog.Trigger>
            <Dialog.Content maxWidth="520px" onEscapeKeyDown={event => { if (savingRef.current) event.preventDefault(); }} onInteractOutside={event => { if (savingRef.current) event.preventDefault(); }}>
              <Flex justify="between" align="center" mb="3"><Dialog.Title mb="0">{editingId === null ? "Tạo MANAGER" : "Cập nhật MANAGER"}</Dialog.Title><Dialog.Close><IconButton variant="ghost" color="gray" disabled={saving} aria-label="Đóng hồ sơ MANAGER"><Cross2Icon /></IconButton></Dialog.Close></Flex>
              <Dialog.Description size="2" mb="5">MANAGER quản lý trường học, người dùng, nội dung và hoạt động của nền tảng.</Dialog.Description>
              <form onSubmit={submit} className="directory-create-form">
                <label>Họ và tên<TextField.Root required disabled={saving} maxLength={120} autoComplete="name" size="3" value={form.fullName} onChange={e => setForm({ ...form, fullName: e.target.value })} /></label>
                <label>Email<TextField.Root required disabled={saving || editingId !== null} type="email" autoComplete="email" size="3" value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} /></label>
                <AccountProfileFields idPrefix="directory-manager" value={form} name={form.fullName} disabled={saving} onChange={updateDetails} />
                {editingId === null && <label>Mật khẩu ban đầu<TextField.Root required disabled={saving} minLength={8} maxLength={120} type="password" autoComplete="new-password" size="3" value={form.password} onChange={e => setForm({ ...form, password: e.target.value })} /><Text as="span" size="1" color="gray">Tối thiểu 8 ký tự. MANAGER phải đổi mật khẩu khi đăng nhập lần đầu.</Text></label>}
                <Flex align="center" justify="between"><Text size="2" color="gray">Vai trò được cấp</Text><Badge color="indigo">MANAGER</Badge></Flex>
                {formError && <Callout.Root color="red" size="1" role="alert"><Callout.Text>{formError}</Callout.Text></Callout.Root>}
                <Flex justify="end" gap="3"><Dialog.Close><Button variant="soft" color="gray" disabled={saving}>Hủy</Button></Dialog.Close><Button type="submit" loading={saving} disabled={saving || !form.fullName.trim()}>{editingId === null ? "Tạo tài khoản" : "Lưu hồ sơ"}</Button></Flex>
              </form>
            </Dialog.Content>
          </Dialog.Root>
        </div>
        <div className="directory-stats" aria-label="Thống kê tài khoản">
          {[
            { label: 'Tổng tài khoản', value: users.length, color: 'indigo' as const, icon: <PersonIcon /> },
            { label: 'Đang hoạt động', value: users.filter(u => u.active !== false).length, color: 'cyan' as const, icon: <CheckCircledIcon /> },
            { label: 'MANAGER', value: users.filter(u => u.role === 'MANAGER').length, color: 'iris' as const, icon: <LearningIcon name="shield" /> },
            { label: 'Tạm khóa', value: users.filter(u => u.active === false).length, color: 'amber' as const, icon: <LockClosedIcon /> },
          ].map((stat, i) => <motion.div key={stat.label} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * .035 }} whileHover={{ y: -2 }}><Card size="3" className="directory-stat"><Flex justify="between" align="center" gap="3"><Text size="2" color="gray" weight="medium">{stat.label}</Text><Badge color={stat.color} size="3" className="directory-stat-icon">{stat.icon}</Badge></Flex><Heading as="h2" size="8" mt="3">{loading ? '—' : stat.value}</Heading></Card></motion.div>)}
        </div>
        {success && <Callout.Root color="green" mb="4" role="status"><Callout.Icon><CheckCircledIcon /></Callout.Icon><Callout.Text>{success}</Callout.Text></Callout.Root>}
        <div className={`directory-workspace ${selected ? 'with-detail' : ''}`}>
          <Card className="directory-panel" size="1" aria-label="Danh sách tài khoản">
            <div className="directory-panel-heading"><div><Heading as="h2" size="4">Danh sách tài khoản</Heading><Text as="p" size="2" color="gray">{loading ? 'Đang tải dữ liệu…' : `${filtered.length} / ${users.length} tài khoản`}</Text></div><IconButton variant="soft" color="gray" onClick={() => void load()} disabled={loading} aria-label="Làm mới danh sách"><ReloadIcon /></IconButton></div>
            <div className="directory-filters">
              <TextField.Root aria-label="Tìm tài khoản" placeholder="Tìm tên, email hoặc mã user…" value={query} onChange={e => setQuery(e.target.value)} size="3"><TextField.Slot><MagnifyingGlassIcon /></TextField.Slot></TextField.Root>
              <Select.Root value={role || 'all'} onValueChange={value => setRole(value === 'all' ? '' : value)} size="3"><Select.Trigger aria-label="Lọc vai trò" /><Select.Content><Select.Item value="all">Tất cả vai trò</Select.Item>{Object.values(ROLE_NAMES).map(value => <Select.Item key={value} value={value}>{value}</Select.Item>)}</Select.Content></Select.Root>
              <Select.Root value={status || 'all'} onValueChange={value => setStatus(value === 'all' ? '' : value)} size="3"><Select.Trigger aria-label="Lọc trạng thái" /><Select.Content><Select.Item value="all">Tất cả trạng thái</Select.Item><Select.Item value="active">Hoạt động</Select.Item><Select.Item value="inactive">Tạm khóa</Select.Item></Select.Content></Select.Root>
            </div>
            {error ? <div className="directory-empty" role="alert"><p>{error}</p><Button variant="soft" onClick={() => void load()}>Thử lại</Button></div> : loading ? <div className="directory-empty" role="status">Đang tải danh sách tài khoản…</div> : filtered.length === 0 ? <div className="directory-empty"><MagnifyingGlassIcon /><Heading as="h3" size="4">Chưa tìm thấy tài khoản</Heading><p>Thử tìm theo tên hoặc thay đổi bộ lọc.</p><Button variant="soft" onClick={() => { setQuery(''); setRole(''); setStatus(''); }}>Xóa bộ lọc</Button></div> :
              <Table.Root size="3" className="directory-table"><Table.Header><Table.Row><Table.ColumnHeaderCell>Người dùng</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Vai trò</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Trạng thái</Table.ColumnHeaderCell><Table.ColumnHeaderCell><span className="sr-only">Chi tiết</span></Table.ColumnHeaderCell></Table.Row></Table.Header><Table.Body>{filtered.map(item => <Table.Row key={item.id} className={selectedId === item.id ? 'selected' : ''}><Table.Cell><div className="directory-person"><Avatar size="2" src={item.avatarUrl || undefined} color={roleColors[item.role]} fallback={(item.fullName || item.email).slice(0, 1).toUpperCase()} /><div><Text weight="medium">{item.fullName || 'Chưa có họ tên'}</Text><Text as="div" color="gray" size="2">{item.email}</Text></div></div></Table.Cell><Table.Cell><Badge color={roleColors[item.role]}>{item.role}</Badge></Table.Cell><Table.Cell><Badge color={item.active === false ? 'amber' : 'cyan'} variant="soft">{item.active === false ? 'Tạm khóa' : 'Hoạt động'}</Badge></Table.Cell><Table.Cell><Button variant="ghost" aria-label={`Xem chi tiết ${item.fullName || item.email}`} onClick={() => { setSelectedId(item.id); setCreating(false); }}>Chi tiết<ArrowRightIcon /></Button></Table.Cell></Table.Row>)}</Table.Body></Table.Root>}
            <footer className="directory-table-footer"><LearningIcon name="shield" />Quản lý MANAGER · Xem thông tin các vai trò khác</footer>
          </Card>
          <AnimatePresence initial={false}>{selected && <motion.div key={selected.id} initial={{ opacity: 0, x: 12 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0 }}><Card className="directory-detail" size="3" aria-label="Thông tin tài khoản">
            <Flex justify="between" align="center" mb="5"><Heading as="h2" size="4">Thông tin tài khoản</Heading><IconButton variant="ghost" color="gray" aria-label="Đóng chi tiết" onClick={() => setSelectedId(null)}><Cross2Icon /></IconButton></Flex>
            <div className="directory-detail-identity"><Avatar size="5" src={selected.avatarUrl || undefined} color={roleColors[selected.role]} fallback={(selected.fullName || selected.email).slice(0, 1)} /><Heading as="h3" size="4" mt="3" mb="2">{selected.fullName}</Heading><Badge color={roleColors[selected.role]}>{selected.role}</Badge></div>
            <dl>{[['Mã tài khoản', `#${selected.id}`], ['Email', selected.email], ['Vai trò', getRoleLabel(selected.role)], ['Trạng thái', selected.active === false ? 'Tạm khóa' : 'Hoạt động'], ['Ngày sinh', selected.dateOfBirth ? new Date(`${selected.dateOfBirth}T00:00:00`).toLocaleDateString('vi-VN') : 'Chưa cập nhật'], ['Đăng nhập gần nhất', formatDate(selected.lastLogin)], ['Trường', selected.schoolName || selected.schoolId || selected.institutionId || 'Tài khoản cấp nền tảng'], ...(selected.role === 'STAFF' ? [['Loại giáo viên', selected.staffType === 'DEPARTMENT_HEAD' ? 'Trưởng bộ môn' : 'Giáo viên']] : []), ...(selected.role === 'REVIEWER' ? [['Quyền', [selected.reviewerCanEdit && 'Chỉnh sửa', selected.reviewerCanReview && 'Kiểm duyệt'].filter(Boolean).join(' · ') || 'Chưa cấp quyền']] : []), ['Đổi mật khẩu', selected.mustChangePassword ? 'Bắt buộc khi đăng nhập' : 'Đã hoàn tất']].map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>
            {selected.role === "MANAGER" && <Flex direction="column" gap="3" mt="4"><Button variant="surface" disabled={saving} onClick={() => startEdit(selected)}>Sửa hồ sơ</Button><Button variant="surface" disabled={saving} onClick={() => setResetUser(selected)}>Đặt lại mật khẩu</Button><Button variant="soft" color={selected.active === false ? "cyan" : "red"} disabled={saving} onClick={() => void toggleManager(selected)}>{selected.active === false ? "Kích hoạt lại" : "Tạm khóa"}</Button></Flex>}
          </Card></motion.div>}</AnimatePresence>
        </div>
      </div>
    </main>
    <ResetManagedPasswordDialog user={resetUser} onClose={() => setResetUser(null)} onUpdated={updated => { setUsers(items => items.map(item => item.id === updated.id ? updated : item)); setSuccess(`Đã đặt lại mật khẩu cho ${updated.email}.`); }} />
  </div>;
}
