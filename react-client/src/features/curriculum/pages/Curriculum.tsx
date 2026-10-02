import { useEffect, useState } from "react";
import { curriculum } from "../api/curriculumApi";
import type { Curriculum as CurriculumTree } from "../../../shared/types/physlive";

type Topic = CurriculumTree["topics"][number];
type Module = Topic["modules"][number];

function Curriculum() {
  const [tree, setTree] = useState<CurriculumTree | null>(null);
  useEffect(() => { void curriculum().then(setTree).catch(() => setTree({ topics: [] })); }, []);
  const topics = tree?.topics ?? [];
  return <main className="main curriculum-page">
    <div className="hero"><div>
      <h1>Chương trình học</h1>
      <p className="muted">Cấu trúc chủ đề, chương và bài học dùng chung cho đề bài, thư viện mô phỏng và bài tập.</p>
    </div></div>
    {tree === null && <p className="muted" aria-busy="true">Đang tải chương trình…</p>}
    {tree !== null && topics.length === 0 && <section className="card"><p className="muted">Chưa có chương trình học. Quản trị viên có thể tạo trong mục Chương trình học của trang vận hành.</p></section>}
    {topics.map(topic => <CurriculumTopic key={topic.id} topic={topic} />)}
  </main>;
}

function CurriculumTopic({ topic }: Readonly<{ topic: Topic }>) {
  return <section className="curriculum-topic" aria-labelledby={`topic-${topic.id}`}>
    <h2 id={`topic-${topic.id}`}>{topic.name}</h2>
    <div className="curriculum-modules">
      {topic.modules.map(module => <CurriculumModule key={module.id} module={module} />)}
    </div>
  </section>;
}

function CurriculumModule({ module }: Readonly<{ module: Module }>) {
  return <article className="card curriculum-module">
    <h3>{module.name}</h3>
    {module.levels.map(level => <div className="curriculum-level" key={level.id}>
      <span>{level.name}</span>
      {level.lessons.length
        ? <ul>{level.lessons.map(lesson => <li key={lesson.id}>{lesson.name}</li>)}</ul>
        : <p className="muted">Chưa có bài học.</p>}
    </div>)}
  </article>;
}
export default Curriculum;
