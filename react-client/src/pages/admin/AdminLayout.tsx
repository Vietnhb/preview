import { useState } from "react";
import { NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import LearningIcon from "../../components/common/LearningIcon";
import { usePhysliveStore } from "../../store/usePhysliveStore";
import { clearToken } from "../../utils/token";

type AdminNavItem = { label: string; path: string; icon: "grid" | "users" | "message" | "book" | "activity" };

const navigation: AdminNavItem[] = [
  { label: "Dashboard", path: "/admin", icon: "grid" },
  { label: "Users", path: "/admin/users", icon: "users" },
  { label: "Feedback", path: "/admin/feedback", icon: "message" },
  { label: "Messages", path: "/admin/messages", icon: "message" },
  { label: "Schools", path: "/admin/schools", icon: "book" },
  { label: "Curriculum", path: "/admin/curriculum", icon: "book" },
  { label: "Validation", path: "/admin/validation", icon: "activity" },
];

const pageMeta: Record<string, { title: string; description: string }> = {
  "/admin": { title: "Dashboard", description: "Welcome to your admin dashboard" },
  "/admin/users": { title: "User Management", description: "Manage all users and their roles" },
  "/admin/feedback": { title: "Feedback", description: "Review feedback from PhysLive users" },
  "/admin/messages": { title: "Messages", description: "Manage conversations with users" },
  "/admin/schools": { title: "Schools", description: "Manage schools and their accounts" },
  "/admin/curriculum": { title: "Curriculum", description: "Manage the PhysLive curriculum" },
  "/admin/validation": { title: "Validation", description: "Review solver validation runs" },
};

export default function AdminLayout() {
  const user = usePhysliveStore(state => state.user);
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const [profileOpen, setProfileOpen] = useState(false);
  const meta = pageMeta[pathname] ?? pageMeta["/admin"];
  const initials = user?.fullName?.trim().slice(0, 1).toUpperCase() || "A";

  const logout = () => {
    clearToken();
    usePhysliveStore.getState().setUser(null);
    navigate("/login", { replace: true });
  };

  return <div className="admin-shell">
    <aside className="admin-sidebar">
      <NavLink to="/admin" className="admin-brand" aria-label="Open admin dashboard">
        <LearningIcon name="settings" />
        <span className="admin-brand-copy"><strong>Admin Panel</strong><small>PhysLive</small></span>
      </NavLink>
      <nav className="admin-nav" aria-label="Admin navigation">
        {navigation.map(item => <NavLink key={item.path} to={item.path} end={item.path === "/admin"} className={({ isActive }) => `admin-nav-item ${isActive ? "active" : ""}`}>
          <LearningIcon name={item.icon} /><span>{item.label}</span>
        </NavLink>)}
      </nav>
      <div className="admin-sidebar-footer">
        <div className="admin-sidebar-user"><span className="admin-user-avatar">{initials}</span><span className="admin-sidebar-user-copy"><strong>{user?.fullName || "Administrator"}</strong><small>{user?.email || ""}</small></span></div>
        <button type="button" className="admin-back-button" onClick={() => navigate("/")}><LearningIcon name="back" />Back to Site</button>
      </div>
    </aside>
    <main className="admin-main">
      <header className="admin-topbar">
        <div className="admin-page-topbar-copy">
          <div className="admin-page-title-line"><strong>{meta.title}</strong><span className="admin-role-pill"><LearningIcon name="shield" />Admin</span></div>
          <span>{meta.description}</span>
        </div>
        <div className="admin-topbar-actions">
          <button type="button" className="admin-notification-button" aria-label="Notifications"><LearningIcon name="bell" /></button>
          <div className="admin-profile-wrap">
            <button type="button" className="admin-topbar-user" aria-expanded={profileOpen} onClick={() => setProfileOpen(value => !value)}>
              <span className="admin-user-avatar">{initials}</span><span>{user?.email || "admin@physlive.local"}</span><span className="admin-user-chevron">⌄</span>
            </button>
            {profileOpen && <div className="admin-profile-menu" role="menu">
              <strong>Admin account</strong>
              <button type="button" onClick={() => navigate("/profile")}>Profile</button>
              <button type="button" onClick={() => navigate("/")}>Back to site</button>
              <button type="button" className="danger" onClick={logout}>Sign out</button>
            </div>}
          </div>
        </div>
      </header>
      <Outlet />
    </main>
  </div>;
}
