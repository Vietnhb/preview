import { useMemo } from "react";
import type { SubmissionTableProps } from "./teacherSubmissionTypes";
import { formatDateTime, initials, predictionText } from "./teacherSubmissionUtils";

export function TeacherSubmissionTable({
  record,
  students,
  filter,
  query,
}: Readonly<SubmissionTableProps>) {
  const rows = useMemo(() => {
    const normalizedQuery = query.trim().toLocaleLowerCase("vi-VN");
    const submissionsByStudent = new Map(
      record.submissions.map((submission) => [
        submission.studentId,
        submission,
      ]),
    );

    return record.assignment.studentIds
      .map((studentId) => {
        const submission = submissionsByStudent.get(studentId);
        const student = students.get(studentId);
        const studentName =
          submission?.studentName?.trim() ||
          student?.fullName?.trim() ||
          `Học sinh #${studentId}`;
        return { studentId, studentName, submission };
      })
      .filter(({ submission, studentId, studentName }) => {
        const matchesFilter =
          filter === "all" ||
          (filter === "submitted" ? Boolean(submission) : !submission);
        const matchesQuery =
          !normalizedQuery ||
          studentName.toLocaleLowerCase("vi-VN").includes(normalizedQuery) ||
          String(studentId).includes(normalizedQuery);
        return matchesFilter && matchesQuery;
      });
  }, [filter, query, record, students]);

  if (record.assignment.studentIds.length === 0) {
    return (
      <div className="lab-empty-state">
        <strong>Chưa có học sinh trong bài giao này</strong>
        <span>Thêm học sinh ở khu giao bài để bắt đầu theo dõi.</span>
      </div>
    );
  }

  if (rows.length === 0) {
    return (
      <div className="lab-empty-state">
        Không có học sinh phù hợp với bộ lọc hiện tại.
      </div>
    );
  }

  return (
    <div className="modern-table-wrapper lab-submission-table-wrapper">
      <table className="modern-table lab-submission-table">
        <thead>
          <tr>
            <th scope="col">Học sinh</th>
            <th scope="col">Dự đoán</th>
            <th scope="col">Nộp lúc</th>
            <th scope="col">Trạng thái</th>
          </tr>
        </thead>
        <tbody>
          {rows.map(({ studentId, studentName, submission }) => (
            <tr key={studentId}>
              <td data-label="Học sinh">
                <div className="lab-student-cell">
                  <span className="lab-student-avatar" aria-hidden="true">
                    {initials(studentName, studentId)}
                  </span>
                  <span>
                    <strong>{studentName}</strong>
                    <small>ID {studentId}</small>
                  </span>
                </div>
              </td>
              <td
                data-label="Dự đoán"
                className={submission ? "" : "lab-muted-cell"}
              >
                {submission
                  ? predictionText(submission.predictions)
                  : "Chưa có dự đoán"}
              </td>
              <td data-label="Nộp lúc">
                {submission ? (
                  <time dateTime={submission.submittedAt}>
                    {formatDateTime(submission.submittedAt)}
                  </time>
                ) : (
                  "—"
                )}
              </td>
              <td data-label="Trạng thái">
                <span
                  className={`lab-status ${submission ? "submitted" : "pending"}`}
                >
                  <span className="lab-status-dot" aria-hidden="true" />
                  {submission ? "Đã nộp" : "Chưa nộp"}
                </span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

