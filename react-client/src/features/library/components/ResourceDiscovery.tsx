import { useMemo, useRef, useState, type ReactNode } from "react";
import { Avatar, Badge, Button, Card, Dialog, IconButton, Inset, SegmentedControl, Select, Spinner, TextField, Theme } from "@radix-ui/themes";
import { ArrowTopRightIcon, BackpackIcon, ChevronRightIcon, Cross2Icon, GlobeIcon, MagnifyingGlassIcon, PauseIcon, PlayIcon, ReaderIcon, ResetIcon, StarFilledIcon } from "@radix-ui/react-icons";
import { AnimatePresence, motion, useReducedMotion } from "motion/react";
import PhysicsScene from "../../../components/simulation/PhysicsScene";
import type { Curriculum, LibraryItem, Simulation } from "../../../types/physlive";
import { buildCatalog, filterLibrary, indexLessonPaths, type CatalogSelection, type LibraryScope } from "./catalogModel";
import styles from "./ResourceDiscovery.module.css";

type Props = {
  items: LibraryItem[]; loading: boolean; selectedItem: LibraryItem | null; simulation: Simulation | null;
  curriculum?: Curriculum | null;
  time: number; simulationLoading: boolean; simulationError: string; frame: number; playing: boolean;
  vectors: { grid: boolean; trajectory: boolean; velocity: boolean; acceleration: boolean };
  onOpen: (item: LibraryItem) => void; onClose: () => void; onTogglePlaying: () => void; onReset: () => void;
  onFrameChange: (frame: number) => void; onTimeChange: (time: number) => void; onPlaybackEnd: () => void;
  error?: string;
  allowSchoolScope?: boolean;
  onRetry?: () => void;
};

type Accent = "indigo" | "cyan" | "amber";
function resourceAccent(moduleId: string): Accent {
  let hash = 0;
  for (const character of moduleId) hash = (hash * 31 + character.charCodeAt(0)) | 0;
  return (["indigo", "cyan", "amber"] as const)[Math.abs(hash) % 3];
}

function TreeRow({ label, count, selected, expanded, onToggle, onSelect }: { label: string; count: number; selected: boolean; expanded?: boolean; onToggle?: () => void; onSelect: () => void }) {
  return <div className={styles.treeRow} data-selected={selected}>
    {onToggle ? <IconButton size="1" variant="ghost" color="gray" className={styles.treeToggle} onClick={onToggle} aria-label={`${expanded ? "Thu gọn" : "Mở rộng"} ${label}`} aria-expanded={expanded}><ChevronRightIcon className={expanded ? styles.expanded : ""} /></IconButton> : <span className={styles.leafMark} aria-hidden="true" />}
    <Button size="2" variant={selected ? "soft" : "ghost"} color={selected ? "indigo" : "gray"} highContrast={!selected} className={styles.treeLabel} aria-pressed={selected} onClick={onSelect}><span>{label}</span><Badge size="1" color={selected ? "indigo" : "gray"} variant="soft">{count}</Badge></Button>
  </div>;
}

function TreeChildren({ open, children }: { open: boolean; children: ReactNode }) {
  const reducedMotion = useReducedMotion();
  return <AnimatePresence initial={false}>{open && <motion.ul className={styles.treeBranch} initial={{ height: 0, opacity: 0 }} animate={{ height: "auto", opacity: 1 }} exit={{ height: 0, opacity: 0 }} transition={{ duration: reducedMotion ? 0 : .18 }}>{children}</motion.ul>}</AnimatePresence>;
}

function ResourceDiagram() {
  return <svg className={styles.coverDiagram} viewBox="0 0 100 76" fill="none" aria-hidden="true"><path d="M10 8v58h80M10 48h80M10 30h80M30 8v58M50 8v58M70 8v58" stroke="currentColor" opacity=".16" /><path d="M12 55c12 0 15-30 28-30s15 27 27 27 12-32 23-32" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" /><circle cx="40" cy="25" r="4" fill="currentColor" /><circle cx="67" cy="52" r="3" fill="currentColor" opacity=".6" /></svg>;
}

export function ResourceDiscovery({ items, curriculum = null, loading, selectedItem, simulation, time, simulationLoading, simulationError, frame, playing, vectors, onOpen, onClose, onTogglePlaying, onReset, onFrameChange, onTimeChange, onPlaybackEnd, error = "", onRetry, allowSchoolScope = true }: Readonly<Props>) {
  const [selection, setSelection] = useState<CatalogSelection>({});
  const [query, setQuery] = useState("");
  const [scope, setScope] = useState<LibraryScope>("ALL");
  const [sort, setSort] = useState<"latest" | "featured" | "title">("latest");
  const [expanded, setExpanded] = useState<Map<string, boolean>>(new Map());
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
  }), [items, paths, selection, scope, query, sort]);
  const selectedSubject = catalog.find(subject => subject.id === selection.subjectId);
  const selectedGrade = selectedSubject?.grades.find(grade => grade.name === selection.grade);
  const selectedModule = selectedGrade?.modules.find(module => module.id === selection.moduleId);
  const selectedLesson = selectedModule?.lessons.find(lesson => lesson.id === selection.lessonId);
  const breadcrumb = [selectedSubject?.name, selectedGrade?.name, selectedModule?.name, selectedLesson?.name].filter(Boolean);
  const toggle = (key: string, currentlyOpen: boolean) => setExpanded(previous => {
    const next = new Map(previous);
    next.set(key, !currentlyOpen);
    return next;
  });
  const countLessons = (lessons: { id: string }[]) => lessons.reduce((total, lesson) => total + (counts.get(lesson.id) ?? 0), 0);
  const selectedPath = selectedItem ? paths.get(selectedItem.lessonId) : undefined;

  return <Theme asChild accentColor="indigo" grayColor="slate" radius="large" scaling="100%" hasBackground={false}><motion.section className={styles.discovery} aria-label="Kho cộng đồng" initial={reducedMotion ? false : { opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={transition}>
    <div className={`${styles.layout} ${curriculum ? "" : styles.withoutCatalog}`}>
      {curriculum && <aside className={styles.catalog} aria-label="Mục lục tài nguyên"><Card size="2" className={styles.catalogPanel}>
        <div className={styles.catalogHeading}><h2><ReaderIcon /> Mục lục</h2><Button size="1" variant="ghost" onClick={() => setSelection({})} disabled={!selection.subjectId}>Đặt lại</Button></div>
        <nav aria-label="Môn, lớp, module và lesson">
          <Button size="2" variant={!selection.subjectId ? "soft" : "ghost"} color="indigo" className={styles.allResources} onClick={() => setSelection({})} aria-pressed={!selection.subjectId}><span>Tất cả tài nguyên</span><Badge variant="soft">{scopedItems.length}</Badge></Button>
          <ul className={styles.tree}>{catalog.map(subject => {
            const subjectOpen = expanded.get(subject.id) ?? true;
            return <li key={subject.id}><TreeRow label={subject.name} count={countLessons(subject.grades.flatMap(grade => grade.modules.flatMap(module => module.lessons)))} selected={selection.subjectId === subject.id && !selection.grade} expanded={subjectOpen} onToggle={() => toggle(subject.id, subjectOpen)} onSelect={() => setSelection({ subjectId: subject.id })} />
              <TreeChildren open={subjectOpen}>{subject.grades.map(grade => {
                const gradeKey = `${subject.id}/${grade.name}`;
                const gradeOpen = expanded.get(gradeKey) ?? (selection.subjectId === subject.id && selection.grade === grade.name);
                return <li key={gradeKey}><TreeRow label={grade.name} count={countLessons(grade.modules.flatMap(module => module.lessons))} selected={selection.subjectId === subject.id && selection.grade === grade.name && !selection.moduleId} expanded={gradeOpen} onToggle={() => toggle(gradeKey, gradeOpen)} onSelect={() => setSelection({ subjectId: subject.id, grade: grade.name })} />
                  <TreeChildren open={gradeOpen}>{grade.modules.map(module => {
                    const moduleKey = `${gradeKey}/${module.id}`;
                    const moduleOpen = expanded.get(moduleKey) ?? (selection.subjectId === subject.id && selection.grade === grade.name && selection.moduleId === module.id);
                    return <li key={moduleKey}><TreeRow label={module.name} count={countLessons(module.lessons)} selected={selection.subjectId === subject.id && selection.grade === grade.name && selection.moduleId === module.id && !selection.lessonId} expanded={moduleOpen} onToggle={() => toggle(moduleKey, moduleOpen)} onSelect={() => setSelection({ subjectId: subject.id, grade: grade.name, moduleId: module.id })} />
                      <TreeChildren open={moduleOpen}>{module.lessons.map(lesson => <li key={lesson.id}><TreeRow label={lesson.name} count={counts.get(lesson.id) ?? 0} selected={selection.lessonId === lesson.id} onSelect={() => setSelection({ subjectId: subject.id, grade: grade.name, moduleId: module.id, lessonId: lesson.id })} /></li>)}</TreeChildren>
                    </li>;
                  })}</TreeChildren>
                </li>;
              })}</TreeChildren>
            </li>;
          })}</ul>
          {!catalog.length && <p className={styles.catalogEmpty}>Chưa có môn học.</p>}
        </nav>
      </Card></aside>}
      <div className={styles.results}>
        <Card size="2" className={styles.toolbarPanel}>
          <div className={styles.toolbar}>
            <TextField.Root className={styles.search} size="3" type="search" aria-label="Tìm tài nguyên" value={query} onChange={event => setQuery(event.target.value)} placeholder="Tìm tài nguyên, bài học, giáo viên…"><TextField.Slot><MagnifyingGlassIcon width="18" height="18" /></TextField.Slot></TextField.Root>
            <Select.Root size="3" value={sort} onValueChange={value => setSort(value as typeof sort)}><Select.Trigger aria-label="Sắp xếp tài nguyên" className={styles.sort} /><Select.Content><Select.Item value="latest">Mới nhất</Select.Item><Select.Item value="featured">Nổi bật</Select.Item><Select.Item value="title">Tên A–Z</Select.Item></Select.Content></Select.Root>
          </div>
          <div className={styles.filterRow}><SegmentedControl.Root size="2" value={scope} onValueChange={value => { if (value) setScope(value as LibraryScope); }} aria-label="Phạm vi chia sẻ"><SegmentedControl.Item value="ALL">Tất cả</SegmentedControl.Item><SegmentedControl.Item value="PUBLIC">Toàn hệ thống</SegmentedControl.Item><SegmentedControl.Item value="SHARED">Trong trường</SegmentedControl.Item></SegmentedControl.Root><Badge size="2" color="indigo" variant="soft" aria-live="polite">{loading ? "Đang tải…" : `${visible.length} tài nguyên`}</Badge></div>
        </Card>
        <AnimatePresence initial={false}>{breadcrumb.length > 0 && <motion.div className={styles.breadcrumb} aria-label="Danh mục đang chọn" initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: "auto" }} exit={{ opacity: 0, height: 0 }} transition={transition}><span>{breadcrumb.join(" / ")}</span><IconButton size="1" variant="ghost" aria-label="Bỏ lọc danh mục" onClick={() => setSelection({})}><Cross2Icon /></IconButton></motion.div>}</AnimatePresence>
        {loading ? <div className={styles.loading} aria-label="Đang tải thư viện"><span /><span /><span /><span /></div> : error ? <Card size="4" className={styles.empty}><h2>Không tải được tài nguyên</h2><p>{error}</p>{onRetry && <Button variant="soft" onClick={onRetry}>Thử lại</Button>}</Card> : visible.length === 0 ? <Card size="4" className={styles.empty}><span className={styles.emptyIcon}><ReaderIcon width="26" height="26" /></span><h2>Chưa có tài nguyên</h2><p>{query || selection.subjectId ? "Không có kết quả phù hợp với bộ lọc." : scope === "SHARED" ? "Chưa có tài nguyên được chia sẻ trong trường." : "Chưa có tài nguyên được chia sẻ."}</p>{(query || selection.subjectId) && <Button variant="soft" onClick={() => { setQuery(""); setSelection({}); }}>Xóa bộ lọc</Button>}</Card> : <motion.div layout className={styles.resourceGrid} transition={transition}><AnimatePresence initial={false} mode="popLayout">{visible.map(item => {
          const path = paths.get(item.lessonId);
          const accent = resourceAccent(path?.moduleId || item.topic || item.id);
          return <motion.article layout className={styles.resourceMotion} key={item.id} initial={reducedMotion ? false : { opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, scale: reducedMotion ? 1 : .97 }} whileHover={reducedMotion ? undefined : { y: -3 }} transition={transition}><Card size="3" className={styles.resource} data-accent={accent}>
            <Inset clip="padding-box" side="top" pb="0"><div className={styles.cover}><div className={styles.coverContent}><Badge size="1" color={accent} variant="solid">Vật lý{path?.grade ? ` · ${path.grade}` : ""}</Badge><strong>{path?.module || "Mô phỏng vật lý"}</strong></div><ResourceDiagram /></div></Inset>
            <div className={styles.resourceMeta}><Badge size="1" color={item.visibility === "PUBLIC" ? "cyan" : "indigo"} variant="soft">{item.visibility === "PUBLIC" ? <GlobeIcon /> : <BackpackIcon />}{item.visibility === "PUBLIC" ? "Toàn hệ thống" : "Trong trường"}</Badge>{item.moderationStatus === "FEATURED" && <Badge size="1" color="amber" variant="soft"><StarFilledIcon /> Nổi bật</Badge>}</div>
            <h2>{item.title}</h2>
            <p className={styles.lesson}>{path?.lesson || "Mô phỏng vật lý"}</p>
            <div className={styles.resourceFooter}><div className={styles.author}><Avatar size="2" color={accent} radius="full" fallback={(item.sharedByName || "GV").trim().charAt(0).toUpperCase()} /><div><strong>{item.sharedByName || "Giáo viên"}</strong><span>{item.schoolName || "PhysLive"}</span></div></div><Button size="2" onClick={event => { returnFocusRef.current = event.currentTarget; onOpen(item); }} aria-label={`Mở ${item.title}`}>Mở mô phỏng <ArrowTopRightIcon /></Button></div>
          </Card></motion.article>;
        })}</AnimatePresence></motion.div>}
      </div>
    </div>
    <Dialog.Root open={Boolean(selectedItem)} onOpenChange={open => { if (!open) onClose(); }}><Dialog.Content maxWidth="980px" size="3" onCloseAutoFocus={event => { event.preventDefault(); returnFocusRef.current?.focus(); }}>
      <div className={styles.dialogHeading}><div><Badge color="indigo" variant="soft">Vật lý{selectedPath?.grade ? ` · ${selectedPath.grade}` : ""}</Badge><Dialog.Title mt="3" mb="2">{selectedItem?.title || "Mô phỏng"}</Dialog.Title><Dialog.Description size="2" color="gray">{selectedItem?.sharedByName || "Giáo viên"}{selectedItem?.schoolName ? ` · ${selectedItem.schoolName}` : ""}</Dialog.Description></div><Dialog.Close><IconButton size="2" variant="soft" color="gray" aria-label="Đóng mô phỏng"><Cross2Icon /></IconButton></Dialog.Close></div>
      {simulationLoading && <div className={styles.playerState}><Spinner size="3" /> Đang mở mô phỏng…</div>}
      {!simulationLoading && simulationError && <p className={`${styles.playerState} ${styles.error}`}>{simulationError}</p>}
      {!simulationLoading && !simulationError && simulation && <motion.div initial={reducedMotion ? false : { opacity: 0 }} animate={{ opacity: 1 }} transition={transition}><div className={styles.scene}><PhysicsScene simulation={simulation} index={frame} overlays={vectors} time={time} playing={playing} onTimeChange={onTimeChange} onPlaybackEnd={onPlaybackEnd} /></div><div className={styles.playback}><Button onClick={onTogglePlaying}>{playing ? <PauseIcon /> : <PlayIcon />}{playing ? "Tạm dừng" : "Chạy mô phỏng"}</Button><Button color="gray" variant="soft" onClick={onReset}><ResetIcon /> Về đầu</Button><input aria-label="Thời gian mô phỏng" type="range" min={0} max={Math.max(0, simulation.time.length - 1)} value={frame} onChange={event => onFrameChange(Number(event.target.value))} /><span>{time.toFixed(2)} s</span></div></motion.div>}
    </Dialog.Content></Dialog.Root>
  </motion.section></Theme>;
}
