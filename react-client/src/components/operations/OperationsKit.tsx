import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { usePhysliveStore } from "../../store/usePhysliveStore";
import { getToken } from "../../utils/token";
import "../../styles/operations.css";

export function Access({ reviewer = false, children }: Readonly<{ reviewer?: boolean; children: ReactNode }>) {
  const user = usePhysliveStore((state) => state.user);
  if (!getToken()) return <main className="main ops"><h1>Cần đăng nhập</h1><Link to="/login">Đăng nhập để tiếp tục</Link></main>;
  if (!user) return <main className="main ops"><output>Đang xác thực tài khoản…</output></main>;
  if (user.role !== "ADMIN" && !(reviewer && user.role === "CONTENT_REVIEWER")) return <main className="main ops"><h1>Không có quyền truy cập</h1><Link to="/workspace">Về workspace</Link></main>;
  return children;
}

export function Shell({ reviewer, tab, setTab, tabs, children }: Readonly<{ reviewer?: boolean; tab: string; setTab: (tab: string) => void; tabs: { id: string; label: string; detail: string }[]; children: ReactNode }>) {
  return <main className={`main ops ${reviewer ? "ops-reviewer" : ""}`}><header className="ops-hero"><div><span className="ops-kicker">PHYSLIVE / {reviewer ? "CONTENT QUALITY" : "OPERATIONS"}</span><h1>{reviewer ? "Kiểm duyệt nội dung" : "Quản trị nền tảng"}</h1><p>{reviewer ? "Từ dữ kiện đáng tin cậy đến mô hình sẵn sàng cho lớp học." : "Con người, chương trình học và chất lượng mô phỏng trong một nơi."}</p></div><span className="ops-role">{reviewer ? "Physics Content Reviewer" : "Administrator"}</span></header><nav className="ops-tabs" aria-label="Khu vực tác vụ">{tabs.map((item, index) => <button key={item.id} type="button" aria-current={tab === item.id ? "page" : undefined} className={tab === item.id ? "selected" : ""} onClick={() => setTab(item.id)}><span className="ops-tab-index">0{index + 1}</span><span><strong>{item.label}</strong><small>{item.detail}</small></span></button>)}</nav>{children}</main>;
}

export function LoadState({ loading, error, refresh }: Readonly<{ loading: boolean; error?: string; refresh: () => void }>) {
  return <>{loading && <output className="ops-loading">Đang tải dữ liệu…</output>}{error && <div className="ops-alert" role="alert">{error} <button className="secondary" onClick={refresh}>Thử lại</button></div>}</>;
}

export function Badge({ value }: Readonly<{ value: string }>) {
  const goodValues = ["APPROVED", "ACTIVE", "PASS", "GOLD_READY"];
  const badValues = ["RETIRED", "SUSPENDED", "FAIL", "DISAGREEMENT"];
  const tone = goodValues.includes(value) ? "good" : badValues.includes(value) ? "bad" : "pending";
  const labels: Record<string, string> = { APPROVED: "Đã duyệt", ACTIVE: "Hoạt động", PASS: "Đạt", GOLD_READY: "Gold sẵn sàng", RETIRED: "Ngừng dùng", SUSPENDED: "Tạm khóa", FAIL: "Không đạt", DISAGREEMENT: "Cần phân xử", DRAFT: "Bản nháp", ANNOTATING: "Đang annotate" };
  return <span className={`ops-badge ${tone}`}>{labels[value] ?? value}</span>;
}

export function Panel({ title, caption, action, children }: Readonly<{ title: string; caption?: string; action?: ReactNode; children: ReactNode }>) {
  return <section className="ops-panel"><div className="ops-panel-heading"><div><h2>{title}</h2>{caption && <p>{caption}</p>}</div>{action}</div>{children}</section>;
}

export function JsonEditor({ value, onChange, label = "Đặc tả JSON" }: Readonly<{ value: string; onChange: (value: string) => void; label?: string }>) {
  return <label className="ops-field"><span>{label}</span><textarea className="ops-code" spellCheck={false} rows={14} required value={value} onChange={(event) => onChange(event.target.value)} /></label>;
}
