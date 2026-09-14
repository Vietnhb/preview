import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { Link, useLocation } from "react-router-dom";
import axiosClient from "../api/axios";
import { createLibraryFolder, curriculum, getSimulation, saveLibrary } from "../api/physliveApi";
import { useTeacherLibrary } from "../store/useTeacherLibrary";
import PhysicsScene from "./PhysicsScene";
import LearningChart from "./LearningChart";
import Icon from "./LearningIcon";
import TeacherLibraryPane from "./TeacherLibraryPane";
import type { Curriculum, Problem, Simulation } from "../types/physlive";
import type { LibraryItem } from "../types/physlive";
import { controlValue, indexAtTime, learningSeries, lessonCopy, lessonKind, numberLabel } from "../utils/learningModel";
import { reSolveSimulation } from "../utils/clientSolver";
import { usePhysliveStore } from "../store/usePhysliveStore";

function Tabs<T extends string>({ id, label, items, value, onChange }: { id: string; label: string; items: { value: T; label: string }[]; value: T; onChange: (value: T) => void }) {
  return <div className="learn-tabs" role="tablist" aria-label={label}>{items.map((item, index) => <button key={item.value} type="button" role="tab" id={`${id}-${item.value}`} aria-controls={`${id}-panel`} aria-selected={item.value === value} tabIndex={item.value === value ? 0 : -1}
    onClick={() => onChange(item.value)} onKeyDown={event => {
      const next = event.key === "ArrowRight" ? (index + 1) % items.length : event.key === "ArrowLeft" ? (index + items.length - 1) % items.length : event.key === "Home" ? 0 : event.key === "End" ? items.length - 1 : -1;
      if (next >= 0) { event.preventDefault(); onChange(items[next].value); document.getElementById(`${id}-${items[next].value}`)?.focus(); }
    }}>{item.label}</button>)}</div>;
}

type ThemeMode = "light" | "dark" | "system";

export function LearningHeader({ onNewSimulation, viewMode = "2d", threeDEnabled = false, onViewModeChange, libraryCollapsed = false, onToggleLibrary }: { onNewSimulation?: () => void; viewMode?: "2d" | "3d"; threeDEnabled?: boolean; onViewModeChange?: (mode: "2d" | "3d") => void; libraryCollapsed?: boolean; onToggleLibrary?: () => void } = {}) {
  const pathname = useLocation().pathname;
  const [settingsOpen, setSettingsOpen] = useState(false);
  const settingsRef = useRef<HTMLDivElement>(null);
  const [theme, setTheme] = useState<ThemeMode>(() => {
    try {
      const stored = window.localStorage.getItem("physlive.theme");
      return stored === "light" || stored === "dark" || stored === "system" ? stored : "system";
    } catch {
      return "system";
    }
  });

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    const media = window.matchMedia("(prefers-color-scheme: dark)");
    const applyEffectiveTheme = () => { document.documentElement.dataset.themeEffective = theme === "dark" || (theme === "system" && media.matches) ? "dark" : "light"; };
    applyEffectiveTheme();
    if (theme === "system") media.addEventListener("change", applyEffectiveTheme);
    try { window.localStorage.setItem("physlive.theme", theme); } catch { /* storage can be unavailable in private contexts */ }
    return () => media.removeEventListener("change", applyEffectiveTheme);
  }, [theme]);

  useEffect(() => {
    if (!settingsOpen) return;
    const closeOnOutsidePress = (event: PointerEvent) => {
      if (!settingsRef.current?.contains(event.target as Node)) setSettingsOpen(false);
    };
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") setSettingsOpen(false);
    };
    document.addEventListener("pointerdown", closeOnOutsidePress);
    document.addEventListener("keydown", closeOnEscape);
    return () => {
      document.removeEventListener("pointerdown", closeOnOutsidePress);
      document.removeEventListener("keydown", closeOnEscape);
    };
  }, [settingsOpen]);
  const toggleFullscreen = async () => {
    if (document.fullscreenElement) await document.exitFullscreen();
    else await document.querySelector<HTMLElement>(".learning-app")?.requestFullscreen();
  };

  return <header className="learn-header unified-header">
    <div className="learn-header-left">
      <Link className="learn-brand" to="/" aria-label="PhysLive — về trang nhập đề">PhysLive <small>Phòng học tương tác</small></Link>
      <div className="learn-settings-anchor" ref={settingsRef}>
        <button type="button" className="learn-header-icon" aria-label="Cài đặt workspace" title="Cài đặt workspace" aria-haspopup="dialog" aria-expanded={settingsOpen} onClick={() => setSettingsOpen(value => !value)}><Icon name="settings" /></button>
        {settingsOpen && <div className="learn-settings-popover" role="dialog" aria-label="Cài đặt workspace">
          <strong>Cài đặt</strong>
          <span>Giao diện</span>
          <div className="learn-settings-segmented" role="group" aria-label="Giao diện"><button type="button" className={theme === "light" ? "active" : ""} onClick={() => setTheme("light")}><Icon name="sun" />Sáng</button><button type="button" className={theme === "dark" ? "active" : ""} onClick={() => setTheme("dark")}><Icon name="moon" />Tối</button><button type="button" className={theme === "system" ? "active" : ""} onClick={() => setTheme("system")}><Icon name="system" />Hệ thống</button></div>
          <label htmlFor="learn-language">Ngôn ngữ</label>
          <select id="learn-language" defaultValue="vi" disabled><option value="vi">Tiếng Việt</option></select>
        </div>}
      </div>
      {onToggleLibrary && <button type="button" className="learn-header-icon" aria-label={libraryCollapsed ? "Mở thư viện" : "Thu gọn thư viện"} title={libraryCollapsed ? "Mở thư viện" : "Thu gọn thư viện"} onClick={onToggleLibrary}><Icon name="panel" /></button>}
    </div>
    <nav className="learn-journey" aria-label="Điều hướng workspace"><Link to="/workspace" aria-current={pathname === "/workspace" ? "page" : undefined} className={pathname === "/workspace" ? "active" : ""}>Workspace đề bài</Link><Link to="/models" aria-current={pathname === "/models" ? "page" : undefined} className={pathname === "/models" ? "active" : ""}>Không gian mô phỏng</Link><Link to="/lab" aria-current={pathname === "/lab" ? "page" : undefined} className={pathname === "/lab" ? "active" : ""}>Phòng thí nghiệm</Link></nav>
    <div className="learn-header-actions">
      {onNewSimulation && <button type="button" className="learn-header-action" onClick={onNewSimulation}>Nhập đề</button>}
      <div className="learn-view-switch" role="group" aria-label="Chế độ hiển thị">
        <button type="button" className={viewMode === "2d" ? "active" : ""} aria-pressed={viewMode === "2d"} onClick={() => onViewModeChange?.("2d")} title="Hiển thị mặt phẳng 2D"><Icon name="view2d" />2D</button>
        <button type="button" className={viewMode === "3d" ? "active" : ""} aria-pressed={viewMode === "3d"} disabled={!threeDEnabled} onClick={() => onViewModeChange?.("3d")} title={threeDEnabled ? "Hiển thị không gian 3D" : "3D chưa được cung cấp cho schema này"}><Icon name="view3d" />3D</button>
      </div>
      <button type="button" className="learn-header-icon" aria-label="Toàn màn hình" title="Toàn màn hình" onClick={() => void toggleFullscreen()}><Icon name="fullscreen" /></button>
      <Link className="learn-header-action" to="/models">Khám phá mô hình</Link>
      <Link className="learn-back" to="/"><Icon name="back" />Về đề bài</Link>
    </div>
  </header>;
}

export default function LearningWorkspace({ simulation, problem, onUpdate, onNewSimulation }: { simulation: Simulation; problem: Problem | null; onUpdate: (simulation: Simulation) => void; onNewSimulation?: () => void }) {
  const user = usePhysliveStore(state => state.user);
  const kind = lessonKind(simulation.schemaId), copy = lessonCopy[kind];
  const times = simulation.time;
  const allSeries = useMemo(() => learningSeries(simulation), [simulation]);
  const controls = useMemo(() => simulation.visualization?.controls ?? [], [simulation.visualization]);
  // Snapshot of the original server-generated simulation – never overwritten by client re-solves.
  const baseSimulationRef = useRef<Simulation>(simulation);
  useEffect(() => {
    // Only update the baseline when a NEW simulation arrives from the server (different id).
    baseSimulationRef.current = simulation;
  }, [simulation.simulationId]);

  // initialValues is derived from the baseline ref once per simulationId, so it stays stable
  // while the user drags sliders and onUpdate fires with re-solved copies.
  const [initialValues, setInitialValues] = useState<Record<string, number>>(
    () => Object.fromEntries(controls.map(c => [c.key, controlValue(c, simulation, problem?.currentSpecification)]))
  );
  useEffect(() => {
    setInitialValues(
      Object.fromEntries(controls.map(c => [c.key, controlValue(c, baseSimulationRef.current, problem?.currentSpecification)]))
    );
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [simulation.simulationId]);

  const [draft, setDraft] = useState<Record<string, string>>(
    () => Object.fromEntries(Object.entries(initialValues).map(([k, v]) => [k, String(v)]))
  );
  useEffect(() => {
    // Reset draft whenever a new baseline arrives.
    setDraft(Object.fromEntries(Object.entries(
      Object.fromEntries(controls.map(c => [c.key, controlValue(c, baseSimulationRef.current, problem?.currentSpecification)]))
    ).map(([k, v]) => [k, String(v)])));
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [simulation.simulationId]);
  const [inspector, setInspector] = useState<"experiment" | "understand" | "steps" | "problem">("experiment");
  const [libraryCollapsed, setLibraryCollapsed] = useState(false);
  const [mobilePanel, setMobilePanel] = useState<"observe" | "inspect">("observe");
  const [bottomTab, setBottomTab] = useState<"graph" | "data">("graph");
  const [selectedSeries, setSelectedSeries] = useState<string | null>(null);
  const [playing, setPlaying] = useState(false);
  const [speed, setSpeed] = useState(1);
  const [time, setTime] = useState(times[0] ?? 0);
  const timeRef = useRef(time);
  const [error, setError] = useState("");
  const [exportError, setExportError] = useState("");
  const [downloading, setDownloading] = useState<string | null>(null);
  const [showSave, setShowSave] = useState(false);
  const [saving, setSaving] = useState(false);
  const { folders, setFolders, libraryItems, setLibraryItems, libraryLoading, libraryError, setLibraryError, retryLibrary } = useTeacherLibrary();
  const savedItem = libraryItems.find(item => item.simulationId === simulation.simulationId) ?? null;
  const [openingLibraryId, setOpeningLibraryId] = useState<string | null>(null);
  const [folderId, setFolderId] = useState("");
  const [saveError, setSaveError] = useState("");
  const [saveTitle, setSaveTitle] = useState(() => {
    const source = problem?.editableText || problem?.originalText || copy.title;
    return source.trim().replace(/\s+/g, " ").slice(0, 120);
  });
  const [visibility, setVisibility] = useState<LibraryItem["visibility"]>("PERSONAL");
  const [curriculumTree, setCurriculumTree] = useState<Curriculum | null>(null);
  const [topicId, setTopicId] = useState("");
  const [moduleId, setModuleId] = useState("");
  const [levelId, setLevelId] = useState("");
  const [lessonId, setLessonId] = useState("");
  const [overlays, setOverlays] = useState({ grid: true, trajectory: true, velocity: true, acceleration: false });
  const mounted = useRef(true);
  const lastTime = times[times.length - 1] ?? 0;
  const index = indexAtTime(times, time);
  const series = allSeries.find(item => item.key === selectedSeries) ?? allSeries[0];
  const validData = times.length > 1 && times.every((t, i) => Number.isFinite(t) && (i === 0 || t > times[i - 1])) && Boolean(series);
  const canPlay = validData && simulation.valid;
  const dirty = controls.some(c => draft[c.key].trim() === "" || Number(draft[c.key]) !== initialValues[c.key]);

  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  useEffect(() => { if (user?.role === "TEACHER" && simulation.valid) void curriculum().then(setCurriculumTree).catch(() => setCurriculumTree(null)); }, [user?.role, simulation.valid]);
  useEffect(() => {
    // Reset time when simulation changes (e.g., after parameter adjustment)
    setTime(times[0] ?? 0);
    timeRef.current = times[0] ?? 0;
    setPlaying(false);
  }, [simulation.simulationId, times]);
  
  const handleParamChange = (key: string, valueStr: string) => {
    const nextDraft = { ...draft, [key]: valueStr };
    setDraft(nextDraft);

    const val = Number(valueStr);
    const ctrl = controls.find(c => c.key === key);
    if (!valueStr.trim() || !Number.isFinite(val) || (ctrl && ctrl.min >= 0 && val < ctrl.min)) {
      return;
    }

    const numericOverrides: Record<string, number> = {};
    for (const c of controls) {
      const v = Number(nextDraft[c.key]);
      if (Number.isFinite(v)) numericOverrides[c.key] = v;
    }

    try {
      const base = baseSimulationRef.current;
      const updated = reSolveSimulation(base, numericOverrides, problem?.currentSpecification);
      onUpdate(updated);
      setError("");
      // Auto-reset to t=0 so the user just needs to press Play.
      setPlaying(false);
      setTime(updated.time[0] ?? 0);
      timeRef.current = updated.time[0] ?? 0;
    } catch {
      setError("Không thể tính toán với giá trị này.");
    }
  };

  useEffect(() => {
    if (!curriculumTree || topicId) return;
    const simulationTopic = problem?.currentSpecification?.topic;
    const match = curriculumTree.topics.find(item => item.name.toLowerCase() === simulationTopic?.toLowerCase());
    if (match) setTopicId(match.id);
  }, [curriculumTree, problem?.currentSpecification?.topic, topicId]);
  useEffect(() => { timeRef.current = time; }, [time]);
  useEffect(() => {
    if (!playing || !canPlay) return;
    const startWall = performance.now(), startTime = timeRef.current;
    let frame = 0;
    const tick = (now: number) => {
      const next = Math.min(lastTime, startTime + (now - startWall) / 1000 * speed);
      setTime(next);
      if (next >= lastTime) setPlaying(false); else frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [playing, canPlay, lastTime, speed]);

  const seek = (next: number) => { setPlaying(false); setTime(Math.max(times[0] ?? 0, Math.min(lastTime, next))); };
  const togglePlayback = () => { if (time >= lastTime) { timeRef.current = times[0]; setTime(times[0]); } setPlaying(value => !value); };
  const resetDraft = () => {
    const base = baseSimulationRef.current;
    const resetValues = Object.fromEntries(
      controls.map(c => [c.key, controlValue(c, base, problem?.currentSpecification)])
    );
    setInitialValues(resetValues);
    setDraft(Object.fromEntries(Object.entries(resetValues).map(([k, v]) => [k, String(v)])));
    setError("");
    // Restore original server-validated simulation.
    onUpdate(base);
    setTime(base.time[0] ?? 0);
    timeRef.current = base.time[0] ?? 0;
    setPlaying(false);
  };
  const download = async (format: "json" | "csv" | "pdf" | "html") => {
    setDownloading(format); setExportError("");
    try {
      const blob: Blob = (await axiosClient.get(`/exports/${simulation.specificationId}/${format}`, { responseType: "blob" })).data;
      const url = URL.createObjectURL(blob), link = document.createElement("a");
      link.href = url; link.download = `physlive-${simulation.specificationId}.${format}`;
      link.click(); window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch { setExportError("Chưa tải được dữ liệu. Vui lòng thử lại."); }
    finally { setDownloading(null); }
  };

  const persistToLibrary = async (event: FormEvent) => {
    event.preventDefault();
    if (!simulation.valid || !saveTitle.trim() || !folderId || !lessonId || saving) return;
    setSaving(true); setSaveError("");
    try {
      const item = await saveLibrary(simulation.simulationId, folderId, lessonId, saveTitle.trim(), visibility);
      setShowSave(false);
      setLibraryItems(current => [item, ...current.filter(record => record.id !== item.id)]);
    } catch {
      setSaveError("Chưa lưu được simulation. Hãy kiểm tra phiên đăng nhập và trạng thái validation rồi thử lại.");
    } finally { setSaving(false); }
  };

  const createFolder = async (name: string) => {
    setLibraryError("");
    try {
      const folder = await createLibraryFolder(name);
      setFolders(current => [...current, folder].sort((left, right) => left.name.localeCompare(right.name, "vi")));
      setFolderId(folder.id);
      return true;
    } catch {
      setLibraryError("Chưa tạo được thư mục. Tên thư mục có thể đã tồn tại.");
      return false;
    }
  };

  const openLibraryItem = async (item: LibraryItem) => {
    if (item.simulationId === simulation.simulationId || openingLibraryId) return;
    setOpeningLibraryId(item.id);
    setLibraryError("");
    try {
      const selected = await getSimulation(item.simulationId);
      onUpdate(selected);
    } catch {
      setLibraryError("Không mở được mô phỏng đã lưu.");
    } finally {
      setOpeningLibraryId(null);
    }
  };

  const topics = curriculumTree?.topics ?? [];
  const selectedTopic = topics.find(item => item.id === topicId);
  const modules = selectedTopic?.modules ?? [];
  const selectedModule = modules.find(item => item.id === moduleId);
  const levels = selectedModule?.levels ?? [];
  const selectedLevel = levels.find(item => item.id === levelId);
  const lessons = selectedLevel?.lessons ?? [];

  return <div className="learning-app">
    <LearningHeader onNewSimulation={onNewSimulation} libraryCollapsed={libraryCollapsed} onToggleLibrary={() => setLibraryCollapsed(value => !value)} />
    <main className="learn-workspace" id="learning-workspace">
      <div className="learn-top-area">
      {showSave && <form className="learn-save-panel" onSubmit={persistToLibrary}>
        <label><span>Thư mục cá nhân</span><select value={folderId} onChange={event => setFolderId(event.target.value)} required><option value="">Chọn thư mục</option>{folders.map(folder => <option key={folder.id} value={folder.id}>{folder.name}</option>)}</select></label>
        <label><span>Topic do AI xác định</span><select value={topicId} onChange={event => { setTopicId(event.target.value); setModuleId(""); setLevelId(""); setLessonId(""); }} disabled={Boolean(problem?.currentSpecification?.topic)} required><option value="">Chọn topic</option>{topics.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
        <label><span>Module</span><select value={moduleId} disabled={!topicId} onChange={event => { setModuleId(event.target.value); setLevelId(""); setLessonId(""); }} required><option value="">Chọn module</option>{modules.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
        <label><span>Grade / Level</span><select value={levelId} disabled={!moduleId} onChange={event => { setLevelId(event.target.value); setLessonId(""); }} required><option value="">Chọn lớp</option>{levels.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
        <label><span>Lesson</span><select value={lessonId} disabled={!levelId} onChange={event => setLessonId(event.target.value)} required><option value="">Chọn lesson</option>{lessons.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
        <div><strong>Lưu simulation đã kiểm chứng</strong><small>Bản lưu thuộc tài khoản giáo viên và có thể dùng để giao bài.</small></div>
        <label><span>Tên trong thư viện</span><input value={saveTitle} maxLength={160} onChange={event => setSaveTitle(event.target.value)} required /></label>
        <label><span>Phạm vi</span><select value={visibility} onChange={event => setVisibility(event.target.value as LibraryItem["visibility"])}><option value="PERSONAL">Cá nhân</option><option value="SHARED">Chia sẻ</option></select></label>
        <button type="submit" disabled={saving || !saveTitle.trim() || !folderId || !lessonId}>{saving ? "Đang lưu…" : "Xác nhận lưu"}</button>
        <button type="button" className="secondary" onClick={() => setShowSave(false)} disabled={saving}>Hủy</button>
        {saveError && <p role="alert">{saveError}</p>}
      </form>}
      </div>
      <nav className="learn-mobile-nav" aria-label="Chuyển vùng học tập"><button type="button" aria-pressed={mobilePanel === "observe"} onClick={() => setMobilePanel("observe")}><Icon name="play" />Quan sát</button><button type="button" aria-pressed={mobilePanel === "inspect" && inspector === "experiment"} onClick={() => { setMobilePanel("inspect"); setInspector("experiment"); }}><Icon name="sliders" />Thử nghiệm</button><button type="button" aria-pressed={mobilePanel === "inspect" && inspector !== "experiment"} onClick={() => { setMobilePanel("inspect"); setInspector("understand"); }}><Icon name="book" />Giải thích</button></nav>
      <div className="learn-layout" data-mobile-panel={mobilePanel} data-library-pane={user?.role === "TEACHER"} data-library-collapsed={libraryCollapsed}>
        {user?.role === "TEACHER" && <TeacherLibraryPane folders={folders} items={libraryItems}
          onRetry={retryLibrary}
          currentSimulationId={simulation.simulationId} loading={libraryLoading} error={libraryError}
          openingId={openingLibraryId} onCreateFolder={createFolder} onOpen={openLibraryItem} onNewSimulation={onNewSimulation} />}
        <section className="learn-exploration" aria-label="Quan sát và khám phá">
          <section className="learn-stage" aria-label="Mô phỏng tương tác">
            {/* Canvas + Controls in unified container (like Desmos) */}
            <div className="learn-stage-wrapper">
              {/* Formula Hero Bar (HUD) - Above Canvas */}
              {canPlay && <div className="studio-canvas-formula">
                <div className="formula-hero-left">
                  <div>
                    <div className="formula-hero-kicker">PHƯƠNG TRÌNH CHUYỂN ĐỘNG</div>
                    <div className="formula-latex-text">{copy.formula}</div>
                  </div>
                </div>
                <div className="formula-hero-actions">
                  <span className="formula-tag">Dual-Validation: PASSED ✓</span>
                  <button className="formula-reset-btn" onClick={() => seek(times[0])} title="Reset View">⟲</button>
                </div>
              </div>}

              {/* Canvas viewport container */}
              <div className="learn-canvas-container">
                <div className="learn-canvas">
                  {!canPlay ? <div className="learn-blocked" role="alert"><Icon name="book" /><h2>{validData ? "Mô hình cần được kiểm tra lại" : "Chưa có đủ dữ liệu để quan sát"}</h2><p>Trở về đề bài, kiểm tra thông tin và chạy lại mô phỏng.</p><Link to="/" className="learn-primary-link">Về đề bài <Icon name="arrow" /></Link></div>
                    : <PhysicsScene simulation={simulation} index={index} overlays={overlays} time={time} />}
                </div>
                <div className="learn-overlay-controls" role="group" aria-label="Thành phần hiển thị">{([
                  ["grid", "Lưới"], ["trajectory", kind === "circuit" ? "Tín hiệu" : "Quỹ đạo"], ["velocity", kind === "circuit" ? "Dòng điện" : "Vận tốc"], ...(!["circuit", "collision"].includes(kind) ? [["acceleration", "Gia tốc"]] : []),
                ] as [keyof typeof overlays, string][]).map(([key, label]) => <button key={key} type="button" aria-pressed={overlays[key]} onClick={() => setOverlays(current => ({ ...current, [key]: !current[key] }))}><span className={`learn-toggle-dot ${key}`} />{label}</button>)}</div>
              </div>

              {/* Metrics Dock */}
              <div className="learn-readouts" aria-label="Đại lượng tại thời điểm đang xem">{allSeries.slice(0, kind === "collision" ? 4 : 3).map(item => <button type="button" key={item.key} className={selectedSeries === item.key ? "selected" : ""} aria-pressed={selectedSeries === item.key} onClick={() => { setSelectedSeries(item.key); setBottomTab("graph"); }}><span><i style={{ background: item.color }} />{item.label}</span><strong>{numberLabel(item.data[index], kind === "circuit" ? 4 : 2)} <small>{item.unit}</small></strong></button>)}</div>

              {/* Floating Playback Dock */}
              {canPlay && <div className="floating-playback-dock" aria-label="Điều khiển phát">
                <button className="studio-play" onClick={togglePlayback} disabled={!canPlay} aria-label={playing ? "Tạm dừng" : "Chạy mô phỏng"}>
                  {playing ? (
                    <svg xmlns="http://www.w3.org/2000/svg" width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="lucide lucide-pause" aria-hidden="true">
                      <rect x="14" y="4" width="4" height="16" rx="1"/><rect x="6" y="4" width="4" height="16" rx="1"/>
                    </svg>
                  ) : (
                    <svg xmlns="http://www.w3.org/2000/svg" width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="lucide lucide-play" aria-hidden="true">
                      <path d="M5 5a2 2 0 0 1 3.008-1.728l11.997 6.998a2 2 0 0 1 .003 3.458l-12 7A2 2 0 0 1 5 19z"></path>
                    </svg>
                  )}
                </button>
                <button onClick={() => seek(times[0])} aria-label="Về đầu mô phỏng">
                  <svg xmlns="http://www.w3.org/2000/svg" width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="lucide lucide-rotate-ccw" aria-hidden="true">
                    <path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"></path><path d="M3 3v5h5"></path>
                  </svg>
                </button>
                <label className="playback-time-label">
                  Thời gian
                  <input
                    className="playback-scrubber" 
                    type="range" 
                    min={times[0] ?? 0} 
                    max={lastTime || 1} 
                    step={0.001}
                    value={time} 
                    onChange={event => seek(Number(event.target.value))} 
                    aria-label="Tua thời gian" 
                  />
                </label>
                <span className="playback-time-display">{numberLabel(time, 2)} / {numberLabel(lastTime, 2)} s</span>
                <select 
                  className="playback-speed-select"
                  value={speed} 
                  onChange={event => setSpeed(Number(event.target.value))}
                  aria-label="Tốc độ mô phỏng"
                >
                  <option value={0.25}>0.25×</option>
                  <option value={0.5}>0.5×</option>
                  <option value={1}>1×</option>
                  <option value={2}>2×</option>
                </select>
              </div>}
            </div>
          </section>
        </section>
        <aside className="learn-inspector" aria-label="Hướng dẫn học và thông số">
          <div className="learn-inspector-nav">
            <Tabs id="inspector" label="Bảng học tập" items={[
              { value: "experiment", label: "Tham số" },
              { value: "steps", label: "Lời giải" },
              { value: "understand", label: "Số liệu" },
              { value: "problem", label: "Xuất" }
            ]} value={inspector} onChange={setInspector} />
            {user?.role === "TEACHER" && simulation.valid && (savedItem
              ? <Link className="learn-library-link" to={`/assignments?libraryItemId=${savedItem.id}`}>Giao bài</Link>
              : <button type="button" className="learn-save-button" onClick={() => setShowSave(value => !value)}>Lưu</button>)}
          </div>
          <div key={inspector} className="learn-inspector-body" id="inspector-panel" role="tabpanel" aria-labelledby={`inspector-${inspector}`} tabIndex={0}>
            {inspector === "experiment" && <>
              <div className="learn-section-title"><h2>Điều chỉnh tham số</h2><button type="button" className="learn-icon-button" aria-label="Hoàn tác thông số" title="Hoàn tác về đề ban đầu" disabled={!dirty} onClick={resetDraft}><Icon name="reset" /></button></div>
              <p className="learn-note">Kéo thanh trượt để thay đổi. Mô phỏng tự động cập nhật theo thời gian thực.</p>
              <div className="learn-parameters">
                {controls.map(control => {
                  const value = Number(draft[control.key]);
                  const fieldInvalid = !draft[control.key].trim() || !Number.isFinite(value) || (control.min >= 0 && value < control.min);
                  const min = Math.min(control.min, initialValues[control.key], Number.isFinite(value) ? value : 0);
                  const max = Math.max(control.max, initialValues[control.key], Number.isFinite(value) ? value : 0);
                  return <div className="learn-parameter" key={control.key}><label htmlFor={`parameter-${control.key}`}><span className="learn-variable">{control.symbol}</span>{control.label}</label><div className="learn-parameter-value"><input id={`parameter-${control.key}`} aria-invalid={fieldInvalid} type="number" inputMode="decimal" step="any" min={control.min >= 0 ? control.min : undefined} value={draft[control.key]} onChange={event => handleParamChange(control.key, event.target.value)} /><span>{control.unit}</span></div><input aria-label={`Điều chỉnh ${control.label.toLowerCase()}`} type="range" min={min} max={max} step={control.step} value={Number.isFinite(value) ? value : control.min} onChange={event => handleParamChange(control.key, event.target.value)} /><div className="learn-range-labels"><span>{numberLabel(min)}</span><span>{numberLabel(max)} {control.unit}</span></div></div>;
                })}
                {error && <p className="learn-error" role="alert">{error}</p>}
              </div>
              <div className="learn-discovery"><span className="learn-discovery-kicker"><Icon name="bulb" />Thử nghĩ trước khi chạy</span><p>{copy.prompt}</p><button type="button" onClick={() => setInspector("steps")}>Xem lời giải chi tiết <Icon name="arrow" /></button></div>
            </>}
            
            {inspector === "steps" && <>
              <span className="learn-small-label">Lời giải theo yêu cầu của Thầy Phương</span>
              <h2>📐 Các bước tính toán chi tiết</h2>
              
              <div className="calc-step-card" style={{ marginTop: '20px' }}>
                <div className="calc-step-badge">Bước 1</div>
                <h3>Giả thiết và xác định đại lượng</h3>
                <div className="learn-equation"><p>{copy.formula}</p></div>
                <dl className="learn-givens" style={{ marginTop: '12px' }}>
                  {controls.map(control => <div key={control.key}><dt>{control.label} ({control.symbol})</dt><dd>{numberLabel(initialValues[control.key], 3)} {control.unit}</dd></div>)}
                </dl>
              </div>

              <div className="calc-step-card">
                <div className="calc-step-badge">Bước 2</div>
                <h3>Phương trình chuyển động</h3>
                <p className="learn-explanation">{copy.explanation}</p>
              </div>

              <div className="calc-step-card">
                <div className="calc-step-badge">Bước 3</div>
                <h3>Thay số và tính toán</h3>
                <div className="learn-givens" style={{ marginTop: '12px' }}>
                  {allSeries.slice(0, 3).map(item => (
                    <div key={item.key}>
                      <dt>{item.label} cực đại</dt>
                      <dd>{numberLabel(Math.max(...item.data), 4)} {item.unit}</dd>
                    </div>
                  ))}
                </div>
              </div>

              <div className="calc-step-card">
                <div className="calc-step-badge">Bước 4</div>
                <h3>Kết quả và đối chứng</h3>
                <div className="learn-validation" style={{ marginTop: '10px', display: 'inline-flex' }}>
                  <Icon name="check" />Dual-Validation: PASSED ✓
                </div>
                <p className="learn-note" style={{ marginTop: '12px' }}>
                  Mô phỏng đã được kiểm chứng với {times.length} mốc thời gian.<br/>
                  Thời gian mô phỏng: {numberLabel(lastTime)} s
                </p>
              </div>

            </>}
            
            {inspector === "understand" && <>
              <span className="learn-small-label">Xem đồ thị và dữ liệu</span>
              <h2>📊 Đồ thị & Bảng số liệu</h2>
              
              {/* Chart/Data Toggle */}
              <div style={{ marginTop: '16px' }}>
                <Tabs id="analysis" label="Cách xem dữ liệu" items={[{ value: "graph", label: "Đồ thị" }, { value: "data", label: "Bảng số" }]} value={bottomTab} onChange={setBottomTab} />
              </div>

              {/* Chart View */}
              {bottomTab === "graph" && <>
                {series && <label className="learn-series-select" style={{ marginTop: '12px', display: 'block' }}>
                  <span style={{ fontSize: '11px', color: '#6c7d94', marginBottom: '6px', display: 'block' }}>Đại lượng hiển thị</span>
                  <select value={selectedSeries ?? ""} onChange={event => setSelectedSeries(event.target.value)} style={{ width: '100%' }}>
                    {allSeries.map(item => <option value={item.key} key={item.key}>{item.label} ({item.unit})</option>)}
                  </select>
                </label>}
                <div style={{ marginTop: '16px', height: '280px', border: '1px solid #e5e9ef', borderRadius: '8px', overflow: 'hidden' }}>
                  {series && validData ? <LearningChart series={series} times={times} index={index} onSeek={seek} /> : <p className="learn-note" style={{ padding: '20px', textAlign: 'center' }}>Chưa có chuỗi dữ liệu hợp lệ.</p>}
                </div>
                {series && <div className="learn-instant" style={{ marginTop: '16px' }}><span>Ở thời điểm {numberLabel(times[index])} s</span><strong>{series.label}: {numberLabel(series.data[index], 4)} {series.unit}</strong></div>}
              </>}

              {/* Data Table View */}
              {bottomTab === "data" && <>
                <div style={{ marginTop: '12px' }}>
                  <div className="learn-export" style={{ marginBottom: '12px' }}>
                    {(["csv", "json", "pdf", "html"] as const).map(format => <button type="button" key={format} onClick={() => void download(format)} disabled={Boolean(downloading)} aria-label={`Tải ${format.toUpperCase()}`}><Icon name="download" />{downloading === format ? "Đang tải…" : format.toUpperCase()}</button>)}
                  </div>
                </div>
                <div className="learn-data-wrap" style={{ maxHeight: '400px', border: '1px solid #e5e9ef', borderRadius: '8px', overflow: 'auto' }}>
                  <table><caption>Dữ liệu từng thời điểm · {times.length} mốc</caption><thead><tr><th>Thời gian (s)</th>{allSeries.map(item => <th key={item.key}>{item.symbol} ({item.unit})</th>)}</tr></thead><tbody>{times.map((t, i) => <tr key={i} aria-current={i === index ? "true" : undefined}><td><button type="button" onClick={() => seek(t)} aria-label={`Quan sát tại ${t} giây`}>{numberLabel(t, 3)}</button></td>{allSeries.map(item => <td key={item.key}>{numberLabel(item.data[i], 4)}</td>)}</tr>)}</tbody></table>
                </div>
                {exportError && <p role="alert" className="learn-error">{exportError}</p>}
              </>}

              {/* Explanation below */}
              <div style={{ marginTop: '24px', paddingTop: '20px', borderTop: '1px solid #e5e9ef' }}>
                <h3 style={{ fontSize: '13px', marginBottom: '10px' }}>Giải thích ý nghĩa</h3>
                <div className="learn-equation"><p>{copy.formula}</p></div>
                <p className="learn-explanation">{copy.explanation}</p>
                <div className="learn-observe"><Icon name="chart" /><div><h3>Đọc tại cùng một thời điểm</h3><p>Kéo thanh thời gian hoặc chạm vào đồ thị. Vị trí vật, các đại lượng và điểm trên đồ thị sẽ cùng thay đổi.</p></div></div>
              </div>
            </>}
            
            {inspector === "problem" && <>
              <span className="learn-small-label">Bộ công cụ xuất dữ liệu</span>
              <h2>📥 Xuất báo cáo & dữ liệu</h2>
              
              <div style={{ marginTop: '24px' }}>
                <h3 style={{ fontSize: '13px', marginBottom: '12px', color: '#40516a' }}>Xuất JSON Replay Specification</h3>
                <div className="learn-export">
                  <button type="button" className="export-action-btn" onClick={() => void download("json")} disabled={Boolean(downloading)}>
                    <Icon name="download" />{downloading === "json" ? "Đang tải…" : "JSON"}
                  </button>
                </div>
              </div>

              <div style={{ marginTop: '24px' }}>
                <h3 style={{ fontSize: '13px', marginBottom: '12px', color: '#40516a' }}>Xuất CSV chuỗi thời gian</h3>
                <div className="learn-export">
                  <button type="button" className="export-action-btn" onClick={() => void download("csv")} disabled={Boolean(downloading)}>
                    <Icon name="download" />{downloading === "csv" ? "Đang tải…" : "CSV"}
                  </button>
                </div>
              </div>

              <div style={{ marginTop: '24px' }}>
                <h3 style={{ fontSize: '13px', marginBottom: '12px', color: '#40516a' }}>In báo cáo PDF</h3>
                <div className="learn-export">
                  <button type="button" className="export-action-btn" onClick={() => window.print()}>
                    <Icon name="download" />In PDF (Ctrl+P)
                  </button>
                </div>
              </div>

              {exportError && <p role="alert" className="learn-error" style={{ marginTop: '16px' }}>{exportError}</p>}

              <blockquote className="learn-problem-text" style={{ marginTop: '32px' }}>
                <strong>Ngữ cảnh đề bài:</strong><br/><br/>
                {problem?.editableText || problem?.originalText || "Nội dung đề bài chưa có trong phiên này."}
              </blockquote>
            </>}
          </div>
          <div className="learn-inspector-footer"><Icon name="book" /><span>Quan sát · Đặt câu hỏi · Tự khám phá</span></div>
        </aside>
      </div>
    </main>
  </div>;
}
