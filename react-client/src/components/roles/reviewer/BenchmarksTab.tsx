import { useState, type FormEvent } from "react";
import api from "../../../api/axios";
import { LoadState } from "../../operations/OperationsKit";
import { downloadJson, parseObject, useAction, useResource } from "../../operations/operationsData";
import type { Benchmark, Evaluation } from "./reviewerTypes";

export function BenchmarksTab() {
  const resource = useResource<Benchmark[]>("/reviewer/benchmarks");
  const action = useAction();
  const [creating, setCreating] = useState(false);
  const [evaluation, setEvaluation] = useState<Evaluation | null>(null);
  const [reviewing, setReviewing] = useState<{
    id: string;
    mode: "annotations" | "adjudication";
  } | null>(null);
  const [reviewJson, setReviewJson] = useState(
    '{\n  "objects": [],\n  "quantities": [],\n  "relations": []\n}',
  );

  const create = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const body = Object.fromEntries(new FormData(e.currentTarget));
    const ok = await action.run(() => api.post("/reviewer/benchmarks", body));
    if (ok) {
      setCreating(false);
      resource.refresh();
    }
  };

  const runEvaluationPipeline = async () => {
    await action.run(async () => {
      const res = await api.post<Evaluation>("/evaluations/run");
      setEvaluation(res.data);
    }, "Đã chạy pipeline đánh giá comparative evaluation trên benchmark corpus.");
  };

  const submitReview = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    let specification: unknown;
    try {
      specification = parseObject(reviewJson);
    } catch (error) {
      await action.run(async () => {
        throw error;
      });
      return;
    }
    const ok = await action.run(
      () =>
        api.post(`/reviewer/benchmarks/${reviewing?.id}/${reviewing?.mode}`, {
          specification,
        }),
      "Đã lưu thẩm định benchmark.",
    );
    if (ok) {
      setReviewing(null);
      resource.refresh();
    }
  };

  return (
    <div>
      {/* KPI Cards when evaluation is run */}
      {evaluation && (
        <div className="modern-kpis">
          <div className="modern-kpi-card">
            <div className="modern-kpi-title">Precision (Độ chính xác)</div>
            <div
              className="modern-kpi-value"
              style={{ color: "var(--role-student)" }}
            >
              {(evaluation.precision * 100).toFixed(1)}%
            </div>
            <div className="modern-kpi-sub">Field-level precision</div>
          </div>
          <div className="modern-kpi-card">
            <div className="modern-kpi-title">Recall (Độ bao phủ)</div>
            <div
              className="modern-kpi-value"
              style={{ color: "var(--role-teacher)" }}
            >
              {(evaluation.recall * 100).toFixed(1)}%
            </div>
            <div className="modern-kpi-sub">Field-level recall</div>
          </div>
          <div className="modern-kpi-card">
            <div className="modern-kpi-title">F1 Score</div>
            <div
              className="modern-kpi-value"
              style={{ color: "var(--role-reviewer)" }}
            >
              {(evaluation.f1 * 100).toFixed(1)}%
            </div>
            <div className="modern-kpi-sub">F1 harmonic mean</div>
          </div>
          <div className="modern-kpi-card">
            <div className="modern-kpi-title">
              Cohen's Kappa (Độ đồng thuận)
            </div>
            <div className="modern-kpi-value">
              {evaluation.kappa.toFixed(3)}
            </div>
            <div className="modern-kpi-sub">Inter-annotator agreement</div>
          </div>
        </div>
      )}

      <div className="modern-card">
        <div className="modern-card-header">
          <div>
            <h2>Tập đề Benchmark & Đánh giá Nghiên cứu (FR-REV-06)</h2>
            <p>
              Quy trình hai chuyên gia độc lập annotate và chuyên gia thứ ba
              phân xử tạo Gold Specification.
            </p>
          </div>
          <div style={{ display: "flex", gap: "8px" }}>
            <button
              type="button"
              className="role-switch-pill"
              style={{
                border: "1px solid var(--border-subtle)",
                background: "#ffffff",
                padding: "8px 14px",
              }}
              disabled={!resource.data?.some((b) => b.goldSpecification)}
              onClick={() =>
                downloadJson(
                  resource.data?.filter((b) => b.goldSpecification),
                  "physlive-gold-corpus.json",
                )
              }
            >
              Xuất Gold Corpus JSON
            </button>
            <button
              type="button"
              className="prediction-submit-btn"
              style={{
                background: "var(--role-reviewer)",
                padding: "8px 16px",
                fontSize: "13px",
              }}
              disabled={action.busy}
              onClick={() => void runEvaluationPipeline()}
            >
              {action.busy ? "Đang tính toán…" : "Chạy Đánh giá Nghiên cứu ⚡"}
            </button>
            <button
              type="button"
              className="role-switch-pill"
              style={{
                border: "1px solid var(--border-subtle)",
                background: "#ffffff",
                padding: "8px 14px",
              }}
              onClick={() => setCreating(true)}
            >
              + Thêm bài Benchmark
            </button>
          </div>
        </div>

        <LoadState {...resource} />
        {action.feedback}

        {creating && (
          <dialog
            open
            className="modern-modal-overlay"
            onPointerDown={(event) => {
              if (event.target === event.currentTarget) setCreating(false);
            }}
          >
            <div className="modern-modal-content">
              <div className="modern-modal-header">
                <h3>Thêm đề bài vào Benchmark Corpus</h3>
                <button
                  type="button"
                  className="modern-modal-close"
                  onClick={() => setCreating(false)}
                >
                  ✕
                </button>
              </div>
              <form onSubmit={create}>
                <div className="form-group" style={{ marginBottom: "14px" }}>
                  <label htmlFor="benchmark-problem-text">
                    Nội dung đề bài *
                  </label>
                  <textarea
                    id="benchmark-problem-text"
                    name="problemText"
                    rows={4}
                    required
                    placeholder="Ví dụ: Một ô tô bắt đầu chuyển động thẳng nhanh dần đều..."
                  />
                </div>
                <div className="form-row">
                  <div className="form-group">
                    <label htmlFor="benchmark-topic">Chủ đề *</label>
                    <select id="benchmark-topic" name="topic">
                      <option value="Kinematics">Kinematics (Động học)</option>
                      <option value="Dynamics">Dynamics (Động lực học)</option>
                      <option value="Circuits">Circuits (Mạch điện)</option>
                    </select>
                  </div>
                  <div className="form-group">
                    <label htmlFor="benchmark-grade">Khối lớp THPT *</label>
                    <select id="benchmark-grade" name="gradeScope">
                      <option value="10">Lớp 10</option>
                      <option value="11">Lớp 11</option>
                      <option value="12">Lớp 12</option>
                    </select>
                  </div>
                </div>
                <div className="form-group" style={{ marginBottom: "18px" }}>
                  <label htmlFor="benchmark-source">
                    Nguồn đề / Quyền sử dụng *
                  </label>
                  <input
                    id="benchmark-source"
                    name="sourceCategory"
                    required
                    placeholder="SGK Vật lý 10 Kết nối tri thức, Tr. 32"
                  />
                </div>
                <div
                  style={{
                    display: "flex",
                    justifyContent: "flex-end",
                    gap: "10px",
                  }}
                >
                  <button
                    type="button"
                    className="role-switch-pill"
                    style={{
                      border: "1px solid var(--border-subtle)",
                      padding: "8px 16px",
                    }}
                    onClick={() => setCreating(false)}
                  >
                    Hủy
                  </button>
                  <button
                    type="submit"
                    className="prediction-submit-btn"
                    style={{ background: "var(--role-reviewer)" }}
                    disabled={action.busy}
                  >
                    Thêm vào Corpus
                  </button>
                </div>
              </form>
            </div>
          </dialog>
        )}

        {reviewing && (
          <dialog
            open
            className="modern-modal-overlay"
            onPointerDown={(event) => {
              if (event.target === event.currentTarget) setReviewing(null);
            }}
          >
            <div className="modern-modal-content">
              <div className="modern-modal-header">
                <h3>
                  {reviewing.mode === "annotations"
                    ? "Annotate benchmark"
                    : "Phân xử benchmark"}
                </h3>
                <button
                  type="button"
                  className="modern-modal-close"
                  onClick={() => setReviewing(null)}
                >
                  ✕
                </button>
              </div>
              <p>
                Nhập specification JSON. Backend sẽ kiểm tra đủ objects,
                quantities, relations và quy tắc độc lập annotator.
              </p>
              <form onSubmit={submitReview}>
                <textarea
                  id="review-json"
                  aria-label="Specification JSON"
                  className="ops-code"
                  rows={16}
                  spellCheck={false}
                  value={reviewJson}
                  onChange={(e) => setReviewJson(e.target.value)}
                  required
                />
                <div
                  style={{
                    display: "flex",
                    justifyContent: "flex-end",
                    gap: "10px",
                    marginTop: "14px",
                  }}
                >
                  <button
                    type="button"
                    className="role-switch-pill"
                    onClick={() => setReviewing(null)}
                  >
                    Hủy
                  </button>
                  <button
                    type="submit"
                    className="prediction-submit-btn"
                    disabled={action.busy}
                  >
                    {action.busy ? "Đang lưu…" : "Lưu thẩm định"}
                  </button>
                </div>
              </form>
            </div>
          </dialog>
        )}

        <div className="modern-table-wrapper">
          <table className="modern-table">
            <thead>
              <tr>
                <th>Đề bài Benchmark</th>
                <th>Chủ đề / Khối</th>
                <th>Nguồn bài</th>
                <th>Trạng thái Gold</th>
                <th>Annotations</th>
              </tr>
            </thead>
            <tbody>
              {resource.data?.map((b) => (
                <tr key={b.id}>
                  <td style={{ maxWidth: "340px" }}>
                    <span style={{ fontSize: "13px" }}>{b.problemText}</span>
                  </td>
                  <td>
                    <strong>{b.topic}</strong>
                    <small
                      style={{ display: "block", color: "var(--text-muted)" }}
                    >
                      Lớp {b.gradeScope}
                    </small>
                  </td>
                  <td>
                    <small style={{ color: "var(--text-secondary)" }}>
                      {b.sourceCategory}
                    </small>
                  </td>
                  <td>
                    <span
                      className={`status-pill ${b.goldSpecification ? "pass" : "draft"}`}
                    >
                      {b.goldSpecification ? "Gold Ready" : "Chờ thẩm định"}
                    </span>
                  </td>
                  <td>
                    <small style={{ color: "var(--text-muted)" }}>
                      {b.annotationCount} chuyên gia đã chú giải
                    </small>
                    <div
                      style={{ display: "flex", gap: "6px", marginTop: "8px" }}
                    >
                      {b.canAnnotate && (
                        <button
                          type="button"
                          className="role-switch-pill"
                          onClick={() => {
                            setReviewing({ id: b.id, mode: "annotations" });
                            setReviewJson(
                              '{\n  "objects": [],\n  "quantities": [],\n  "relations": []\n}',
                            );
                          }}
                        >
                          Annotate
                        </button>
                      )}
                      {b.canAdjudicate && (
                        <button
                          type="button"
                          className="prediction-submit-btn"
                          style={{ padding: "5px 9px", fontSize: "11px" }}
                          onClick={() => {
                            setReviewing({ id: b.id, mode: "adjudication" });
                            setReviewJson(
                              JSON.stringify(
                                b.annotations[0]?.specification ?? {
                                  objects: [],
                                  quantities: [],
                                  relations: [],
                                },
                                null,
                                2,
                              ),
                            );
                          }}
                        >
                          Phân xử
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}




