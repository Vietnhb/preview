import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import "../../styles/NavBar.css";
import { clearToken } from "../../utils/token";
import { usePhysliveStore } from "../../store/usePhysliveStore";
import LearningIcon from "./LearningIcon";

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

const navItems: NavItem[] = [
  { to: "/", label: "Trang chủ", icon: "grid" },
  { to: "/about", label: "Giới thiệu", icon: "folder" },
  { to: "/terms", label: "Điều khoản", icon: "book" },
  { to: "/player", label: "Workspace", icon: "grid" },
];

const workspaceRoles = new Set(["TEACHER", "REVIEWER", "ADMIN"]);

function NavItemLink({
  item,
  onClick,
}: {
  item: NavItem;
  onClick?: () => void;
}) {
  return (
    <Link to={item.to} onClick={onClick} className="learning-nav-link">
      <LearningIcon name={item.icon} />
      {item.label}
    </Link>
  );
}

function ThemeMenu({ onClose }: { onClose: () => void }) {
  const setTheme = (theme: "light" | "dark") => {
    document.documentElement.dataset.theme = theme;
    document.documentElement.dataset.themeEffective = theme;
    try {
      window.localStorage.setItem("physlive.theme", theme);
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
  const [themeOpen, setThemeOpen] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const logout = () => {
    clearToken();
    setUser(null);
    setMobileOpen(false);
    navigate("/login");
  };
  const visibleItems = navItems.filter(
    (item) =>
      item.to !== "/player" || workspaceRoles.has(user?.role ?? ""),
  );

  return (
    <nav className="learning-navbar">
      <div className="learning-nav-container">
        <div className="learning-nav-inner">
          <Link
            to="/"
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
            {(user?.role === "REVIEWER" || user?.role === "ADMIN") && (
              <NavItemLink
                item={{ to: "/reviewer", label: "Thẩm định", icon: "settings" }}
              />
            )}
            {user?.role === "ADMIN" && (
              <NavItemLink
                item={{ to: "/admin", label: "Quản trị", icon: "settings" }}
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
                      {user.fullName?.slice(0, 1).toUpperCase() || "U"}
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
                    Đăng ký
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
          {user?.role === "REVIEWER" || user?.role === "ADMIN" ? (
            <NavItemLink
              item={{ to: "/reviewer", label: "Thẩm định", icon: "settings" }}
              onClick={() => setMobileOpen(false)}
            />
          ) : null}
          {user?.role === "ADMIN" ? (
            <NavItemLink
              item={{ to: "/admin", label: "Quản trị", icon: "settings" }}
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
                item={{ to: "/signup", label: "Đăng ký", icon: "plus" }}
                onClick={() => setMobileOpen(false)}
              />
            </>
          )}
        </div>
      )}
    </nav>
  );
}
