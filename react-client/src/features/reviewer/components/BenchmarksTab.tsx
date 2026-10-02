import { useState, type FormEvent } from "react";
import { Badge, Button, Card, Heading, SegmentedControl, Table, Text, TextArea, TextField } from "@radix-ui/themes";
import api from "../../../shared/api/client";
import { LoadState } from "../../../shared/ui/OperationsKit";
import { downloadJson, useAction, useResource } from "../../../shared/hooks/operationsData";
import type { Benchmark, EvaluationPage } from "../model/reviewerTypes";
import { formatDate, matches } from "../model/reviewerUtils";
import { ReviewerDialog } from "./ReviewerDialog";
import { ReviewerFormSelect, ReviewerIcon, ReviewerRefresh, ReviewerSearch, ReviewStatus } from "./ReviewerKit";
import { asSpec, EMPTY_SPEC, finalizeSpec, SpecificationForm, SpecSummary, type Spec } from "./SpecificationForm";

type Filter = "MINE" | "ALL" | "DRAFT" | "IN_PROGRESS" | "GOLD_READY" | "ARCHIVED";
const IN_PROGRESS = ["ACTIVE", "ANNOTATING", "DISAGREEMENT"];
const STEPS = [
  { key: "DRAFT", label: "Soạn đề" },
  { key: "ANNOTATING", label: "2 người gán đáp án" },
  { key: "DISAGREEMENT", label: "Phân xử (nếu lệch)" },
  { key: "GOLD_READY", label: "Đáp án chuẩn" },
];
const stepIndex = (status: string) => status === "DRAFT" ? 0 : status === "ACTIVE" || status === "ANNOTATING" ? 1 : status === "DISAGREEMENT" ? 2 : status === "GOLD_READY" ? 3 : -1;

export function BenchmarksTab() {
  const resource = useResource<Benchmark[]>("/reviewer/benchmarks");
  const history = useResource<EvaluationPage>("/evaluations/history?page=0&size=10");
  const action = useAction();
  const [filter, setFilter] = useState<Filter>("MINE");
  const [query, setQuery] = useState("");
  const [creating, setCreating] = useState(false);
  const [reviewing, setReviewing] = useState<{ benchmark: Benchmark; mode: "annotations" | "adjudication" } | null>(null);
  const [archiving, setArchiving] = useState<Benchmark | null>(null);
  const all = resource.data ?? [];
  const needsMe = (item: Benchmark) => item.canAnnotate || item.canAdjudicate;
  const rows = all.filter(item => {
    if (filter === "MINE" && !needsMe(item)) return false;
    if (filter === "DRAFT" && item.status !== "DRAFT") return false;
    if (filter === "IN_PROGRESS" && !IN_PROGRESS.includes(item.status)) return false;
    if (filter === "GOLD_READY" && item.status !== "GOLD_READY") return false;
    if (filter === "ARCHIVED" && item.status !== "ARCHIVED") return false;
    if (filter !== "ARCHIVED" && filter !== "ALL" && item.status === "ARCHIVED") return false;
    return matches(query, item.topic, item.problemText);
  });
  const goldCount = all.filter(item => item.goldSpecification).length;
  const refresh = () => { resource.refresh(); history.refresh(); };

  const create = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const body = Object.fromEntries(new FormData(event.currentTarget));
    const activateNow = body.activateNow === "on";
    delete body.activateNow;
    const ok = await action.run(async () => {
      const response = await api.post<Benchmark>("/reviewer/benchmarks", body);
      if (activateNow && response.data?.id) await api.post(`/reviewer/benchmarks/${response.data.id}/activate`);
    }, activateNow ? "Đã tạo đề và mở cho chuyên gia gán đáp án." : "Đã lưu đề ở dạng bản nháp.");
    if (ok) { setCreating(false); refresh(); }
  };
  const activate = async (id: string) => {
    const ok = await action.run(() => api.post(`/reviewer/benchmarks/${id}/activate`), "Đã mở đề cho chuyên gia gán đáp án.");
    if (ok) refresh();
  };

  return <div className="reviewer-stack">
    <header className="reviewer-page-head">
      <div><Heading as="h1" size="6">Đề kiểm thử AI</Heading><Text as="p" size="2" color="gray" mt="1">Bộ đề có đáp án chuẩn do chuyên gia xác nhận, dùng để đo AI đọc đề chính xác đến đâu. Mỗi đề cần 2 chuyên gia gán đáp án độc lập; nếu lệch nhau, chuyên gia thứ ba phân xử.</Text></div>
      <div className="reviewer-actions">
        <Button type="button" variant="soft" color="gray" size="2" disabled={!goldCount} onClick={() => downloadJson(all.filter(item => item.goldSpecification), "physlive-dap-an-chuan.json")}>Tải đáp án chuẩn ({goldCount})</Button>
        <Button type="button" size="2" onClick={() => setCreating(true)}><ReviewerIcon name="add" size={16} />Thêm đề</Button>
      </div>
    </header>
    <ol className="reviewer-steps">{STEPS.map((step, index) => <li key={step.key}><span>{index + 1}</span>{step.label}</li>)}</ol>
    <div className="reviewer-toolbar">
      <SegmentedControl.Root size="2" value={filter} onValueChange={value => setFilter(value as Filter)} aria-label="Lọc đề">
        <SegmentedControl.Item value="MINE">Cần tôi ({all.filter(needsMe).length})</SegmentedControl.Item>
        <SegmentedControl.Item value="IN_PROGRESS">Đang xử lý</SegmentedControl.Item>
        <SegmentedControl.Item value="DRAFT">Bản nháp</SegmentedControl.Item>
        <SegmentedControl.Item value="GOLD_READY">Đã xong</SegmentedControl.Item>
        <SegmentedControl.Item value="ARCHIVED">Lưu trữ</SegmentedControl.Item>
        <SegmentedControl.Item value="ALL">Tất cả</SegmentedControl.Item>
      </SegmentedControl.Root>
      <ReviewerSearch aria-label="Tìm đề" placeholder="Tìm theo nội dung hoặc chủ đề" value={query} onChange={event => setQuery(event.target.value)} />
      <ReviewerRefresh refresh={refresh} loading={resource.loading} />
    </div>
    <LoadState {...resource} />{action.feedback}

    <div className="reviewer-card-list">
      {rows.map(benchmark => {
        const step = stepIndex(benchmark.status);
        return <Card key={benchmark.id} size="2" className={`reviewer-bench${needsMe(benchmark) ? " has-work" : ""}`}>
          <div className="reviewer-bench-head"><div className="reviewer-detail-meta"><ReviewStatus status={benchmark.status} /><Badge color="gray" variant="soft">{benchmark.topic}</Badge><Badge color="gray" variant="soft">Lớp {benchmark.gradeScope}</Badge></div>{benchmark.createdAt && <Text size="1" color="gray">{formatDate(benchmark.createdAt)}</Text>}</div>
          <Text as="p" size="3" className="reviewer-bench-text">{benchmark.problemText}</Text>
          {step >= 0 && <div className="reviewer-progress" aria-label={`Bước ${step + 1}/4`}>{STEPS.map((item, index) => <span key={item.key} className={index < step ? "done" : index === step ? "current" : ""} title={item.label} />)}<Text size="1" color="gray">{benchmark.status === "ANNOTATING" || benchmark.status === "ACTIVE" ? `${benchmark.annotationCount}/2 người đã gán đáp án` : STEPS[step].label}</Text></div>}
          <div className="reviewer-actions">
            {benchmark.canAnnotate && <Button type="button" size="2" onClick={() => setReviewing({ benchmark, mode: "annotations" })}>Gán đáp án</Button>}
            {benchmark.canAdjudicate && <Button type="button" size="2" onClick={() => setReviewing({ benchmark, mode: "adjudication" })}>Phân xử</Button>}
            {benchmark.status === "DRAFT" && <Button type="button" variant="soft" size="2" disabled={action.busy} onClick={() => void activate(benchmark.id)}>Mở cho gán đáp án</Button>}
            {!benchmark.canAnnotate && benchmark.annotationCount > 0 && IN_PROGRESS.includes(benchmark.status) && !benchmark.canAdjudicate && <Text size="2" color="gray">Đang chờ chuyên gia khác.</Text>}
            <span className="reviewer-spacer" />
            {benchmark.status !== "ARCHIVED" && <Button type="button" variant="ghost" color="gray" size="2" onClick={() => setArchiving(benchmark)}>Lưu trữ</Button>}
          </div>
        </Card>;
      })}
      {!resource.loading && !resource.error && rows.length === 0 && <Card size="3"><div className="reviewer-empty"><span className="reviewer-empty-icon"><ReviewerIcon name="check" size={30} /></span><Heading as="h2" size="4">{filter === "MINE" ? "Không có đề nào cần bạn" : "Không có đề phù hợp"}</Heading><Text as="p" color="gray" size="2">{filter === "MINE" ? "Đề mới được mở cho gán đáp án sẽ xuất hiện tại đây." : "Thử bộ lọc khác hoặc thêm đề mới."}</Text></div></Card>}
    </div>

    <EvaluationHistory history={history} />

    {creating && <ReviewerDialog title="Thêm đề kiểm thử" onClose={() => setCreating(false)}><form onSubmit={create}>
      <label className="reviewer-field"><span>Nội dung đề bài *</span><TextArea size="3" name="problemText" rows={5} required placeholder="Một quả bóng được ném thẳng đứng lên với vận tốc 20 m/s…" /></label>
      <div className="reviewer-form-row"><label className="reviewer-field"><span>Chủ đề *</span><TextField.Root size="3" name="topic" required placeholder="Động học" /></label><label className="reviewer-field"><span>Khối lớp *</span><ReviewerFormSelect name="gradeScope" label="Khối lớp" defaultValue="10" options={[{ value: "10", label: "Lớp 10" }, { value: "11", label: "Lớp 11" }, { value: "12", label: "Lớp 12" }]} /></label></div>
      <label className="reviewer-field"><span>Nguồn đề *</span><TextField.Root size="3" name="sourceCategory" required placeholder="SGK Vật lí 10, tự biên soạn…" /><Text size="1" color="gray">Ghi rõ nguồn để đảm bảo quyền sử dụng.</Text></label>
      <label className="reviewer-check"><input type="checkbox" name="activateNow" defaultChecked />Mở ngay cho chuyên gia gán đáp án</label>
      {action.feedback}
      <div className="reviewer-form-footer"><Button type="button" variant="soft" color="gray" size="2" onClick={() => setCreating(false)}>Hủy</Button><Button type="submit" size="3" disabled={action.busy}>{action.busy ? "Đang lưu…" : "Lưu đề"}</Button></div>
    </form></ReviewerDialog>}

    {reviewing && <AnnotationDialog benchmark={reviewing.benchmark} mode={reviewing.mode} onClose={() => setReviewing(null)} onSaved={() => { setReviewing(null); refresh(); }} />}
    {archiving && <ArchiveDialog benchmark={archiving} onClose={() => setArchiving(null)} onSaved={() => { setArchiving(null); refresh(); }} />}
  </div>;
}

function AnnotationDialog({ benchmark, mode, onClose, onSaved }: Readonly<{ benchmark: Benchmark; mode: "annotations" | "adjudication"; onClose: () => void; onSaved: () => void }>) {
  const action = useAction();
  const [spec, setSpec] = useState<Spec>(() => asSpec(mode === "adjudication" ? benchmark.annotations[0]?.specification ?? EMPTY_SPEC : EMPTY_SPEC));
  const [rationale, setRationale] = useState("");
  const [categories, setCategories] = useState<string[]>([]);
  const adjudicating = mode === "adjudication";
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const specification = finalizeSpec(spec);
    const body = adjudicating ? { specification, rationale: rationale.trim(), disagreementCategories: categories.join(", ") || undefined } : { specification };
    const ok = await action.run(() => api.post(`/reviewer/benchmarks/${benchmark.id}/${mode}`, body), adjudicating ? "Đã lưu kết luận phân xử. Đề đã có đáp án chuẩn." : "Đã lưu đáp án của bạn.");
    if (ok) onSaved();
  };
  return <ReviewerDialog title={adjudicating ? "Phân xử đáp án" : "Gán đáp án"} wide onClose={onClose}><form onSubmit={submit}>
    <blockquote className="reviewer-problem">{benchmark.problemText}</blockquote>
    {!adjudicating && <div className="reviewer-note"><ReviewerIcon name="info" size={16} /> Hãy tự đọc đề và điền đáp án. Bạn sẽ không thấy đáp án của chuyên gia khác để đảm bảo tính độc lập.</div>}
    {adjudicating && <>
      <Text as="p" size="2" color="gray">Hai chuyên gia đưa ra đáp án khác nhau. So sánh, chọn bản đúng làm gốc rồi chỉnh nếu cần.</Text>
      <div className="reviewer-compare">{benchmark.annotations.slice(0, 2).map((annotation, index) => <Card key={index} size="1" variant="surface">
        <div className="reviewer-compare-head"><Text size="2" weight="bold">Chuyên gia {index + 1}</Text><Button type="button" size="1" variant="soft" onClick={() => setSpec(asSpec(annotation.specification))}>Dùng làm gốc</Button></div>
        <SpecSummary value={annotation.specification} />
      </Card>)}</div>
    </>}
    <Heading as="h3" size="3" mt="4" mb="2">{adjudicating ? "Đáp án chuẩn cuối cùng" : "Đáp án của bạn"}</Heading>
    <SpecificationForm value={spec} onChange={setSpec} />
    {adjudicating && <>
      <fieldset className="reviewer-checklist"><legend>Hai đáp án lệch nhau ở đâu?</legend>
        {["Đối tượng", "Đại lượng", "Đơn vị", "Quan hệ"].map(label => <label key={label}><input type="checkbox" checked={categories.includes(label)} onChange={event => setCategories(values => event.target.checked ? [...values, label] : values.filter(value => value !== label))} />{label}</label>)}
      </fieldset>
      <label className="reviewer-field"><span>Căn cứ phân xử *</span><TextArea size="3" rows={3} required placeholder="Vì sao chọn đáp án này?" value={rationale} onChange={event => setRationale(event.target.value)} /></label>
    </>}
    {action.feedback}
    <div className="reviewer-form-footer"><Button type="button" variant="soft" color="gray" size="2" onClick={onClose}>Hủy</Button><Button type="submit" size="3" disabled={action.busy || (adjudicating && !rationale.trim())}>{action.busy ? "Đang lưu…" : adjudicating ? "Chốt đáp án chuẩn" : "Lưu đáp án"}</Button></div>
  </form></ReviewerDialog>;
}

function ArchiveDialog({ benchmark, onClose, onSaved }: Readonly<{ benchmark: Benchmark; onClose: () => void; onSaved: () => void }>) {
  const action = useAction();
  const [reason, setReason] = useState("");
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!reason.trim()) return;
    const ok = await action.run(() => api.post(`/reviewer/benchmarks/${benchmark.id}/archive`, { reason: reason.trim() }), "Đã lưu trữ đề.");
    if (ok) onSaved();
  };
  return <ReviewerDialog title="Lưu trữ đề kiểm thử?" onClose={onClose}><form onSubmit={submit}>
    <p className="reviewer-context-text">{benchmark.problemText}</p>
    <Text as="p" size="2" color="gray" mb="3">Đề lưu trữ không được dùng cho các lần đánh giá sau. Lịch sử gán đáp án vẫn được giữ lại.</Text>
    <label className="reviewer-field"><span>Lý do *</span><TextArea size="3" rows={3} required placeholder="Đề trùng, sai dữ kiện…" value={reason} onChange={event => setReason(event.target.value)} /></label>
    {action.feedback}
    <div className="reviewer-form-footer"><Button type="button" variant="soft" color="gray" size="2" onClick={onClose}>Hủy</Button><Button type="submit" size="3" color="red" disabled={action.busy || !reason.trim()}>{action.busy ? "Đang lưu…" : "Lưu trữ"}</Button></div>
  </form></ReviewerDialog>;
}

function EvaluationHistory({ history }: Readonly<{ history: ReturnType<typeof useResource<EvaluationPage>> }>) {
  const runs = history.data?.items ?? [];
  const f1 = (metrics: Record<string, unknown> | undefined) => {
    const value = (metrics?.confirmFlow as { f1?: number } | undefined)?.f1;
    return typeof value === "number" ? `${(value * 100).toFixed(1)}%` : "—";
  };
  return <details className="reviewer-disclosure reviewer-history">
    <summary>Kết quả các lần đánh giá AI {runs.length ? `(${runs.length} lần gần nhất)` : ""}</summary>
    <LoadState {...history} />
    <div className="reviewer-table-scroll"><Table.Root variant="surface" size="1"><Table.Header><Table.Row><Table.ColumnHeaderCell>Thời gian</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Trạng thái</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Số đề</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Độ chính xác</Table.ColumnHeaderCell></Table.Row></Table.Header><Table.Body>
      {runs.map(run => <Table.Row key={run.id}><Table.Cell>{run.createdAt ? new Date(run.createdAt).toLocaleString("vi-VN") : "—"}</Table.Cell><Table.Cell><ReviewStatus status={run.status} /></Table.Cell><Table.Cell>{run.benchmarkCount}</Table.Cell><Table.Cell title="Điểm F1: cân bằng giữa đọc đúng và đọc đủ dữ kiện">{f1(run.metrics)}</Table.Cell></Table.Row>)}
      {!history.loading && !history.error && !runs.length && <Table.Row><Table.Cell colSpan={4} className="reviewer-table-empty">Chưa có lần đánh giá nào.</Table.Cell></Table.Row>}
    </Table.Body></Table.Root></div>
  </details>;
}
