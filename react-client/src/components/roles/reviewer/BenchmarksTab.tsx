import { useState, type FormEvent } from "react";
import { Button, Card, Heading, Table, TextArea, TextField } from "@radix-ui/themes";
import api from "../../../api/axios";
import { LoadState } from "../../operations/OperationsKit";
import { downloadJson, parseObject, useAction, useResource } from "../../operations/operationsData";
import type { Benchmark, Evaluation, EvaluationPage } from "./reviewerTypes";
import { ReviewerDialog } from "./ReviewerDialog";
import { ReviewerFilter, ReviewerFormSelect, ReviewerHeader, ReviewerIcon, ReviewerMetric, ReviewerRefresh, ReviewerSearch, ReviewStatus } from "./ReviewerKit";

const EMPTY_SPEC = { objects: [], quantities: [], relations: [] };

export function BenchmarksTab() {
  const resource = useResource<Benchmark[]>("/reviewer/benchmarks");
  const history = useResource<EvaluationPage>("/evaluations/history?page=0&size=10");
  const action = useAction();
  const [creating, setCreating] = useState(false);
  const [evaluation, setEvaluation] = useState<Evaluation | null>(null);
  const [reviewing, setReviewing] = useState<{ id: string; mode: "annotations" | "adjudication" } | null>(null);
  const [reviewJson, setReviewJson] = useState(JSON.stringify(EMPTY_SPEC, null, 2));
  const [rationale, setRationale] = useState("");
  const [categories, setCategories] = useState("");
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState("");
  const [archiving, setArchiving] = useState<Benchmark | null>(null);
  const [archiveReason, setArchiveReason] = useState("");
  const rows = (resource.data ?? []).filter((item) => (!filter || item.status === filter) && `${item.topic} ${item.problemText}`.toLowerCase().includes(query.toLowerCase()));

  const refresh = () => {
    resource.refresh();
    history.refresh();
  };

  const create = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const body = Object.fromEntries(new FormData(event.currentTarget));
    const ok = await action.run(() => api.post("/reviewer/benchmarks", body), "Đã tạo đề đánh giá ở trạng thái bản nháp.");
    if (ok) {
      setCreating(false);
      refresh();
    }
  };

  const activate = async (id: string) => {
    const ok = await action.run(() => api.post(`/reviewer/benchmarks/${id}/activate`), "Đã kích hoạt đề để gán nhãn.");
    if (ok) refresh();
  };

  const archive = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!archiving || !archiveReason.trim()) return;
    const ok = await action.run(
      () => api.post(`/reviewer/benchmarks/${archiving.id}/archive`, { reason: archiveReason.trim() }),
      "Đã lưu trữ đề đánh giá.",
    );
    if (ok) { setArchiving(null); refresh(); }
  };

  const runEvaluationPipeline = async () => {
    const ok = await action.run(async () => {
      const response = await api.post<Evaluation>("/evaluations/run");
      setEvaluation(response.data);
    }, "Đã đánh giá bộ đề có đáp án chuẩn.");
    if (ok) history.refresh();
  };

  const submitReview = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!reviewing) return;
    let specification: unknown;
    try {
      specification = parseObject(reviewJson);
    } catch (error) {
      await action.run(async () => { throw error; });
      return;
    }
    const body = reviewing.mode === "adjudication"
      ? { specification, rationale: rationale.trim(), disagreementCategories: categories.trim() || undefined }
      : { specification };
    const ok = await action.run(
      () => api.post(`/reviewer/benchmarks/${reviewing.id}/${reviewing.mode}`, body),
      reviewing.mode === "adjudication" ? "Đã lưu kết luận phân xử." : "Đã lưu nhãn độc lập.",
    );
    if (ok) {
      setReviewing(null);
      refresh();
    }
  };

  const openReview = (benchmark: Benchmark, mode: "annotations" | "adjudication") => {
    setReviewing({ id: benchmark.id, mode });
    setRationale("");
    setCategories("");
    setReviewJson(JSON.stringify(mode === "adjudication" ? benchmark.annotations[0]?.specification ?? EMPTY_SPEC : EMPTY_SPEC, null, 2));
  };

  return (
    <div className="reviewer-stack">
      {evaluation && (
        <div className="reviewer-metrics reviewer-metrics-four">
          <ReviewerMetric label="Độ chính xác (Precision)" value={`${(evaluation.precision * 100).toFixed(1)}%`} icon="check" tone="indigo" />
          <ReviewerMetric label="Độ bao phủ (Recall)" value={`${(evaluation.recall * 100).toFixed(1)}%`} icon="schema" tone="cyan" />
          <ReviewerMetric label="Điểm F1" value={`${(evaluation.f1 * 100).toFixed(1)}%`} icon="benchmark" tone="indigo" />
          <ReviewerMetric label="Độ đồng thuận (Kappa)" value={evaluation.kappa.toFixed(3)} icon="module" tone="amber" />
        </div>
      )}

      <Card size="3" className="reviewer-panel">
        <ReviewerHeader title="Bộ đề đánh giá" icon="benchmark" count={resource.data?.length} actions={<>
            <Button type="button" variant="soft" color="gray" size="2" disabled={!resource.data?.some((item) => item.goldSpecification)} onClick={() => downloadJson(resource.data?.filter((item) => item.goldSpecification), "physlive-gold-corpus.json")}>Xuất đáp án JSON</Button>
            <Button type="button" variant="soft" color="cyan" size="2" disabled={action.busy || !resource.data?.some((item) => item.goldSpecification)} onClick={() => void runEvaluationPipeline()}><ReviewerIcon name="benchmark" size={16} />{action.busy ? "Đang chạy…" : "Chạy đánh giá"}</Button>
            <Button type="button" size="3" onClick={() => setCreating(true)}><ReviewerIcon name="add" size={18} />Tạo đề mới</Button>
          </>} />
        <LoadState {...resource} />
        {action.feedback}

        {creating && (
          <ReviewerDialog title="Tạo đề đánh giá" onClose={() => setCreating(false)}>
              <form onSubmit={create}>
                <label className="reviewer-field"><span>Nội dung đề bài *</span><TextArea size="3" name="problemText" rows={4} required /></label>
                <div className="reviewer-form-row">
                  <label className="reviewer-field"><span>Chủ đề *</span><TextField.Root size="3" name="topic" required /></label>
                  <label className="reviewer-field"><span>Khối lớp *</span><ReviewerFormSelect name="gradeScope" label="Khối lớp" defaultValue="10" options={[{ value: "10", label: "Lớp 10" }, { value: "11", label: "Lớp 11" }, { value: "12", label: "Lớp 12" }]} /></label>
                </div>
                <label className="reviewer-field"><span>Nguồn đề / quyền sử dụng *</span><TextField.Root size="3" name="sourceCategory" required /></label>
                {action.feedback}
                <div className="reviewer-form-footer"><Button type="button" variant="soft" color="gray" size="2" onClick={() => setCreating(false)}>Hủy</Button><Button type="submit" size="3" disabled={action.busy}>{action.busy ? "Đang lưu…" : "Lưu bản nháp"}</Button></div>
              </form>
          </ReviewerDialog>
        )}

        {reviewing && (
          <ReviewerDialog title={reviewing.mode === "adjudication" ? "Phân xử bất đồng" : "Gán nhãn độc lập"} onClose={() => setReviewing(null)} wide>
              <form onSubmit={submitReview}>
                <label className="reviewer-field"><span>Đặc tả bài toán (JSON)</span><TextArea size="3" className="reviewer-json" rows={16} spellCheck={false} value={reviewJson} onChange={(event) => setReviewJson(event.target.value)} required /></label>
                {reviewing.mode === "adjudication" && <>
                  <label className="reviewer-field"><span>Căn cứ phân xử *</span><TextArea size="3" rows={3} value={rationale} onChange={(event) => setRationale(event.target.value)} required /></label>
                  <label className="reviewer-field"><span>Nhóm bất đồng</span><TextField.Root size="3" value={categories} onChange={(event) => setCategories(event.target.value)} placeholder="Cấu trúc, đại lượng, đơn vị" /></label>
                </>}
                {action.feedback}
                <div className="reviewer-form-footer"><Button type="button" variant="soft" color="gray" size="2" onClick={() => setReviewing(null)}>Hủy</Button><Button type="submit" size="3" disabled={action.busy}>{action.busy ? "Đang lưu…" : "Lưu kết quả"}</Button></div>
              </form>
          </ReviewerDialog>
        )}

        {archiving && <ReviewerDialog title="Lưu trữ đề đánh giá" onClose={() => setArchiving(null)}><form onSubmit={archive}><p className="reviewer-context-text">{archiving.problemText}</p><label className="reviewer-field"><span>Lý do lưu trữ *</span><TextArea size="3" rows={3} required value={archiveReason} onChange={(event) => setArchiveReason(event.target.value)} /></label>{action.feedback}<div className="reviewer-form-footer"><Button type="button" variant="soft" color="gray" size="2" onClick={() => setArchiving(null)}>Hủy</Button><Button type="submit" size="3" disabled={action.busy || !archiveReason.trim()}>Lưu trữ</Button></div></form></ReviewerDialog>}
        <div className="reviewer-toolbar"><ReviewerSearch aria-label="Tìm đề đánh giá" placeholder="Tìm đề bài hoặc chủ đề" value={query} onChange={(event) => setQuery(event.target.value)} /><ReviewerFilter label="Lọc trạng thái đề đánh giá" value={filter} onChange={setFilter} statuses={["DRAFT", "ACTIVE", "ANNOTATING", "DISAGREEMENT", "GOLD_READY", "ARCHIVED"]} /></div>

        <div className="reviewer-table-scroll">
          <Table.Root variant="surface" size="2"><Table.Header><Table.Row><Table.ColumnHeaderCell>Đề bài</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Chủ đề / lớp</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Trạng thái</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Gán nhãn</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Thao tác</Table.ColumnHeaderCell></Table.Row></Table.Header>
            <Table.Body>{rows.map((benchmark) => <Table.Row key={benchmark.id}>
              <Table.Cell style={{ maxWidth: "340px" }}>{benchmark.problemText}</Table.Cell>
              <Table.Cell><strong>{benchmark.topic}</strong><small>Lớp {benchmark.gradeScope}</small></Table.Cell>
              <Table.Cell><ReviewStatus status={benchmark.status} /></Table.Cell>
              <Table.Cell>{benchmark.annotationCount}/2</Table.Cell>
              <Table.Cell><div className="reviewer-actions">
                {benchmark.status === "DRAFT" && <Button type="button" variant="soft" color="gray" size="2" disabled={action.busy} onClick={() => void activate(benchmark.id)}>Kích hoạt</Button>}
                {benchmark.canAnnotate && <Button type="button" variant="soft" color="gray" size="2" onClick={() => openReview(benchmark, "annotations")}>Gán nhãn</Button>}
                {benchmark.canAdjudicate && <Button type="button" size="2" onClick={() => openReview(benchmark, "adjudication")}>Phân xử</Button>}
                {benchmark.status !== "ARCHIVED" && <Button type="button" variant="soft" color="gray" size="2" onClick={() => { setArchiving(benchmark); setArchiveReason(""); }}>Lưu trữ</Button>}
              </div>
              </Table.Cell>
            </Table.Row>)}{!resource.loading && !resource.error && rows.length === 0 && <Table.Row><Table.Cell colSpan={5} className="reviewer-table-empty">{query || filter ? "Không có đề phù hợp." : "Chưa có đề đánh giá. Tạo đề mới để bắt đầu."}</Table.Cell></Table.Row>}</Table.Body>
          </Table.Root>
        </div>
      </Card>

      <Card size="3" className="reviewer-panel">
        <div className="reviewer-panel-heading"><div className="reviewer-title-group"><span className="reviewer-icon-tile reviewer-tone-cyan"><ReviewerIcon name="clock" /></span><Heading as="h2" size="5">Lịch sử đánh giá</Heading></div><ReviewerRefresh refresh={history.refresh} loading={history.loading} /></div>
        <LoadState {...history} />
        <div className="reviewer-table-scroll"><Table.Root variant="surface" size="2"><Table.Header><Table.Row><Table.ColumnHeaderCell>Thời gian</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Trạng thái</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Số đề</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Mã bộ dữ liệu</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Điểm F1</Table.ColumnHeaderCell></Table.Row></Table.Header><Table.Body>
          {history.data?.items.map((run) => <Table.Row key={run.id}><Table.Cell>{run.createdAt ? new Date(run.createdAt).toLocaleString("vi-VN") : "—"}</Table.Cell><Table.Cell><ReviewStatus status={run.status} /></Table.Cell><Table.Cell>{run.benchmarkCount}</Table.Cell><Table.Cell><code>{run.benchmarkSnapshotHash?.slice(0, 12) ?? "—"}</code></Table.Cell><Table.Cell>{Number((run.metrics?.confirmFlow as { f1?: number } | undefined)?.f1 ?? 0).toFixed(3)}</Table.Cell></Table.Row>)}
          {!history.loading && !history.error && !history.data?.items.length && <Table.Row><Table.Cell colSpan={5} className="reviewer-table-empty">Chưa có lần đánh giá nào.</Table.Cell></Table.Row>}
        </Table.Body></Table.Root></div>
      </Card>
    </div>
  );
}
