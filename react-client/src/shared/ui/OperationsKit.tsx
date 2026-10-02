import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { useSessionStore } from "../auth/sessionStore";
import { getToken } from "../lib/token";
import "../../styles/operations.css";

export function Access({ reviewer = false, children }: Readonly<{ reviewer?: boolean; children: ReactNode }>) {
  const user = useSessionStore((state) => state.user);
  if (!getToken()) return <main className="main ops"><h1>Cần đăng nhập</h1><Link to="/login">Đăng nhập để tiếp tục</Link></main>;
  if (!user) return <main className="main ops"><output>Đang xác thực tài khoản…</output></main>;
  if (user.role !== "MANAGER" && !(reviewer && user.role === "REVIEWER")) return <main className="main ops"><h1>Không có quyền truy cập</h1><Link to="/workspace">Về workspace</Link></main>;
  return children;
}

export function LoadState({ loading, error, refresh }: Readonly<{ loading: boolean; error?: string; refresh: () => void }>) {
  return <>{loading && <output className="ops-loading">Đang tải dữ liệu…</output>}{error && <div className="ops-alert" role="alert">{error} <button className="secondary" onClick={refresh}>Thử lại</button></div>}</>;
}

