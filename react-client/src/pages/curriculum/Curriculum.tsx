import { useEffect, useState } from "react";
import { curriculum } from "../api/physliveApi";
import type { Curriculum as CurriculumTree } from "../types/physlive";

function Curriculum() {
  const [tree, setTree] = useState<CurriculumTree | null>(null);
  useEffect(() => { void curriculum().then(setTree).catch(() => setTree({ topics: [] })); }, []);
  return <main className="main"><div className="hero"><div><span className="eyebrow">F16 · taxonomy</span><h1>Chương trình học</h1><p className="muted">Một cây taxonomy dùng chung cho problem, library và assignment.</p></div></div><div className="grid">{tree?.topics.map((topic) => <section className="card span-4" key={topic.id}><h2>{topic.name}</h2>{topic.modules.map((module) => <div key={module.id}><strong>{module.name}</strong>{module.levels.map((level) => <div className="muted" style={{ margin: "8px 0 8px 12px" }} key={level.id}>{level.name}: {level.lessons.map((lesson) => lesson.name).join(", ") || "chưa có bài học"}</div>)}</div>)}</section>)}{!tree?.topics.length && <section className="card span-12"><p className="muted">Chưa có taxonomy. Admin có thể tạo bằng `/api/admin/curriculum`.</p></section>}</div></main>;
}
export default Curriculum;
