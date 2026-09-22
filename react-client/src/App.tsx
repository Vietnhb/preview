import { useEffect } from "react";
import AppRoutes from "./app/AppRoutes";
import AppShell from "./app/AppShell";
import { useSessionBootstrap } from "./app/useSessionBootstrap";
import { usePhysliveStore } from "./store/usePhysliveStore";
import { useRealtimeRevision } from "./realtime/useRealtimeRevision";
import { getMe } from "./api/userApi";

export default function App() {
  const authReady = useSessionBootstrap();
  const user = usePhysliveStore(state => state.user);
  const setUser = usePhysliveStore(state => state.setUser);
  const hasUser = user !== null;
  const realtimeRevision = useRealtimeRevision(authReady && hasUser);
  useEffect(() => {
    if (realtimeRevision === 0 || !hasUser) return;
    let active = true;
    void getMe().then(latest => { if (active) setUser(latest); }).catch(() => undefined);
    return () => { active = false; };
  }, [hasUser, realtimeRevision, setUser]);
  return (
    <AppShell key={realtimeRevision}>
      <AppRoutes authReady={authReady} />
    </AppShell>
  );
}
