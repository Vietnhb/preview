import SimulationThumb from "./SimulationThumb";
import { useMemo, useRef, useState } from "react";
import { Avatar, Badge, Button, Dialog, IconButton, SegmentedControl, Select, Spinner, TextField, Theme } from "@radix-ui/themes";
import { ArrowTopRightIcon, ChevronRightIcon, Cross2Icon, MagnifyingGlassIcon, PauseIcon, PlayIcon, ReaderIcon, ResetIcon, StarFilledIcon } from "@radix-ui/react-icons";
import { AnimatePresence, motion, useReducedMotion } from "motion/react";
import PhysicsScene from "../../simulation/components/CanvasPhysicsScene";
import SvgPixiScene from "../../simulation/components/SvgPixiScene";
import type { GeneratedSimulationResult } from "../../simulation/api/simulationUnderstandingApi";
import "../../simulation/styles/simulation.css";
import type { Curriculum, LibraryItem, Simulation } from "../../../shared/types/physlive";
import { SpotlightCard } from "../../../shared/effects/Motion";
import { buildCatalog, filterLibrary, indexLessonPaths, type CatalogSelection, type CatalogSubject, type LibraryScope } from "../model/catalogModel";
import { ResourceDiscussion } from "./ResourceDiscussion";
import styles from "./ResourceDiscovery.module.css";

type Props = {
  items: LibraryItem[]; loading: boolean; selectedItem: LibraryItem | null; simulation: Simulation | null;
  curriculum?: Curriculum | null;
  /** The author's scene for the open item; shown instead of the generic player when available. */
  generated?: GeneratedSimulationResult | null;
  time: number; simulationLoading: boolean; simulationError: string; frame: number; playing: boolean;
  vectors: { grid: boolean; trajectory: boolean; velocity: boolean; acceleration: boolean };
  onOpen: (item: LibraryItem) => void; onClose: () => void; onTogglePlaying: () => void; onReset: () => void;
  onFrameChange: (frame: number) => void; onTimeChange: (time: number) => void; onPlaybackEnd: () => void;
  error?: string;
  allowSchoolScope?: boolean;
  onRetry?: () => void;
};

type Accent = "indigo" | "cyan" | "amber";
function hashOf(value: string) {
  let hash = 0;
  for (const character of value) hash = (hash * 31 + character.charCodeAt(0)) | 0;
  return Math.abs(hash);
}
const resourceAccent = (moduleId: string): Accent => (["indigo", "cyan", "amber"] as const)[hashOf(moduleId) % 3];

type CatalogNavProps = { catalog: CatalogSubject[]; selection: CatalogSelection; total: number; counts: Map<string, number>; onSelect: (selection: CatalogSelection) => void };

/** Table of contents: grade tabs, then modules that open to their lessons. One marker slides to the active row. */
function CatalogNav({ catalog, selection, total, counts, onSelect }: Readonly<CatalogNavProps>) {
  const reducedMotion = useReducedMotion();
  const [gradeTab, setGradeTab] = useState<Record<string, string>>({});
  const [open, setOpen] = useState<Record<string, boolean>>({});
  /** Chapters without any simulation stay folded away per grade until asked for. */
  const [showEmpty, setShowEmpty] = useState<Record<string, boolean>>({});
  const spring = reducedMotion ? { duration: 0 } : { type: "spring" as const, stiffness: 420, damping: 36 };
  const sum = (lessons: { id: string }[]) => lessons.reduce((value, lesson) => value + (counts.get(lesson.id) ?? 0), 0);
  const marker = <motion.span layoutId="catalog-marker" className={styles.navMarker} aria-hidden="true" transition={spring} />;

  return <nav className={styles.nav} aria-label="Mục lục tài nguyên">
    <div className={styles.navHead}><h2>Mục lục</h2>{selection.subjectId && <button type="button" className={styles.navReset} onClick={() => onSelect({})}>Bỏ lọc</button>}</div>
    <button type="button" className={styles.navRow} aria-pressed={!selection.subjectId} onClick={() => onSelect({})}>{!selection.subjectId && marker}<span>Tất cả mô phỏng</span><span className={styles.navCount} data-has>{total}</span></button>
    {catalog.map(subject => {
      const withItems = subject.grades.find(grade => sum(grade.modules.flatMap(module => module.lessons)) > 0);
      const activeGrade = subject.grades.find(grade => grade.name === (selection.subjectId === subject.id && selection.grade ? selection.grade : gradeTab[subject.id])) ?? withItems ?? subject.grades[0];
      if (!activeGrade) return null;
      return <section key={subject.id} className={styles.navSubject} aria-label={subject.name}>
        {catalog.length > 1 && <h3>{subject.name}</h3>}
        <div className={styles.gradeTabs} role="tablist" aria-label={`Khối lớp ${subject.name}`}>
          {subject.grades.map(grade => {
            const active = grade.name === activeGrade.name;
            return <button key={grade.name} type="button" role="tab" aria-selected={active} className={styles.gradeTab}
              onClick={() => { setGradeTab(previous => ({ ...previous, [subject.id]: grade.name })); onSelect({ subjectId: subject.id, grade: grade.name }); }}>
              {active && <motion.span layoutId={`catalog-grade-${subject.id}`} className={styles.gradePill} aria-hidden="true" transition={spring} />}
              <span>{grade.name}</span>{sum(grade.modules.flatMap(module => module.lessons)) > 0 && <small>{sum(grade.modules.flatMap(module => module.lessons))}</small>}
            </button>;
          })}
        </div>
        <ul className={styles.modules}>
          {(() => {
            const gradeKey = `${subject.id}/${activeGrade.name}`;
            const filled = activeGrade.modules.filter(module => sum(module.lessons) > 0);
            const empty = activeGrade.modules.filter(module => sum(module.lessons) === 0);
            // With nothing shared in this grade yet, list every chapter so the outline is not blank.
            const folded = filled.length > 0 && empty.length > 0 && !showEmpty[gradeKey]
              && !empty.some(module => selection.subjectId === subject.id && selection.grade === activeGrade.name && selection.moduleId === module.id);
            return <>
          {(folded ? filled : [...filled, ...empty]).map(module => {
            const key = `${subject.id}/${activeGrade.name}/${module.id}`;
            const inModule = selection.subjectId === subject.id && selection.grade === activeGrade.name && selection.moduleId === module.id;
            const expanded = open[key] ?? inModule;
            const moduleCount = sum(module.lessons);
            const base = { subjectId: subject.id, grade: activeGrade.name, moduleId: module.id };
            return <li key={key}>
              <button type="button" className={styles.navRow} data-empty={moduleCount === 0 || undefined} aria-expanded={expanded} aria-pressed={inModule && !selection.lessonId}
                onClick={() => { setOpen(previous => ({ ...previous, [key]: inModule && !selection.lessonId ? !expanded : true })); onSelect(base); }}>
                {inModule && !selection.lessonId && marker}
                <ChevronRightIcon className={styles.navChevron} data-open={expanded || undefined} />
                <span>{module.name}</span>{moduleCount > 0 && <span className={styles.navCount} data-has>{moduleCount}</span>}
              </button>
              <AnimatePresence initial={false}>{expanded && <motion.ul className={styles.lessons} initial={{ height: 0, opacity: 0 }} animate={{ height: "auto", opacity: 1 }} exit={{ height: 0, opacity: 0 }} transition={{ duration: reducedMotion ? 0 : 0.2 }}>
                {module.lessons.map(lesson => {
                  const active = selection.lessonId === lesson.id;
                  const lessonCount = counts.get(lesson.id) ?? 0;
                  return <li key={lesson.id}><button type="button" className={`${styles.navRow} ${styles.navLesson}`} data-empty={lessonCount === 0 || undefined} aria-pressed={active} onClick={() => onSelect({ ...base, lessonId: lesson.id })}>
                    {active && marker}<span>{lesson.name}</span>{lessonCount > 0 && <span className={styles.navCount} data-has>{lessonCount}</span>}
                  </button></li>;
                })}
              </motion.ul>}</AnimatePresence>
            </li>;
          })}
          {filled.length > 0 && empty.length > 0 && <li><button type="button" className={styles.navMore} aria-expanded={!folded} onClick={() => setShowEmpty(previous => ({ ...previous, [gradeKey]: folded }))}>
            {folded ? `Xem thêm ${empty.length} chương chưa có mô phỏng` : "Ẩn các chương chưa có mô phỏng"}
          </button></li>}
            </>;
          })()}
        </ul>
      </section>;
    })}
    {!catalog.length && <p className={styles.catalogEmpty}>Chưa có chương trình học.</p>}
  </nav>;
}

export function ResourceDiscovery({ items, curriculum = null, generated = null, loading, selectedItem, simulation, time, simulationLoading, simulationError, frame, playing, vectors, onOpen, onClose, onTogglePlaying, onReset, onFrameChange, onTimeChange, onPlaybackEnd, error = "", onRetry, allowSchoolScope = true }: Readonly<Props>) {
  const [selection, setSelection] = useState<CatalogSelection>({});
  const [query, setQuery] = useState("");
  const [scope, setScope] = useState<LibraryScope>("ALL");
  const [sort, setSort] = useState<"latest" | "featured" | "title">("latest");
  const returnFocusRef = useRef<HTMLButtonElement | null>(null);
  const reducedMotion = useReducedMotion();
  const transition = { duration: reducedMotion ? 0 : .18 };
  const catalog = useMemo(() => buildCatalog(curriculum), [curriculum]);
  const paths = useMemo(() => indexLessonPaths(catalog), [catalog]);
  const effectiveScope = allowSchoolScope ? scope : "PUBLIC";
  const scopedItems = useMemo(() => filterLibrary(items, paths, {}, effectiveScope, ""), [items, paths, effectiveScope]);
  const counts = useMemo(() => {
    const result = new Map<string, number>();
    for (const item of scopedItems) result.set(item.lessonId, (result.get(item.lessonId) ?? 0) + 1);
    return result;
  }, [scopedItems]);
  const visible = useMemo(() => filterLibrary(items, paths, selection, effectiveScope, query).sort((a, b) => {
    if (sort === "title") return a.title.localeCompare(b.title, "vi");
    if (sort === "featured") {
      const difference = Number(b.moderationStatus === "FEATURED") - Number(a.moderationStatus === "FEATURED");
      if (difference) return difference;
    }
    return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
  }), [items, paths, selection, effectiveScope, query, sort]);
  const selectedSubject = catalog.find(subject => subject.id === selection.subjectId);
  const selectedGrade = selectedSubject?.grades.find(grade => grade.name === selection.grade);
  const selectedModule = selectedGrade?.modules.find(module => module.id === selection.moduleId);
  const selectedLesson = selectedModule?.lessons.find(lesson => lesson.id === selection.lessonId);
  const breadcrumb = [selectedGrade?.name, selectedModule?.name, selectedLesson?.name].filter(Boolean);
  const selectedPath = selectedItem ? paths.get(selectedItem.lessonId) : undefined;
  const filtered = Boolean(query || selection.subjectId);

  return <Theme asChild accentColor="indigo" grayColor="slate" radius="large" scaling="100%" hasBackground={false}><motion.section className={styles.discovery} aria-label="Kho cộng đồng" initial={reducedMotion ? false : { opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={transition}>
    <div className={`${styles.layout} ${curriculum ? "" : styles.withoutCatalog}`}>
      {curriculum && <aside className={styles.catalog}><CatalogNav catalog={catalog} selection={selection} total={scopedItems.length} counts={counts} onSelect={setSelection} /></aside>}
      <div className={styles.results}>
        <div className={styles.toolbar}>
          <TextField.Root className={styles.search} size="3" type="search" aria-label="Tìm tài nguyên" value={query} onChange={event => setQuery(event.target.value)} placeholder="Tìm mô phỏng, bài học, giáo viên…"><TextField.Slot><MagnifyingGlassIcon width="18" height="18" /></TextField.Slot></TextField.Root>
          {allowSchoolScope && <SegmentedControl.Root size="2" value={scope} onValueChange={value => { if (value) setScope(value as LibraryScope); }} aria-label="Phạm vi chia sẻ"><SegmentedControl.Item value="ALL">Tất cả</SegmentedControl.Item><SegmentedControl.Item value="PUBLIC">Toàn hệ thống</SegmentedControl.Item><SegmentedControl.Item value="SHARED">Trong trường</SegmentedControl.Item></SegmentedControl.Root>}
          <Select.Root size="3" value={sort} onValueChange={value => setSort(value as typeof sort)}><Select.Trigger aria-label="Sắp xếp tài nguyên" className={styles.sort} variant="surface" /><Select.Content><Select.Item value="latest">Mới nhất</Select.Item><Select.Item value="featured">Nổi bật</Select.Item><Select.Item value="title">Tên A–Z</Select.Item></Select.Content></Select.Root>
        </div>
        <div className={styles.resultHead} aria-live="polite">
          <p>{loading ? "Đang tải…" : <><strong>{visible.length}</strong> mô phỏng{breadcrumb.length > 0 && <> trong <span className={styles.crumbs}>{breadcrumb.join(" › ")}</span></>}</>}</p>
          {filtered && !loading && <button type="button" className={styles.navReset} onClick={() => { setQuery(""); setSelection({}); }}><Cross2Icon /> Xóa bộ lọc</button>}
        </div>
        {loading ? <div className={styles.loading} aria-label="Đang tải thư viện"><span /><span /><span /><span /><span /><span /></div> : error ? <div className={styles.empty}><h2>Không tải được tài nguyên</h2><p>{error}</p>{onRetry && <Button variant="soft" onClick={onRetry}>Thử lại</Button>}</div> : visible.length === 0 ? <div className={styles.empty}><span className={styles.emptyIcon}><ReaderIcon width="26" height="26" /></span><h2>{filtered ? "Không tìm thấy mô phỏng phù hợp" : "Chưa có mô phỏng nào"}</h2><p>{filtered ? "Thử từ khóa khác hoặc chọn mục khác trong mục lục." : scope === "SHARED" ? "Chưa có mô phỏng được chia sẻ trong trường." : "Mô phỏng do giáo viên chia sẻ và được kiểm duyệt sẽ xuất hiện tại đây."}</p>{filtered && <Button variant="soft" onClick={() => { setQuery(""); setSelection({}); }}>Xóa bộ lọc</Button>}</div> : <motion.div layout className={styles.resourceGrid} transition={transition}><AnimatePresence initial={false} mode="popLayout">{visible.map((item, index) => {
          const path = paths.get(item.lessonId);
          const seed = path?.moduleId || item.topic || item.id;
          const accent = resourceAccent(seed);
          const featured = item.moderationStatus === "FEATURED";
          return <motion.article layout className={styles.resourceMotion} key={item.id} initial={reducedMotion ? false : { opacity: 0, y: 14 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, scale: reducedMotion ? 1 : .97 }} whileHover={reducedMotion ? undefined : { y: -4 }} transition={reducedMotion ? transition : { duration: .28, delay: Math.min(index, 8) * .035 }}>
            <SpotlightCard className={styles.resource}>
              <div className={styles.cover} data-accent={accent}><SimulationThumb simulationId={item.simulationId} />{featured && <span className={styles.featured}><StarFilledIcon /> Nổi bật</span>}</div>
              <div className={styles.resourceBody}>
                <p className={styles.eyebrow}>{[path?.grade, path?.module].filter(Boolean).join(" · ") || "Mô phỏng vật lý"}</p>
                <h2>{item.title}</h2>
                {path?.lesson && <p className={styles.lesson}>{path.lesson}</p>}
                <div className={styles.resourceFooter}>
                  <div className={styles.author}><Avatar size="1" color={accent} radius="full" fallback={(item.sharedByName || "GV").trim().charAt(0).toUpperCase()} /><div><strong>{item.sharedByName || "Giáo viên"}</strong><span>{item.visibility === "PUBLIC" ? item.schoolName || "PhysLive" : "Trong trường"}</span></div></div>
                  <Button size="2" variant="soft" onClick={event => { returnFocusRef.current = event.currentTarget; onOpen(item); }} aria-label={`Mở ${item.title}`}>Mở <ArrowTopRightIcon /></Button>
                </div>
              </div>
            </SpotlightCard>
          </motion.article>;
        })}</AnimatePresence></motion.div>}
      </div>
    </div>
    <Dialog.Root open={Boolean(selectedItem)} onOpenChange={open => { if (!open) onClose(); }}><Dialog.Content maxWidth="980px" size="3" onCloseAutoFocus={event => { event.preventDefault(); returnFocusRef.current?.focus(); }}>
      <div className={styles.dialogHeading}><div><Badge color="indigo" variant="soft">Vật lý{selectedPath?.grade ? ` · ${selectedPath.grade}` : ""}</Badge><Dialog.Title mt="3" mb="2">{selectedItem?.title || "Mô phỏng"}</Dialog.Title><Dialog.Description size="2" color="gray">{selectedItem?.sharedByName || "Giáo viên"}{selectedItem?.schoolName ? ` · ${selectedItem.schoolName}` : ""}</Dialog.Description></div><Dialog.Close><IconButton size="2" variant="soft" color="gray" aria-label="Đóng mô phỏng"><Cross2Icon /></IconButton></Dialog.Close></div>
      {simulationLoading && <div className={styles.playerState}><Spinner size="3" /> Đang mở mô phỏng…</div>}
      {!simulationLoading && simulationError && <p className={`${styles.playerState} ${styles.error}`}>{simulationError}</p>}
      {!simulationLoading && !simulationError && generated?.simulationSpec.solverTimeline && <div>
        <SvgPixiScene program={generated.simulationSpec.visualProgram ?? { code: "" }} timeline={generated.simulationSpec.solverTimeline}
          parameters={generated.savedParameters ?? Object.fromEntries(generated.parameters.map(parameter => [parameter.name, parameter.value]))}
          models={generated.simulationSpec.physicsModels}
          fieldMeta={generated.simulationSpec.solverFieldMeta as Record<string, { unit?: string; label?: string }> | undefined}
          verificationStatus={generated.validation?.status ?? "VISUAL_ONLY_UNVERIFIED"} />
      </div>}
      {!simulationLoading && !simulationError && !generated && simulation && <motion.div initial={reducedMotion ? false : { opacity: 0 }} animate={{ opacity: 1 }} transition={transition}><div className={styles.scene}><PhysicsScene simulation={simulation} index={frame} overlays={vectors} time={time} playing={playing} onTimeChange={onTimeChange} onPlaybackEnd={onPlaybackEnd} /></div><div className={styles.playback}><Button onClick={onTogglePlaying}>{playing ? <PauseIcon /> : <PlayIcon />}{playing ? "Tạm dừng" : "Chạy mô phỏng"}</Button><Button color="gray" variant="soft" onClick={onReset}><ResetIcon /> Về đầu</Button><input aria-label="Thời gian mô phỏng" type="range" min={0} max={Math.max(0, simulation.time.length - 1)} value={frame} onChange={event => onFrameChange(Number(event.target.value))} /><span>{time.toFixed(2)} s</span></div></motion.div>}
      {selectedItem && <ResourceDiscussion key={selectedItem.id} resourceId={selectedItem.id} />}
    </Dialog.Content></Dialog.Root>
  </motion.section></Theme>;
}