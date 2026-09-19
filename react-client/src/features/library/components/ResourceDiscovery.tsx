import { useMemo, useState } from "react";
import PhysicsScene from "../../../components/simulation/PhysicsScene";
import type { Curriculum, LibraryItem, Simulation } from "../../../types/physlive";

type Props = {
  items: LibraryItem[]; loading: boolean; selectedItem: LibraryItem | null; simulation: Simulation | null;
  curriculum?: Curriculum | null;
  time: number; simulationLoading: boolean; simulationError: string; frame: number; playing: boolean;
  vectors: { grid: boolean; trajectory: boolean; velocity: boolean; acceleration: boolean };
  onOpen: (item: LibraryItem) => void; onClose: () => void; onTogglePlaying: () => void; onReset: () => void;
  onFrameChange: (frame: number) => void; onTimeChange: (time: number) => void; onPlaybackEnd: () => void;
  error?: string;
  onRetry?: () => void;
};

export function ResourceDiscovery({ items, curriculum = null, loading, selectedItem, simulation, time, simulationLoading, simulationError, frame, playing, vectors, onOpen, onClose, onTogglePlaying, onReset, onFrameChange, onTimeChange, onPlaybackEnd, error = "", onRetry }: Readonly<Props>) {
  const [grade, setGrade] = useState("");
  const [subject, setSubject] = useState("");
  const [lesson, setLesson] = useState("");
  const [query, setQuery] = useState("");
  const [sort, setSort] = useState<"featured" | "latest" | "title">("featured");
  const grades = useMemo(() => [...new Set(curriculum?.topics.flatMap(topic => topic.modules.flatMap(module => module.levels.map(level => level.name))) ?? [])].sort((a, b) => a.localeCompare(b, "vi", { numeric: true })), [curriculum]);
  const subjects = useMemo(() => (curriculum?.topics ?? []).filter(topic => topic.enabled && (!grade || topic.modules.some(module => module.levels.some(level => level.name === grade)))).sort((a, b) => a.name.localeCompare(b.name, "vi")), [curriculum, grade]);
  const lessonIds = useMemo(() => {
    const ids = new Set<string>();
    for (const topic of curriculum?.topics ?? []) {
      if (!topic.enabled || (subject && topic.id !== subject)) continue;
      for (const module of topic.modules) for (const level of module.levels) {
        if (!grade || level.name === grade) for (const entry of level.lessons) ids.add(entry.id);
      }
    }
    return ids;
  }, [curriculum, subject, grade]);
  const visible = [...items
    .filter(item => ((!grade && !subject && !lesson) || (lesson ? item.lessonId === lesson : lessonIds.has(item.lessonId))) && (!query.trim() || `${item.title} ${item.sharedByName ?? ""} ${item.schoolName ?? ""}`.toLocaleLowerCase("vi").includes(query.trim().toLocaleLowerCase("vi"))))]
    .sort((a, b) => {
      if (sort === "title") return a.title.localeCompare(b.title, "vi");
      if (sort === "latest") return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
      const featured = Number(b.moderationStatus === "FEATURED") - Number(a.moderationStatus === "FEATURED");
      return featured || new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
    });
  return <section className="student-discovery">
    <div className={`resource-catalog-layout${curriculum ? "" : " no-sidebar"}`}>
      {curriculum && <aside className="resource-catalog-sidebar" aria-label="Mục lục tài nguyên">
        <div className="resource-catalog-heading"><strong>Mục lục</strong><button type="button" onClick={() => { setGrade(""); setSubject(""); setLesson(""); }} disabled={!grade && !subject && !lesson}>Xóa lọc</button></div>
        <section className="resource-catalog-group"><h2>Khối lớp</h2><button type="button" className={!grade ? "selected" : ""} aria-pressed={!grade} onClick={() => { setGrade(""); setSubject(""); setLesson(""); }}>Tất cả khối</button>{grades.map(value => <button type="button" key={value} className={grade === value ? "selected" : ""} aria-pressed={grade === value} onClick={() => { setGrade(grade === value ? "" : value); setSubject(""); setLesson(""); }}>{value}</button>)}</section>
        <section className="resource-catalog-group"><h2>Môn học</h2>{subjects.length ? subjects.map(value => <details key={value.id} open={subject === value.id || (!subject && Boolean(grade))}><summary className={subject === value.id ? "selected" : ""}><input aria-label={value.name} type="checkbox" checked={subject === value.id} onChange={() => { setSubject(subject === value.id ? "" : value.id); setLesson(""); }} />{value.name}</summary>{value.modules.filter(module => module.levels.some(level => !grade || level.name === grade)).map(module => <div className="resource-catalog-lessons" key={module.id}><h3>{module.name}</h3>{module.levels.filter(level => !grade || level.name === grade).flatMap(level => level.lessons).map(entry => <button type="button" key={entry.id} className={lesson === entry.id ? "selected" : ""} aria-pressed={lesson === entry.id} onClick={() => { setSubject(value.id); setLesson(lesson === entry.id ? "" : entry.id); }}>{entry.name}</button>)}</div>)}</details>) : <p>{curriculum ? "Chưa có môn học trong khối này." : "Đang tải mục lục…"}</p>}</section>
      </aside>}
      <div className="resource-catalog-results">
    <div className="student-discovery-toolbar">
      <div className="student-discovery-search-row"><label><span className="sr-only">Tìm mô phỏng</span><input value={query} onChange={event => setQuery(event.target.value)} placeholder="Tìm mô phỏng, giáo viên hoặc trường…" /></label><label className="student-discovery-sort"><span>Sắp xếp</span><select value={sort} onChange={event => setSort(event.target.value as typeof sort)}><option value="featured">Nổi bật</option><option value="latest">Mới nhất</option><option value="title">Tên A–Z</option></select></label></div>
    </div>
    {loading ? <div className="student-discovery-loading" aria-label="Đang tải thư viện"><span /><span /><span /></div> : error ? <div className="assignment-empty"><h2>Chưa tải được kho cộng đồng</h2><p>{error}</p>{onRetry && <button type="button" onClick={onRetry}>Thử lại</button>}</div> : visible.length === 0 ? <div className="assignment-empty"><h2>Chưa tìm thấy mô phỏng</h2><p>Thử chủ đề hoặc từ khóa khác.</p></div> : <div className="student-discovery-grid">{visible.map((item, index) => <article className="student-resource-card" key={item.id}>
      <div className={`student-resource-cover cover-${index % 4}`}><span>{item.topic || "Vật lý"}</span><div className="student-resource-atom" aria-hidden="true">●</div><small>{item.moderationStatus === "FEATURED" ? "NỔI BẬT · ĐÃ KIỂM CHỨNG" : item.visibility === "PUBLIC" ? "CỘNG ĐỒNG · ĐÃ KIỂM CHỨNG" : "TRONG TRƯỜNG · ĐÃ KIỂM CHỨNG"}</small></div>
      <div className="student-resource-body"><h2>{item.title}</h2><div className="student-resource-author"><span>{(item.sharedByName || "GV").trim().charAt(0).toUpperCase()}</span><p><strong>{item.sharedByName || "Giáo viên PhysLive"}</strong><small>{item.schoolName || "Cộng đồng PhysLive"}</small></p></div><button type="button" onClick={() => onOpen(item)}>Mở mô phỏng <span aria-hidden="true">→</span></button></div>
    </article>)}</div>}
      </div>
    </div>
    {selectedItem && <div className="student-resource-dialog" role="dialog" aria-modal="true" aria-label={selectedItem.title}><div className="student-resource-dialog-card">
      <header><div><span>{selectedItem.topic || "Vật lý"}</span><h2>{selectedItem.title}</h2><p>Chia sẻ bởi {selectedItem.sharedByName || "Giáo viên PhysLive"} · {selectedItem.schoolName || "Cộng đồng PhysLive"}</p></div><button type="button" aria-label="Đóng mô phỏng" onClick={onClose}>×</button></header>
      {simulationLoading && <p className="student-player-state">Đang mở mô phỏng…</p>}
      {!simulationLoading && simulationError && <p className="student-player-state error">{simulationError}</p>}
      {!simulationLoading && !simulationError && simulation && <><div className="student-resource-scene"><PhysicsScene simulation={simulation} index={frame} overlays={vectors} time={time} playing={playing} onTimeChange={onTimeChange} onPlaybackEnd={onPlaybackEnd} /></div><div className="student-playback"><button type="button" className="prediction-submit-btn" onClick={onTogglePlaying}>{playing ? "Tạm dừng" : "Chạy mô phỏng"}</button><button type="button" className="modern-tab-btn" onClick={onReset}>Tua về đầu</button><input aria-label="Thời gian mô phỏng" type="range" min={0} max={Math.max(0, simulation.time.length - 1)} value={frame} onChange={event => onFrameChange(Number(event.target.value))} /><span>{time.toFixed(2)} s</span></div></>}
    </div></div>}
  </section>;
}
