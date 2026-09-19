import type { StudentClassSummary } from "../../../api/schoolApi";

type Props = {
  studentName: string;
  schoolName?: string | null;
  classes: StudentClassSummary[];
  loading: boolean;
  pendingAssignments: number;
  completedAssignments: number;
  sharedResources: number;
  onExplore: () => void;
};

export function StudentClassOverview({ studentName, schoolName, classes, loading, pendingAssignments, completedAssignments, sharedResources, onExplore }: Readonly<Props>) {
  const firstName = studentName.trim().split(/\s+/).at(-1) || studentName;
  return <>
    <section className="student-home-hero">
      <div className="student-home-copy">
        <span className="student-home-kicker">KHÔNG GIAN HỌC TẬP</span>
        <h1>Chào {firstName}, hôm nay mình khám phá gì?</h1>
        <p>{schoolName ? `${schoolName} · ` : ""}Theo dõi bài tập, lớp học và thử các mô phỏng do giáo viên chia sẻ.</p>
        <button type="button" className="student-explore-button" onClick={onExplore}>Khám phá mô phỏng <span aria-hidden="true">→</span></button>
      </div>
      <div className="student-home-orbit" aria-hidden="true"><span /><span /><span /><div>F = ma</div></div>
    </section>

    <section className="student-home-stats" aria-label="Tổng quan học tập">
      <article><span className="student-stat-icon pending">!</span><div><strong>{pendingAssignments}</strong><span>Bài cần hoàn thành</span></div></article>
      <article><span className="student-stat-icon completed">✓</span><div><strong>{completedAssignments}</strong><span>Bài đã gửi</span></div></article>
      <article><span className="student-stat-icon resource">◇</span><div><strong>{sharedResources}</strong><span>Mô phỏng để khám phá</span></div></article>
    </section>

    <section className="student-class-section">
      <div className="student-section-heading"><div><span className="student-home-kicker">LỚP CỦA TÔI</span><h2>Nơi bạn đang học</h2></div></div>
      {loading ? <div className="student-class-skeleton" aria-label="Đang tải thông tin lớp" /> : classes.length === 0 ?
        <div className="student-class-empty"><strong>Chưa có lớp học đang hoạt động</strong><span>Khi nhà trường xếp lớp, thông tin giáo viên và bạn học sẽ xuất hiện tại đây.</span></div> :
        <div className="student-class-grid">{classes.map(item => <article className="student-class-card" key={item.id}>
          <div className="student-class-grade">{item.gradeLevel}</div>
          <div className="student-class-content">
            <div className="student-class-title"><div><span>{item.subject || "Vật lý"}</span><h3>{item.name}</h3></div><span className="student-class-year">{item.schoolYear}</span></div>
            <p className="student-class-school">{item.schoolName}</p>
            <div className="student-class-people">
              <div className="student-avatar-stack" aria-hidden="true">{item.teachers.slice(0, 3).map(teacher => <span key={teacher.id}>{teacher.fullName.trim().charAt(0).toUpperCase()}</span>)}</div>
              <p>{item.teachers.length ? item.teachers.map(teacher => teacher.fullName).join(", ") : "Chưa phân công giáo viên"}<small>{item.classmateCount} bạn cùng lớp</small></p>
            </div>
          </div>
        </article>)}</div>}
    </section>
  </>;
}
