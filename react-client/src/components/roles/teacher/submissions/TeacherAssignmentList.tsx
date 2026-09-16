import { Link } from "react-router-dom";
import Icon from "../../../common/LearningIcon";
import type { AssignmentRecord } from "./teacherSubmissionTypes";
import { formatShortDate } from "./teacherSubmissionUtils";

export function TeacherAssignmentList({
  records,
  selectedId,
  onSelect,
  submissionsLoading,
}: Readonly<{
  records: AssignmentRecord[];
  selectedId: string;
  onSelect: (id: string) => void;
  submissionsLoading: boolean;
}>) {
  return (
    <aside className="lab-assignment-list" aria-label="Danh sách bài đã giao">
      <div className="lab-list-heading">
        <div>
          <h2>Bài đã giao</h2>
          <p>Chọn một bài để xem tiến độ.</p>
        </div>
        <span className="lab-list-count">{records.length}</span>
      </div>

      <label className="lab-mobile-selector">
        <span>Bài đang xem</span>
        <select
          value={selectedId}
          onChange={(event) => onSelect(event.target.value)}
          aria-label="Chọn bài đang xem"
        >
          {records.map(({ assignment }) => (
            <option key={assignment.id} value={assignment.id}>
              {assignment.title}
            </option>
          ))}
        </select>
      </label>

      <nav className="lab-assignment-items" aria-label="Bài tập đã giao">
        {records.map(({ assignment, submissions }) => (
          <button
            type="button"
            key={assignment.id}
            className={`lab-assignment-item${selectedId === assignment.id ? " active" : ""}`}
            aria-pressed={selectedId === assignment.id}
            onClick={() => onSelect(assignment.id)}
          >
            <span className="lab-assignment-item-title">
              {assignment.title}
            </span>
            <span className="lab-assignment-item-meta">
              {submissionsLoading
                ? "Đang tải bài nộp…"
                : `${submissions.length}/${assignment.studentIds.length} đã nộp`} · Hạn{" "}
              {formatShortDate(assignment.dueAt)}
            </span>
          </button>
        ))}
      </nav>

      <Link className="lab-create-link" to="/assignments/workspace">
        <Icon name="plus" />
        Tạo bài giao mới
      </Link>
    </aside>
  );
}


