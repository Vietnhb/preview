import AppRoutes from "./app/AppRoutes";
import AppShell from "./app/AppShell";
import { useSessionBootstrap } from "./app/useSessionBootstrap";

export default function App() {
  const authReady = useSessionBootstrap();
  return (
    <AppShell>
      <AppRoutes authReady={authReady} />
    </AppShell>
  );
}
