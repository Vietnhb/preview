import { useState } from "react";
import { Link, NavLink, useLocation, useNavigate } from "react-router-dom";
import "./NavBar.css";
import { clearToken } from "../lib/token";
import { useSessionStore } from "../auth/sessionStore";
import type { User } from "../auth/types";
import LearningIcon from "../ui/LearningIcon";
import BrandMark from "../ui/BrandMark";
import { ActivePill } from "../effects/Motion";
import { canReviewContent, canViewUsers, isAdminRole, isStudentRole, ROLE_NAMES, roleHome } from "../auth/roles";
import { canTeach, isDepartmentHead, userHome } from "../auth/permissions";

type NavItem = { to: string; label: string; end?: boolean };

const publicItems: NavItem[] = [
  { to: "/", label: "Trang chủ", end: true },
  { to: "/community", label: "Cộng đồng" },
  { to: "/about", label: "Giới thiệu" },
  { to: "/terms", label: "Điều khoản" },
];

function itemsFor(user: User | null): NavItem[] {
  if (!user) return publicItems;
  const items: NavItem[] = [];
  if (isStudentRole(user.role)) items.push({ to: "/assignments", label: "Học tập" });
  if (canTeach(user) && !canViewUsers(user.role)) {
    items.push({ to: "/workspace", label: "Workspace" }, { to: "/assignments/workspace", label: "Giao bài" });
  }
  if (isDepartmentHead(user)) items.push({ to: "/department", label: "Quản lý chuyên môn" });
  if (user.role === ROLE_NAMES.SCHOOL) items.push({ to: "/school", label: "Quản lý trường" });
  if (canReviewContent(user.role) && !canViewUsers(user.role)) items.push({ to: "/reviewer", label: "Kiểm duyệt" });
  if (canViewUsers(user.role)) items.push({ to: roleHome(user.role), label: isAdminRole(user.role) ? "Tài khoản" : "Vận hành" });
  if (!isAdminRole(user.role)) items.push({ to: "/community", label: "Cộng đồng" });
  return items;
}

function Avatar({ user }: Readonly<{ user: User }>) {
  return (
    <span className="site-nav__avatar" aria-hidden="true">
      {user.avatarUrl ? <img src={user.avatarUrl} alt="" /> : (user.fullName?.trim().slice(0, 1).toUpperCase() || "U")}
    </span>
  );
}

export default function NavBar() {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const user = useSessionStore((state) => state.user);
  const setUser = useSessionStore((state) => state.setUser);
  // The menu belongs to the page it was opened on, so navigating closes it without an effect.
  const [menuPath, setMenuPath] = useState<string | null>(null);
  const menuOpen = menuPath === pathname;
  const setMenuOpen = (open: boolean) => setMenuPath(open ? pathname : null);
  const items = itemsFor(user);
  const tone = pathname === "/" ? "site-nav site-nav--dark" : "site-nav";

  const logout = () => {
    clearToken();
    setUser(null);
    navigate("/login");
  };

  return (
    <header className={tone}>
      <div className="site-nav__inner">
        <Link to={userHome(user)} className="site-nav__brand" aria-label="PhysLive">
          <BrandMark size={30} />
        </Link>
        <nav className="site-nav__links" aria-label="Điều hướng chính">
          {items.map((item) => (
            <NavLink key={item.to} to={item.to} end={item.end} className="site-nav__link">
              {({ isActive }) => <>{isActive && <ActivePill id="site-nav-pill" />}<span>{item.label}</span></>}
            </NavLink>
          ))}
        </nav>
        <div className="site-nav__account">
          {user ? (
            <>
              <Link to="/profile" className="site-nav__profile" title="Hồ sơ cá nhân">
                <Avatar user={user} />
                <span>{user.fullName}</span>
              </Link>
              <button type="button" className="site-nav__icon" onClick={logout} aria-label="Đăng xuất" title="Đăng xuất">
                <LearningIcon name="logout" />
              </button>
            </>
          ) : (
            <>
              <Link to="/login" className="site-nav__button">Đăng nhập</Link>
              <Link to="/signup" className="site-nav__button site-nav__button--primary">Đăng ký cho trường</Link>
            </>
          )}
        </div>
        <button
          type="button"
          className="site-nav__icon site-nav__menu-toggle"
          aria-label={menuOpen ? "Đóng menu" : "Mở menu"}
          aria-expanded={menuOpen}
          onClick={() => setMenuOpen(!menuOpen)}
        >
          <LearningIcon name={menuOpen ? "close" : "menu"} />
        </button>
      </div>
      {menuOpen && (
        <div className="site-nav__sheet">
          {items.map((item) => (
            <NavLink key={item.to} to={item.to} end={item.end} className="site-nav__sheet-link">
              {item.label}
            </NavLink>
          ))}
          <div className="site-nav__sheet-divider" />
          {user ? (
            <>
              <Link to="/profile" className="site-nav__sheet-link">Hồ sơ cá nhân</Link>
              <button type="button" className="site-nav__sheet-link site-nav__sheet-logout" onClick={logout}>Đăng xuất</button>
            </>
          ) : (
            <>
              <Link to="/login" className="site-nav__sheet-link">Đăng nhập</Link>
              <Link to="/signup" className="site-nav__button site-nav__button--primary">Đăng ký cho trường</Link>
            </>
          )}
        </div>
      )}
    </header>
  );
}
