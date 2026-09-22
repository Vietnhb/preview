import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import axiosClient from "../../api/axios";
import { createLibraryFolder, saveLibrary } from "../../api/libraryApi";
import { curriculum } from "../../api/curriculumApi";
import { adjustSimulation } from "../../api/simulationApi";
import { useTeacherLibrary } from "../../store/useTeacherLibrary";
import PhysicsScene from "../simulation/PhysicsScene";
import Icon from "../common/LearningIcon";
import LearningHeader from "../common/LearningHeader";
import TeacherLibraryPane from "./TeacherLibraryPane";
import type { Curriculum, Problem, Simulation, LibraryItem } from "../../types/physlive";
import { controlValue, indexAtTime, interpolateAtTime, isWithinControlBounds, learningSeries, lessonCopy, lessonKind, numberLabel } from "../../utils/learningModel";
import { usePhysliveStore } from "../../store/usePhysliveStore";
import { LearningInspector } from "./learning/LearningInspector";
import { canManageLearning } from "../../types/roles";

export default function LearningWorkspace({ simulation, problem, onUpdate, onNewSimulation, onSelectSimulation, simulationLoading = false, loadingSimulationId = null }: Readonly<{ simulation: Simulation; problem: Problem | null; onUpdate: (simulation: Simulation) => void; onNewSimulation?: () => void; onSelectSimulation?: (simulationId: string) => boolean | void; simulationLoading?: boolean; loadingSimulationId?: string | null }>) {
  const user = usePhysliveStore(state => state.user);
  const canManageLearningContent = canManageLearning(user?.role);
  const kind = lessonKind(simulation.schemaId), copy = lessonCopy[kind];
  const times = simulation.time;
  const allSeries = useMemo(() => learningSeries(simulation), [simulation]);
  const controls = useMemo(() => simulation.visualization?.controls ?? [], [simulation.visualization]);
  // Snapshot of the original server-generated simulation – never overwritten by client re-solves.
  const baseSimulationRef = useRef<Simulation>(simulation);
  const baseSimulationIdentityRef = useRef(simulation.simulationId);
  useEffect(() => {
    // Client-side parameter changes keep the same identity. Only replace the
    // baseline when a genuinely new server simulation/run arrives.
    const identity = simulation.simulationId;
    if (identity === baseSimulationIdentityRef.current) return;
    baseSimulationIdentityRef.current = identity;
    baseSimulationRef.current = simulation;
  }, [simulation]);

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
  const [adjusting, setAdjusting] = useState(false);
  const [speed, setSpeed] = useState(1);
  const [time, setTime] = useState(times[0] ?? 0);
  const [seekRevision, setSeekRevision] = useState(0);
  const timeRef = useRef(time);
  const [error, setError] = useState("");
  const [exportError, setExportError] = useState("");
  const [downloading, setDownloading] = useState<string | null>(null);
  const [showSave, setShowSave] = useState(false);
  const [saving, setSaving] = useState(false);
  const { folders, setFolders, libraryItems, setLibraryItems, libraryLoading, libraryError, setLibraryError, retryLibrary } = useTeacherLibrary();
  const savedItem = libraryItems.find(item => item.simulationId === simulation.simulationId) ?? null;
  const openingLibraryId = libraryItems.find(item => item.simulationId === loadingSimulationId)?.id ?? null;
  const [folderId, setFolderId] = useState("");
  const [saveError, setSaveError] = useState("");
  const [saveTitle, setSaveTitle] = useState(() => {
    const source = problem?.editableText || problem?.originalText || copy.title;
    return source.trim().replaceAll(/\s+/g, " ").slice(0, 120);
  });
  const [visibility, setVisibility] = useState<LibraryItem["visibility"]>("PERSONAL");
  const [curriculumTree, setCurriculumTree] = useState<Curriculum | null>(null);
  const [topicId, setTopicId] = useState("");
  const [moduleId, setModuleId] = useState("");
  const [levelId, setLevelId] = useState("");
  const [lessonId, setLessonId] = useState("");
  const [overlays, setOverlays] = useState({ grid: true, trajectory: true, velocity: true, acceleration: false });
  const mounted = useRef(true);
  const adjustmentTimerRef = useRef<number | null>(null);
  const adjustmentRequestRef = useRef(0);
  const lastTime = times.at(-1) ?? 0;
  const index = indexAtTime(times, time);
  const series = allSeries.find(item => item.key === selectedSeries) ?? allSeries[0];
  const validData = times.length > 1 && times.every((t, i) => Number.isFinite(t) && (i === 0 || t > times[i - 1])) && Boolean(series);
  const canPlay = validData && simulation.valid;
  // A simulation swap keeps this component mounted. During the one render
  // before the reset effect runs, the new controls can briefly be paired with
  // the previous simulation's draft. Keep that transition render-safe.
  const dirty = controls.some(c => {
    const draftValue = draft[c.key] ?? "";
    const initialValue = initialValues[c.key];
    return draftValue.trim() === "" || !Number.isFinite(initialValue) || Number(draftValue) !== initialValue;
  });

  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  useEffect(() => () => {
    if (adjustmentTimerRef.current !== null) globalThis.clearTimeout(adjustmentTimerRef.current);
  }, []);
  useEffect(() => { if (canManageLearningContent && simulation.valid) void curriculum().then(setCurriculumTree).catch(() => setCurriculumTree(null)); }, [canManageLearningContent, simulation.valid]);
  useEffect(() => {
    // Reset time when simulation changes (e.g., after parameter adjustment)
    setTime(times[0] ?? 0);
    timeRef.current = times[0] ?? 0;
    setPlaying(false);
  }, [simulation.simulationId, times]);
  useEffect(() => {
    // Keep the workspace mounted between simulations so the shell does not
    // flash/reload, while explicitly resetting simulation-specific controls.
    if (adjustmentTimerRef.current !== null) globalThis.clearTimeout(adjustmentTimerRef.current);
    adjustmentRequestRef.current += 1;
    setInspector("experiment");
    setMobilePanel("observe");
    setBottomTab("graph");
    setSelectedSeries(null);
    setAdjusting(false);
    setSpeed(1);
    setError("");
    setExportError("");
    setDownloading(null);
    setShowSave(false);
    setSaving(false);
    setFolderId("");
    setSaveError("");
    setSaveTitle((problem?.editableText || problem?.originalText || copy.title).trim().replaceAll(/\s+/g, " ").slice(0, 120));
    setVisibility("PERSONAL");
    setCurriculumTree(null);
    setTopicId("");
    setModuleId("");
    setLevelId("");
    setLessonId("");
    setOverlays({ grid: true, trajectory: true, velocity: true, acceleration: false });
  }, [copy.title, problem?.editableText, problem?.originalText, simulation.simulationId]);
  
  const handleParamChange = (key: string, valueStr: string) => {
    if (adjusting) return;
    // Stop and reset immediately, before the debounced solver request returns.
    setPlaying(false);
    setTime(times[0] ?? 0);
    timeRef.current = times[0] ?? 0;
    setSeekRevision(value => value + 1);
    if (adjustmentTimerRef.current !== null) globalThis.clearTimeout(adjustmentTimerRef.current);
    adjustmentRequestRef.current += 1;
    const nextDraft = { ...draft, [key]: valueStr };
    setDraft(nextDraft);

    const val = Number(valueStr);
    const ctrl = controls.find(c => c.key === key);
    if (!valueStr.trim() || !ctrl || !isWithinControlBounds(ctrl, val)) {
      return;
    }

    const numericOverrides: Record<string, number> = {};
    for (const c of controls) {
      const raw = nextDraft[c.key] ?? "";
      const v = Number(raw);
      if (!raw.trim() || !isWithinControlBounds(c, v)) return;
      numericOverrides[c.key] = v;
    }

    if (adjustmentTimerRef.current !== null) globalThis.clearTimeout(adjustmentTimerRef.current);
    const requestId = ++adjustmentRequestRef.current;
    const simulationId = simulation.simulationId;
    adjustmentTimerRef.current = globalThis.setTimeout(() => {
      setAdjusting(true);
      void adjustSimulation(simulationId, numericOverrides)
        .then(updated => {
          if (!mounted.current || requestId !== adjustmentRequestRef.current
              || usePhysliveStore.getState().simulation?.simulationId !== simulationId) return;
          onUpdate(updated);
          setError("");
          setAdjusting(false);
          setPlaying(false);
          setTime(updated.time[0] ?? 0);
          timeRef.current = updated.time[0] ?? 0;
        })
        .catch(() => {
          setAdjusting(false);
          if (mounted.current && requestId === adjustmentRequestRef.current) {
            setError("Không thể cập nhật mô phỏng với giá trị này.");
          }
        });
    }, 180);
  };

  useEffect(() => {
    if (!curriculumTree || topicId) return;
    const simulationTopic = problem?.currentSpecification?.topic;
    const match = curriculumTree.topics.find(item => item.name.toLowerCase() === simulationTopic?.toLowerCase());
    if (match) setTopicId(match.id);
  }, [curriculumTree, problem?.currentSpecification?.topic, topicId]);
  useEffect(() => { timeRef.current = time; }, [time]);

  const handleCanvasTimeChange = useCallback((next: number) => {
    timeRef.current = next;
    setTime(current => current === next ? current : next);
  }, []);
  const handlePlaybackEnd = useCallback(() => setPlaying(false), []);
  const seek = (next: number) => {
    const clamped = Math.max(times[0] ?? 0, Math.min(lastTime, next));
    timeRef.current = clamped;
    setPlaying(false);
    setTime(clamped);
    setSeekRevision(value => value + 1);
  };
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
  const download = async (format: "json" | "csv" | "pdf" | "html" | "slides") => {
    setDownloading(format); setExportError("");
    try {
      const blob: Blob = (await axiosClient.get(`/exports/${simulation.specificationId}/${format}`, { responseType: "blob" })).data;
      const url = URL.createObjectURL(blob), link = document.createElement("a");
      link.href = url; link.download = `physlive-${simulation.specificationId}.${format}`;
      link.click(); globalThis.setTimeout(() => URL.revokeObjectURL(url), 1000);
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
    if (item.simulationId === simulation.simulationId || openingLibraryId || simulationLoading) return;
    setLibraryError("");
    onSelectSimulation?.(item.simulationId);
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
      <div className="learn-top-area" />
      <nav className="learn-mobile-nav" aria-label="Chuyển vùng học tập"><button type="button" aria-pressed={mobilePanel === "observe"} onClick={() => setMobilePanel("observe")}><Icon name="play" />Quan sát</button><button type="button" aria-pressed={mobilePanel === "inspect" && inspector === "experiment"} onClick={() => { setMobilePanel("inspect"); setInspector("experiment"); }}><Icon name="sliders" />Thử nghiệm</button><button type="button" aria-pressed={mobilePanel === "inspect" && inspector !== "experiment"} onClick={() => { setMobilePanel("inspect"); setInspector("understand"); }}><Icon name="book" />Giải thích</button></nav>
      <div className="learn-layout" data-mobile-panel={mobilePanel} data-library-pane={canManageLearningContent} data-library-collapsed={libraryCollapsed}>
        {canManageLearningContent && <TeacherLibraryPane folders={folders} items={libraryItems}
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
                {simulationLoading && <div className="learn-simulation-loading" role="status" aria-live="polite"><span className="learn-loading-spinner" aria-hidden="true" />Đang mở simulation…</div>}
                <div className="learn-canvas">
                  {canPlay ? <PhysicsScene simulation={simulation} index={index} overlays={overlays} time={time} seekRevision={seekRevision} playing={playing} speed={speed} onTimeChange={handleCanvasTimeChange} onPlaybackEnd={handlePlaybackEnd} />
                    : <div className="learn-blocked" role="alert"><Icon name="book" /><h2>{validData ? "Mô hình cần được kiểm tra lại" : "Chưa có đủ dữ liệu để quan sát"}</h2><p>Trở về đề bài, kiểm tra thông tin và chạy lại mô phỏng.</p><Link to="/workspace" className="learn-primary-link">Về đề bài <Icon name="arrow" /></Link></div>}
                </div>
                <fieldset className="learn-overlay-controls"><legend className="learn-sr-only">Thành phần hiển thị</legend>{([
                  ["grid", "Lưới"], ["trajectory", kind === "circuit" ? "Tín hiệu" : "Quỹ đạo"], ["velocity", kind === "circuit" ? "Dòng điện" : "Vận tốc"], ...(["circuit", "collision"].includes(kind) ? [] : [["acceleration", "Gia tốc"]]),
                ] as [keyof typeof overlays, string][]).map(([key, label]) => <button key={key} type="button" aria-pressed={overlays[key]} onClick={() => setOverlays(current => ({ ...current, [key]: !current[key] }))}><span className={`learn-toggle-dot ${key}`} />{label}</button>)}</fieldset>
              </div>

              {/* Metrics Dock */}
              <div className="learn-readouts" aria-label="Đại lượng tại thời điểm đang xem">{allSeries.slice(0, kind === "collision" ? 4 : 3).map(item => <button type="button" key={item.key} className={selectedSeries === item.key ? "selected" : ""} aria-pressed={selectedSeries === item.key} onClick={() => { setSelectedSeries(item.key); setBottomTab("graph"); }}><span><i style={{ background: item.color }} />{item.label}</span><strong>{numberLabel(interpolateAtTime(times, item.data, time), kind === "circuit" ? 4 : 2)} <small>{item.unit}</small></strong></button>)}</div>

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
                  <span>Thời gian</span>
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
        <LearningInspector
          userRole={user?.role}
          simulation={simulation}
          problem={problem}
          copy={copy}
          times={times}
          time={time}
          lastTime={lastTime}
          allSeries={allSeries}
          controls={controls}
          initialValues={initialValues}
          draft={draft}
          inspector={inspector}
          bottomTab={bottomTab}
          selectedSeries={selectedSeries}
          series={series}
          index={index}
          validData={validData}
          dirty={dirty}
          savedItem={savedItem}
          error={error}
          saveError={saveError}
          exportError={exportError}
          downloading={downloading}
          showSave={showSave}
          saving={saving}
          folders={folders}
          topics={topics}
          modules={modules}
          levels={levels}
          lessons={lessons}
          folderId={folderId}
          topicId={topicId}
          moduleId={moduleId}
          levelId={levelId}
          lessonId={lessonId}
          saveTitle={saveTitle}
          visibility={visibility}
          onInspectorChange={value => { setInspector(value); setShowSave(false); }}
          onBottomTabChange={setBottomTab}
          onToggleSave={() => setShowSave(value => !value)}
          onParamChange={handleParamChange}
          onResetDraft={resetDraft}
          onSeek={seek}
          onSelectedSeriesChange={setSelectedSeries}
          onDownload={download}
          onPersist={persistToLibrary}
          onFolderChange={setFolderId}
          onTopicChange={id => { setTopicId(id); setModuleId(""); setLevelId(""); setLessonId(""); }}
          onModuleChange={id => { setModuleId(id); setLevelId(""); setLessonId(""); }}
          onLevelChange={id => { setLevelId(id); setLessonId(""); }}
          onLessonChange={setLessonId}
          onSaveTitleChange={setSaveTitle}
          onVisibilityChange={setVisibility}
          onCancelSave={() => setShowSave(false)}
        />
      </div>
    </main>
  </div>;
}
