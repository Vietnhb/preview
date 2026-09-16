import { useState, type FormEvent } from "react";
import api from "../../../api/axios";
import { LoadState } from "../../operations/OperationsKit";
import { useAction, useResource } from "../../operations/operationsData";
import type { Specification } from "../../../types/physlive";
import type { ReviewItem } from "./reviewerTypes";

export function QueueTab() {
  const resource = useResource<ReviewItem[]>("/reviewer/ambiguities");
  const [selectedId, setSelectedId] = useState("");
  const [query, setQuery] = useState("");

  const items = (resource.data ?? []).filter((i) =>
    `${i.topic} ${i.question} ${i.problemText}`
      .toLowerCase()
      .includes(query.toLowerCase()),
  );
  const selected = items.find((i) => i.id === selectedId) ?? items[0];

  return (
    <section className="modern-card reviewer-queue-card">
      <div className="modern-card-header">
        <div>
          <h2>Hàng đợi Phân xử Extraction (FR-REV-03)</h2>
          <p>
            Các trường hợp trích xuất có dữ kiện hoặc hướng chuyển động chưa đủ
            tin cậy cần chuyên gia giải quyết.
          </p>
        </div>
        <button
          type="button"
          className="role-switch-pill"
          style={{
            border: "1px solid var(--border-subtle)",
            background: "#ffffff",
            padding: "8px 16px",
          }}
          onClick={resource.refresh}
          disabled={resource.loading}
        >
          Làm mới
        </button>
      </div>

      <LoadState {...resource} />

      <div className="reviewer-queue-search">
        <input
          placeholder="Tìm theo nội dung đề bài, câu hỏi, chủ đề…"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
      </div>

      {!resource.loading && !resource.error && items.length === 0 ? (
        <div
          style={{
            padding: "40px",
            textAlign: "center",
            color: "var(--text-muted)",
          }}
        >
          <span
            style={{ fontSize: "28px", display: "block", marginBottom: "8px" }}
          >
            ✨
          </span>
          <strong>Hàng đợi trống</strong>
          <p style={{ margin: "4px 0 0 0", fontSize: "13px" }}>
            Không có extraction nào đang chờ phân xử.
          </p>
        </div>
      ) : (
        <div className="reviewer-queue-layout">
          {/* Left: Ambiguity Items List */}
          <aside
            className="reviewer-queue-list"
            aria-label="Danh sách ca cần review"
          >
            {items.map((item) => (
              <button
                key={item.id}
                type="button"
                className={`reviewer-queue-item${selected?.id === item.id ? " active" : ""}`}
                onClick={() => setSelectedId(item.id)}
              >
                <div className="reviewer-queue-item-meta">
                  <span className="status-pill draft">
                    {item.topic || "Vật lý"}
                  </span>
                  <small>{item.fieldPath}</small>
                </div>
                <strong>{item.question}</strong>
                <p>{item.problemText?.slice(0, 100)}…</p>
              </button>
            ))}
          </aside>

          {/* Right: Resolution Workspace */}
          <main className="reviewer-queue-detail">
            {selected && (
              <ResolutionForm
                key={selected.id}
                item={selected}
                onResolved={resource.refresh}
              />
            )}
          </main>
        </div>
      )}
    </section>
  );
}

function ResolutionForm({
  item,
  onResolved,
}: Readonly<{ item: ReviewItem; onResolved: () => void }>) {
  const [answer, setAnswer] = useState("");
  const [comment, setComment] = useState("");
  const action = useAction();

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    await action.run(async () => {
      const response = await api.post<Specification>(
        `/reviewer/ambiguities/${item.id}/resolve`,
        {
          answer: answer.trim(),
          comment,
        },
      );
      const open =
        response.data.ambiguityCases ?? response.data.ambiguities ?? [];
      if (open.some((a) => a.code === item.code && a.status === "OPEN")) {
        throw new Error(
          "Câu trả lời đã gửi nhưng dữ kiện vẫn chưa đủ rõ. Hãy bổ sung giá trị hoặc hướng chuẩn.",
        );
      }
      onResolved();
    }, "Đã cập nhật đặc tả specification với phân xử chuyên môn thành công.");
  };

  return (
    <article className="reviewer-resolution-form">
      <h3 className="reviewer-detail-title">Đề bài gốc</h3>
      <div className="reviewer-original-problem">
        {item.problemText || "(Chưa có văn bản đề bài)"}
      </div>

      <details className="reviewer-extraction-details">
        <summary>
          Xem thực thể & quan hệ đã bóc tách (Quantities & Relations)
        </summary>
        <pre>
          {JSON.stringify(
            { quantities: item.quantities, relations: item.relations },
            null,
            2,
          )}
        </pre>
      </details>

      <form onSubmit={submit}>
        <div className="form-group reviewer-question-group">
          <p className="reviewer-review-question">
            ❓ {item.question}
          </p>
          <textarea
            rows={3}
            required
            placeholder="Nhập câu trả lời phân xử chính xác có kèm đại lượng, đơn vị hoặc hướng..."
            value={answer}
            onChange={(e) => setAnswer(e.target.value)}
          />
        </div>

        {Array.isArray(item.options) && item.options.length > 0 && (
          <div className="reviewer-suggestions">
            <small className="reviewer-suggestion-label">
              Gợi ý nhanh từ pipeline:
            </small>
            <div className="reviewer-suggestion-list">
              {item.options.map((opt) => (
                <button
                  key={opt}
                  type="button"
                  className="role-switch-pill"
                  onClick={() => setAnswer(opt)}
                >
                  {opt}
                </button>
              ))}
            </div>
          </div>
        )}

        <div className="form-group reviewer-comment-group">
          <label htmlFor="review-comment">
            Căn cứ chuyên môn / Ghi chú thẩm định:
          </label>
          <input
            id="review-comment"
            placeholder="Ví dụ: Lấy g = 9.8 m/s² theo giả định sách giáo khoa hiện hành..."
            value={comment}
            onChange={(e) => setComment(e.target.value)}
          />
        </div>

        {action.feedback}

        <button
          type="submit"
          className="prediction-submit-btn reviewer-resolution-submit"
          disabled={action.busy || !answer.trim()}
        >
          {action.busy
            ? "Đang cập nhật đặc tả…"
            : "Gửi kết luận phân xử chuyên môn"}
        </button>
      </form>
    </article>
  );
}




