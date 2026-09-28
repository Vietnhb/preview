import { useEffect, useState, type FormEvent } from "react";
import { curriculum } from "../../api/curriculumApi";
import { createLibraryFolder } from "../../api/libraryApi";
import { saveGeneratedSimulation, type GeneratedSimulationResult } from "../../api/simulationUnderstandingApi";
import type { Curriculum, LibraryFolder, LibraryItem } from "../../types/physlive";
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
    curriculum().then(value => { if (!cancelled) setCatalog(value); })
      .catch(() => { if (!cancelled) setError("Chưa tải được danh sách bài học. Vui lòng thử lại."); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [reload]);
  const topic = String(simulation.simulationSpec.topic ?? "");
  const topics = catalog?.topics.filter(item => item.enabled && (!topic || item.name.toLowerCase() === topic.toLowerCase())) ?? [];
  const lessons = topics.flatMap(t => t.modules.flatMap(m => m.levels.flatMap(l => l.lessons.map(lesson => ({
    id: lesson.id, label: `${m.name} / ${l.name} / ${lesson.name}`,
  })))));
  const save = async (event: FormEvent) => {
    event.preventDefault();
    if (busy) return;
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
      <label>Phân loại bài học{topic && <small>Chủ đề: {topic}</small>}
        <select required value={lessonId} onChange={e => setLessonId(e.target.value)} disabled={loading}>
          <option value="">{loading ? "Đang tải bài học…" : "Chọn chương / cấp độ / bài học"}</option>
          {lessons.map(lesson => <option key={lesson.id} value={lesson.id}>{lesson.label}</option>)}
        </select>
      </label>
      {!loading && catalog && !lessons.length && <p>Chưa có bài học cho chủ đề này trong chương trình.</p>}
      {error && <p role="alert">{error}</p>}
      {!catalog && !loading && <button type="button" onClick={() => { setLoading(true); setError(""); setReload(n => n + 1); }}>Tải lại bài học</button>}
      <div className="simulation-actions">
        <button type="button" onClick={onClose}>Hủy</button>
        <button type="submit" disabled={loading || !title.trim() || !lessonId || (!folderId && !folderName.trim())}>{busy ? "Đang lưu…" : "Lưu vào thư viện"}</button>
      </div>
    </fieldset>
  </form>;
}
