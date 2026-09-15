import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { problemHistory } from "../../api/problemApi";
import type { ProblemSummary, User } from "../../types/physlive";
import "../../styles/modern-roles.css";

interface ProfileModalProps {
  user: User | null;
  onClose: () => void;
}

export default function ProfileModal({ user, onClose }: ProfileModalProps) {
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState<"info" | "history">("info");
  const [history, setHistory] = useState<ProblemSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (activeTab === "history") {
      const request = Promise.resolve()
        .then(() => {
          setLoading(true);
          setError("");
          return problemHistory(0, 30);
        })
        .then((res) => {
          setHistory(res.content || []);
        })
        .catch(() => {
          setError("Không thể tải lịch sử đề bài đã gửi.");
        })
        .finally(() => {
          setLoading(false);
        });
      void request;
    }
  }, [activeTab]);

  return (
    <div className="modern-modal-overlay" onClick={onClose}>
      <div
        className="modern-modal-content"
        onClick={(e) => e.stopPropagation()}
        style={{ maxWidth: "720px" }}
      >
        <div className="modern-modal-header">
          <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
            <span style={{ fontSize: "24px" }}>👤</span>
            <div>
              <h3 style={{ margin: 0 }}>Hồ sơ người dùng</h3>
              <small style={{ color: "var(--text-muted)" }}>
                {user?.email}
              </small>
            </div>
          </div>
          <button
            type="button"
            className="modern-modal-close"
            onClick={onClose}
          >
            ✕
          </button>
        </div>

        {/* Tabs */}
        <div className="modern-tabs">
          <button
            type="button"
            className={`modern-tab-btn ${activeTab === "info" ? "active" : ""}`}
            onClick={() => setActiveTab("info")}
          >
            Thông tin tài khoản
          </button>
          <button
            type="button"
            className={`modern-tab-btn ${activeTab === "history" ? "active" : ""}`}
            onClick={() => setActiveTab("history")}
          >
            Lịch sử đề bài đã gửi (FR-USR-04)
          </button>
        </div>

        {/* TAB 1: User Info */}
        {activeTab === "info" && (
          <div
            style={{ display: "flex", flexDirection: "column", gap: "16px" }}
          >
            <div
              style={{
                display: "grid",
                gridTemplateColumns: "1fr 1fr",
                gap: "14px",
                background: "#f8fafc",
                padding: "16px",
                borderRadius: "10px",
                border: "1px solid var(--border-subtle)",
              }}
            >
              <div>
                <small style={{ color: "var(--text-muted)", display: "block" }}>
                  Họ và tên
                </small>
                <strong style={{ fontSize: "15px" }}>
                  {user?.fullName || "Chưa cập nhật"}
                </strong>
              </div>
              <div>
                <small style={{ color: "var(--text-muted)", display: "block" }}>
                  Email đăng nhập
                </small>
                <strong style={{ fontSize: "15px" }}>{user?.email}</strong>
              </div>
              <div>
                <small style={{ color: "var(--text-muted)", display: "block" }}>
                  Vai trò hệ thống
                </small>
                <span
                  className={`modern-badge-role ${user?.role?.toLowerCase() || ""}`}
                >
                  {user?.role}
                </span>
              </div>
              <div>
                <small style={{ color: "var(--text-muted)", display: "block" }}>
                  Mã định danh
                </small>
                <code style={{ fontSize: "13px" }}>ID #{user?.id}</code>
              </div>
            </div>

            <div
              style={{
                background: "#eff6ff",
                border: "1px solid #bfdbfe",
                padding: "14px",
                borderRadius: "8px",
                fontSize: "13px",
                color: "#1e40af",
              }}
            >
              💡 <strong>Quyền hạn tài khoản:</strong> Bạn đang đăng nhập với
              quyền <strong>{user?.role}</strong>.
              {user?.role === "TEACHER" &&
                " Bạn có thể soạn đề, tạo mô phỏng, lưu thư viện và giao bài cho học sinh."}
              {user?.role === "STUDENT" &&
                " Bạn có thể hoàn thành bài tập dự đoán và khám phá thư viện mô phỏng."}
              {user?.role === "REVIEWER" &&
                " Bạn có thể phân xử extraction, duyệt Topic Schemas, cấu hình Reference Solvers và thẩm định Benchmark corpus."}
              {user?.role === "ADMIN" &&
                " Bạn có toàn quyền quản trị tài khoản, trường học, chương trình và giám sát chất lượng kiểm định."}
            </div>
          </div>
        )}

        {/* TAB 2: Submission History */}
        {activeTab === "history" && (
          <div>
            {loading ? (
              <p style={{ color: "var(--text-muted)", padding: "20px" }}>
                Đang tải lịch sử đề bài…
              </p>
            ) : error ? (
              <div className="status-pill fail">{error}</div>
            ) : history.length === 0 ? (
              <div
                style={{
                  padding: "40px",
                  textAlign: "center",
                  color: "var(--text-muted)",
                }}
              >
                Bạn chưa gửi đề bài nào trên hệ thống.
              </div>
            ) : (
              <div
                className="modern-table-wrapper"
                style={{ maxHeight: "380px" }}
              >
                <table className="modern-table">
                  <thead>
                    <tr>
                      <th>Nội dung trích đoạn</th>
                      <th>Thời gian</th>
                      <th>Trạng thái</th>
                      <th>Thao tác</th>
                    </tr>
                  </thead>
                  <tbody>
                    {history.map((item) => (
                      <tr key={item.id}>
                        <td style={{ maxWidth: "340px" }}>
                          <span
                            style={{
                              fontSize: "13px",
                              color: "var(--text-primary)",
                            }}
                          >
                            {item.previewText ||
                              "(Đề bài không có văn bản xem trước)"}
                          </span>
                          {item.hasImage && (
                            <span
                              className="status-pill info"
                              style={{ marginLeft: "6px", fontSize: "10px" }}
                            >
                              Ảnh OCR
                            </span>
                          )}
                        </td>
                        <td>
                          <small style={{ color: "var(--text-muted)" }}>
                            {new Date(item.createdAt).toLocaleDateString(
                              "vi-VN",
                            )}
                          </small>
                        </td>
                        <td>
                          <span className="status-pill pass">
                            {item.status}
                          </span>
                        </td>
                        <td>
                          <button
                            type="button"
                            className="role-switch-pill"
                            style={{
                              background: "#ffffff",
                              border: "1px solid var(--border-subtle)",
                            }}
                            onClick={() => {
                              onClose();
                              navigate(`/workspace?problemId=${item.id}`);
                            }}
                          >
                            Mở →
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}

        <div style={{ marginTop: "24px", textAlign: "right" }}>
          <button
            type="button"
            className="role-switch-pill"
            style={{
              border: "1px solid var(--border-subtle)",
              padding: "8px 20px",
              fontSize: "13px",
            }}
            onClick={onClose}
          >
            Đóng
          </button>
        </div>
      </div>
    </div>
  );
}
