import { useState, type FormEvent } from "react";
import api from "../../api/axios";
import { Access, LoadState } from "../../components/operations/OperationsKit";
import { downloadJson, parseObject, useAction, useResource } from "../../components/operations/operationsData";
import type { Specification } from "../../types/physlive";
import "../../styles/modern-roles.css";

type ReviewItem = {
  id: string;
  specificationId: string;
  question: string;
  fieldPath: string;
  code: string;
  options: string[];
  problemText: string;
  topic: string;
  quantities: unknown;
  relations: unknown;
};

type Version = {
  id: string;
  schemaId: string;
  version: string;
  lifecycleStatus: string;
  name?: string;
  topic?: string;
  definition?: unknown;
  solverId?: string;
  outputDefinition?: { referenceSolverId?: string };
  createdAt: string;
};

type Benchmark = {
  id: string;
  problemText: string;
  topic: string;
  gradeScope: string;
  sourceCategory: string;
  status: string;
  annotationCount: number;
  canAnnotate: boolean;
  canAdjudicate: boolean;
  annotations: { actor: string; specification: unknown }[];
  goldSpecification: unknown;
};

type Evaluation = {
  benchmarkCount: number;
  precision: number;
  recall: number;
  f1: number;
  kappa: number;
  incorrectRate: number;
};

const TABS = [
  { id: "queue", label: "Hàng đợi Phân xử Extraction (Queue)" },
  { id: "schemas", label: "Topic Schemas" },
  { id: "solvers", label: "Reference Solvers" },
  { id: "benchmarks", label: "Benchmark & Đánh giá Nghiên cứu" }
];

export default function ReviewerConsole() {
  return (
    <Access reviewer>
      <ReviewerPage />
    </Access>
  );
}

function ReviewerPage() {
  const [tab, setTab] = useState("queue");

  return (
    <div className="main">
      <div className="modern-container">
      {/* Header */}
      <header className="modern-header">
        <div className="modern-header-title">
          <div style={{ display: "flex", alignItems: "center", gap: "10px", marginBottom: "6px" }}>
            <h1>Kiểm duyệt Nội dung & Thẩm định Vật lý</h1>
            <span className="modern-badge-role reviewer">Content Reviewer</span>
          </div>
          <p>Phân xử các ca trích xuất mơ hồ, định nghĩa Topic Schemas, liên kết solver độc lập và đánh giá benchmark gold standard.</p>
        </div>
      </header>

      {/* Tabs */}
      <div className="modern-tabs">
        {TABS.map(t => (
          <button
            key={t.id}
            type="button"
            className={`modern-tab-btn ${tab === t.id ? "active" : ""}`}
            onClick={() => setTab(t.id)}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === "queue" && <QueueTab />}
      {tab === "schemas" && <VersionsTab solver={false} />}
      {tab === "solvers" && <VersionsTab solver={true} />}
      {tab === "benchmarks" && <BenchmarksTab />}
    </div>
    </div>
  );
}

// --------------------------------------------------------------------------
// TAB 1: ADJUDICATION QUEUE
// --------------------------------------------------------------------------
function QueueTab() {
  const resource = useResource<ReviewItem[]>("/reviewer/ambiguities");
  const [selectedId, setSelectedId] = useState("");
  const [query, setQuery] = useState("");

  const items = (resource.data ?? []).filter(i =>
    `${i.topic} ${i.question} ${i.problemText}`.toLowerCase().includes(query.toLowerCase())
  );
  const selected = items.find(i => i.id === selectedId) ?? items[0];

  return (
    <div className="modern-card">
      <div className="modern-card-header">
        <div>
          <h2>Hàng đợi Phân xử Extraction (FR-REV-03)</h2>
          <p>Các trường hợp trích xuất có dữ kiện hoặc hướng chuyển động chưa đủ tin cậy cần chuyên gia giải quyết.</p>
        </div>
        <button
          type="button"
          className="role-switch-pill"
          style={{ border: "1px solid var(--border-subtle)", background: "#ffffff", padding: "8px 16px" }}
          onClick={resource.refresh}
          disabled={resource.loading}
        >
          Làm mới
        </button>
      </div>

      <LoadState {...resource} />

      <div style={{ marginBottom: "16px" }}>
        <input
          style={{ width: "100%", maxWidth: "420px", border: "1px solid var(--border-strong)", borderRadius: "8px", padding: "8px 12px", fontSize: "13.5px" }}
          placeholder="Tìm theo nội dung đề bài, câu hỏi, chủ đề…"
          value={query}
          onChange={e => setQuery(e.target.value)}
        />
      </div>

      {!resource.loading && !resource.error && items.length === 0 ? (
        <div style={{ padding: "40px", textAlign: "center", color: "var(--text-muted)" }}>
          <span style={{ fontSize: "28px", display: "block", marginBottom: "8px" }}>✨</span>
          <strong>Hàng đợi trống</strong>
          <p style={{ margin: "4px 0 0 0", fontSize: "13px" }}>Không có extraction nào đang chờ phân xử.</p>
        </div>
      ) : (
        <div style={{ display: "grid", gridTemplateColumns: "360px 1fr", gap: "20px", alignItems: "start" }}>
          {/* Left: Ambiguity Items List */}
          <div style={{ display: "flex", flexDirection: "column", gap: "10px", maxHeight: "640px", overflowY: "auto" }}>
            {items.map(item => (
              <button
                key={item.id}
                type="button"
                style={{
                  textAlign: "left",
                  background: selected?.id === item.id ? "#eff6ff" : "#ffffff",
                  border: `1.5px solid ${selected?.id === item.id ? "var(--role-reviewer)" : "var(--border-subtle)"}`,
                  borderRadius: "10px",
                  padding: "14px",
                  cursor: "pointer",
                  transition: "all 0.15s ease"
                }}
                onClick={() => setSelectedId(item.id)}
              >
                <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "6px" }}>
                  <span className="status-pill draft">{item.topic || "Vật lý"}</span>
                  <small style={{ color: "var(--text-muted)" }}>{item.fieldPath}</small>
                </div>
                <strong style={{ fontSize: "14px", color: "var(--text-primary)", display: "block", marginBottom: "4px" }}>
                  {item.question}
                </strong>
                <p style={{ margin: 0, fontSize: "12px", color: "var(--text-secondary)", lineHeight: "1.4" }}>
                  {item.problemText?.slice(0, 100)}…
                </p>
              </button>
            ))}
          </div>

          {/* Right: Resolution Workspace */}
          {selected && <ResolutionForm key={selected.id} item={selected} onResolved={resource.refresh} />}
        </div>
      )}
    </div>
  );
}

function ResolutionForm({ item, onResolved }: { item: ReviewItem; onResolved: () => void }) {
  const [answer, setAnswer] = useState("");
  const [comment, setComment] = useState("");
  const action = useAction();

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    await action.run(async () => {
      const response = await api.post<Specification>(`/reviewer/ambiguities/${item.id}/resolve`, {
        answer: answer.trim(),
        comment
      });
      const open = response.data.ambiguityCases ?? response.data.ambiguities ?? [];
      if (open.some(a => a.code === item.code && a.status === "OPEN")) {
        throw new Error("Câu trả lời đã gửi nhưng dữ kiện vẫn chưa đủ rõ. Hãy bổ sung giá trị hoặc hướng chuẩn.");
      }
      onResolved();
    }, "Đã cập nhật đặc tả specification với phân xử chuyên môn thành công.");
  };

  return (
    <div style={{ background: "#ffffff", border: "1px solid var(--border-subtle)", borderRadius: "10px", padding: "20px" }}>
      <h3 style={{ margin: "0 0 14px 0", fontSize: "16px" }}>Đề bài gốc</h3>
      <div style={{ background: "#f8fafc", padding: "14px", borderRadius: "8px", border: "1px solid var(--border-subtle)", marginBottom: "16px", fontSize: "13.5px", lineHeight: "1.6" }}>
        {item.problemText || "(Chưa có văn bản đề bài)"}
      </div>

      <details style={{ marginBottom: "16px", fontSize: "13px" }}>
        <summary style={{ cursor: "pointer", fontWeight: "600", color: "var(--role-reviewer)" }}>
          Xem thực thể & quan hệ đã bóc tách (Quantities & Relations)
        </summary>
        <pre style={{ background: "#1e293b", color: "#f8fafc", padding: "12px", borderRadius: "8px", overflowX: "auto", fontSize: "12px", marginTop: "8px" }}>
          {JSON.stringify({ quantities: item.quantities, relations: item.relations }, null, 2)}
        </pre>
      </details>

      <form onSubmit={submit}>
        <div className="form-group" style={{ marginBottom: "14px" }}>
          <label style={{ color: "var(--role-reviewer)", fontSize: "14px", fontWeight: "700" }}>
            ❓ {item.question}
          </label>
          <textarea
            rows={3}
            required
            placeholder="Nhập câu trả lời phân xử chính xác có kèm đại lượng, đơn vị hoặc hướng..."
            value={answer}
            onChange={e => setAnswer(e.target.value)}
          />
        </div>

        {Array.isArray(item.options) && item.options.length > 0 && (
          <div style={{ marginBottom: "14px" }}>
            <small style={{ color: "var(--text-muted)", display: "block", marginBottom: "6px" }}>Gợi ý nhanh từ pipeline:</small>
            <div style={{ display: "flex", flexWrap: "wrap", gap: "6px" }}>
              {item.options.map(opt => (
                <button
                  key={opt}
                  type="button"
                  className="role-switch-pill"
                  style={{ background: "#f1f5f9", border: "1px solid var(--border-subtle)" }}
                  onClick={() => setAnswer(opt)}
                >
                  {opt}
                </button>
              ))}
            </div>
          </div>
        )}

        <div className="form-group" style={{ marginBottom: "16px" }}>
          <label>Căn cứ chuyên môn / Ghi chú thẩm định:</label>
          <input
            placeholder="Ví dụ: Lấy g = 9.8 m/s² theo giả định sách giáo khoa hiện hành..."
            value={comment}
            onChange={e => setComment(e.target.value)}
          />
        </div>

        {action.feedback}

        <button
          type="submit"
          className="prediction-submit-btn"
          style={{ background: "var(--role-reviewer)" }}
          disabled={action.busy || !answer.trim()}
        >
          {action.busy ? "Đang cập nhật đặc tả…" : "Gửi kết luận phân xử chuyên môn"}
        </button>
      </form>
    </div>
  );
}

// --------------------------------------------------------------------------
// TAB 2 & 3: SCHEMAS AND REFERENCE SOLVERS
// --------------------------------------------------------------------------
function VersionsTab({ solver }: { solver: boolean }) {
  const resource = useResource<Version[]>(solver ? "/reviewer/solvers" : "/reviewer/schemas");
  const implementations = useResource<{ numerical: string[]; reference: string[] }>("/reviewer/solver-implementations");
  const action = useAction();

  const [filter, setFilter] = useState("");
  const [query, setQuery] = useState("");
  const [editor, setEditor] = useState<{ version?: Version; clone: boolean } | null>(null);
  const [decision, setDecision] = useState<{ version: Version; status: string } | null>(null);

  const rows = (resource.data ?? []).filter(
    v => (!filter || v.lifecycleStatus === filter) && `${v.schemaId} ${v.name ?? ""} ${v.version}`.toLowerCase().includes(query.toLowerCase())
  );

  return (
    <div className="modern-card">
      <div className="modern-card-header">
        <div>
          <h2>{solver ? "Reference Solvers & Module Bindings (FR-REV-02)" : "Topic Schemas & Phê duyệt Module (FR-REV-01)"}</h2>
          <p>
            {solver
              ? "Liên kết numerical solver module với independent closed-form reference solver đã cài trên server."
              : "Quản lý đặc tả chủ đề thuộc phạm vi THPT: Bản nháp (DRAFT) → Phê duyệt (APPROVED) → Ngừng dùng (RETIRED)."}
          </p>
        </div>
        <button
          type="button"
          className="prediction-submit-btn"
          style={{ background: "var(--role-reviewer)", padding: "8px 16px", fontSize: "13px" }}
          onClick={() => setEditor({ clone: false })}
        >
          + Tạo bản nháp mới
        </button>
      </div>

      <LoadState {...resource} />
      {action.feedback}

      {/* Lifecycle Decision Dialog */}
      {decision && (
        <div className="modern-modal-overlay" onClick={() => setDecision(null)}>
          <div className="modern-modal-content" onClick={e => e.stopPropagation()} style={{ maxWidth: "480px" }}>
            <div className="modern-modal-header">
              <h3>{decision.status === "APPROVED" ? "Phê duyệt phát hành" : "Ngừng sử dụng"}</h3>
              <button type="button" className="modern-modal-close" onClick={() => setDecision(null)}>✕</button>
            </div>
            <p style={{ fontSize: "14px", lineHeight: "1.5" }}>
              Bạn có chắc chắn muốn chuyển trạng thái <strong>{decision.version.schemaId}</strong> @{decision.version.version} sang <strong>{decision.status}</strong>?
            </p>
            <div style={{ display: "flex", justifyContent: "flex-end", gap: "10px", marginTop: "20px" }}>
              <button type="button" className="role-switch-pill" style={{ border: "1px solid var(--border-subtle)", padding: "8px 16px" }} onClick={() => setDecision(null)}>
                Hủy
              </button>
              <button
                type="button"
                className="prediction-submit-btn"
                style={{ background: decision.status === "APPROVED" ? "var(--role-student)" : "#dc2626" }}
                disabled={action.busy}
                onClick={() =>
                  void action.run(
                    () => api.put(`/reviewer/${solver ? "solvers" : "schema-versions"}/${decision.version.id}/lifecycle`, null, {
                      params: { status: decision.status }
                    }),
                    `Đã cập nhật trạng thái sang ${decision.status}`
                  ).then(ok => {
                    if (ok) { setDecision(null); resource.refresh(); }
                  })
                }
              >
                Xác nhận
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Editor Modal */}
      {editor && (
        <VersionEditorModal
          solver={solver}
          initial={editor.version}
          clone={editor.clone}
          implementations={implementations.data}
          onClose={() => setEditor(null)}
          onSaved={() => { setEditor(null); resource.refresh(); }}
        />
      )}

      {/* Filter bar */}
      <div style={{ display: "flex", gap: "12px", marginBottom: "16px", flexWrap: "wrap" }}>
        <input
          style={{ flex: 1, minWidth: "240px", border: "1px solid var(--border-strong)", borderRadius: "8px", padding: "8px 12px", fontSize: "13.5px" }}
          placeholder="Tìm schema ID, tên hoặc phiên bản…"
          value={query}
          onChange={e => setQuery(e.target.value)}
        />
        <select
          style={{ border: "1px solid var(--border-strong)", borderRadius: "8px", padding: "8px 12px", fontSize: "13.5px" }}
          value={filter}
          onChange={e => setFilter(e.target.value)}
        >
          <option value="">Tất cả trạng thái</option>
          {["DRAFT", "APPROVED", "RETIRED"].map(s => (
            <option key={s} value={s}>{s}</option>
          ))}
        </select>
        <button
          type="button"
          className="role-switch-pill"
          style={{ border: "1px solid var(--border-subtle)", background: "#ffffff", padding: "8px 16px" }}
          onClick={resource.refresh}
        >
          Làm mới
        </button>
      </div>

      {/* Table */}
      <div className="modern-table-wrapper">
        <table className="modern-table">
          <thead>
            <tr>
              <th>{solver ? "Solver / Schema" : "Schema / Chủ đề"}</th>
              <th>Phiên bản</th>
              <th>Trạng thái</th>
              <th>Thao tác</th>
            </tr>
          </thead>
          <tbody>
            {rows.map(v => (
              <tr key={v.id}>
                <td>
                  <strong>{solver ? v.solverId : v.name}</strong>
                  <small style={{ display: "block", color: "var(--text-muted)" }}>
                    {v.schemaId} {v.topic ? `· ${v.topic}` : ""}
                  </small>
                  {solver && (
                    <small style={{ color: "var(--role-reviewer)" }}>
                      Reference solver: <code>{v.outputDefinition?.referenceSolverId || "closed-form"}</code>
                    </small>
                  )}
                </td>
                <td>
                  <code>v{v.version}</code>
                  <small style={{ display: "block", color: "var(--text-muted)" }}>
                    {new Date(v.createdAt).toLocaleDateString("vi-VN")}
                  </small>
                </td>
                <td>
                  <span className={`status-pill ${v.lifecycleStatus === "APPROVED" ? "pass" : v.lifecycleStatus === "DRAFT" ? "draft" : "fail"}`}>
                    {v.lifecycleStatus}
                  </span>
                </td>
                <td>
                  <div style={{ display: "flex", gap: "6px" }}>
                    <button
                      type="button"
                      className="role-switch-pill"
                      style={{ border: "1px solid var(--border-subtle)", background: "#ffffff" }}
                      onClick={() => setEditor({ version: v, clone: v.lifecycleStatus !== "DRAFT" })}
                    >
                      {v.lifecycleStatus === "DRAFT" ? "Sửa" : "Nhân bản"}
                    </button>
                    {v.lifecycleStatus === "DRAFT" && (
                      <button
                        type="button"
                        className="role-switch-pill"
                        style={{ background: "var(--status-pass-bg)", color: "var(--status-pass-text)" }}
                        onClick={() => setDecision({ version: v, status: "APPROVED" })}
                      >
                        Phê duyệt
                      </button>
                    )}
                    {v.lifecycleStatus === "APPROVED" && (
                      <button
                        type="button"
                        className="role-switch-pill"
                        style={{ background: "var(--status-fail-bg)", color: "var(--status-fail-text)" }}
                        onClick={() => setDecision({ version: v, status: "RETIRED" })}
                      >
                        Ngừng dùng
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
  );
}

function VersionEditorModal({
  solver,
  initial,
  clone,
  implementations,
  onClose,
  onSaved
}: {
  solver: boolean;
  initial?: Version;
  clone: boolean;
  implementations?: { numerical: string[]; reference: string[] };
  onClose: () => void;
  onSaved: () => void;
}) {
  const [definition, setDefinition] = useState(
    JSON.stringify((solver ? initial?.outputDefinition : initial?.definition) ?? {}, null, 2)
  );
  const action = useAction();
  const isEdit = Boolean(initial && !clone);

  const submit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const fields = Object.fromEntries(new FormData(e.currentTarget));
    const json = parseObject(definition);

    const body = solver
      ? {
          schemaId: fields.schemaId,
          version: fields.version,
          solverId: fields.solverId,
          outputDefinition: { ...json, referenceSolverId: fields.referenceSolverId }
        }
      : { ...fields, definition: json };

    const ok = await action.run(() =>
      isEdit
        ? api.put(`/reviewer/${solver ? "solvers" : "schema-versions"}/${initial?.id}`, body)
        : api.post(`/reviewer/${solver ? "solvers" : "schemas"}`, body)
    );
    if (ok) onSaved();
  };

  return (
    <div className="modern-modal-overlay" onClick={onClose}>
      <div className="modern-modal-content" onClick={e => e.stopPropagation()} style={{ maxWidth: "680px" }}>
        <div className="modern-modal-header">
          <h3>{isEdit ? "Chỉnh sửa bản nháp" : "Tạo phiên bản mới"}</h3>
          <button type="button" className="modern-modal-close" onClick={onClose}>✕</button>
        </div>

        <form onSubmit={submit}>
          <div className="form-row">
            <div className="form-group">
              <label>Schema ID *</label>
              <input name="schemaId" required maxLength={80} readOnly={isEdit} defaultValue={initial?.schemaId ?? ""} placeholder="kinematics-1d" />
            </div>
            <div className="form-group">
              <label>Phiên bản *</label>
              <input name="version" required maxLength={16} readOnly={isEdit} defaultValue={clone ? "" : initial?.version ?? ""} placeholder="1.0.0" />
            </div>
          </div>

          {!solver && (
            <div className="form-row">
              <div className="form-group">
                <label>Tên Schema *</label>
                <input name="name" required defaultValue={initial?.name ?? ""} placeholder="Chuyển động thẳng biến đổi đều" />
              </div>
              <div className="form-group">
                <label>Chủ đề *</label>
                <select name="topic" defaultValue={initial?.topic ?? "Kinematics"}>
                  <option value="Kinematics">Kinematics (Động học)</option>
                  <option value="Dynamics">Dynamics (Động lực học)</option>
                  <option value="Circuits">Circuits (Mạch điện)</option>
                </select>
              </div>
            </div>
          )}

          {solver && (
            <div className="form-row">
              <div className="form-group">
                <label>Numerical Module *</label>
                <select name="solverId" required defaultValue={initial?.solverId ?? ""}>
                  <option value="">-- Chọn numerical solver --</option>
                  {implementations?.numerical.map(id => <option key={id} value={id}>{id}</option>)}
                </select>
              </div>
              <div className="form-group">
                <label>Independent Reference Solver *</label>
                <select name="referenceSolverId" required defaultValue={initial?.outputDefinition?.referenceSolverId ?? ""}>
                  <option value="">-- Chọn reference solver --</option>
                  {implementations?.reference.map(id => <option key={id} value={id}>{id}</option>)}
                </select>
              </div>
            </div>
          )}

          <div className="form-group" style={{ marginBottom: "18px" }}>
            <label>Định nghĩa JSON (Schema / Output definition)</label>
            <textarea
              rows={8}
              style={{ fontFamily: "monospace", fontSize: "12.5px" }}
              value={definition}
              onChange={e => setDefinition(e.target.value)}
            />
          </div>

          {action.feedback}

          <div style={{ display: "flex", justifyContent: "flex-end", gap: "10px" }}>
            <button type="button" className="role-switch-pill" style={{ border: "1px solid var(--border-subtle)", padding: "8px 16px" }} onClick={onClose}>
              Hủy
            </button>
            <button type="submit" className="prediction-submit-btn" style={{ background: "var(--role-reviewer)" }} disabled={action.busy}>
              {action.busy ? "Đang lưu…" : "Lưu phiên bản"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

// --------------------------------------------------------------------------
// TAB 4: BENCHMARK & EVALUATION
// --------------------------------------------------------------------------
function BenchmarksTab() {
  const resource = useResource<Benchmark[]>("/reviewer/benchmarks");
  const action = useAction();
  const [creating, setCreating] = useState(false);
  const [evaluation, setEvaluation] = useState<Evaluation | null>(null);

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

  return (
    <div>
      {/* KPI Cards when evaluation is run */}
      {evaluation && (
        <div className="modern-kpis">
          <div className="modern-kpi-card">
            <div className="modern-kpi-title">Precision (Độ chính xác)</div>
            <div className="modern-kpi-value" style={{ color: "var(--role-student)" }}>
              {(evaluation.precision * 100).toFixed(1)}%
            </div>
            <div className="modern-kpi-sub">Field-level precision</div>
          </div>
          <div className="modern-kpi-card">
            <div className="modern-kpi-title">Recall (Độ bao phủ)</div>
            <div className="modern-kpi-value" style={{ color: "var(--role-teacher)" }}>
              {(evaluation.recall * 100).toFixed(1)}%
            </div>
            <div className="modern-kpi-sub">Field-level recall</div>
          </div>
          <div className="modern-kpi-card">
            <div className="modern-kpi-title">F1 Score</div>
            <div className="modern-kpi-value" style={{ color: "var(--role-reviewer)" }}>
              {(evaluation.f1 * 100).toFixed(1)}%
            </div>
            <div className="modern-kpi-sub">F1 harmonic mean</div>
          </div>
          <div className="modern-kpi-card">
            <div className="modern-kpi-title">Cohen's Kappa (Độ đồng thuận)</div>
            <div className="modern-kpi-value">{evaluation.kappa.toFixed(3)}</div>
            <div className="modern-kpi-sub">Inter-annotator agreement</div>
          </div>
        </div>
      )}

      <div className="modern-card">
        <div className="modern-card-header">
          <div>
            <h2>Tập đề Benchmark & Đánh giá Nghiên cứu (FR-REV-06)</h2>
            <p>Quy trình hai chuyên gia độc lập annotate và chuyên gia thứ ba phân xử tạo Gold Specification.</p>
          </div>
          <div style={{ display: "flex", gap: "8px" }}>
            <button
              type="button"
              className="role-switch-pill"
              style={{ border: "1px solid var(--border-subtle)", background: "#ffffff", padding: "8px 14px" }}
              disabled={!resource.data?.some(b => b.goldSpecification)}
              onClick={() => downloadJson(resource.data?.filter(b => b.goldSpecification), "physlive-gold-corpus.json")}
            >
              Xuất Gold Corpus JSON
            </button>
            <button
              type="button"
              className="prediction-submit-btn"
              style={{ background: "var(--role-reviewer)", padding: "8px 16px", fontSize: "13px" }}
              disabled={action.busy}
              onClick={() => void runEvaluationPipeline()}
            >
              {action.busy ? "Đang tính toán…" : "Chạy Đánh giá Nghiên cứu ⚡"}
            </button>
            <button
              type="button"
              className="role-switch-pill"
              style={{ border: "1px solid var(--border-subtle)", background: "#ffffff", padding: "8px 14px" }}
              onClick={() => setCreating(true)}
            >
              + Thêm bài Benchmark
            </button>
          </div>
        </div>

        <LoadState {...resource} />
        {action.feedback}

        {creating && (
          <div className="modern-modal-overlay" onClick={() => setCreating(false)}>
            <div className="modern-modal-content" onClick={e => e.stopPropagation()}>
              <div className="modern-modal-header">
                <h3>Thêm đề bài vào Benchmark Corpus</h3>
                <button type="button" className="modern-modal-close" onClick={() => setCreating(false)}>✕</button>
              </div>
              <form onSubmit={create}>
                <div className="form-group" style={{ marginBottom: "14px" }}>
                  <label>Nội dung đề bài *</label>
                  <textarea name="problemText" rows={4} required placeholder="Ví dụ: Một ô tô bắt đầu chuyển động thẳng nhanh dần đều..." />
                </div>
                <div className="form-row">
                  <div className="form-group">
                    <label>Chủ đề *</label>
                    <select name="topic">
                      <option value="Kinematics">Kinematics (Động học)</option>
                      <option value="Dynamics">Dynamics (Động lực học)</option>
                      <option value="Circuits">Circuits (Mạch điện)</option>
                    </select>
                  </div>
                  <div className="form-group">
                    <label>Khối lớp THPT *</label>
                    <select name="gradeScope">
                      <option value="10">Lớp 10</option>
                      <option value="11">Lớp 11</option>
                      <option value="12">Lớp 12</option>
                    </select>
                  </div>
                </div>
                <div className="form-group" style={{ marginBottom: "18px" }}>
                  <label>Nguồn đề / Quyền sử dụng *</label>
                  <input name="sourceCategory" required placeholder="SGK Vật lý 10 Kết nối tri thức, Tr. 32" />
                </div>
                <div style={{ display: "flex", justifyContent: "flex-end", gap: "10px" }}>
                  <button type="button" className="role-switch-pill" style={{ border: "1px solid var(--border-subtle)", padding: "8px 16px" }} onClick={() => setCreating(false)}>
                    Hủy
                  </button>
                  <button type="submit" className="prediction-submit-btn" style={{ background: "var(--role-reviewer)" }} disabled={action.busy}>
                    Thêm vào Corpus
                  </button>
                </div>
              </form>
            </div>
          </div>
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
              {resource.data?.map(b => (
                <tr key={b.id}>
                  <td style={{ maxWidth: "340px" }}>
                    <span style={{ fontSize: "13px" }}>{b.problemText}</span>
                  </td>
                  <td>
                    <strong>{b.topic}</strong>
                    <small style={{ display: "block", color: "var(--text-muted)" }}>Lớp {b.gradeScope}</small>
                  </td>
                  <td>
                    <small style={{ color: "var(--text-secondary)" }}>{b.sourceCategory}</small>
                  </td>
                  <td>
                    <span className={`status-pill ${b.goldSpecification ? "pass" : "draft"}`}>
                      {b.goldSpecification ? "Gold Ready" : "Chờ thẩm định"}
                    </span>
                  </td>
                  <td>
                    <small style={{ color: "var(--text-muted)" }}>
                      {b.annotationCount} chuyên gia đã chú giải
                    </small>
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
