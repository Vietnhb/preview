import { useEffect, useState } from "react";

export type WorkspaceTheme = "LIGHT" | "DARK";

export function readWorkspaceTheme(): WorkspaceTheme {
  const root = document.documentElement;
  return root.dataset.themeEffective === "dark" || root.dataset.theme === "dark" || root.classList.contains("dark")
    ? "DARK" : "LIGHT";
}

/** Follows the workspace theme switch (LearningHeader/NavBar set data-theme-effective). */
export function useWorkspaceTheme() {
  const [theme, setTheme] = useState<WorkspaceTheme>(readWorkspaceTheme);
  useEffect(() => {
    const observer = new MutationObserver(() => setTheme(readWorkspaceTheme()));
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ["class", "data-theme", "data-theme-effective"] });
    return () => observer.disconnect();
  }, []);
  return theme;
}
