import { useEffect, useRef, useState } from "react";
import { Link, useLocation } from "react-router-dom";
import Icon from "./LearningIcon";

type ThemeMode = "light" | "dark" | "system";
type HeaderVariant = "workspace" | "submissions";

export type LearningHeaderProps = {
  variant?: HeaderVariant;
  onNewSimulation?: () => void;
  viewMode?: "2d" | "3d";
  threeDEnabled?: boolean;
  onViewModeChange?: (mode: "2d" | "3d") => void;
  libraryCollapsed?: boolean;
  onToggleLibrary?: () => void;
};

export default function LearningHeader({
  variant = "workspace",
  onNewSimulation,
  viewMode = "2d",
  threeDEnabled = false,
  onViewModeChange,
  libraryCollapsed = false,
  onToggleLibrary,
}: Readonly<LearningHeaderProps>) {
  const pathname = useLocation().pathname;
  const [settingsOpen, setSettingsOpen] = useState(false);
  const settingsRef = useRef<HTMLDivElement>(null);
  const [theme, setTheme] = useState<ThemeMode>(() => {
    try {
      const stored = globalThis.localStorage.getItem("physlive.theme");
      return stored === "light" || stored === "dark" || stored === "system" ? stored : "system";
    } catch {
      return "system";
    }
  });

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    const media = globalThis.matchMedia("(prefers-color-scheme: dark)");
    const applyEffectiveTheme = () => {
      document.documentElement.dataset.themeEffective = theme === "dark" || (theme === "system" && media.matches) ? "dark" : "light";
    };
    applyEffectiveTheme();
    if (theme === "system") media.addEventListener("change", applyEffectiveTheme);
    try {
      globalThis.localStorage.setItem("physlive.theme", theme);
    } catch {
      // Storage can be unavailable in private contexts.
    }
    return () => media.removeEventListener("change", applyEffectiveTheme);
  }, [theme]);

  useEffect(() => {
    if (!settingsOpen) return;
    const closeOnOutsidePress = (event: PointerEvent) => {
      if (!settingsRef.current?.contains(event.target as Node)) setSettingsOpen(false);
    };
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") setSettingsOpen(false);
    };
    document.addEventListener("pointerdown", closeOnOutsidePress);
    document.addEventListener("keydown", closeOnEscape);
    return () => {
      document.removeEventListener("pointerdown", closeOnOutsidePress);
      document.removeEventListener("keydown", closeOnEscape);
    };
  }, [settingsOpen]);

  const toggleFullscreen = async () => {
    if (document.fullscreenElement) await document.exitFullscreen();
    else await document.querySelector<HTMLElement>(".learning-app")?.requestFullscreen();
  };
  const isSubmissions = variant === "submissions";

  return (
    <header className="learn-header unified-header" data-header-variant={variant}>
      <div className="learn-header-left">
        <Link className="learn-brand" to="/" aria-label="PhysLive — về trang chủ">
          PhysLive <small>Phòng học tương tác</small>
        </Link>
        {!isSubmissions && (
          <>
            <div className="learn-settings-anchor" ref={settingsRef}>
              <button type="button" className="learn-header-icon" aria-label="Cài đặt workspace" title="Cài đặt workspace" aria-haspopup="dialog" aria-expanded={settingsOpen} onClick={() => setSettingsOpen(value => !value)}><Icon name="settings" /></button>
              {settingsOpen && <dialog open className="learn-settings-popover" aria-label="Cài đặt workspace">
                <strong>Cài đặt</strong>
                <span>Giao diện</span>
                <fieldset className="learn-settings-segmented"><legend className="learn-sr-only">Giao diện</legend><button type="button" className={theme === "light" ? "active" : ""} onClick={() => setTheme("light")}><Icon name="sun" />Sáng</button><button type="button" className={theme === "dark" ? "active" : ""} onClick={() => setTheme("dark")}><Icon name="moon" />Tối</button><button type="button" className={theme === "system" ? "active" : ""} onClick={() => setTheme("system")}><Icon name="system" />Hệ thống</button></fieldset>
                <label htmlFor="learn-language">Ngôn ngữ</label>
                <select id="learn-language" defaultValue="vi" disabled><option value="vi">Tiếng Việt</option></select>
              </dialog>}
            </div>
            {onToggleLibrary && <button type="button" className="learn-header-icon" aria-label={libraryCollapsed ? "Mở thư viện" : "Thu gọn thư viện"} title={libraryCollapsed ? "Mở thư viện" : "Thu gọn thư viện"} onClick={onToggleLibrary}><Icon name="panel" /></button>}
          </>
        )}
      </div>

      <nav className="learn-journey" aria-label="Điều hướng workspace">
        <Link to="/workspace" aria-current={pathname === "/workspace" ? "page" : undefined} className={pathname === "/workspace" ? "active" : ""}>Workspace đề bài</Link>
        <Link to="/assignments/workspace" aria-current={pathname === "/assignments/workspace" ? "page" : undefined} className={pathname === "/assignments/workspace" ? "active" : ""}>Giao bài</Link>
        <Link to="/lab" aria-current={pathname === "/lab" ? "page" : undefined} className={pathname === "/lab" ? "active" : ""}>{isSubmissions ? "Bài nộp" : "Phòng thí nghiệm"}</Link>
      </nav>

      <div className="learn-header-actions">
        {isSubmissions ? <>
          <Link className="learn-header-action" to="/assignments/workspace">Giao bài</Link>
          <Link className="learn-back" to="/"><Icon name="back" />Trang chủ</Link>
        </> : <>
          {onNewSimulation && <button type="button" className="learn-header-action" onClick={onNewSimulation}>Nhập đề</button>}
          <fieldset className="learn-view-switch"><legend className="learn-sr-only">Chế độ hiển thị</legend>
            <button type="button" className={viewMode === "2d" ? "active" : ""} aria-pressed={viewMode === "2d"} onClick={() => onViewModeChange?.("2d")} title="Hiển thị mặt phẳng 2D"><Icon name="view2d" />2D</button>
            <button type="button" className={viewMode === "3d" ? "active" : ""} aria-pressed={viewMode === "3d"} disabled={!threeDEnabled} onClick={() => onViewModeChange?.("3d")} title={threeDEnabled ? "Hiển thị không gian 3D" : "3D chưa được cung cấp cho schema này"}><Icon name="view3d" />3D</button>
          </fieldset>
          <button type="button" className="learn-header-icon" aria-label="Toàn màn hình" title="Toàn màn hình" onClick={() => void toggleFullscreen()}><Icon name="fullscreen" /></button>
          <Link className="learn-header-action" to="/assignments/workspace">Giao bài</Link>
          <Link className="learn-back" to="/"><Icon name="back" />Về đề bài</Link>
        </>}
      </div>
    </header>
  );
}
