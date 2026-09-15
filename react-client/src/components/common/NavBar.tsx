import { Link, NavLink, useNavigate } from "react-router-dom";
import type { ReactNode } from "react";
import "../styles/NavBar.css";
import { clearToken, getToken } from "../utils/token";
import { usePhysliveStore } from "../store/usePhysliveStore";

type IconName = "studio" | "player" | "workspace" | "library" | "assignments" | "curriculum" | "reviewer" | "admin";

function NavIcon({ name }: { name: IconName }) {
    const paths: Record<IconName, string> = {
        studio: "M4 4h16v16H4z M8 16l3-4 2 2 3-5 2 3",
        player: "M8 5l11 7-11 7V5z",
        workspace: "M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z",
        library: "M4 5.5A2.5 2.5 0 0 1 6.5 3H20v17H6.5A2.5 2.5 0 0 1 4 17.5v-12z M4 6h16",
        assignments: "M7 3h10v4H7z M5 7h14v14H5z M8 11h8 M8 15h6",
        curriculum: "M4 5h6a3 3 0 0 1 3 3v11a3 3 0 0 0-3-3H4z M20 5h-6a3 3 0 0 0-3 3v11a3 3 0 0 1 3-3h6z",
        reviewer: "M12 3l7 3v5c0 4.5-3 8-7 10-4-2-7-5.5-7-10V6l7-3z M9 12l2 2 4-4",
        admin: "M12 3l2 2.5 3.2-.3.8 3.1 2.8 1.6-1.6 2.8.8 3.1-3.2.3L12 19l-2-2.5-3.2.3-.8-3.1L3.2 12l1.6-2.8L4 6.1l3.2.3L9 3.9z M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z"
    };
    return <svg className="nav-icon" viewBox="0 0 24 24" aria-hidden="true"><path d={paths[name]} /></svg>;
}

function SidebarLink({ to, name, children }: { to: string; name: IconName; children: ReactNode }) {
    return <NavLink className={({ isActive }) => `sidebar-link${isActive ? " active" : ""}`} to={to}><NavIcon name={name} /><span>{children}</span></NavLink>;
}

function NavBar() {
    const navigate = useNavigate();
    const user = usePhysliveStore((state) => state.user);
    const token = getToken();
    const logout = () => { clearToken(); navigate("/login"); window.location.reload(); };

    return <>
        <header className="navbar"><div className="topbar-context"><span className="topbar-kicker">PHYSLIVE / STUDIO</span><span className="topbar-title">Interactive physics workspace</span></div><div className="topbar-actions">{user && <span className="user-chip"><span className="user-avatar">{user.fullName.slice(0, 1).toUpperCase()}</span>{user.fullName}</span>}{!token ? <NavLink className="topbar-login" to="/login">Đăng nhập</NavLink> : <button className="topbar-logout" onClick={logout}>Đăng xuất</button>}</div></header>
        <aside className="sidebar"><Link className="brand" to="/"><div className="studio-brand-badge">⚛️</div><div className="brand-text"><span className="brand-title">PhysLive</span><span className="brand-subtitle">Interactive Studio</span></div></Link><div className="sidebar-rule" /><nav className="sidebar-nav" aria-label="Điều hướng chính"><span className="sidebar-label">Làm việc</span><SidebarLink to="/workspace" name="workspace">Workspace</SidebarLink>{token && <><SidebarLink to="/library" name="library">Thư viện</SidebarLink><SidebarLink to="/curriculum" name="curriculum">Chương trình</SidebarLink></>}{(user?.role === "REVIEWER" || user?.role === "ADMIN") && <><span className="sidebar-label sidebar-label-spaced">Review</span><SidebarLink to="/reviewer" name="reviewer">Kiểm duyệt nội dung</SidebarLink></>}{user?.role === "ADMIN" && <><span className="sidebar-label sidebar-label-spaced">System</span><SidebarLink to="/admin" name="admin">Quản trị</SidebarLink></>}</nav><div className="sidebar-footer"><span className="sidebar-footer-dot" />Validated physics<br /><span>OpenRouter + solver engine</span></div></aside>
    </>;
}
export default NavBar;
