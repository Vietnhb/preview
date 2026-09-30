import { useMemo } from "react";
import { Avatar, Button, IconButton } from "@radix-ui/themes";
import { Link, NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import LearningIcon from "../../components/common/LearningIcon";
import { usePhysliveStore } from "../../store/usePhysliveStore";
import { clearToken } from "../../utils/token";

const navigation = [
  { label: "Tổng quan", path: "/school", icon: "grid" },
  { label: "Tài khoản", path: "/school/users", icon: "users" },
  { label: "Lớp học", path: "/school/classes", icon: "book" },
  { label: "Báo cáo", path: "/school/reports", icon: "chart" },
  { label: "Mua / gia hạn gói", path: "/school/billing", icon: "activity" },
] as const;

const pageMeta: Record<string, { title: string; description: string }> = {
  "/school": { title: "Tổng quan", description: "Tình hình tài khoản, lớp học, license và quota của trường." },
  "/school/users": { title: "Tài khoản", description: "Quản lý giáo viên và học sinh thuộc trường." },
  "/school/classes": { title: "Lớp học", description: "Tạo lớp, phân công giáo viên và xếp học sinh." },
  "/school/reports": { title: "Báo cáo", description: "Theo dõi hoạt động và xuất dữ liệu của trường." },
  "/school/billing": { title: "Mua / gia hạn gói", description: "Chọn gói, tiếp tục thanh toán hoặc gia hạn license cho trường." },
};

export default function SchoolLayout() {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const user = usePhysliveStore(state => state.user);
  const setUser = usePhysliveStore(state => state.setUser);
  const meta = useMemo(() => pageMeta[pathname] ?? pageMeta["/school"], [pathname]);
  const initials = user?.fullName?.trim().slice(0, 1).toUpperCase() || "T";
  const visibleNavigation = user?.billingRequired
    ? navigation.filter(item => item.path === "/school/billing")
    : navigation;
  const schoolHome = user?.billingRequired ? "/school/billing" : "/school";

  const logout = () => {
    clearToken();
    setUser(null);
    navigate("/login", { replace: true });
  };

  return <div className="admin-shell school-shell">
    <aside className="admin-sidebar school-sidebar">
      <NavLink to={schoolHome} className="admin-brand" aria-label="Mở cổng quản lý trường">
        <img src="/favicon.ico" alt="" />
        <span className="admin-brand-copy"><strong>PhysLive</strong><small>SCHOOL</small></span>
      </NavLink>
      <p className="admin-sidebar-label">Không gian trường</p>
      <nav className="admin-nav" aria-label="Điều hướng quản lý trường">
        {visibleNavigation.map(item => <NavLink key={item.path} to={item.path} end={item.path === "/school"} className={({ isActive }) => `admin-nav-item ${isActive ? "active" : ""}`}>
          <LearningIcon name={item.icon} /><span>{item.label}</span>
        </NavLink>)}
      </nav>
      <div className="admin-sidebar-footer">
        <div className="admin-sidebar-user"><Avatar size="2" radius="full" color="cyan" src={user?.avatarUrl || undefined} fallback={initials} /><span className="admin-sidebar-user-copy"><strong>{user?.fullName || "Quản lý trường"}</strong><small>{user?.email || ""}</small></span></div>
        {!user?.billingRequired && <Link className="admin-back-button" to="/"><LearningIcon name="back" />Về trang PhysLive</Link>}
        <Button variant="ghost" color="gray" onClick={logout}><LearningIcon name="logout" />Đăng xuất</Button>
      </div>
    </aside>
    <main className="admin-main">
      <header className="admin-topbar school-topbar">
        <div className="admin-page-topbar-copy">
          <div className="admin-page-title-line"><strong>{meta.title}</strong><span className="admin-role-pill"><LearningIcon name="shield" />Quản lý trường</span></div>

        </div>
        <div className="admin-topbar-actions school-topbar-actions">
          {user?.billingRequired
            ? <span className="admin-topbar-user school-profile-link"><Avatar size="2" radius="full" color="cyan" src={user?.avatarUrl || undefined} fallback={initials} /><span>{user?.email || "Tài khoản trường"}</span></span>
            : <Link to="/profile" className="admin-topbar-user school-profile-link"><Avatar size="2" radius="full" color="cyan" src={user?.avatarUrl || undefined} fallback={initials} /><span>{user?.email || "Tài khoản trường"}</span></Link>}
          <IconButton variant="soft" color="gray" aria-label="Đăng xuất" onClick={logout}><LearningIcon name="logout" /></IconButton>
        </div>
      </header>
      <nav className="admin-mobile-nav" aria-label="Điều hướng quản lý trường">
        {visibleNavigation.map(item => <NavLink key={item.path} to={item.path} end={item.path === "/school"} className={({ isActive }) => `admin-nav-item ${isActive ? "active" : ""}`}>
          <LearningIcon name={item.icon} /><span>{item.label}</span>
        </NavLink>)}
      </nav>
      <Outlet />
    </main>
  </div>;
}
