import { lazy, Suspense, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { library, personalLibrary } from "../api/libraryApi";
import type { LibraryItem } from "../../../shared/types/physlive";
import { useSessionStore } from "../../../shared/auth/sessionStore";
import { isStudentRole } from "../../../shared/auth/roles";
import { canTeach } from "../../../shared/auth/permissions";

const StudentAssignments = lazy(() => import("../../assignments/pages/StudentAssignments"));

function LibraryRows({ items, assignable }: Readonly<{ items: LibraryItem[]; assignable: boolean }>) {
  if (!items.length) return <p className="muted">Chưa có mô phỏng trong mục này.</p>;
  const visibilityLabel = (item: LibraryItem) => item.visibility === "PERSONAL" ? "Cá nhân" : item.visibility === "PUBLIC" ? "Công khai" : "Trong trường";
  return <div className="table-wrap"><table><thead><tr><th>Tên</th><th>Chủ đề</th><th>Phạm vi</th><th>Người chia sẻ</th><th>Ngày lưu</th>{assignable && <th />}</tr></thead><tbody>{items.map(item => <tr key={item.id}><td><strong>{assignable ? <Link to={`/workspace?simulationId=${item.simulationId}`}>{item.title}</Link> : item.title}</strong></td><td>{item.topic ?? "—"}</td><td>{visibilityLabel(item)}</td><td>{item.sharedByName || "—"}{item.schoolName ? <small className="library-school">{item.schoolName}</small> : null}</td><td>{new Date(item.createdAt).toLocaleDateString("vi-VN")}</td>{assignable && <td><Link className="table-action" to={`/assignments/workspace?libraryItemId=${item.id}`}>Giao bài</Link></td>}</tr>)}</tbody></table></div>;
}

export default function Library() {
  const user = useSessionStore(state => state.user);
  if (isStudentRole(user?.role)) {
    return <Suspense fallback={<main className="route-loading" aria-busy="true" />}><StudentAssignments initialTab="library" /></Suspense>;
  }
  return <LibraryCatalog canManage={canTeach(user)} />;
}

function LibraryCatalog({ canManage }: Readonly<{ canManage: boolean }>) {
  const [mine, setMine] = useState<LibraryItem[]>([]);
  const [accessible, setAccessible] = useState<LibraryItem[]>([]);
  const [error, setError] = useState("");
  useEffect(() => {
    const requests = canManage ? [personalLibrary(), library()] : [Promise.resolve([]), library()];
    void Promise.all(requests).then(([owned, all]) => { setMine(owned); setAccessible(all); }).catch(() => setError("Không thể tải thư viện."));
  }, [canManage]);
  const mineIds = useMemo(() => new Set(mine.map(item => item.id)), [mine]);
  const shared = accessible.filter(item => item.visibility !== "PERSONAL" && !mineIds.has(item.id));
  return <main className="main">
    <div className="hero"><div><h1>Thư viện mô phỏng</h1><p className="muted">Chỉ mô phỏng đã qua kiểm chứng kép (bộ giải số và lời giải tham chiếu) mới được lưu và giao bài.</p></div></div>
    {canManage && <section className="card library-section"><div className="section-heading"><div><h2>Mô phỏng của tôi</h2></div><Link to="/workspace" className="secondary page-action">Tạo mô phỏng</Link></div><LibraryRows items={mine} assignable /></section>}
    <section className="card library-section"><div className="section-heading"><div><h2>Được chia sẻ với bạn</h2></div></div><LibraryRows items={shared} assignable={false} /></section>
    {error && <p className="error" role="alert">{error}</p>}
  </main>;
}