import { useState, type FormEvent } from "react";
import api from "../../../api/axios";
import { LoadState } from "../../operations/OperationsKit";
import { downloadJson, parseObject, useAction, useResource } from "../../operations/operationsData";
import type { Benchmark, Evaluation, EvaluationPage } from "./reviewerTypes";

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

  const refresh = () => {
    resource.refresh();
    history.refresh();
  };

  const create = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const body = Object.fromEntries(new FormData(event.currentTarget));
    const ok = await action.run(() => api.post("/reviewer/benchmarks", body), "Đã tạo benchmark ở trạng thái bản nháp.");
    if (ok) {
      setCreating(false);
      refresh();
    }
  };

  const activate = async (id: string) => {
    const ok = await action.run(() => api.post(`/reviewer/benchmarks/${id}/activate`), "Đã kích hoạt benchmark để annotate.");
    if (ok) refresh();
  };

  const archive = async (id: string) => {
    const reason = window.prompt("Lý do archive benchmark:", "Không còn phù hợp với bộ đánh giá hiện hành");
    if (!reason?.trim()) return;
    const ok = await action.run(
      () => api.post(`/reviewer/benchmarks/${id}/archive`, { reason: reason.trim() }),
      "Đã archive benchmark và giữ nguyên lịch sử.",
    );
    if (ok) refresh();
  };

  const runEvaluationPipeline = async () => {
    const ok = await action.run(async () => {
      const response = await api.post<Evaluation>("/evaluations/run");
      setEvaluation(response.data);
    }, "Đã chạy evaluation trên gold corpus đã finalized.");
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
      reviewing.mode === "adjudication" ? "Đã lưu adjudication có lý do." : "Đã lưu annotation độc lập.",
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
    <div>
      {evaluation && (
        <div className="modern-kpis">
          <Kpi title="Precision" value={`${(evaluation.precision * 100).toFixed(1)}%`} />
          <Kpi title="Recall" value={`${(evaluation.recall * 100).toFixed(1)}%`} />
          <Kpi title="F1 Score" value={`${(evaluation.f1 * 100).toFixed(1)}%`} />
          <Kpi title="Cohen's Kappa" value={evaluation.kappa.toFixed(3)} />
        </div>
      )}

      <section className="modern-card">
        <div className="modern-card-header">
          <div>
            <h2>Benchmark corpus và evaluation</h2>
            <p>Hai reviewer annotate độc lập; reviewer thứ ba adjudicate khi có bất đồng.</p>
          </div>
          <div style={{ display: "flex", gap: "8px", flexWrap: "wrap" }}>
            <button type="button" className="role-switch-pill" disabled={!resource.data?.some((item) => item.goldSpecification)} onClick={() => downloadJson(resource.data?.filter((item) => item.goldSpecification), "physlive-gold-corpus.json")}>Xuất Gold JSON</button>
            <button type="button" className="prediction-submit-btn" disabled={action.busy} onClick={() => void runEvaluationPipeline()}>{action.busy ? "Đang chạy…" : "Chạy evaluation"}</button>
            <button type="button" className="role-switch-pill" onClick={() => setCreating(true)}>+ Benchmark mới</button>
          </div>
        </div>
        <LoadState {...resource} />
        {action.feedback}

        {creating && (
          <dialog open className="modern-modal-overlay">
            <div className="modern-modal-content">
              <div className="modern-modal-header"><h3>Tạo benchmark draft</h3><button type="button" className="modern-modal-close" onClick={() => setCreating(false)}>×</button></div>
              <form onSubmit={create}>
                <label className="ops-field"><span>Nội dung đề bài *</span><textarea name="problemText" rows={4} required /></label>
                <div className="form-row">
                  <label className="ops-field"><span>Chủ đề *</span><input name="topic" required /></label>
                  <label className="ops-field"><span>Khối lớp *</span><select name="gradeScope" defaultValue="10"><option value="10">Lớp 10</option><option value="11">Lớp 11</option><option value="12">Lớp 12</option></select></label>
                </div>
                <label className="ops-field"><span>Nguồn đề / quyền sử dụng *</span><input name="sourceCategory" required /></label>
                <div style={{ display: "flex", justifyContent: "flex-end", gap: "10px" }}><button type="button" className="role-switch-pill" onClick={() => setCreating(false)}>Hủy</button><button type="submit" className="prediction-submit-btn" disabled={action.busy}>Lưu draft</button></div>
              </form>
            </div>
          </dialog>
        )}

        {reviewing && (
          <dialog open className="modern-modal-overlay">
            <div className="modern-modal-content" style={{ maxWidth: "760px" }}>
              <div className="modern-modal-header"><h3>{reviewing.mode === "adjudication" ? "Adjudicate disagreement" : "Independent annotation"}</h3><button type="button" className="modern-modal-close" onClick={() => setReviewing(null)}>×</button></div>
              <form onSubmit={submitReview}>
                <label className="ops-field"><span>Specification JSON</span><textarea className="ops-code" rows={16} spellCheck={false} value={reviewJson} onChange={(event) => setReviewJson(event.target.value)} required /></label>
                {reviewing.mode === "adjudication" && <>
                  <label className="ops-field"><span>Lý do adjudication *</span><textarea rows={3} value={rationale} onChange={(event) => setRationale(event.target.value)} required /></label>
                  <label className="ops-field"><span>Nhóm bất đồng</span><input value={categories} onChange={(event) => setCategories(event.target.value)} placeholder="schema, quantity, unit…" /></label>
                </>}
                <div style={{ display: "flex", justifyContent: "flex-end", gap: "10px" }}><button type="button" className="role-switch-pill" onClick={() => setReviewing(null)}>Hủy</button><button type="submit" className="prediction-submit-btn" disabled={action.busy}>{action.busy ? "Đang lưu…" : "Gửi quyết định"}</button></div>
              </form>
            </div>
          </dialog>
        )}

        <div className="modern-table-wrapper">
          <table className="modern-table"><thead><tr><th>Đề bài</th><th>Phạm vi</th><th>Trạng thái</th><th>Annotation</th><th>Thao tác</th></tr></thead>
            <tbody>{resource.data?.map((benchmark) => <tr key={benchmark.id}>
              <td style={{ maxWidth: "340px" }}>{benchmark.problemText}</td>
              <td><strong>{benchmark.topic}</strong><small style={{ display: "block" }}>Lớp {benchmark.gradeScope}</small></td>
              <td><span className={`status-pill ${benchmark.status === "GOLD_READY" ? "pass" : "draft"}`}>{benchmark.status}</span></td>
              <td>{benchmark.annotationCount}/2</td>
              <td style={{ display: "flex", gap: "6px", flexWrap: "wrap" }}>
                {benchmark.status === "DRAFT" && <button type="button" className="role-switch-pill" onClick={() => void activate(benchmark.id)}>Activate</button>}
                {benchmark.canAnnotate && <button type="button" className="role-switch-pill" onClick={() => openReview(benchmark, "annotations")}>Annotate</button>}
                {benchmark.canAdjudicate && <button type="button" className="prediction-submit-btn" onClick={() => openReview(benchmark, "adjudication")}>Adjudicate</button>}
                {benchmark.status !== "ARCHIVED" && <button type="button" className="role-switch-pill" onClick={() => void archive(benchmark.id)}>Archive</button>}
              </td>
            </tr>)}</tbody>
          </table>
        </div>
      </section>

      <section className="modern-card" style={{ marginTop: "16px" }}>
        <div className="modern-card-header"><div><h2>Lịch sử evaluation</h2><p>Run được lưu cùng snapshot hash, actor, thời gian và metrics để tái lập.</p></div><button type="button" className="role-switch-pill" onClick={history.refresh}>Làm mới</button></div>
        <LoadState {...history} />
        <div className="modern-table-wrapper"><table className="modern-table"><thead><tr><th>Thời gian</th><th>Status</th><th>Benchmark</th><th>Snapshot</th><th>F1</th></tr></thead><tbody>
          {history.data?.items.map((run) => <tr key={run.id}><td>{run.createdAt ? new Date(run.createdAt).toLocaleString("vi-VN") : "—"}</td><td>{run.status}</td><td>{run.benchmarkCount}</td><td><code>{run.benchmarkSnapshotHash?.slice(0, 12) ?? "—"}</code></td><td>{Number((run.metrics?.confirmFlow as { f1?: number } | undefined)?.f1 ?? 0).toFixed(3)}</td></tr>)}
        </tbody></table></div>
      </section>
    </div>
  );
}

function Kpi({ title, value }: Readonly<{ title: string; value: string }>) {
  return <div className="modern-kpi-card"><div className="modern-kpi-title">{title}</div><div className="modern-kpi-value">{value}</div><div className="modern-kpi-sub">Reviewer quality metric</div></div>;
}
