import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import "../../styles/NavBar.css";
import { clearToken } from "../../utils/token";
import { usePhysliveStore } from "../../store/usePhysliveStore";
import LearningIcon from "./LearningIcon";
import RoleNavigation from "./RoleNavigation";
import { canManageLearning, canReviewContent, canViewUsers, isAdminRole, isStudentRole, ROLE_NAMES, roleHome } from "../../types/roles";

type NavItem = {
  to: string;
  label: string;
  icon:
    | "grid"
    | "play"
    | "book"
    | "folder"
    | "settings"
    | "back"
    | "login"
    | "logout"
    | "menu"
    | "plus";
};

const publicNavItems: NavItem[] = [
  { to: "/", label: "Trang chủ", icon: "grid" },
  { to: "/community", label: "Cộng đồng", icon: "play" },
  { to: "/about", label: "Giới thiệu", icon: "folder" },
  { to: "/terms", label: "Điều khoản", icon: "book" },
];

const studentNavItem: NavItem = { to: "/assignments", label: "Học tập", icon: "book" };

function NavItemLink({
  item,
  onClick,
}: Readonly<{
  item: NavItem;
  onClick?: () => void;
}>) {
  return (
    <Link to={item.to} onClick={onClick} className="learning-nav-link">
      <LearningIcon name={item.icon} />
      {item.label}
    </Link>
  );
}

function ThemeMenu({ onClose }: Readonly<{ onClose: () => void }>) {
  const setTheme = (theme: "light" | "dark") => {
    document.documentElement.dataset.theme = theme;
    document.documentElement.dataset.themeEffective = theme;
    try {
      globalThis.localStorage.setItem("physlive.theme", theme);
    } catch {
      /* storage may be unavailable */
    }
    onClose();
  };
  return (
    <div className="learning-theme-menu" role="menu">
      <button type="button" role="menuitem" onClick={() => setTheme("light")}>
        <LearningIcon name="sun" />
        Sáng
      </button>
      <button type="button" role="menuitem" onClick={() => setTheme("dark")}>
        <LearningIcon name="moon" />
        Tối
      </button>
    </div>
  );
}

export default function NavBar() {
  const navigate = useNavigate();
  const user = usePhysliveStore((state) => state.user);
  const setUser = usePhysliveStore((state) => state.setUser);
  const isAdmin = canViewUsers(user?.role);
  const [themeOpen, setThemeOpen] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const logout = () => {
    clearToken();
    setUser(null);
    setMobileOpen(false);
    navigate("/login");
  };
  const compactNavigation = Boolean(user && user.role !== ROLE_NAMES.STAFF);
  const visibleItems = compactNavigation ? [] : [...publicNavItems];
  if (!isAdmin && canManageLearning(user?.role)) {
    visibleItems.push({ to: "/workspace", label: "Workspace", icon: "grid" });
  }
  if (isStudentRole(user?.role)) visibleItems.push(studentNavItem);
  if (user?.role === ROLE_NAMES.STAFF && user.staffType === "DEPARTMENT_HEAD") {
    visibleItems.push({ to: "/department", label: "Quản lý chuyên môn", icon: "settings" });
  }
  if (user?.role === ROLE_NAMES.SCHOOL) {
    visibleItems.push({ to: "/school", label: "Quản lý trường", icon: "settings" });
  }
  if (compactNavigation && canReviewContent(user?.role) && !isAdmin) visibleItems.push({ to: "/reviewer", label: "Kiểm duyệt", icon: "settings" });
  if (compactNavigation && canViewUsers(user?.role)) visibleItems.push({ to: roleHome(user?.role), label: isAdminRole(user?.role) ? "Tài khoản" : "Vận hành", icon: "settings" });
  if (user && compactNavigation && !isAdminRole(user.role)) visibleItems.push({ to: "/community", label: "Cộng đồng", icon: "play" });

  if (user && compactNavigation) return <RoleNavigation user={user} items={visibleItems} onLogout={logout} />;

  return (
    <nav className="learning-navbar">
      <div className="learning-nav-container">
        <div className="learning-nav-inner">
          <Link
            to={compactNavigation ? roleHome(user?.role) : "/"}
            className="learning-brand"
            onClick={() => setMobileOpen(false)}
          >
            <img src="/favicon.ico" alt="" aria-hidden="true" />
            <span>PhysLive</span>
          </Link>
          <div className="learning-desktop-nav">
            {visibleItems.map((item) => (
              <NavItemLink key={item.to} item={item} />
            ))}
            {!compactNavigation && canReviewContent(user?.role) && !isAdmin && (
              <NavItemLink
                item={{ to: "/reviewer", label: "Kiểm duyệt", icon: "settings" }}
              />
            )}
            {!compactNavigation && canViewUsers(user?.role) && (
              <NavItemLink
                item={{ to: roleHome(user?.role), label: isAdminRole(user?.role) ? "Tài khoản" : "Vận hành", icon: "settings" }}
              />
            )}
            <div className="learning-theme-anchor">
              <button
                type="button"
                className="learning-icon-button"
                title="Chọn theme"
                aria-label="Chọn theme"
                aria-haspopup="menu"
                aria-expanded={themeOpen}
                onClick={() => setThemeOpen((open) => !open)}
              >
                <LearningIcon name="sun" />
                <span className="sr-only">Chọn theme</span>
              </button>
              {themeOpen && <ThemeMenu onClose={() => setThemeOpen(false)} />}
            </div>
            <div className="learning-auth-divider">
              {user ? (
                <div className="learning-auth-user">
                  <Link to="/profile" className="learning-profile-link">
                    <span className="learning-avatar">
{user.avatarUrl ? <img src={user.avatarUrl} alt="" style={{ width: "100%", height: "100%", objectFit: "cover", borderRadius: "50%" }} /> : user.fullName?.slice(0, 1).toUpperCase() || "U"}
                    </span>
                    <span className="learning-profile-name">
                      {user.fullName}
                    </span>
                  </Link>
                  <button
                    type="button"
                    className="learning-auth-button"
                    onClick={logout}
                  >
                    <LearningIcon name="logout" />
                    Đăng xuất
                  </button>
                </div>
              ) : (
                <div className="learning-auth-actions">
                  <Link to="/login" className="learning-auth-button">
                    <LearningIcon name="login" />
                    Đăng nhập
                  </Link>
                  <Link
                    to="/signup"
                    className="learning-auth-button learning-auth-button-primary"
                  >
                    Liên hệ trường
                  </Link>
                </div>
              )}
            </div>
          </div>
          <div className="learning-mobile-nav">
            <div className="learning-theme-anchor">
              <button
                type="button"
                className="learning-icon-button"
                title="Chọn theme"
                aria-label="Chọn theme"
                aria-haspopup="menu"
                aria-expanded={themeOpen}
                onClick={() => setThemeOpen((open) => !open)}
              >
                <LearningIcon name="sun" />
              </button>
              {themeOpen && <ThemeMenu onClose={() => setThemeOpen(false)} />}
            </div>
            <button
              type="button"
              className="learning-mobile-menu-button"
              aria-label="Mở menu điều hướng"
              aria-expanded={mobileOpen}
              onClick={() => setMobileOpen((open) => !open)}
            >
              <LearningIcon name="menu" />
            </button>
          </div>
        </div>
      </div>
      {mobileOpen && (
        <div className="learning-mobile-menu">
          <div className="learning-mobile-menu-title">Menu</div>
          {visibleItems.map((item) => (
            <NavItemLink
              key={item.to}
              item={item}
              onClick={() => setMobileOpen(false)}
            />
          ))}
          {!compactNavigation && canReviewContent(user?.role) && !isAdmin ? (
            <NavItemLink
              item={{ to: "/reviewer", label: "Kiểm duyệt", icon: "settings" }}
              onClick={() => setMobileOpen(false)}
            />
          ) : null}
          {!compactNavigation && canViewUsers(user?.role) ? (
            <NavItemLink
              item={{ to: roleHome(user?.role), label: isAdminRole(user?.role) ? "Tài khoản" : "Vận hành", icon: "settings" }}
              onClick={() => setMobileOpen(false)}
            />
          ) : null}
          <div className="learning-mobile-separator" />
          {user ? (
            <>
              <NavItemLink
                item={{ to: "/profile", label: "Hồ sơ", icon: "settings" }}
                onClick={() => setMobileOpen(false)}
              />
              <button
                type="button"
                className="learning-mobile-logout"
                onClick={logout}
              >
                <LearningIcon name="logout" />
                Đăng xuất
              </button>
            </>
          ) : (
            <>
              <NavItemLink
                item={{ to: "/login", label: "Đăng nhập", icon: "login" }}
                onClick={() => setMobileOpen(false)}
              />
              <NavItemLink
                item={{ to: "/signup", label: "Liên hệ trường", icon: "plus" }}
                onClick={() => setMobileOpen(false)}
              />
            </>
          )}
        </div>
      )}
    </nav>
  );
}
