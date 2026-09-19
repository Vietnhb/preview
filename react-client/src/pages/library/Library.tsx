import { lazy, Suspense, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { library, personalLibrary } from "../../api/libraryApi";
import type { LibraryItem } from "../../types/physlive";
import { usePhysliveStore } from "../../store/usePhysliveStore";
import { canManageLearning, isStudentRole } from "../../types/roles";

const StudentAssignments = lazy(() => import("../student/StudentAssignments"));

function LibraryRows({ items, assignable }: Readonly<{ items: LibraryItem[]; assignable: boolean }>) {
  if (!items.length) return <p className="muted">Chưa có simulation trong mục này.</p>;
  const visibilityLabel = (item: LibraryItem) => item.visibility === "PERSONAL" ? "Cá nhân" : item.visibility === "PUBLIC" ? "Công khai" : "Trong trường";
  return <div className="table-wrap"><table><thead><tr><th>Tên</th><th>Chủ đề</th><th>Phạm vi</th><th>Người chia sẻ</th><th>Ngày lưu</th>{assignable && <th />}</tr></thead><tbody>{items.map(item => <tr key={item.id}><td><strong>{item.title}</strong></td><td>{item.topic ?? "—"}</td><td>{visibilityLabel(item)}</td><td>{item.sharedByName || "—"}{item.schoolName ? <small>{item.schoolName}</small> : null}</td><td>{new Date(item.createdAt).toLocaleDateString("vi-VN")}</td>{assignable && <td><Link className="table-action" to={`/assignments/workspace?libraryItemId=${item.id}`}>Giao bài</Link></td>}</tr>)}</tbody></table></div>;
}

export default function Library() {
  const user = usePhysliveStore(state => state.user);
  if (isStudentRole(user?.role)) {
    return <Suspense fallback={<main className="route-loading" aria-busy="true" />}><StudentAssignments initialTab="library" /></Suspense>;
  }
  return <LibraryCatalog userRole={user?.role} />;
}

function LibraryCatalog({ userRole }: Readonly<{ userRole?: string }>) {
  const [mine, setMine] = useState<LibraryItem[]>([]);
  const [accessible, setAccessible] = useState<LibraryItem[]>([]);
  const [error, setError] = useState("");
  useEffect(() => {
    const requests = canManageLearning(userRole) ? [personalLibrary(), library()] : [Promise.resolve([]), library()];
    void Promise.all(requests).then(([owned, all]) => { setMine(owned); setAccessible(all); }).catch(() => setError("Không thể tải thư viện."));
  }, [userRole]);
  const mineIds = useMemo(() => new Set(mine.map(item => item.id)), [mine]);
  const shared = accessible.filter(item => item.visibility !== "PERSONAL" && !mineIds.has(item.id));
  return <main className="main">
    <div className="hero"><div><span className="eyebrow">F08 · nội dung tái sử dụng</span><h1>Thư viện simulation</h1><p className="muted">Chỉ simulation đã vượt dual validation mới có thể được lưu và giao.</p></div></div>
    {canManageLearning(userRole) && <section className="card library-section"><div className="section-heading"><div><span className="eyebrow">Simulation cá nhân</span><h2>Simulation của tôi</h2></div><Link to="/workspace" className="secondary page-action">Tạo simulation</Link></div><LibraryRows items={mine} assignable /></section>}
    <section className="card library-section"><div className="section-heading"><div><span className="eyebrow">Dùng chung</span><h2>Được chia sẻ với bạn</h2></div></div><LibraryRows items={shared} assignable={false} /></section>
    {error && <p className="error" role="alert">{error}</p>}
  </main>;
}
