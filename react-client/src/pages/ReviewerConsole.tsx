import { useState, type FormEvent } from "react";
import api from "../api/axios";
import { Access, Badge, JsonEditor, LoadState, Panel, Shell } from "../components/operations/OperationsKit";
import { downloadJson, parseObject, useAction, useResource } from "../components/operations/operationsData";
import type { Specification } from "../types/physlive";

type ReviewItem = { id: string; specificationId: string; question: string; fieldPath: string; code: string; options: string[]; problemText: string; topic: string; quantities: unknown; relations: unknown };
type Version = { id: string; schemaId: string; version: string; lifecycleStatus: string; name?: string; topic?: string; definition?: unknown; solverId?: string; outputDefinition?: { referenceSolverId?: string }; createdAt: string };
type Benchmark = { id: string; problemText: string; topic: string; gradeScope: string; sourceCategory: string; status: string; annotationCount: number; canAnnotate: boolean; canAdjudicate: boolean; annotations: { actor: string; specification: unknown }[]; goldSpecification: unknown };
type Evaluation = { benchmarkCount: number; precision: number; recall: number; f1: number; kappa: number; incorrectRate: number };
const tabs = [
  { id: "queue", label: "Hàng đợi", detail: "Phân xử extraction" },
  { id: "schemas", label: "Topic schema", detail: "Định nghĩa & phê duyệt" },
  { id: "solvers", label: "Reference solver", detail: "Module & phiên bản" },
  { id: "benchmarks", label: "Benchmark", detail: "Annotation & gold corpus" },
];
export default function Reviewer() { return <Access reviewer><ReviewerPage /></Access>; }
function ReviewerPage() {
  const [tab, setTab] = useState("queue");
  return <Shell reviewer tab={tab} setTab={setTab} tabs={tabs}>{tab === "queue" ? <Queue /> : tab === "benchmarks" ? <Benchmarks /> : <Versions key={tab} solver={tab === "solvers"} />}</Shell>;
}
function Queue() {
  const resource = useResource<ReviewItem[]>("/reviewer/ambiguities");
  const [selectedId, setSelectedId] = useState(""); const [query, setQuery] = useState("");
  const items = (resource.data ?? []).filter(i => `${i.topic} ${i.question} ${i.problemText}`.toLowerCase().includes(query.toLowerCase()));
  const selected = items.find(i => i.id === selectedId) ?? items[0];
  return <><Panel title="Extraction cần làm rõ" caption="Xem đề gốc, dữ kiện đã trích xuất và gửi câu trả lời cụ thể để cập nhật specification." action={<button className="secondary" onClick={resource.refresh} disabled={resource.loading}>Làm mới</button>}>
    <LoadState {...resource} /><div className="ops-toolbar"><input aria-label="Tìm extraction" placeholder="Tìm theo đề bài, câu hỏi, chủ đề…" value={query} onChange={e => setQuery(e.target.value)} /></div>
    {!resource.loading && !resource.error && !items.length && <p className="ops-empty">Không có extraction đang chờ trong bộ lọc này.</p>}
    <div className="ops-split"><div className="ops-list">{items.map(i => <button key={i.id} className={selected?.id === i.id ? "selected" : ""} onClick={() => setSelectedId(i.id)}><small>{i.topic} · {i.fieldPath}</small><strong>{i.question}</strong><p>{i.problemText?.slice(0, 140)}</p></button>)}</div>
    {selected && <Resolution key={selected.id} item={selected} refresh={resource.refresh} />}</div>
  </Panel></>;
}
function Resolution({ item, refresh }: { item: ReviewItem; refresh: () => void }) {
  const [answer, setAnswer] = useState(""); const [comment, setComment] = useState(""); const action = useAction();
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    await action.run(async () => {
      const response = await api.post<Specification>(`/reviewer/ambiguities/${item.id}/resolve`, { answer: answer.trim(), comment });
      const open = response.data.ambiguityCases ?? response.data.ambiguities ?? [];
      if (open.some(a => a.code === item.code && a.status === "OPEN")) throw new Error("Câu trả lời đã được xử lý nhưng dữ kiện vẫn chưa rõ. Hãy bổ sung giá trị, đơn vị hoặc giả thiết.");
      refresh();
    }, "Đã cập nhật specification. Những ambiguity còn lại vẫn cần được giải quyết.");
  };
  return <form onSubmit={submit}><h3>Đề bài gốc</h3><div className="ops-context">{item.problemText || "Chưa có nội dung văn bản."}</div>
    <details><summary>Dữ kiện và quan hệ đã trích xuất</summary><pre>{JSON.stringify({ quantities: item.quantities, relations: item.relations }, null, 2)}</pre></details>
    <label className="ops-field"><span>{item.question}</span><textarea rows={4} required value={answer} onChange={e => setAnswer(e.target.value)} placeholder="Nhập câu trả lời có giá trị, đơn vị, hướng hoặc giả thiết cụ thể…" /></label>
    <div className="ops-actions">{Array.isArray(item.options) && item.options.map(o => <button type="button" className="secondary" key={o} onClick={() => setAnswer(o)}>{o}</button>)}</div>
    <label className="ops-field">Lý do / ghi chú chuyên môn<textarea rows={2} value={comment} onChange={e => setComment(e.target.value)} /></label>
    {action.feedback}<button disabled={action.busy || !answer.trim()}>{action.busy ? "Đang cập nhật đặc tả…" : "Gửi kết luận phân xử"}</button><p className="muted">Specification vẫn phải vượt dual validation trước khi mô phỏng được phát hành.</p>
  </form>;
}
function Versions({ solver }: { solver: boolean }) {
  const resource = useResource<Version[]>(solver ? "/reviewer/solvers" : "/reviewer/schemas");
  const [filter, setFilter] = useState(""); const [query, setQuery] = useState("");
  const [editor, setEditor] = useState<{ version?: Version; clone: boolean } | null>(null);
  const [decision, setDecision] = useState<{ version: Version; status: string } | null>(null); const action = useAction();
  const rows = (resource.data ?? []).filter(v => (!filter || v.lifecycleStatus === filter) && `${v.schemaId} ${v.name ?? ""} ${v.version}`.toLowerCase().includes(query.toLowerCase()));
  return <Panel title={solver ? "Reference solver & module bindings" : "Topic schema & phát hành module"} caption={solver ? "Liên kết module số với module tham chiếu độc lập đã được cài trên server; tạo phiên bản mới để cập nhật." : "Bản nháp → phê duyệt → ngừng dùng. Schema chỉ được duyệt khi solver cùng phiên bản đã sẵn sàng."} action={<button onClick={() => setEditor({ clone: false })}>+ Tạo bản nháp</button>}>
    <LoadState {...resource} />{action.feedback}
    {editor && <VersionEditor key={`${editor.version?.id ?? "new"}:${editor.clone}`} solver={solver} initial={editor.version} clone={editor.clone} onClose={() => setEditor(null)} onSaved={() => { setEditor(null); resource.refresh(); }} />}
    {decision && <div className="ops-form" role="alert"><h3>{decision.status === "APPROVED" ? "Phê duyệt" : "Ngừng sử dụng"} {decision.version.schemaId} @{decision.version.version}?</h3><p>{decision.status === "APPROVED" ? "Nội dung này sẽ được sử dụng cho các mô phỏng đủ điều kiện. Kiểm tra kỹ định nghĩa và module tham chiếu trước khi xác nhận." : "Không dùng phiên bản này cho lượt chạy mới. Định nghĩa và kết quả lịch sử được giữ lại để truy vết."}</p><div className="ops-actions"><button disabled={action.busy} onClick={() => void action.run(() => api.put(`/reviewer/${solver ? "solvers" : "schema-versions"}/${decision.version.id}/lifecycle`, null, { params: { status: decision.status } })).then(ok => { if (ok) { setDecision(null); resource.refresh(); } })}>Xác nhận</button><button disabled={action.busy} className="secondary" onClick={() => setDecision(null)}>Hủy</button></div></div>}
    <div className="ops-toolbar"><input aria-label="Tìm phiên bản" placeholder="Tìm schema, tên hoặc phiên bản…" value={query} onChange={e => setQuery(e.target.value)} /><select aria-label="Lọc vòng đời" value={filter} onChange={e => setFilter(e.target.value)}><option value="">Tất cả trạng thái</option>{["DRAFT", "APPROVED", "RETIRED"].map(s => <option key={s}>{s}</option>)}</select><button className="secondary" disabled={resource.loading} onClick={resource.refresh}>Làm mới</button></div>
    <div className="table-wrap"><table><thead><tr><th>{solver ? "Solver / schema" : "Schema / chủ đề"}</th><th>Phiên bản</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>{rows.map(v => <tr key={v.id}><td><strong>{solver ? v.solverId : v.name}</strong><small>{v.schemaId}{v.topic ? ` · ${v.topic}` : ""}</small>{solver && <small>Reference: {v.outputDefinition?.referenceSolverId}</small>}<details><summary>Xem định nghĩa</summary><pre>{JSON.stringify(solver ? v.outputDefinition : v.definition, null, 2)}</pre></details></td><td>{v.version}<small>{new Date(v.createdAt).toLocaleDateString("vi-VN")}</small></td><td><Badge value={v.lifecycleStatus} /></td><td><div className="ops-actions"><button className="secondary" onClick={() => setEditor({ version: v, clone: v.lifecycleStatus !== "DRAFT" })}>{v.lifecycleStatus === "DRAFT" ? "Sửa nháp" : "Tạo phiên bản mới"}</button>{v.lifecycleStatus === "DRAFT" && <button disabled={action.busy} onClick={() => setDecision({ version: v, status: "APPROVED" })}>Phê duyệt</button>}{v.lifecycleStatus === "APPROVED" && <button className="danger" disabled={action.busy} onClick={() => setDecision({ version: v, status: "RETIRED" })}>Ngừng dùng</button>}</div></td></tr>)}</tbody></table></div>
    {!resource.loading && !resource.error && !rows.length && <p className="ops-empty">Chưa có phiên bản phù hợp.</p>}
  </Panel>;
}
function VersionEditor({ solver, initial, clone, onClose, onSaved }: { solver: boolean; initial?: Version; clone: boolean; onClose: () => void; onSaved: () => void }) {
  const [definition, setDefinition] = useState(JSON.stringify((solver ? initial?.outputDefinition : initial?.definition) ?? {}, null, 2));
  const implementations = useResource<{ numerical: string[]; reference: string[] }>("/reviewer/solver-implementations"); const action = useAction();
  const edit = Boolean(initial && !clone);
  const submit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault(); const fields = Object.fromEntries(new FormData(e.currentTarget));
    if (await action.run(() => {
      const json = parseObject(definition);
      const body = solver ? { schemaId: fields.schemaId, version: fields.version, solverId: fields.solverId, outputDefinition: { ...json, referenceSolverId: fields.referenceSolverId } } : { ...fields, definition: json };
      return edit ? api.put(`/reviewer/${solver ? "solvers" : "schema-versions"}/${initial?.id}`, body) : api.post(`/reviewer/${solver ? "solvers" : "schemas"}`, body);
    })) onSaved();
  };
  return <form className="ops-form" onSubmit={submit}><h3>{edit ? "Chỉnh sửa bản nháp" : "Định nghĩa phiên bản mới"}</h3><div className="ops-form-grid">
    <label className="ops-field">Schema ID<input name="schemaId" required maxLength={80} readOnly={edit} defaultValue={initial?.schemaId ?? ""} placeholder="ID archetype" /></label>
    <label className="ops-field">Version<input name="version" required maxLength={16} readOnly={edit} defaultValue={clone ? "" : initial?.version ?? ""} placeholder="Ví dụ: 2.0.0" /></label>
    {!solver && <><label className="ops-field">Tên schema<input name="name" required defaultValue={initial?.name ?? ""} /></label><label className="ops-field">Chủ đề<input name="topic" required maxLength={32} defaultValue={initial?.topic ?? ""} placeholder="Kinematics / Dynamics / Circuits" /></label></>}
    {solver && <><label className="ops-field">Numerical module<select name="solverId" required defaultValue={initial?.solverId ?? ""}><option value="">Chọn module đã cài</option>{implementations.data?.numerical.map(id => <option key={id}>{id}</option>)}</select></label><label className="ops-field">Independent reference module<select name="referenceSolverId" required defaultValue={initial?.outputDefinition?.referenceSolverId ?? ""}><option value="">Chọn reference solver</option>{implementations.data?.reference.map(id => <option key={id}>{id}</option>)}</select></label></>}
    </div>{solver && <LoadState {...implementations} />}<JsonEditor value={definition} onChange={setDefinition} label={solver ? "Output definition / thông tin module (JSON)" : "Schema definition (JSON)"} />
    {action.feedback}<div className="ops-actions"><button disabled={action.busy || (solver && implementations.loading)}>Lưu bản nháp</button><button type="button" className="secondary" disabled={action.busy} onClick={onClose}>Hủy</button></div>
  </form>;
}
function Benchmarks() {
  const resource = useResource<Benchmark[]>("/reviewer/benchmarks"); const action = useAction();
  const [creating, setCreating] = useState(false); const [selectedId, setSelectedId] = useState(""); const [evaluation, setEvaluation] = useState<Evaluation | null>(null);
  const selected = resource.data?.find(b => b.id === selectedId) ?? resource.data?.[0];
  const create = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault(); const body = Object.fromEntries(new FormData(e.currentTarget));
    if (await action.run(() => api.post("/reviewer/benchmarks", body))) { setCreating(false); resource.refresh(); }
  };
  return <Panel title="Benchmark corpus" caption="Hai chuyên gia annotate độc lập; bất đồng được phân xử bởi reviewer thứ ba." action={<button onClick={() => setCreating(true)}>+ Thêm bài benchmark</button>}>
    <LoadState {...resource} />{action.feedback}
    {creating && <form className="ops-form" onSubmit={create}><div className="ops-form-grid"><label className="ops-field wide">Đề bài<textarea name="problemText" rows={4} required /></label><label className="ops-field">Chủ đề<input name="topic" required maxLength={32} /></label><label className="ops-field">Khối lớp<select name="gradeScope">{["10", "11", "12"].map(g => <option key={g}>{g}</option>)}</select></label><label className="ops-field wide">Nguồn / quyền sử dụng<input name="sourceCategory" required maxLength={80} placeholder="Nguồn bài và ghi chú quyền sử dụng" /></label></div><div className="ops-actions"><button disabled={action.busy}>Thêm bài</button><button type="button" className="secondary" onClick={() => setCreating(false)}>Hủy</button></div></form>}
    <div className="ops-toolbar"><button className="secondary" disabled={resource.loading} onClick={resource.refresh}>Làm mới</button><button className="secondary" disabled={!resource.data?.some(b => b.goldSpecification)} onClick={() => downloadJson(resource.data?.filter(b => b.goldSpecification), "physlive-gold-corpus.json")}>Xuất gold corpus</button><button disabled={action.busy || !resource.data?.some(b => b.goldSpecification)} onClick={() => void action.run(async () => { const r = await api.post<Evaluation>("/evaluations/run"); setEvaluation(r.data); }, "Đã đánh giá extraction trên corpus có gold.")}>{action.busy ? "Đang xử lý…" : "Đánh giá extraction"}</button></div>
    {evaluation && <div className="ops-form"><h3>Kết quả extraction · {evaluation.benchmarkCount} bài có gold</h3><div className="ops-metrics">{["precision", "recall", "f1", "kappa"].map(k => <div className="ops-metric" key={k}><span>{k.toUpperCase()}</span><strong>{Number(evaluation[k as keyof Evaluation]).toFixed(3)}</strong></div>)}</div><p>Tỷ lệ extraction lệch gold: {(evaluation.incorrectRate * 100).toFixed(1)}%. Chỉ số này chưa phải tỷ lệ mô phỏng sai hoặc kết quả so sánh confirm-flow với baseline.</p><button className="secondary" onClick={() => downloadJson(evaluation, "physlive-extraction-evaluation.json")}>Xuất kết quả</button></div>}
    <div className="ops-split"><div className="ops-list">{resource.data?.map(b => <button key={b.id} className={selected?.id === b.id ? "selected" : ""} onClick={() => setSelectedId(b.id)}><Badge value={b.status} /><p>{b.problemText.slice(0, 160)}</p><small>{b.topic} · Lớp {b.gradeScope} · {b.annotationCount}/2 annotations</small></button>)}</div>{selected && <BenchmarkEditor key={`${selected.id}:${selected.annotationCount}:${selected.status}`} item={selected} refresh={resource.refresh} />}</div>
    {!resource.loading && !resource.error && !resource.data?.length && <p className="ops-empty">Chưa có bài benchmark. Thêm bài để bắt đầu annotation độc lập.</p>}
  </Panel>;
}
function BenchmarkEditor({ item, refresh }: { item: Benchmark; refresh: () => void }) {
  const [definition, setDefinition] = useState(JSON.stringify(item.goldSpecification ?? { objects: [], quantities: [], relations: [] }, null, 2)); const action = useAction();
  return <div><div className="ops-context">{item.problemText}</div><small>Nguồn: {item.sourceCategory}</small>
    {item.annotations.map(a => <details key={a.actor}><summary>Annotation · {a.actor}</summary><pre>{JSON.stringify(a.specification, null, 2)}</pre></details>)}
    {item.goldSpecification ? <><h3>Gold specification</h3><pre>{JSON.stringify(item.goldSpecification, null, 2)}</pre></> : item.canAnnotate || item.canAdjudicate ? <form onSubmit={e => { e.preventDefault(); void action.run(() => api.post(`/reviewer/benchmarks/${item.id}/${item.canAdjudicate ? "adjudication" : "annotations"}`, { specification: parseObject(definition) }), "Đã ghi nhận kết quả chuyên gia.").then(ok => { if (ok) refresh(); }); }}>
      <JsonEditor value={definition} onChange={setDefinition} label={item.canAdjudicate ? "Gold specification sau phân xử" : "Annotation độc lập của bạn"} />{action.feedback}<button disabled={action.busy}>{item.canAdjudicate ? "Chốt gold specification" : "Gửi annotation"}</button><p className="muted">Annotation đã gửi không được ghi đè. Dữ liệu của chuyên gia khác chỉ hiện sau khi có đủ hai annotations.</p>
    </form> : <p className="ops-context">Đang chờ chuyên gia độc lập tiếp theo. Người đã annotate không được phân xử bài này.</p>}
  </div>;
}
