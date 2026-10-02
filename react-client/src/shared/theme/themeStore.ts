import { create } from "zustand";

/** One theme for the whole app. The choice is stored under the same key the
 *  workspace settings always used, so existing preferences carry over. */
export type ThemeMode = "light" | "dark" | "system";
export type EffectiveTheme = "light" | "dark";

const STORAGE_KEY = "physlive.theme";
const media = () => globalThis.matchMedia?.("(prefers-color-scheme: dark)");

function readStored(): ThemeMode {
  try {
    const stored = globalThis.localStorage.getItem(STORAGE_KEY);
    return stored === "light" || stored === "dark" || stored === "system" ? stored : "system";
  } catch {
    return "system";
  }
}

function resolve(mode: ThemeMode): EffectiveTheme {
  return mode === "dark" || (mode === "system" && media()?.matches) ? "dark" : "light";
}

/** Writes the attributes every stylesheet keys off (see tokens.css). index.html
 *  runs the same logic inline before first paint to avoid a flash. */
function apply(mode: ThemeMode, effective: EffectiveTheme) {
  const root = document.documentElement;
  root.dataset.theme = mode;
  root.dataset.themeEffective = effective;
  root.style.colorScheme = effective;
  // Radix Themes with appearance="inherit" (pages that mount their own <Theme>) follow these classes.
  root.classList.toggle("dark", effective === "dark");
  root.classList.toggle("light", effective === "light");
  document.querySelector('meta[name="theme-color"]')?.setAttribute("content", effective === "dark" ? "#10151d" : "#f5f7fa");
}

type ThemeState = { mode: ThemeMode; effective: EffectiveTheme; setMode: (mode: ThemeMode, origin?: { x: number; y: number }) => void };

export const useThemeStore = create<ThemeState>((set, get) => {
  const mode = readStored();
  const effective = resolve(mode);
  apply(mode, effective);
  media()?.addEventListener("change", () => {
    const current = get().mode;
    if (current !== "system") return;
    const next = resolve(current);
    apply(current, next);
    set({ effective: next });
  });
  return {
    mode,
    effective,
    setMode: (next, origin) => {
      const nextEffective = resolve(next);
      try { globalThis.localStorage.setItem(STORAGE_KEY, next); } catch { /* private mode: keep the choice for this visit only */ }
      const commit = () => { apply(next, nextEffective); set({ mode: next, effective: nextEffective }); };
      if (nextEffective === get().effective) { commit(); return; }
      revealTransition(commit, origin);
    },
  };
});

type TransitionDocument = Document & { startViewTransition?: (update: () => void) => { ready: Promise<void> } };

/** Grows the new theme outward from the control that was pressed (View Transitions API);
 *  browsers without it, and people who prefer reduced motion, get an instant switch. */
function revealTransition(commit: () => void, origin?: { x: number; y: number }) {
  const doc = document as TransitionDocument;
  const reduced = globalThis.matchMedia?.("(prefers-reduced-motion: reduce)").matches;
  if (!doc.startViewTransition || reduced) { commit(); return; }
  const x = origin?.x ?? innerWidth - 40;
  const y = origin?.y ?? 32;
  const radius = Math.hypot(Math.max(x, innerWidth - x), Math.max(y, innerHeight - y));
  document.documentElement.classList.add("theme-switching");
  const transition = doc.startViewTransition(commit);
  void transition.ready.then(() => {
    const animation = document.documentElement.animate(
      { clipPath: [`circle(0px at ${x}px ${y}px)`, `circle(${radius}px at ${x}px ${y}px)`] },
      { duration: 520, easing: "cubic-bezier(0.22, 1, 0.36, 1)", pseudoElement: "::view-transition-new(root)" },
    );
    animation.onfinish = animation.oncancel = () => document.documentElement.classList.remove("theme-switching");
  }).catch(() => document.documentElement.classList.remove("theme-switching"));
}

export const useEffectiveTheme = () => useThemeStore(state => state.effective);
