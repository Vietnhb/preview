import { useCallback, useEffect, useMemo, useState } from "react";
import { Navigate, useSearchParams } from "react-router-dom";
import PhysicsScene from "../components/PhysicsScene";
import { getSimulation } from "../api/physliveApi";
import type { Simulation } from "../types/physlive";
import "../styles/assignment-studio.css";
import { createAssignment, personalLibrary, studentAssignments, studentOptions, teacherAssignments } from "../api/physliveApi";
import type { Assignment, LibraryItem, StudentOption } from "../types/physlive";
import { usePhysliveStore } from "../store/usePhysliveStore";

const questionPrompt = (questions: unknown) => typeof questions === "object" && questions !== null && "prompt" in questions && typeof (questions as { prompt?: unknown }).prompt === "string" ? (questions as { prompt: string }).prompt : "";

export default function Assignments({ studio = false }: { studio?: boolean }) {
  const user = usePhysliveStore(state => state.user);
  const [searchParams] = useSearchParams();
  const [items, setItems] = useState<Assignment[]>([]);
  const [saved, setSaved] = useState<LibraryItem[]>([]);
  const [students, setStudents] = useState<StudentOption[]>([]);
  const [libraryItemId, setLibraryItemId] = useState(searchParams.get("libraryItemId") ?? "");
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [prompt, setPrompt] = useState("");
  const [dueAt, setDueAt] = useState("");
  const [selectedStudents, setSelectedStudents] = useState<number[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [notice, setNotice] = useState("");
  const [preview, setPreview] = useState<Simulation | null>(null);
  const [previewError, setPreviewError] = useState("");
  const [previewLoading, setPreviewLoading] = useState(false);
  const [frame, setFrame] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [query, setQuery] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      if (user?.role === "STUDENT") { setItems(await studentAssignments()); return; }
      if (user?.role === "TEACHER") {
        const [assignments, libraryItems, studentItems] = await Promise.all([teacherAssignments(), personalLibrary(), studentOptions()]);
        setItems(assignments); setSaved(libraryItems); setStudents(studentItems);
      }
    } catch { setError("Không thể tải dữ liệu giao bài."); }
    finally { setLoading(false); }
  }, [user?.role, user?.id]);

  useEffect(() => { void load(); }, [load]);
  useEffect(() => {
    const selected = saved.find(item => item.id === libraryItemId);
    if (selected && !title) setTitle(selected.title);
  }, [saved, libraryItemId, title]);

  const valid = Boolean(libraryItemId && title.trim() && prompt.trim() && selectedStudents.length);
  const selectedLibrary = useMemo(() => saved.find(item => item.id === libraryItemId), [saved, libraryItemId]);
  useEffect(() => {
    let active = true;
    setPreview(null); setFrame(0); setPlaying(false); setPreviewError("");
    if (!studio || !selectedLibrary?.simulationId) { setPreviewLoading(false); return; }
    setPreviewLoading(true);
    void getSimulation(selectedLibrary.simulationId).then(value => { if (active) setPreview(value); })
      .catch(() => { if (active) setPreviewError("Không tải được mô phỏng. Hãy chọn lại để thử lại."); })
      .finally(() => { if (active) setPreviewLoading(false); });
    return () => { active = false; };
  }, [studio, selectedLibrary]);
  useEffect(() => {
    if (!playing || !preview?.time.length) return;
    const timer = window.setInterval(() => setFrame(value => (value + 1) % preview.time.length), 50);
    return () => window.clearInterval(timer);
  }, [playing, preview]);
  const toggleStudent = (id: number) => setSelectedStudents(current => current.includes(id) ? current.filter(value => value !== id) : [...current, id]);
  const submit = async () => {
    if (!valid || submitting) return;
    setSubmitting(true); setError("");
    try {
      await createAssignment({ libraryItemId, title: title.trim(), description: description.trim() || undefined, questions: { prompt: prompt.trim() }, studentIds: selectedStudents, dueAt: dueAt ? new Date(dueAt).toISOString() : undefined });
      setTitle(""); setDescription(""); setPrompt(""); setDueAt(""); setSelectedStudents([]); setLibraryItemId("");
      setNotice("Đã giao bài cho học sinh thành công.");
      await load();
    } catch { setError("Không thể giao bài. Simulation phải còn trong thư viện cá nhân và người nhận phải là học sinh hợp lệ."); }
    finally { setSubmitting(false); }
  };

  if (!studio && user?.role === "TEACHER") return <Navigate to={`/assignments/workspace${searchParams.size ? `?${searchParams.toString()}` : ""}`} replace />;
  return <main className={studio ? "assignment-studio" : "main"}>
    {studio && <aside className="assignment-source"><h2>Chọn mô phỏng</h2><p>Học liệu trong thư viện của tôi</p><input aria-label="Tìm mô phỏng" placeholder="Tìm mô phỏng…" value={query} onChange={event => setQuery(event.target.value)} />
      {loading && <p role="status">Đang tải thư viện…</p>}
      {saved.filter(item => item.title.toLocaleLowerCase("vi").includes(query.toLocaleLowerCase("vi"))).map(item => <button type="button" key={item.id} className={item.id === libraryItemId ? "selected" : ""} aria-pressed={item.id === libraryItemId} disabled={submitting} onClick={() => { setLibraryItemId(item.id); setTitle(item.title); }}><strong>{item.title}</strong><small>{item.topic} · {item.validationStatus}</small></button>)}
      {!loading && !saved.length && <p>Lưu mô phỏng từ Workspace để bắt đầu giao bài.</p>}
    </aside>}
    {studio && <section className="assignment-preview"><div><span>XEM TRƯỚC HỌC LIỆU</span><h1>{selectedLibrary?.title ?? "Chọn mô phỏng để giao bài"}</h1></div>
      {previewLoading ? <p role="status">Đang tải mô phỏng…</p> : previewError ? <p role="alert">{previewError}</p> : preview ? <><PhysicsScene simulation={preview} index={frame} overlays={{ grid: true, trajectory: true, velocity: false, acceleration: false }} /><div className="assignment-playback"><button type="button" onClick={() => setPlaying(value => !value)}>{playing ? "Tạm dừng" : "Phát"}</button><input aria-label="Thời điểm mô phỏng" type="range" min={0} max={Math.max(0, preview.time.length - 1)} value={frame} onChange={event => { setPlaying(false); setFrame(Number(event.target.value)); }} /><span>{(preview.time[frame] ?? 0).toFixed(2)} s</span></div></> : <div className="assignment-preview-empty">Chọn học liệu ở cột bên trái, sau đó thiết lập câu hỏi và người nhận ở cột bên phải.</div>}
    </section>}
    <div className="hero"><div><span className="eyebrow">F09 · assignment</span><h1>{user?.role === "STUDENT" ? "Bài được giao" : "Giao simulation"}</h1><p className="muted">Bài giao luôn xuất phát từ một simulation đã lưu và đã kiểm chứng.</p></div></div>
    {user?.role === "TEACHER" && <section className="card assignment-form"><div className="section-heading"><div><span className="eyebrow">Tạo bài giao</span><h2>Chọn học liệu và học sinh</h2></div></div>
      {!saved.length ? <p className="muted">Bạn chưa có simulation đã lưu. Hãy mở một simulation đã kiểm chứng và chọn “Lưu vào thư viện”.</p> : <>
        <div className="form-grid"><label><span>Simulation trong thư viện</span><select value={libraryItemId} onChange={event => setLibraryItemId(event.target.value)}><option value="">Chọn simulation</option>{saved.map(item => <option key={item.id} value={item.id}>{item.title}</option>)}</select></label><label><span>Tên bài giao</span><input value={title} maxLength={160} onChange={event => setTitle(event.target.value)} /></label><label><span>Hạn hoàn thành</span><input type="datetime-local" value={dueAt} onChange={event => setDueAt(event.target.value)} /></label></div>
        {selectedLibrary && <p className="selection-note">Đang giao: <strong>{selectedLibrary.title}</strong> · {selectedLibrary.topic ?? "Chưa phân loại"} · Dual validation {selectedLibrary.validationStatus}</p>}
        <div className="form-grid form-grid-two"><label><span>Câu hỏi dự đoán trước khi xem kết quả</span><textarea value={prompt} onChange={event => setPrompt(event.target.value)} rows={3} /></label><label><span>Hướng dẫn thêm (không bắt buộc)</span><textarea value={description} onChange={event => setDescription(event.target.value)} rows={3} /></label></div>
        <fieldset className="student-picker"><legend>Học sinh nhận bài</legend>{students.length ? students.map(student => <label key={student.id}><input type="checkbox" checked={selectedStudents.includes(student.id)} onChange={() => toggleStudent(student.id)} /><span>{student.fullName}</span></label>) : <p className="muted">Chưa có tài khoản học sinh đang hoạt động.</p>}</fieldset>
        <button disabled={!valid || submitting} onClick={() => void submit()}>{submitting ? "Đang giao…" : `Giao cho ${selectedStudents.length} học sinh`}</button>
      </>}
    </section>}
    <section className="card assignment-list"><div className="section-heading"><div><span className="eyebrow">Đã lưu trong DB</span><h2>{user?.role === "STUDENT" ? "Danh sách của tôi" : "Bài đã giao"}</h2></div></div>{items.map(item => <article className="quantity" key={item.id}><span><strong>{item.title}</strong><br /><small>{questionPrompt(item.questions) || item.description || "Không có mô tả"} · {item.studentIds.length} học sinh{item.dueAt ? ` · Hạn ${new Date(item.dueAt).toLocaleString("vi-VN")}` : ""}</small></span><span className="status">{item.status}</span></article>)}{!items.length && !error && <p className="muted">Chưa có bài giao.</p>}</section>
    {notice && <p className="assignment-notice" role="status">{notice}</p>}
    {error && <p className="error" role="alert">{error} <button type="button" onClick={() => void load()}>Thử lại</button></p>}
  </main>;
}
