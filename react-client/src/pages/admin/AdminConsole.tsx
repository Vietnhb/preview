import { useState, type FormEvent } from "react";
import api from "../api/axios";
import type { Curriculum, User } from "../types/physlive";
import { usePhysliveStore } from "../store/usePhysliveStore";
import { Access, Badge, LoadState, Panel, Shell } from "../components/operations/OperationsKit";
import { useAction, useResource } from "../components/operations/operationsData";

type ManagedUser = User & { institutionId?: string };
type School = { id: string; code: string; name: string; address: string; active: boolean };
type ValidationRow = { id: string; submissionId: string; topic: string; schemaId: string; schemaVersion: string; solverVersion: string; passed: boolean; status: string; errorMessage?: string; createdAt: string };
const tabs = [
  { id: "users", label: "Tài khoản", detail: "Vai trò & quyền truy cập" },
  { id: "schools", label: "Trường học", detail: "Đơn vị & thành viên" },
  { id: "curriculum", label: "Chương trình", detail: "Chủ đề được sử dụng" },
  { id: "quality", label: "Validation", detail: "Chất lượng & truy vết" },
];
export default function Admin() { return <Access><AdminPage /></Access>; }
function AdminPage() {
  const [tab, setTab] = useState("users");
  return <Shell tab={tab} setTab={setTab} tabs={tabs}>{tab === "users" ? <Accounts /> : tab === "schools" ? <Schools /> : tab === "curriculum" ? <Topics /> : <Quality />}</Shell>;
}
function Accounts() {
  const users = useResource<ManagedUser[]>("/admin/users"); const schools = useResource<School[]>("/admin/schools");
  const currentUser = usePhysliveStore(s => s.user); const action = useAction();
  const [query, setQuery] = useState(""); const [role, setRole] = useState("");
  const [editing, setEditing] = useState<ManagedUser | "new" | null>(null);
  const [suspending, setSuspending] = useState<ManagedUser | null>(null);
  const filtered = (users.data ?? []).filter(u => (!role || u.role === role) && `${u.fullName} ${u.email}`.toLowerCase().includes(query.toLowerCase()));
  const submit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault(); const body = Object.fromEntries(new FormData(e.currentTarget)); if (!body.institutionId) delete body.institutionId;
    if (await action.run(() => editing === "new" ? api.post("/admin/users", body) : api.put(`/admin/users/${editing?.id}`, body))) { setEditing(null); users.refresh(); }
  };
  return <>
    <div className="ops-metrics"><Metric title="Tổng tài khoản" value={users.data?.length} /><Metric title="Giáo viên" value={users.data?.filter(u => u.role === "TEACHER").length} /><Metric title="Đang tạm khóa" value={users.data?.filter(u => !u.active).length} /></div>
    <Panel title="Tài khoản hệ thống" caption="Quản lý giáo viên, reviewer và quyền truy cập theo trường." action={<button onClick={() => { setEditing("new"); setSuspending(null); }}>+ Tạo tài khoản</button>}>
      <LoadState {...users} />{action.feedback}
      {editing && <form className="ops-form" onSubmit={submit} key={editing === "new" ? "new" : editing.id}>
        <h3>{editing === "new" ? "Tài khoản mới" : `Chỉnh sửa · ${editing.email}`}</h3>
        <fieldset disabled={action.busy} className="ops-form-grid" style={{ border: 0, padding: 0 }}>
          <label className="ops-field">Họ và tên<input name="fullName" required maxLength={200} defaultValue={editing === "new" ? "" : editing.fullName} /></label>
          {editing === "new" && <label className="ops-field">Email<input type="email" name="email" required /></label>}
          <label className="ops-field">Vai trò<select name="role" defaultValue={editing === "new" ? "TEACHER" : editing.role}>{["TEACHER", "REVIEWER", "STUDENT", "ADMIN"].map(r => <option key={r}>{r}</option>)}</select></label>
          <label className="ops-field">Trường học<select name="institutionId" defaultValue={editing === "new" ? "" : editing.institutionId ?? ""}><option value="">Không liên kết</option>{schools.data?.map(s => <option key={s.id} value={s.id} disabled={!s.active}>{s.name}{!s.active ? " (ngừng hoạt động)" : ""}</option>)}</select></label>
          {editing === "new" && <label className="ops-field">Mật khẩu ban đầu<input type="password" name="password" minLength={8} autoComplete="new-password" required /></label>}
        </fieldset><div className="ops-actions"><button disabled={action.busy || schools.loading}>{action.busy ? "Đang lưu…" : "Lưu tài khoản"}</button><button className="secondary" type="button" disabled={action.busy} onClick={() => setEditing(null)}>Hủy</button></div><LoadState {...schools} />
      </form>}
      {suspending && <div className="ops-form" role="alert"><h3>Tạm khóa {suspending.fullName}?</h3><p>Tài khoản này sẽ không thể tiếp tục truy cập. Dữ liệu đã tạo vẫn được giữ lại.</p><div className="ops-actions"><button className="danger" disabled={action.busy} onClick={() => void action.run(() => api.put(`/admin/users/${suspending.id}/suspend`), "Đã tạm khóa tài khoản.").then(ok => { if (ok) { setSuspending(null); users.refresh(); } })}>Xác nhận tạm khóa</button><button className="secondary" disabled={action.busy} onClick={() => setSuspending(null)}>Hủy</button></div></div>}
      <div className="ops-toolbar"><input aria-label="Tìm tài khoản" placeholder="Tìm theo tên hoặc email…" value={query} onChange={e => setQuery(e.target.value)} /><select aria-label="Lọc vai trò" value={role} onChange={e => setRole(e.target.value)}><option value="">Tất cả vai trò</option>{["TEACHER", "REVIEWER", "ADMIN", "STUDENT"].map(r => <option key={r}>{r}</option>)}</select><button className="secondary" disabled={users.loading} onClick={users.refresh}>Làm mới</button></div>
      <div className="table-wrap"><table><thead><tr><th>Thành viên</th><th>Vai trò</th><th>Trường học</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>{filtered.map(u => <tr key={u.id}><td><strong>{u.fullName}</strong><small>{u.email}</small></td><td>{u.role}</td><td>{schools.data?.find(s => s.id === u.institutionId)?.name ?? u.institutionId ?? "—"}</td><td><Badge value={u.active ? "ACTIVE" : "SUSPENDED"} /></td><td><div className="ops-actions"><button className="secondary" disabled={action.busy} onClick={() => setEditing(u)}>Sửa</button><button className={u.active ? "danger" : "secondary"} disabled={action.busy || u.id === currentUser?.id} onClick={() => u.active ? setSuspending(u) : void action.run(() => api.put(`/admin/users/${u.id}/restore`), "Đã khôi phục tài khoản.").then(ok => { if (ok) users.refresh(); })}>{u.active ? "Tạm khóa" : "Khôi phục"}</button></div></td></tr>)}</tbody></table></div>
      {!users.loading && !users.error && !filtered.length && <p className="ops-empty">Không có tài khoản phù hợp.</p>}
    </Panel></>;
}
function Schools() {
  const resource = useResource<School[]>("/admin/schools"); const action = useAction(); const [editing, setEditing] = useState<School | "new" | null>(null);
  const submit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault(); const form = new FormData(e.currentTarget); const body = { ...Object.fromEntries(form), active: form.get("active") === "true" };
    if (await action.run(() => editing === "new" ? api.post("/admin/schools", body) : api.put(`/admin/schools/${editing?.id}`, body))) { setEditing(null); resource.refresh(); }
  };
  return <Panel title="Danh mục trường học" caption="Ngừng hoạt động trường không xóa tài khoản hoặc dữ liệu lịch sử." action={<button onClick={() => setEditing("new")}>+ Thêm trường</button>}>
    <LoadState {...resource} />{action.feedback}
    {editing && <form className="ops-form" onSubmit={submit} key={editing === "new" ? "new" : editing.id}><div className="ops-form-grid">
      <label className="ops-field">Mã trường<input name="code" required maxLength={80} readOnly={editing !== "new"} defaultValue={editing === "new" ? "" : editing.code} /></label>
      <label className="ops-field">Tên trường<input name="name" required maxLength={200} defaultValue={editing === "new" ? "" : editing.name} /></label>
      <label className="ops-field">Địa chỉ<input name="address" maxLength={300} defaultValue={editing === "new" ? "" : editing.address} /></label>
      <label className="ops-field">Trạng thái<select name="active" defaultValue={editing === "new" ? "true" : String(editing.active)}><option value="true">Hoạt động</option><option value="false">Ngừng hoạt động</option></select></label>
    </div><div className="ops-actions"><button disabled={action.busy}>Lưu trường học</button><button type="button" className="secondary" disabled={action.busy} onClick={() => setEditing(null)}>Hủy</button></div></form>}
    <div className="table-wrap"><table><thead><tr><th>Mã</th><th>Trường học</th><th>Trạng thái</th><th /></tr></thead><tbody>{resource.data?.map(s => <tr key={s.id}><td>{s.code}</td><td><strong>{s.name}</strong><small>{s.address}</small></td><td><Badge value={s.active ? "ACTIVE" : "RETIRED"} /></td><td><button className="secondary" disabled={action.busy} onClick={() => setEditing(s)}>Chỉnh sửa</button></td></tr>)}</tbody></table></div>
    {!resource.loading && !resource.error && !resource.data?.length && <p className="ops-empty">Chưa có trường học. Thêm trường để liên kết tài khoản.</p>}
  </Panel>;
}
function Topics() {
  const resource = useResource<Curriculum>("/admin/curriculum"); const action = useAction();
  return <Panel title="Curriculum topics" caption="Topic được bật mới xuất hiện trong chương trình học của giáo viên." action={<button className="secondary" onClick={resource.refresh} disabled={resource.loading}>Làm mới</button>}>
    <LoadState {...resource} />{action.feedback}
    {resource.data?.topics.map(t => <div className="ops-topic" key={t.id}><div><h3>{t.name}</h3><p>{t.modules.length} module · {t.modules.reduce((n, m) => n + m.levels.reduce((sum, l) => sum + l.lessons.length, 0), 0)} bài học</p></div><div className="ops-actions"><Badge value={t.enabled ? "ACTIVE" : "RETIRED"} /><button className="secondary" disabled={action.busy} onClick={() => void action.run(() => api.put(`/admin/topics/${t.id}/toggle`)).then(ok => { if (ok) resource.refresh(); })}>{t.enabled ? "Tắt chủ đề" : "Bật chủ đề"}</button></div></div>)}
    {!resource.loading && !resource.error && !resource.data?.topics.length && <p className="ops-empty">Chưa có chủ đề trong chương trình.</p>}
  </Panel>;
}
function Metric({ title, value }: { title: string; value?: string | number }) { return <div className="ops-metric"><span>{title}</span><strong>{value ?? "—"}</strong></div>; }
function Quality() {
  const resource = useResource<ValidationRow[]>("/admin/validation-runs"); const [topic, setTopic] = useState(""); const [failedOnly, setFailedOnly] = useState(false); const [query, setQuery] = useState("");
  const all = resource.data ?? []; const topicRows = all.filter(v => !topic || v.topic === topic); const failed = topicRows.filter(v => !v.passed).length;
  const rows = topicRows.filter(v => (!failedOnly || !v.passed) && `${v.submissionId} ${v.schemaId}`.includes(query));
  return <><div className="ops-metrics"><Metric title="Lượt validation trong bộ lọc" value={resource.data ? topicRows.length : undefined} /><Metric title="Không đạt" value={resource.data ? failed : undefined} /><Metric title="Validation failure rate" value={resource.data ? topicRows.length ? `${(failed / topicRows.length * 100).toFixed(1)}%` : "Chưa có dữ liệu" : undefined} /></div>
    <Panel title="Theo dõi validation" caption="Mỗi dòng là một lượt kiểm tra đã ghi nhận; một submission có thể có nhiều lượt." action={<button className="secondary" disabled={resource.loading} onClick={resource.refresh}>Làm mới</button>}>
      <LoadState {...resource} /><div className="ops-toolbar"><input aria-label="Tìm submission" placeholder="Submission ID hoặc schema…" value={query} onChange={e => setQuery(e.target.value)} /><select aria-label="Lọc chủ đề" value={topic} onChange={e => setTopic(e.target.value)}><option value="">Tất cả chủ đề</option>{[...new Set(all.map(v => v.topic).filter(Boolean))].map(t => <option key={t}>{t}</option>)}</select><label><input type="checkbox" checked={failedOnly} onChange={e => setFailedOnly(e.target.checked)} /> Chỉ lỗi</label></div>
      <div className="table-wrap"><table><thead><tr><th>Submission / thời gian</th><th>Schema / solver</th><th>Kết quả</th><th>Chi tiết</th></tr></thead><tbody>{rows.map(v => <tr key={v.id}><td><small>{v.submissionId}</small><small>{new Date(v.createdAt).toLocaleString("vi-VN")}</small></td><td>{v.schemaId} @{v.schemaVersion}<small>{v.solverVersion}</small></td><td><Badge value={v.passed ? "PASS" : "FAIL"} /></td><td>{v.errorMessage || v.status}</td></tr>)}</tbody></table></div>
      {!resource.loading && !resource.error && !rows.length && <p className="ops-empty">Không có lượt validation phù hợp.</p>}
    </Panel></>;
}
