import { useEffect, useState } from "react";

/** Refresh deadline labels while an assignment page stays open. */
export function useAssignmentClock() {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = globalThis.setInterval(() => setNow(Date.now()), 30_000);
    return () => globalThis.clearInterval(timer);
  }, []);
  return now;
}
