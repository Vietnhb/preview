import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { Badge, Button, Card, Dialog, IconButton, Select, Spinner, Table, TextField } from "@radix-ui/themes";
import { Cross2Icon, PlusIcon } from "@radix-ui/react-icons";
import { motion, useReducedMotion } from "motion/react";
import { adminUsers } from "../../api/adminApi";
import { archiveSchoolClass, assignClassTeacher, createSchoolClass, enrollClassStudent, removeClassStudent, removeClassTeacher, schoolClass, schoolClasses, transferClassStudent, updateSchoolClass } from "../../api/schoolApi";
import type { SchoolClassDetail, SchoolClassRequest, SchoolClassSummary } from "../../types/school";
import type { User } from "../../types/physlive";
import { apiMessage } from "../../components/roles/admin/adminUtils";
import { usePhysliveStore } from "../../store/usePhysliveStore";
import SchoolBulkImport from "../../features/school-import/SchoolBulkImport";

const defaultSchoolYear = () => {
  const now = new Date();
  const start = now.getMonth() >= 7 ? now.getFullYear() : now.getFullYear() - 1;
  return `${start}-${start + 1}`;
};
const blank = (): SchoolClassRequest => ({ name: "", gradeLevel: 10, schoolYear: defaultSchoolYear(), subject: "Vật lý" });

function SchoolSelect({ value, disabled, label, placeholder, options, onValueChange }: Readonly<{ value?: string; disabled?: boolean; label: string; placeholder: string; options: { value: string; label: string }[]; onValueChange: (value: string) => void }>) {
  return <Select.Root size="2" value={value} defaultValue="" disabled={disabled} onValueChange={onValueChange}><Select.Trigger placeholder={placeholder} aria-label={label} style={{ width: "100%", minWidth: 0 }} /><Select.Content>{options.map(option => <Select.Item key={option.value} value={option.value}>{option.label}</Select.Item>)}</Select.Content></Select.Root>;
}

export default function SchoolClasses() {
  const reducedMotion = useReducedMotion();
  const nameInputRef = useRef<HTMLInputElement>(null);
  const returnFocusRef = useRef<HTMLButtonElement | null>(null);
  const schoolId = usePhysliveStore(state => state.user?.schoolId);
  const canCreateClass = usePhysliveStore(state => state.user?.role === "SCHOOL");
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
  const teachers = useMemo(() => users.filter(item => item.role === "STAFF" && item.active !== false), [users]);
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
  const refreshImported = async () => {
    await load();
    if (schoolId && detail) setDetail(await schoolClass(schoolId, detail.id));
  };
  if (!schoolId) return null;
  return <div className="admin-content">
    <header className="admin-content-header"><div><h1 className="admin-content-title">Lớp học</h1></div></header>
    {error && <div className="admin-error-banner" role="alert">{error}<Button variant="soft" color="red" onClick={() => void load()}>Tải lại</Button></div>}
    <motion.div className="school-classes-grid" initial={reducedMotion ? false : { opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: reducedMotion ? 0 : .18 }}>
      <Card asChild size="3"><section className="admin-panel">
        <div className="admin-panel-heading"><div><h2>Danh sách lớp</h2></div><div className="school-bulk-tools">{canCreateClass && <><SchoolBulkImport schoolId={schoolId} kind="CLASSES" onImported={refreshImported} /><Button onClick={event => { returnFocusRef.current = event.currentTarget; openCreateForm(); }}><PlusIcon /> Tạo lớp</Button></>}</div></div>
        {loading ? <p role="status"><Spinner /> Đang tải…</p> : <div className="admin-table-scroll"><Table.Root size="2" variant="surface"><Table.Header><Table.Row><Table.ColumnHeaderCell>Lớp</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Khối</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Năm học</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Giáo viên</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Học sinh</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Thao tác</Table.ColumnHeaderCell></Table.Row></Table.Header><Table.Body>
          {items.length === 0 ? <Table.Row><Table.Cell colSpan={6}>{canCreateClass ? "Chưa có lớp. Chọn Tạo lớp hoặc Nhập CSV để bắt đầu." : "Trường chưa có lớp đang hoạt động."}</Table.Cell></Table.Row> : items.map(item => <Table.Row key={item.id} className={detail?.id === item.id ? "admin-row-selected" : ""} style={detail?.id === item.id ? { backgroundColor: "var(--indigo-3)" } : undefined}>
            <Table.RowHeaderCell><Button variant="ghost" onClick={() => void schoolClass(schoolId, item.id).then(setDetail).catch(err => setError(apiMessage(err, "Không thể tải lớp.")))}>{item.name}</Button></Table.RowHeaderCell>
            <Table.Cell><Badge color="indigo" variant="soft">{item.gradeLevel}</Badge></Table.Cell><Table.Cell>{item.schoolYear}</Table.Cell><Table.Cell>{item.teacherCount}</Table.Cell><Table.Cell>{item.studentCount}</Table.Cell>
            <Table.Cell><Button size="1" variant="soft" color="cyan" mr="2" onClick={event => {
              returnFocusRef.current = event.currentTarget;
              void schoolClass(schoolId, item.id).then(next => { setDetail(next); setEditing(true); setForm({ name: next.name, gradeLevel: next.gradeLevel, schoolYear: next.schoolYear, subject: next.subject ?? "" }); setFormError(""); setShowForm(true); }).catch(err => setError(apiMessage(err, "Không thể tải lớp.")));
            }}>Sửa</Button><Button size="1" variant="soft" color="red" disabled={busy} onClick={() => { if (window.confirm(`Tắt lớp ${item.name}?`)) void action(() => archiveSchoolClass(schoolId, item.id)); }}>Tắt</Button></Table.Cell>
          </Table.Row>)}
        </Table.Body></Table.Root></div>}
      </section></Card>
      {detail && <Card asChild size="3"><motion.section className="admin-panel" initial={reducedMotion ? false : { opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: reducedMotion ? 0 : .18 }}>
        <div className="admin-panel-heading"><div><h2>{detail.name}</h2><p className="admin-panel-description">Khối {detail.gradeLevel} · {detail.schoolYear} · {detail.subject || "Chưa chọn môn"}</p></div><Button variant="ghost" color="gray" onClick={() => setDetail(null)}>Đóng</Button></div>
        <div className="admin-class-members"><div className="admin-panel-heading"><h3>Giáo viên</h3><SchoolBulkImport schoolId={schoolId} kind="TEACHER_ASSIGNMENTS" label="Phân công bằng CSV" disabled={busy} onImported={refreshImported} /></div><div className="admin-member-add"><SchoolSelect value={teacherId} label="Chọn giáo viên" placeholder="Chọn giáo viên" options={teachers.filter(user => !detail.teachers.some(item => item.id === user.id)).map(user => ({ value: String(user.id), label: `${user.fullName} · ${user.email}` }))} onValueChange={setTeacherId} /><Button variant="soft" disabled={!teacherId || busy} onClick={() => void action(async () => { await assignClassTeacher(schoolId, detail.id, Number(teacherId)); setTeacherId(""); })}>Phân công</Button></div>
          <ul>{detail.teachers.map(item => <li key={item.id}><span>{item.fullName} <small>{item.email}</small></span><Button size="1" variant="soft" color="red" disabled={busy} onClick={() => void action(() => removeClassTeacher(schoolId, detail.id, item.id))}>Gỡ</Button></li>)}</ul>
        </div>
        <div className="admin-class-members"><div className="admin-panel-heading"><h3>Học sinh <Badge color="cyan" ml="2">{detail.students.length}</Badge></h3><SchoolBulkImport schoolId={schoolId} kind="ENROLLMENTS" label="Xếp lớp bằng CSV" disabled={busy} onImported={refreshImported} /></div><div className="admin-member-add"><SchoolSelect value={studentId} label="Chọn học sinh" placeholder="Chọn học sinh" options={students.filter(user => !detail.students.some(item => item.id === user.id)).map(user => ({ value: String(user.id), label: `${user.fullName} · ${user.email}` }))} onValueChange={setStudentId} /><Button variant="soft" color="cyan" disabled={!studentId || busy} onClick={() => void action(async () => { await enrollClassStudent(schoolId, detail.id, Number(studentId)); setStudentId(""); })}>Thêm vào lớp</Button></div>
          <ul>{detail.students.map(item => <li key={item.id}><span>{item.fullName} <small>{item.email}</small></span><span>{items.some(candidate => candidate.id !== detail.id && candidate.schoolYear === detail.schoolYear) && <SchoolSelect label={`Lớp đích của ${item.fullName}`} placeholder="Chuyển lớp…" disabled={busy} options={items.filter(candidate => candidate.id !== detail.id && candidate.schoolYear === detail.schoolYear).map(candidate => ({ value: candidate.id, label: candidate.name }))} onValueChange={targetClassId => { if (targetClassId) void action(() => transferClassStudent(schoolId, targetClassId, item.id)); }} />}<Button size="1" variant="soft" color="red" disabled={busy} onClick={() => void action(() => removeClassStudent(schoolId, detail.id, item.id))}>Gỡ</Button></span></li>)}</ul>
        </div>
      </motion.section></Card>}
    </motion.div>
    <Dialog.Root open={showForm} onOpenChange={open => { if (!open) closeForm(); }}><Dialog.Content maxWidth="620px" size="3" onEscapeKeyDown={event => { if (busy) event.preventDefault(); }} onPointerDownOutside={event => { if (busy) event.preventDefault(); }} onOpenAutoFocus={event => { event.preventDefault(); nameInputRef.current?.focus(); }} onCloseAutoFocus={event => { event.preventDefault(); returnFocusRef.current?.focus(); }}>
      <div className="admin-modal-header"><div><Dialog.Title>{editing ? "Cập nhật lớp" : "Tạo lớp mới"}</Dialog.Title><Dialog.Description size="2">Học sinh chỉ có một lớp đang hoạt động trong cùng năm học.</Dialog.Description></div><Dialog.Close><IconButton variant="soft" color="gray" aria-label="Đóng" disabled={busy}><Cross2Icon /></IconButton></Dialog.Close></div>
      {formError && <div className="admin-error-banner" role="alert">{formError}</div>}
      <form className="admin-form-grid" onSubmit={submit}>
        <label>Tên lớp<TextField.Root ref={nameInputRef} required maxLength={100} value={form.name} onChange={event => setForm({ ...form, name: event.target.value })} placeholder="Ví dụ: 10A1" aria-label="Tên lớp" /></label>
        <label>Khối<SchoolSelect value={String(form.gradeLevel)} label="Khối" placeholder="Chọn khối" options={[10, 11, 12].map(value => ({ value: String(value), label: String(value) }))} onValueChange={value => setForm({ ...form, gradeLevel: Number(value) })} /></label>
        <label>Năm học<TextField.Root required maxLength={20} value={form.schoolYear} onChange={event => setForm({ ...form, schoolYear: event.target.value })} placeholder="2026-2027" aria-label="Năm học" /></label>
        <label>Môn học<TextField.Root maxLength={50} value={form.subject} onChange={event => setForm({ ...form, subject: event.target.value })} aria-label="Môn học" /></label>
        <div className="admin-form-actions"><Button variant="soft" color="gray" type="button" disabled={busy} onClick={closeForm}>Hủy</Button><Button type="submit" loading={busy} disabled={busy}>{editing ? "Lưu thay đổi" : "Tạo lớp"}</Button></div>
      </form>
    </Dialog.Content></Dialog.Root>
  </div>;
}
