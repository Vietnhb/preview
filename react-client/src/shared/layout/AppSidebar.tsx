import { useEffect, useRef, useState, type ReactNode } from "react";
import { Link, NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { AnimatePresence, motion, useReducedMotion } from "motion/react";
import LearningIcon from "../ui/LearningIcon";
import BrandMark from "../ui/BrandMark";
import { useSessionStore } from "../auth/sessionStore";
import { clearToken } from "../lib/token";
import { getRoleLabel } from "../auth/roles";
import ThemeToggle, { ThemeSegmented } from "../theme/ThemeToggle";
import "./AppSidebar.css";

type IconName = Parameters<typeof LearningIcon>[0]["name"];

export type SidebarItem = {
  to: string;
  label: string;
  icon: IconName;
  /** Match the path exactly (use for a section's index page). */
  end?: boolean;
  /** Count of things waiting; hidden when 0 or undefined. */
  badge?: number;
  /** Page heading when it should differ from the menu label. */
  title?: string;
};
export type SidebarGroup = { label?: string; items: SidebarItem[] };

type Props = {
  /** Unique per area so the sliding highlight does not jump between layouts. */
  id: string;
  /** Line under the product name, e.g. "Kiểm duyệt". */
  subtitle: string;
  home: string;
  groups: SidebarGroup[];
  /** Extra controls on the right of the top bar (notifications…). */
  actions?: ReactNode;
  /** Classes for the content column, so each area keeps its own page styles. */
  contentClassName?: string;
  shellClassName?: string;
  children?: ReactNode;
};

const STORAGE_KEY = "physlive.sidebar";
const readCollapsed = () => { try { return localStorage.getItem(STORAGE_KEY) === "collapsed"; } catch { return false; } };

function isActive(pathname: string, item: SidebarItem) {
  return item.end ? pathname === item.to : pathname === item.to || pathname.startsWith(`${item.to}/`);
}

/**
 * Application shell for signed-in areas: a collapsible sidebar with grouped
 * sections, count badges and a user menu, plus a slim top bar. Each role passes
 * its own groups; every item is a real route.
 */
export default function AppSidebarLayout({ id, subtitle, home, groups, actions, contentClassName = "", shellClassName = "", children }: Readonly<Props>) {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const reduced = useReducedMotion();
  const user = useSessionStore(state => state.user);
  const [collapsed, setCollapsed] = useState(readCollapsed);
  // The drawer and the user menu belong to the page they were opened on; navigating closes them.
  const [drawerPath, setDrawerPath] = useState<string | null>(null);
  const [menuPath, setMenuPath] = useState<string | null>(null);
  const drawerOpen = drawerPath === pathname;
  const menuOpen = menuPath === pathname;
  const menuRef = useRef<HTMLDivElement>(null);

  const toggleCollapsed = () => setCollapsed(value => {
    try { localStorage.setItem(STORAGE_KEY, value ? "expanded" : "collapsed"); } catch { /* keep for this visit */ }
    return !value;
  });

  useEffect(() => {
    const keys = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "b") { event.preventDefault(); toggleCollapsed(); }
      if (event.key === "Escape") { setDrawerPath(null); setMenuPath(null); }
    };
    document.addEventListener("keydown", keys);
    return () => document.removeEventListener("keydown", keys);
  }, []);

  useEffect(() => {
    if (!menuOpen) return;
    const outside = (event: PointerEvent) => { if (!menuRef.current?.contains(event.target as Node)) setMenuPath(null); };
    document.addEventListener("pointerdown", outside);
    return () => document.removeEventListener("pointerdown", outside);
  }, [menuOpen]);

  const logout = () => {
    clearToken();
    useSessionStore.getState().setUser(null);
    navigate("/login", { replace: true });
  };

  const items = groups.flatMap(group => group.items);
  const current = items.filter(item => isActive(pathname, item)).sort((a, b) => b.to.length - a.to.length)[0];
  const initials = user?.fullName?.trim().slice(0, 1).toUpperCase() || "U";
  const spring = reduced ? { duration: 0 } : { type: "spring" as const, stiffness: 420, damping: 36 };

  return (
    <div className={`app-shell ${shellClassName}`} data-collapsed={collapsed || undefined} data-drawer={drawerOpen || undefined}>
      <button type="button" className="app-scrim" aria-label="Đóng menu" tabIndex={drawerOpen ? 0 : -1} onClick={() => setDrawerPath(null)} />
      <aside className="app-sidebar" aria-label={`Điều hướng ${subtitle}`}>
        <div className="app-sidebar__head">
          <Link to={home} className="app-sidebar__brand" aria-label={`PhysLive — ${subtitle}`}>
            <BrandMark size={30} wordmark={false} />
            <span className="app-sidebar__brand-copy"><strong>PhysLive</strong><small>{subtitle}</small></span>
          </Link>
        </div>

        <nav className="app-sidebar__nav">
          {groups.map((group, index) => (
            <div key={group.label ?? index} className="app-sidebar__group">
              {group.label && <p className="app-sidebar__label">{group.label}</p>}
              {group.items.map(item => {
                const active = current?.to === item.to;
                return (
                  <NavLink key={item.to} to={item.to} end={item.end} className="app-sidebar__item" data-active={active || undefined} aria-current={active ? "page" : undefined} title={collapsed ? item.label : undefined}>
                    {active && <motion.span layoutId={`app-sidebar-pill-${id}`} className="app-sidebar__pill" aria-hidden="true" transition={spring} />}
                    <LearningIcon name={item.icon} />
                    <span className="app-sidebar__text">{item.label}</span>
                    {Boolean(item.badge) && <span className="app-sidebar__badge" aria-label={`${item.badge} mục đang chờ`}>{item.badge! > 99 ? "99+" : item.badge}</span>}
                  </NavLink>
                );
              })}
            </div>
          ))}
        </nav>

        <div className="app-sidebar__foot" ref={menuRef}>
          <AnimatePresence>
            {menuOpen && (
              <motion.div className="app-user-menu" role="menu" aria-label="Tài khoản"
                initial={reduced ? { opacity: 0 } : { opacity: 0, y: 8, scale: 0.97 }} animate={{ opacity: 1, y: 0, scale: 1 }}
                exit={reduced ? { opacity: 0 } : { opacity: 0, y: 6, scale: 0.98 }} transition={{ duration: 0.16, ease: [0.22, 1, 0.36, 1] }}>
                <div className="app-user-menu__who"><strong>{user?.fullName || "Tài khoản"}</strong><small>{user?.email}</small></div>
                <Link to="/profile" role="menuitem" className="app-user-menu__item"><LearningIcon name="users" />Hồ sơ cá nhân</Link>
                <div className="app-user-menu__theme"><span>Giao diện</span><ThemeSegmented /></div>
                <button type="button" role="menuitem" className="app-user-menu__item app-user-menu__item--danger" onClick={logout}><LearningIcon name="logout" />Đăng xuất</button>
              </motion.div>
            )}
          </AnimatePresence>
          <button type="button" className="app-sidebar__user" aria-haspopup="menu" aria-expanded={menuOpen} onClick={() => setMenuPath(menuOpen ? null : pathname)} title={collapsed ? user?.fullName : undefined}>
            <span className="app-sidebar__avatar" aria-hidden="true">{user?.avatarUrl ? <img src={user.avatarUrl} alt="" /> : initials}</span>
            <span className="app-sidebar__user-copy"><strong>{user?.fullName || "Tài khoản"}</strong><small>{getRoleLabel(user?.role ?? "")}</small></span>
            <svg className="app-sidebar__caret" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m8 9 4-4 4 4M8 15l4 4 4-4" /></svg>
          </button>
        </div>
      </aside>

      <div className="app-main">
        <header className="app-topbar">
          <button type="button" className="app-topbar__toggle app-topbar__toggle--desktop" onClick={toggleCollapsed} aria-label={collapsed ? "Mở rộng thanh bên" : "Thu gọn thanh bên"} aria-pressed={collapsed} title="Thu gọn / mở rộng (Ctrl+B)"><LearningIcon name="panel" /></button>
          <button type="button" className="app-topbar__toggle app-topbar__toggle--mobile" onClick={() => setDrawerPath(drawerOpen ? null : pathname)} aria-label="Mở menu" aria-expanded={drawerOpen}><LearningIcon name="menu" /></button>
          <span className="app-topbar__divider" aria-hidden="true" />
          <p className="app-topbar__title"><span>{subtitle}</span>{current && <><span aria-hidden="true">/</span><strong>{current.title ?? current.label}</strong></>}</p>
          <div className="app-topbar__actions">{actions}<ThemeToggle /></div>
        </header>
        <div className={`app-content ${contentClassName}`}>{children ?? <Outlet />}</div>
      </div>
    </div>
  );
}
