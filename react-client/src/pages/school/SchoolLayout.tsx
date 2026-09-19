import { useMemo } from "react";
import { Link, NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import LearningIcon from "../../components/common/LearningIcon";
import { usePhysliveStore } from "../../store/usePhysliveStore";
import { clearToken } from "../../utils/token";

const navigation = [
  { label: "Tổng quan", path: "/school", icon: "grid" },
  { label: "Tài khoản", path: "/school/users", icon: "users" },
  { label: "Lớp học", path: "/school/classes", icon: "book" },
  { label: "Báo cáo", path: "/school/reports", icon: "chart" },
  { label: "Gói & thanh toán", path: "/school/billing", icon: "activity" },
] as const;

const pageMeta: Record<string, { title: string; description: string }> = {
  "/school": { title: "Tổng quan", description: "Tình hình tài khoản, lớp học, license và quota của trường." },
  "/school/users": { title: "Tài khoản", description: "Quản lý giáo viên và học sinh thuộc trường." },
  "/school/classes": { title: "Lớp học", description: "Tạo lớp, phân công giáo viên và xếp học sinh." },
  "/school/reports": { title: "Báo cáo", description: "Theo dõi hoạt động và xuất dữ liệu của trường." },
  "/school/billing": { title: "Gói & thanh toán", description: "Quản lý license, quota và lịch sử thanh toán." },
};

export default function SchoolLayout() {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const user = usePhysliveStore(state => state.user);
  const setUser = usePhysliveStore(state => state.setUser);
  const meta = useMemo(() => pageMeta[pathname] ?? pageMeta["/school"], [pathname]);
  const initials = user?.fullName?.trim().slice(0, 1).toUpperCase() || "T";

  const logout = () => {
    clearToken();
    setUser(null);
    navigate("/login", { replace: true });
  };

  return <div className="admin-shell school-shell">
    <aside className="admin-sidebar school-sidebar">
      <NavLink to="/school" className="admin-brand" aria-label="Mở tổng quan trường">
        <img src="/favicon.ico" alt="" />
        <span className="admin-brand-copy"><strong>PhysLive</strong><small>Cổng quản lý trường</small></span>
      </NavLink>
      <p className="admin-sidebar-label">Không gian trường</p>
      <nav className="admin-nav" aria-label="Điều hướng quản lý trường">
        {navigation.map(item => <NavLink key={item.path} to={item.path} end={item.path === "/school"} className={({ isActive }) => `admin-nav-item ${isActive ? "active" : ""}`}>
          <LearningIcon name={item.icon} /><span>{item.label}</span>
        </NavLink>)}
      </nav>
      <div className="admin-sidebar-footer">
        <div className="admin-sidebar-user"><span className="admin-user-avatar">{initials}</span><span className="admin-sidebar-user-copy"><strong>{user?.fullName || "Quản lý trường"}</strong><small>{user?.email || ""}</small></span></div>
        <Link className="admin-back-button" to="/"><LearningIcon name="back" />Về trang PhysLive</Link>
        <button type="button" className="admin-back-button" onClick={logout}><LearningIcon name="logout" />Đăng xuất</button>
      </div>
    </aside>
    <main className="admin-main">
      <header className="admin-topbar school-topbar">
        <div className="admin-page-topbar-copy">
          <div className="admin-page-title-line"><strong>{meta.title}</strong><span className="admin-role-pill"><LearningIcon name="shield" />Quản lý trường</span></div>
          <span>{meta.description}</span>
        </div>
        <div className="admin-topbar-actions school-topbar-actions">
          <Link to="/profile" className="admin-topbar-user school-profile-link"><span className="admin-user-avatar">{initials}</span><span>{user?.email || "Tài khoản trường"}</span></Link>
          <button type="button" className="admin-icon-button school-topbar-logout" aria-label="Đăng xuất" title="Đăng xuất" onClick={logout}><LearningIcon name="logout" /></button>
        </div>
      </header>
      <nav className="admin-mobile-nav" aria-label="Điều hướng quản lý trường">
        {navigation.map(item => <NavLink key={item.path} to={item.path} end={item.path === "/school"} className={({ isActive }) => `admin-nav-item ${isActive ? "active" : ""}`}>
          <LearningIcon name={item.icon} /><span>{item.label}</span>
        </NavLink>)}
      </nav>
      <Outlet />
    </main>
  </div>;
}
