import type { ReactNode } from "react";
import { Navigate } from "react-router-dom";
import { usePhysliveStore } from "../store/usePhysliveStore";
import { hasRole } from "../types/roles";

export default function RequireAccess({ ready, roles, children }: {
  ready: boolean; roles?: readonly string[]; children: ReactNode;
}) {
  const user = usePhysliveStore(state => state.user);
  if (!ready) return <main className="route-loading" aria-busy="true" />;
  if (!user) return <Navigate to="/login" replace />;
  if (roles && !hasRole(user.role, roles)) return <Navigate to="/" replace />;
  return children;
}
