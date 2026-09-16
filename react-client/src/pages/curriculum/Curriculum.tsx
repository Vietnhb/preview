import { useEffect, useState } from "react";
import { curriculum } from "../../api/curriculumApi";
import type { Curriculum as CurriculumTree } from "../../types/physlive";

function Curriculum() {
  const [tree, setTree] = useState<CurriculumTree | null>(null);
  useEffect(() => { void curriculum().then(setTree).catch(() => setTree({ topics: [] })); }, []);
  return <main className="main"><div className="hero"><div><span className="eyebrow">F16 · taxonomy</span><h1>Chương trình học</h1><p className="muted">Một cây taxonomy dùng chung cho problem, library và assignment.</p></div></div><div className="grid">{tree?.topics.map(topic => <CurriculumTopicCard key={topic.id} topic={topic} />)}{!tree?.topics.length && <section className="card span-12"><p className="muted">Chưa có taxonomy. Admin có thể tạo bằng `/api/admin/curriculum`.</p></section>}</div></main>;
}

function CurriculumTopicCard({ topic }: Readonly<{ topic: CurriculumTree["topics"][number] }>) {
  return <section className="card span-4"><h2>{topic.name}</h2>{topic.modules.map(module => <CurriculumModuleSummary key={module.id} module={module} />)}</section>;
}

function CurriculumModuleSummary({ module }: Readonly<{ module: CurriculumTree["topics"][number]["modules"][number] }>) {
  return <div><strong>{module.name}</strong>{module.levels.map(level => <div className="muted" style={{ margin: "8px 0 8px 12px" }} key={level.id}>{level.name}: {level.lessons.map(lesson => lesson.name).join(", ") || "chưa có bài học"}</div>)}</div>;
}
export default Curriculum;
