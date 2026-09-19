import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";
import { adminUsers } from "../../api/adminApi";
import { archiveSchoolClass, assignClassTeacher, createSchoolClass, enrollClassStudent, removeClassStudent, removeClassTeacher, schoolClass, schoolClasses, transferClassStudent, updateSchoolClass } from "../../api/schoolApi";
import type { SchoolClassDetail, SchoolClassRequest, SchoolClassSummary } from "../../types/school";
import type { User } from "../../types/physlive";
import { apiMessage } from "../../components/roles/admin/adminUtils";
import { usePhysliveStore } from "../../store/usePhysliveStore";

const defaultSchoolYear = () => {
  const now = new Date();
  const start = now.getMonth() >= 7 ? now.getFullYear() : now.getFullYear() - 1;
  return `${start}-${start + 1}`;
};
const blank = (): SchoolClassRequest => ({ name: "", gradeLevel: 10, schoolYear: defaultSchoolYear(), subject: "Vật lý" });

export default function SchoolClasses() {
  const schoolId = usePhysliveStore(state => state.user?.schoolId);
  const [items, setItems] = useState<SchoolClassSummary[]>([]);
  const [users, setUsers] = useState<User[]>([]);
  const [detail, setDetail] = useState<SchoolClassDetail | null>(null);
  const [form, setForm] = useState<SchoolClassRequest>(blank);
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState(false);
  const [teacherId, setTeacherId] = useState("");
  const [studentId, setStudentId] = useState("");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [formError, setFormError] = useState("");
  const teachers = useMemo(() => users.filter(item => item.role === "TEACHER" && item.active !== false), [users]);
  const students = useMemo(() => users.filter(item => item.role === "STUDENT" && item.active !== false), [users]);
  const load = useCallback(async () => {
    if (!schoolId) return;
    setLoading(true);
    setError("");
    try {
      const [next, people] = await Promise.all([schoolClasses(schoolId), adminUsers(schoolId)]);
      setItems(next);
      setUsers(people);
    } catch (err) {
      setError(apiMessage(err, "Không thể tải danh sách lớp."));
    } finally {
      setLoading(false);
    }
  }, [schoolId]);
  useEffect(() => {
    const timer = window.setTimeout(() => { void load(); }, 0);
    return () => window.clearTimeout(timer);
  }, [load]);
  const submit = async (event: FormEvent) => {
    event.preventDefault(); if (!schoolId || busy) return; setBusy(true); setFormError("");
    try { const next = editing && detail ? await updateSchoolClass(schoolId, detail.id, form) : await createSchoolClass(schoolId, form); setDetail(next); setEditing(false); setForm(blank()); setShowForm(false); await load(); }
    catch (err) { setFormError(apiMessage(err, "Không thể lưu lớp.")); } finally { setBusy(false); }
  };
  const openCreateForm = () => { setEditing(false); setForm(blank()); setFormError(""); setShowForm(true); };
  const closeForm = () => { if (busy) return; setShowForm(false); setEditing(false); setForm(blank()); setFormError(""); };
  const action = async (work: () => Promise<unknown>) => {
    if (busy) return;
    setBusy(true);
    setError("");
    try {
      await work();
      await load();
      if (schoolId && detail) {
        const refreshed = await schoolClass(schoolId, detail.id).catch(() => null);
        setDetail(refreshed);
      }
    } catch (err) {
      setError(apiMessage(err, "Không thể cập nhật lớp."));
    } finally {
      setBusy(false);
    }
  };
  if (!schoolId) return null;
  return <div className="admin-content">
    {error && <div className="admin-error-banner" role="alert">{error}<button type="button" className="admin-inline-button" onClick={() => void load()}>Tải lại</button></div>}
    <div className="school-classes-grid"><section className="admin-panel"><div className="admin-panel-heading"><div><h2>Danh sách lớp</h2><p className="admin-panel-description">Tạo lớp, phân công giáo viên và xếp học sinh vào lớp.</p></div><button type="button" className="admin-primary-button" onClick={openCreateForm}>Tạo lớp</button></div>{loading ? <p role="status">Đang tải…</p> : <div className="admin-table-scroll"><table className="admin-table"><thead><tr><th>Lớp</th><th>Khối</th><th>Năm học</th><th>Giáo viên</th><th>Học sinh</th><th /></tr></thead><tbody>{items.length === 0 ? <tr><td colSpan={6}>Chưa có lớp. Chọn “Tạo lớp” để bắt đầu.</td></tr> : items.map(item => <tr key={item.id} className={detail?.id === item.id ? "admin-row-selected" : ""}><td><button type="button" className="admin-table-link" onClick={() => void schoolClass(schoolId, item.id).then(setDetail).catch(err => setError(apiMessage(err, "Không thể tải lớp.")))}>{item.name}</button></td><td>{item.gradeLevel}</td><td>{item.schoolYear}</td><td>{item.teacherCount}</td><td>{item.studentCount}</td><td><button type="button" className="admin-inline-button" onClick={() => { void schoolClass(schoolId, item.id).then(next => { setDetail(next); setEditing(true); setForm({ name: next.name, gradeLevel: next.gradeLevel, schoolYear: next.schoolYear, subject: next.subject ?? "" }); setFormError(""); setShowForm(true); }).catch(err => setError(apiMessage(err, "Không thể tải lớp."))) }}>Sửa</button><button type="button" className="admin-inline-button danger" disabled={busy} onClick={() => { if (window.confirm(`Tắt lớp ${item.name}?`)) void action(() => archiveSchoolClass(schoolId, item.id)); }}>Tắt</button></td></tr>)}</tbody></table></div>}</section>
      {detail && <section className="admin-panel"><div className="admin-panel-heading"><div><h2>{detail.name}</h2><p className="admin-panel-description">Khối {detail.gradeLevel} · {detail.schoolYear} · {detail.subject || "Chưa chọn môn"}</p></div><button type="button" className="admin-inline-button" onClick={() => setDetail(null)}>Đóng</button></div><div className="admin-class-members"><h3>Giáo viên</h3><div className="admin-member-add"><select value={teacherId} onChange={e => setTeacherId(e.target.value)}><option value="">Chọn giáo viên</option>{teachers.filter(user => !detail.teachers.some(item => item.id === user.id)).map(user => <option key={user.id} value={user.id}>{user.fullName} · {user.email}</option>)}</select><button type="button" className="admin-secondary-button" disabled={!teacherId || busy} onClick={() => void action(async () => { await assignClassTeacher(schoolId, detail.id, Number(teacherId)); setTeacherId(""); })}>Phân công</button></div><ul>{detail.teachers.map(item => <li key={item.id}><span>{item.fullName} <small>{item.email}</small></span><button type="button" className="admin-inline-button danger" disabled={busy} onClick={() => void action(() => removeClassTeacher(schoolId, detail.id, item.id))}>Gỡ</button></li>)}</ul></div><div className="admin-class-members"><h3>Học sinh ({detail.students.length})</h3><div className="admin-member-add"><select value={studentId} onChange={e => setStudentId(e.target.value)}><option value="">Chọn học sinh</option>{students.filter(user => !detail.students.some(item => item.id === user.id)).map(user => <option key={user.id} value={user.id}>{user.fullName} · {user.email}</option>)}</select><button type="button" className="admin-secondary-button" disabled={!studentId || busy} onClick={() => void action(async () => { await enrollClassStudent(schoolId, detail.id, Number(studentId)); setStudentId(""); })}>Thêm vào lớp</button></div><ul>{detail.students.map(item => <li key={item.id}><span>{item.fullName} <small>{item.email}</small></span><span>{items.some(candidate => candidate.id !== detail.id && candidate.schoolYear === detail.schoolYear) && <select aria-label={`Lớp đích của ${item.fullName}`} defaultValue="" disabled={busy} onChange={event => { const targetClassId = event.target.value; if (targetClassId) void action(() => transferClassStudent(schoolId, targetClassId, item.id)); }}><option value="">Chuyển lớp…</option>{items.filter(candidate => candidate.id !== detail.id && candidate.schoolYear === detail.schoolYear).map(candidate => <option key={candidate.id} value={candidate.id}>{candidate.name}</option>)}</select>}<button type="button" className="admin-inline-button danger" disabled={busy} onClick={() => void action(() => removeClassStudent(schoolId, detail.id, item.id))}>Gỡ</button></span></li>)}</ul></div></section>}
    </div>
    {showForm && <div className="admin-modal-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) closeForm(); }} onKeyDown={event => { if (event.key === "Escape") closeForm(); }}>
      <section className="admin-modal" role="dialog" aria-modal="true" aria-labelledby="school-class-form-title">
        <div className="admin-modal-header"><div><h2 id="school-class-form-title">{editing ? "Cập nhật lớp" : "Tạo lớp mới"}</h2><p>Học sinh chỉ có một lớp đang hoạt động trong cùng năm học.</p></div><button type="button" className="admin-modal-close" aria-label="Đóng" disabled={busy} onClick={closeForm}>×</button></div>
        {formError && <div className="admin-error-banner" role="alert">{formError}</div>}
        <form className="admin-form-grid" onSubmit={submit}><label>Tên lớp<input autoFocus required maxLength={100} value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} placeholder="Ví dụ: 10A1" /></label><label>Khối<select required value={form.gradeLevel} onChange={e => setForm({ ...form, gradeLevel: Number(e.target.value) })}><option value={10}>10</option><option value={11}>11</option><option value={12}>12</option></select></label><label>Năm học<input required maxLength={20} value={form.schoolYear} onChange={e => setForm({ ...form, schoolYear: e.target.value })} placeholder="2026-2027" /></label><label>Môn học<input maxLength={50} value={form.subject} onChange={e => setForm({ ...form, subject: e.target.value })} /></label><div className="admin-form-actions"><button type="button" className="admin-secondary-button" disabled={busy} onClick={closeForm}>Hủy</button><button className="admin-primary-button" type="submit" disabled={busy}>{busy ? "Đang lưu…" : editing ? "Lưu thay đổi" : "Tạo lớp"}</button></div></form>
      </section>
    </div>}
  </div>;
}
