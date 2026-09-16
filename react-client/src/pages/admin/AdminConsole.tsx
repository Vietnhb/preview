import { useState } from "react";
import { useNavigate } from "react-router-dom";
import LearningIcon from "../../components/common/LearningIcon";
import { usePhysliveStore } from "../../store/usePhysliveStore";
import {
  CurriculumView,
  OverviewView,
  SchoolsView,
  UsersView,
  ValidationView,
} from "../../components/roles/admin/AdminRoleViews";
import { roleLabels } from "../../components/roles/admin/adminUtils";
import "../../styles/admin-console.css";

type AdminTab = "overview" | "users" | "schools" | "curriculum" | "validation";
type IconName = "grid" | "users" | "book" | "activity" | "shield" | "refresh" | "plus" | "check" | "search";

const tabs: Array<{ id: AdminTab; label: string; icon: IconName }> = [
  { id: "overview", label: "Tổng quan", icon: "grid" },
  { id: "users", label: "Người dùng", icon: "users" },
  { id: "schools", label: "Trường học", icon: "book" },
  { id: "curriculum", label: "Chương trình", icon: "book" },
  { id: "validation", label: "Validation", icon: "activity" },
];


export default function AdminConsole() {
  const user = usePhysliveStore(state => state.user);
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState<AdminTab>("overview");
  if (user?.role !== "ADMIN") {
    return <div className="admin-shell admin-shell-denied"><section className="admin-access-card">
      <div className="admin-access-icon"><LearningIcon name="shield" /></div>
      <p className="admin-eyebrow">PhysLive Admin</p><h1>Không thể truy cập</h1>
      <p>Bạn cần quyền quản trị viên để mở khu vực này.</p>
      <button type="button" className="admin-primary-button" onClick={() => navigate("/")}>Về trang chủ</button>
    </section></div>;
  }
  const current = tabs.find(tab => tab.id === activeTab) ?? tabs[0];
  const initials = user.fullName?.trim().slice(0, 1).toUpperCase() || "A";
  return <div className="admin-shell">
    <aside className="admin-sidebar">
      <button type="button" className="admin-brand" onClick={() => navigate("/")} aria-label="Về trang chủ PhysLive">
        <img src="/favicon.ico" alt="" aria-hidden="true" /><span className="admin-brand-copy"><strong>PhysLive</strong><small>Quản trị hệ thống</small></span>
      </button>
      <p className="admin-sidebar-label">Không gian quản trị</p>
      <nav className="admin-nav" aria-label="Điều hướng quản trị">{tabs.map(tab => <button key={tab.id} type="button" className={`admin-nav-item ${activeTab === tab.id ? "active" : ""}`} aria-current={activeTab === tab.id ? "page" : undefined} onClick={() => setActiveTab(tab.id)}><LearningIcon name={tab.icon} /><span>{tab.label}</span></button>)}</nav>
      <div className="admin-sidebar-footer"><div className="admin-sidebar-user"><span className="admin-user-avatar">{initials}</span><span className="admin-sidebar-user-copy"><strong>{user.fullName || "Quản trị viên"}</strong><small>{roleLabels[user.role] ?? user.role}</small></span></div><button type="button" className="admin-back-button" onClick={() => navigate("/")}>Về trang chủ</button></div>
    </aside>
    <main className="admin-main"><header className="admin-topbar"><div><span className="admin-topbar-kicker">PhysLive / Quản trị</span><strong>{current.label}</strong></div><div className="admin-topbar-user"><span className="admin-user-avatar">{initials}</span><span>{user.fullName || user.email}</span></div></header>
      {activeTab === "overview" && <OverviewView />}
      {activeTab === "users" && <UsersView />}
      {activeTab === "schools" && <SchoolsView />}
      {activeTab === "curriculum" && <CurriculumView />}
      {activeTab === "validation" && <ValidationView />}
    </main>
  </div>;
}
