import { useCallback, useEffect, useMemo, useState, type ComponentProps, type ReactNode } from "react";
import { Theme } from "@radix-ui/themes";
import { useEffectiveTheme } from "../../../shared/theme/themeStore";
import { Link, NavLink, Outlet } from "react-router-dom";
import LearningHeader from "../../../shared/layout/LearningHeader";
import LearningIcon from "../../../shared/ui/LearningIcon";
import { useSessionStore } from "../../../shared/auth/sessionStore";
import { isDepartmentHead } from "../../../shared/auth/permissions";
import { mySupportItems, type SimulationComplaint, type SupportStatus } from "../../support/api/supportApi";
import { COMPLAINT_STATUS } from "../../support/components/SimulationComplaintDialog";
import "../../support/styles/complaints.css";
import "../../simulation/styles/learning.css";
import "../../simulation/styles/lab.css";

const complaintsOnly = (items: SimulationComplaint[]) => items.filter(item => item.kind === "COMPLAINT");

type RailItem = { to: string; label: string; icon: ComponentProps<typeof LearningIcon>["name"]; end?: boolean; badge?: number };

/**
 * Teacher management area. It keeps the workspace header (Mô phỏng / Giao bài / Quản lý) and adds
 * a narrow rail for the pages that are not authoring: submissions, complaints and resources.
 */
export default function TeacherAreaLayout() {
  const user = useSessionStore(state => state.user);
  // Complaints a reviewer has answered but not closed: the teacher may want to read the reply.
  const [answered, setAnswered] = useState(0);
  useEffect(() => {
    let active = true;
    mySupportItems().then(items => { if (active) setAnswered(complaintsOnly(items).filter(item => item.status === "READ").length); }).catch(() => undefined);
    return () => { active = false; };
  }, []);

  const groups: { label: string; items: RailItem[] }[] = [
    { label: "Theo dõi", items: [
      { to: "/lab", label: "Bài nộp", icon: "chart", end: true },
      { to: "/lab/complaints", label: "Khiếu nại đã gửi", icon: "message", badge: answered },
    ] },
    { label: "Tài nguyên", items: [
      { to: "/lab/library", label: "Thư viện của tôi", icon: "folder" },
      { to: "/lab/community", label: "Kho cộng đồng", icon: "users" },
      ...(isDepartmentHead(user) && user?.schoolId ? [{ to: "/lab/department", label: "Tổ bộ môn", icon: "shield" as const }] : []),
    ] },
  ];
  return <div className="learning-app lab-page">
    <LearningHeader />
    <div className="teacher-area">
      <nav className="teacher-rail" aria-label="Khu quản lý của giáo viên">
        {groups.map(group => <div className="teacher-rail__group" key={group.label}>
          <p>{group.label}</p>
          {group.items.map(item => <NavLink key={item.to} to={item.to} end={item.end} className="teacher-rail__item">
            <LearningIcon name={item.icon} /><span>{item.label}</span>
            {Boolean(item.badge) && <em aria-label={`${item.badge} phản hồi mới`}>{item.badge}</em>}
          </NavLink>)}
        </div>)}
      </nav>
      <div className="teacher-area__content"><Outlet /></div>
    </div>
  </div>;
}

/** Pages built with Radix Themes need its variables; teacher pages are otherwise plain CSS. */
export function TeacherThemed({ children }: Readonly<{ children: ReactNode }>) {
  const appearance = useEffectiveTheme();
  return <Theme appearance={appearance} accentColor="indigo" grayColor="slate" radius="large" scaling="100%" hasBackground={false}>{children}</Theme>;
}

type Filter = "ALL" | SupportStatus;

export function TeacherComplaintsPage() {
  const [items, setItems] = useState<SimulationComplaint[] | null>(null);
  const [error, setError] = useState("");
  const [filter, setFilter] = useState<Filter>("ALL");
  const load = useCallback(async () => {
    setError("");
    try { setItems(complaintsOnly(await mySupportItems())); }
    catch { setItems(current => current ?? []); setError("Chưa tải được danh sách khiếu nại. Vui lòng thử lại."); }
  }, []);
  useEffect(() => { void load(); }, [load]);
  const visible = useMemo(() => (items ?? []).filter(item => filter === "ALL" || item.status === filter), [items, filter]);
  const count = (status: SupportStatus) => (items ?? []).filter(item => item.status === status).length;

  return <div className="complaint-page">
    <header className="complaint-page__head">
      <div><h1>Khiếu nại đã gửi</h1><p>Các lần bạn báo mô phỏng sai và phản hồi của người kiểm duyệt. Để gửi khiếu nại mới, mở mô phỏng rồi bấm “Báo lỗi” phía trên khung mô phỏng.</p></div>
      <button type="button" className="complaint-page__refresh" onClick={() => void load()}>Làm mới</button>
    </header>
    <div className="complaint-page__filters" role="group" aria-label="Lọc theo trạng thái">
      <button type="button" aria-pressed={filter === "ALL"} onClick={() => setFilter("ALL")}>Tất cả · {items?.length ?? 0}</button>
      {(["OPEN", "READ", "RESOLVED"] as const).map(status => <button type="button" key={status} aria-pressed={filter === status} onClick={() => setFilter(status)}>{COMPLAINT_STATUS[status]} · {count(status)}</button>)}
    </div>
    {error && <p className="complaint-error" role="alert">{error}</p>}
    {items === null ? <p className="complaint-page__empty" role="status">Đang tải…</p>
      : visible.length === 0 ? <p className="complaint-page__empty">{items.length === 0 ? <>Bạn chưa gửi khiếu nại nào. <Link to="/workspace">Mở trang Mô phỏng</Link></> : "Không có khiếu nại ở trạng thái này."}</p>
      : <div className="complaint-page__list">{visible.map(item => {
        const [detail, context] = item.content.split("\n— Đề bài —");
        return <article className="complaint-item" key={item.id}>
          <div className="complaint-item__top"><strong>{item.subject}</strong><span className="complaint-status" data-status={item.status}>{COMPLAINT_STATUS[item.status]}</span></div>
          <time dateTime={item.createdAt}>Gửi lúc {new Date(item.createdAt).toLocaleString("vi-VN", { dateStyle: "short", timeStyle: "short" })}</time>
          <p className="complaint-item__body">{detail.trim()}</p>
          {context && <p className="complaint-context" style={{ whiteSpace: "pre-wrap" }}><strong>Đề bài</strong>{"\n"}{context.split("— Thông số đang dùng —")[0].trim()}</p>}
          {item.adminResponse
            ? <p className="complaint-item__answer"><b>Phản hồi của người kiểm duyệt{item.responderName ? ` (${item.responderName})` : ""}{item.respondedAt ? ` · ${new Date(item.respondedAt).toLocaleString("vi-VN", { dateStyle: "short", timeStyle: "short" })}` : ""}</b>{item.adminResponse}</p>
            : <p className="complaint-item__meta">Chưa có phản hồi.</p>}
        </article>;
      })}</div>}
  </div>;
}
