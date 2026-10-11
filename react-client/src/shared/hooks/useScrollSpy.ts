import { useEffect, useState } from "react";

/** Returns the id of the section currently being read, for a table of contents. */
export function useScrollSpy(ids: readonly string[], offset = 140) {
  const [active, setActive] = useState(ids[0] ?? "");
  const key = ids.join("|");
  useEffect(() => {
    const list = key.split("|").filter(Boolean);
    const update = () => {
      let current = list[0] ?? "";
      for (const id of list) {
        const element = document.getElementById(id);
        if (element && element.getBoundingClientRect().top - offset <= 0) current = id;
      }
      // At the very bottom the last short section can never reach the offset line.
      if (window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 4) current = list.at(-1) ?? current;
      setActive(current);
    };
    update();
    window.addEventListener("scroll", update, { passive: true });
    window.addEventListener("resize", update);
    return () => { window.removeEventListener("scroll", update); window.removeEventListener("resize", update); };
  }, [key, offset]);
  return active;
}
