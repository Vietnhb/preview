import { useEffect, useState, type FormEvent } from "react";
import { Button as ThemeButton, Select, Text, TextField } from "@radix-ui/themes";
import { adminCurriculum, createCurriculumNode, toggleCurriculum, type CurriculumTree } from "../api/curriculumApi";
import { apiMessage } from "../../../shared/lib/apiError";
import { curriculumParentOptions, curriculumPath } from "../model/curriculumUtils";
import type { CurriculumKind } from "../model/curriculumUtils";
import { PageHeader, Panel, Loading, ErrorNotice, Button, FormField } from "../../../shared/ui/ManagementUI";

export function CurriculumView() {
  const [tree, setTree] = useState<CurriculumTree | null>(null); const [loading, setLoading] = useState(true); const [busy, setBusy] = useState(false); const [error, setError] = useState(""); const [name, setName] = useState(""); const [kind, setKind] = useState<"topic" | "module" | "level" | "lesson">("topic"); const [parentId, setParentId] = useState("");
  const load = async () => { setLoading(true); try { setTree(await adminCurriculum()); setError(""); } catch (e) { setError(apiMessage(e, "Không thể tải chương trình học.")); } finally { setLoading(false); } }; useEffect(() => { void load(); }, []);
  const create = async (event: FormEvent) => { event.preventDefault(); if (!name.trim() || (kind !== "topic" && !parentId)) { return; } setBusy(true); try { setTree(await createCurriculumNode(curriculumPath(kind, parentId), { name: name.trim() })); setName(""); setParentId(""); } catch (e) { setError(apiMessage(e, "Không thể tạo mục chương trình.")); } finally { setBusy(false); } };
  const toggle = async (type: "topic" | "module" | "level" | "lesson", id: string) => { setBusy(true); try { setTree(await toggleCurriculum(type, id)); } catch (e) { setError(apiMessage(e, "Không thể cập nhật chương trình.")); } finally { setBusy(false); } };
  const parentOptions = curriculumParentOptions(kind, tree);
  return <div className="admin-content"><PageHeader title="Chương trình học" description="" />{error && <ErrorNotice error={error} onRetry={() => void load()} />}{loading ? <Loading /> : <>
    <Panel title="Thêm nội dung"><form className="admin-form-grid admin-form-inline" onSubmit={create}>
      <FormField label="Loại" htmlFor="managed-curriculum-kind"><Select.Root size="3" value={kind} onValueChange={value => { setKind(value as typeof kind); setParentId(""); }}><Select.Trigger id="managed-curriculum-kind" /><Select.Content><Select.Item value="topic">Chủ đề</Select.Item><Select.Item value="module">Chương</Select.Item><Select.Item value="level">Khối lớp</Select.Item><Select.Item value="lesson">Bài học</Select.Item></Select.Content></Select.Root></FormField>
      {kind !== "topic" && <FormField label="Nằm trong" htmlFor="managed-curriculum-parent"><Select.Root size="3" name="parentId" required value={parentId} onValueChange={setParentId}><Select.Trigger id="managed-curriculum-parent" placeholder="Chọn mục cha" /><Select.Content>{parentOptions.map(item => <Select.Item key={item.id} value={item.id}>{item.label}</Select.Item>)}</Select.Content></Select.Root></FormField>}
      <FormField label="Tên" htmlFor="managed-curriculum-name"><TextField.Root id="managed-curriculum-name" size="3" required value={name} onChange={event => setName(event.target.value)} placeholder="Tên mục mới" /></FormField>
      <div className="admin-form-actions"><Button type="submit" primary disabled={busy}>Thêm</Button></div>
    </form></Panel><Panel title="Cấu trúc hiện tại"><CurriculumTreeRows tree={tree} onToggle={toggle} /></Panel>
  </>}</div>;
}

function CurriculumTreeRows({ tree, onToggle }: Readonly<{ tree: CurriculumTree | null; onToggle: (type: CurriculumKind, id: string) => void }>) {
  return <div className="admin-tree">{tree?.topics.map(topic => <CurriculumTopicRow key={topic.id} topic={topic} onToggle={onToggle} />)}</div>;
}

function CurriculumTopicRow({ topic, onToggle }: Readonly<{ topic: CurriculumTree["topics"][number]; onToggle: (type: CurriculumKind, id: string) => void }>) {
  return <div className="admin-tree-topic"><TreeRow label={topic.name} active={topic.enabled} onToggle={() => onToggle("topic", topic.id)} />{topic.modules.map(module => <CurriculumModuleRow key={module.id} module={module} onToggle={onToggle} />)}</div>;
}

function CurriculumModuleRow({ module, onToggle }: Readonly<{ module: CurriculumTree["topics"][number]["modules"][number]; onToggle: (type: CurriculumKind, id: string) => void }>) {
  return <div className="admin-tree-child"><TreeRow label={module.name} active={module.active} onToggle={() => onToggle("module", module.id)} />{module.levels.map(level => <CurriculumLevelRow key={level.id} level={level} onToggle={onToggle} />)}</div>;
}

function CurriculumLevelRow({ level, onToggle }: Readonly<{ level: CurriculumTree["topics"][number]["modules"][number]["levels"][number]; onToggle: (type: CurriculumKind, id: string) => void }>) {
  return <div className="admin-tree-grandchild"><TreeRow label={level.name} active={level.active} onToggle={() => onToggle("level", level.id)} />{level.lessons.map(lesson => <div className="admin-tree-lesson" key={lesson.id}><TreeRow label={lesson.name} active={lesson.active} onToggle={() => onToggle("lesson", lesson.id)} /></div>)}</div>;
}

function TreeRow({ label, active, onToggle }: Readonly<{ label: string; active: boolean; onToggle: () => void }>) { return <div className="admin-tree-row"><Text>{label}</Text><ThemeButton type="button" variant="soft" color={active ? "indigo" : "gray"} size="1" onClick={onToggle}>{active ? "Đang bật" : "Đã tắt"}</ThemeButton></div>; }