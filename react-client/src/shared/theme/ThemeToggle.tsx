import { useEffect, useId, useRef, useState } from "react";
import { AnimatePresence, motion, useReducedMotion } from "motion/react";
import { useThemeStore, type ThemeMode } from "./themeStore";
import "./theme-toggle.css";

const OPTIONS: { mode: ThemeMode; label: string }[] = [
  { mode: "light", label: "Sáng" },
  { mode: "dark", label: "Tối" },
  { mode: "system", label: "Theo hệ thống" },
];

const sunRays = [0, 45, 90, 135, 180, 225, 270, 315];

function Glyph({ mode }: Readonly<{ mode: ThemeMode }>) {
  if (mode === "dark") return <path d="M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z" />;
  if (mode === "system") return <><rect x="3.5" y="5" width="17" height="11" rx="2" /><path d="M9 20h6M12 16v4" /></>;
  return <><circle cx="12" cy="12" r="4" />{sunRays.map(angle => <path key={angle} d="M12 2.5v2.2" transform={`rotate(${angle} 12 12)`} />)}</>;
}

function Icon({ mode, size = 18 }: Readonly<{ mode: ThemeMode; size?: number }>) {
  return <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><Glyph mode={mode} /></svg>;
}

/** Theme picker: a compact trigger that opens a three-option menu (Sáng / Tối / Theo hệ thống).
 *  The page reveals the new theme outward from the trigger. */
export default function ThemeToggle({ className = "", align = "end" }: Readonly<{ className?: string; align?: "start" | "end" }>) {
  const mode = useThemeStore(state => state.mode);
  const effective = useThemeStore(state => state.effective);
  const setMode = useThemeStore(state => state.setMode);
  const reduced = useReducedMotion();
  const [open, setOpen] = useState(false);
  const root = useRef<HTMLDivElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const menuId = useId();

  useEffect(() => {
    if (!open) return;
    const outside = (event: PointerEvent) => { if (!root.current?.contains(event.target as Node)) setOpen(false); };
    const keys = (event: KeyboardEvent) => {
      if (event.key === "Escape") { setOpen(false); trigger.current?.focus(); return; }
      if (event.key !== "ArrowDown" && event.key !== "ArrowUp") return;
      event.preventDefault();
      const items = Array.from(root.current?.querySelectorAll<HTMLButtonElement>('[role="menuitemradio"]') ?? []);
      const index = items.indexOf(document.activeElement as HTMLButtonElement);
      items[(index + (event.key === "ArrowDown" ? 1 : -1) + items.length) % items.length]?.focus();
    };
    document.addEventListener("pointerdown", outside);
    document.addEventListener("keydown", keys);
    root.current?.querySelector<HTMLButtonElement>('[aria-checked="true"]')?.focus();
    return () => { document.removeEventListener("pointerdown", outside); document.removeEventListener("keydown", keys); };
  }, [open]);

  const choose = (next: ThemeMode) => {
    const rect = trigger.current?.getBoundingClientRect();
    setOpen(false);
    trigger.current?.focus();
    setMode(next, rect ? { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 } : undefined);
  };

  return (
    <div ref={root} className={`theme-menu ${className}`}>
      <button ref={trigger} type="button" className="theme-menu__trigger" aria-haspopup="menu" aria-expanded={open} aria-controls={open ? menuId : undefined}
        aria-label={`Giao diện: ${OPTIONS.find(option => option.mode === mode)?.label}`} title="Giao diện" onClick={() => setOpen(value => !value)}>
        <AnimatePresence mode="wait" initial={false}>
          <motion.span key={effective} className="theme-menu__glyph"
            initial={reduced ? false : { rotate: -60, scale: 0.5, opacity: 0 }} animate={{ rotate: 0, scale: 1, opacity: 1 }}
            exit={reduced ? { opacity: 0 } : { rotate: 60, scale: 0.5, opacity: 0 }} transition={{ type: "spring", stiffness: 420, damping: 26 }}>
            <Icon mode={effective} />
          </motion.span>
        </AnimatePresence>
      </button>
      <AnimatePresence>
        {open && (
          <motion.div id={menuId} role="menu" aria-label="Chọn giao diện" className={`theme-menu__list theme-menu__list--${align}`}
            initial={reduced ? { opacity: 0 } : { opacity: 0, y: -6, scale: 0.96 }} animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={reduced ? { opacity: 0 } : { opacity: 0, y: -4, scale: 0.98 }} transition={{ duration: 0.16, ease: [0.22, 1, 0.36, 1] }}>
            {OPTIONS.map(option => (
              <button key={option.mode} type="button" role="menuitemradio" aria-checked={mode === option.mode} className="theme-menu__item" onClick={() => choose(option.mode)}>
                <Icon mode={option.mode} size={16} />
                <span>{option.label}</span>
                {mode === option.mode && <svg className="theme-menu__check" viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M5 12.5l4.5 4.5L19 7.5" /></svg>}
              </button>
            ))}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

/** Three-way control for settings panels where all options should be visible at once. */
export function ThemeSegmented({ className = "" }: Readonly<{ className?: string }>) {
  const mode = useThemeStore(state => state.mode);
  const setMode = useThemeStore(state => state.setMode);
  return (
    <fieldset className={`theme-segmented ${className}`}>
      <legend className="sr-only">Giao diện</legend>
      {OPTIONS.map(option => (
        <button key={option.mode} type="button" aria-pressed={mode === option.mode}
          onClick={event => { const rect = event.currentTarget.getBoundingClientRect(); setMode(option.mode, { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 }); }}>
          {mode === option.mode && <motion.span layoutId="theme-segmented-pill" className="theme-segmented__pill" aria-hidden="true" transition={{ type: "spring", stiffness: 420, damping: 34 }} />}
          <Icon mode={option.mode} size={15} />
          <span>{option.mode === "system" ? "Hệ thống" : option.label}</span>
        </button>
      ))}
    </fieldset>
  );
}
