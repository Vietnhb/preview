import { useEffect, useState, type FormEvent } from "react";
import { simulationCurriculum } from "../../curriculum/api/curriculumApi";
import { createLibraryFolder } from "../api/libraryApi";
import { saveGeneratedSimulation, type GeneratedSimulationResult } from "../../simulation/api/simulationUnderstandingApi";
import type { Curriculum, LibraryFolder, LibraryItem } from "../../../shared/types/physlive";
import axios from "axios";

type Props = {
  simulation: GeneratedSimulationResult;
  parameters: Record<string, number>;
  folders: LibraryFolder[];
  onFolder: (folder: LibraryFolder) => void;
  onSaved: (item: LibraryItem) => void;
  onClose: () => void;
  onBusyChange: (busy: boolean) => void;
};

export default function SaveSimulationPanel({ simulation, parameters, folders, onFolder, onSaved, onClose, onBusyChange }: Props) {
  const [title, setTitle] = useState(simulation.description.slice(0, 160));
  const [folderId, setFolderId] = useState(folders[0]?.id ?? "");
  const [folderName, setFolderName] = useState("");
  const [lessonId, setLessonId] = useState("");
  const [catalog, setCatalog] = useState<Curriculum | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [reload, setReload] = useState(0);
  useEffect(() => {
    let cancelled = false;
    const controller = new AbortController();
    const timeout = window.setTimeout(() => {
      controller.abort();
      if (!cancelled) {
        setLoading(false);
        setError("Tải bài học quá thời gian chờ (10 giây). Kiểm tra kết nối mạng rồi nhấn Tải lại bài học.");
      }
    }, 10000);
    simulationCurriculum(simulation.schemaId, simulation.schemaVersion, controller.signal)
      .then(value => { if (!cancelled && !controller.signal.aborted) setCatalog(value); })
      .catch(cause => {
        if (!cancelled && !controller.signal.aborted) setError(axios.isAxiosError<{ message?: string }>(cause)
          ? cause.response?.data?.message ?? "Chưa tải được danh sách bài học. Vui lòng thử lại."
          : "Chưa tải được danh sách bài học. Vui lòng thử lại.");
      })
      .finally(() => window.clearTimeout(timeout))
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; window.clearTimeout(timeout); controller.abort(); };
  }, [reload, simulation.schemaId, simulation.schemaVersion]);
  const topic = catalog?.topics.map(item => item.name).join(", ") ?? "";
  const topics = catalog?.topics.filter(item => item.enabled) ?? [];
  const lessons = topics.flatMap(t => t.modules.flatMap(m => m.levels.flatMap(l => l.lessons.map(lesson => ({
    id: lesson.id, label: `${m.name} / ${l.name} / ${lesson.name}`,
  })))));
  const save = async (event: FormEvent) => {
    event.preventDefault();
    if (busy) return;
    if (loading || !lessons.some(lesson => lesson.id === lessonId)) {
      setError("Vui lòng chọn bài học trong danh sách trước khi lưu.");
      return;
    }
    setBusy(true); onBusyChange(true); setError("");
    try {
      let target = folderId;
      if (!target) {
        const folder = await createLibraryFolder(folderName.trim());
        target = folder.id; setFolderId(target); onFolder(folder);
      }
      const item = await saveGeneratedSimulation(simulation, parameters, title.trim(), target, lessonId);
      onSaved(item);
    } catch (cause) {
      setError(axios.isAxiosError<{ message?: string }>(cause)
        ? cause.response?.data?.message ?? "Chưa lưu được mô phỏng. Vui lòng thử lại."
        : "Chưa lưu được mô phỏng. Vui lòng thử lại.");
    } finally { setBusy(false); onBusyChange(false); }
  };
  return <form className="simulation-save-panel" onSubmit={save} aria-label="Lưu mô phỏng vào thư viện">
    <h3>Lưu mô phỏng</h3>
    <p>Lưu cảnh minh họa và các tham số hiện tại vào thư viện cá nhân để mở lại.</p>
    <fieldset disabled={busy}>
      <label>Tên bài<input required maxLength={160} value={title} onChange={e => setTitle(e.target.value)} /></label>
      <label>Thư mục<select value={folderId} onChange={e => setFolderId(e.target.value)}>
        <option value="">Tạo thư mục mới…</option>
        {folders.map(folder => <option key={folder.id} value={folder.id}>{folder.name}</option>)}
      </select></label>
      {!folderId && <label>Tên thư mục mới<input required maxLength={120} value={folderName} onChange={e => setFolderName(e.target.value)} /></label>}
      <fieldset className="simulation-lesson-picker" disabled={loading}>
        <legend>Phân loại bài học</legend>
        {topic && <small>Chủ đề: {topic}</small>}
        {loading ? <p role="status">Đang tải bài học…</p> : <div className="simulation-lesson-options">
          {lessons.map(lesson => <label key={lesson.id} className="simulation-lesson-option">
            <input type="radio" name="simulation-lesson" required value={lesson.id}
              checked={lessonId === lesson.id} onChange={() => setLessonId(lesson.id)} />
            <span>{lesson.label}</span>
          </label>)}
        </div>}
      </fieldset>
      {!loading && catalog && !lessons.length && <p role="status">Chưa có bài học đang hoạt động cho mô phỏng này. Hãy tải lại; nếu vẫn trống, quản trị viên cần cập nhật chương trình bài học.</p>}
      {error && <p role="alert">{error}</p>}
      {!loading && (!catalog || !lessons.length) && <button type="button" onClick={() => { setLoading(true); setCatalog(null); setLessonId(""); setError(""); setReload(n => n + 1); }}>Tải lại bài học</button>}
      <div className="simulation-actions">
        <button type="button" onClick={onClose}>Hủy</button>
        <button type="submit" disabled={loading || !title.trim() || !lessonId || (!folderId && !folderName.trim())}>{busy ? "Đang lưu…" : "Lưu vào thư viện"}</button>
      </div>
    </fieldset>
  </form>;
}