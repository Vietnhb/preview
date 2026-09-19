import { Fragment, useMemo, useState, type FormEvent } from "react";
import type { AssignmentSubmission } from "../../../../types/physlive";
import type { SubmissionTableProps } from "./teacherSubmissionTypes";
import { formatDateTime, initials, predictionDetails } from "./teacherSubmissionUtils";

function gradingLabel(submission: AssignmentSubmission) {
  if (!submission.completedAt) return "Đang làm";
  if (submission.gradingStatus === "TEACHER_CONFIRMED") return "Đã chấm";
  if (submission.gradingStatus === "AI_GRADED") return "Điểm tự động";
  if (submission.gradingStatus === "RETURNED") return "Cần làm lại";
  return "Chờ chấm";
}

export function TeacherSubmissionTable({ record, students, filter, query, onGrade, onReopen }: Readonly<SubmissionTableProps>) {
  const [gradingId, setGradingId] = useState("");
  const [score, setScore] = useState("");
  const [feedback, setFeedback] = useState("");
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState("");
  const maxScore = record.assignment.maxScore ?? 10;

  const rows = useMemo(() => {
    const normalizedQuery = query.trim().toLocaleLowerCase("vi-VN");
    const submissionsByStudent = new Map(record.submissions.map(submission => [submission.studentId, submission]));
    return record.assignment.studentIds.map(studentId => {
      const submission = submissionsByStudent.get(studentId);
      const student = students.get(studentId);
      const studentName = submission?.studentName?.trim() || student?.fullName?.trim() || `Học sinh #${studentId}`;
      return { studentId, studentName, submission };
    }).filter(({ submission, studentId, studentName }) => {
      const completed = Boolean(submission?.completedAt);
      const matchesFilter = filter === "all" || (filter === "submitted" ? completed : !completed);
      const matchesQuery = !normalizedQuery || studentName.toLocaleLowerCase("vi-VN").includes(normalizedQuery) || String(studentId).includes(normalizedQuery);
      return matchesFilter && matchesQuery;
    });
  }, [filter, query, record, students]);

  const openGrading = (submission: AssignmentSubmission) => {
    setGradingId(current => current === submission.id ? "" : submission.id);
    setScore(submission.score == null ? "" : String(submission.score));
    setFeedback(submission.feedback ?? "");
    setActionError("");
  };

  const submitGrade = async (event: FormEvent, submissionId: string) => {
    event.preventDefault();
    const numericScore = Number(score);
    if (!score.trim() || !Number.isFinite(numericScore) || numericScore < 0 || numericScore > maxScore) {
      setActionError(`Điểm phải nằm trong khoảng 0 đến ${maxScore}.`);
      return;
    }
    setBusy(true);
    setActionError("");
    try {
      await onGrade(submissionId, numericScore, feedback.trim());
      setGradingId("");
    } catch {
      setActionError("Chưa lưu được điểm. Vui lòng thử lại.");
    } finally {
      setBusy(false);
    }
  };

  const reopen = async (submissionId: string) => {
    setBusy(true);
    setActionError("");
    try {
      await onReopen(submissionId);
      setGradingId("");
    } catch {
      setActionError("Chưa thể trả bài cho học sinh làm lại.");
    } finally {
      setBusy(false);
    }
  };

  if (record.assignment.studentIds.length === 0) return <div className="lab-empty-state"><strong>Chưa có học sinh trong bài giao này</strong><span>Thêm học sinh ở khu giao bài để bắt đầu theo dõi.</span></div>;
  if (rows.length === 0) return <div className="lab-empty-state">Không có học sinh phù hợp với bộ lọc hiện tại.</div>;

  return <div className="modern-table-wrapper lab-submission-table-wrapper">
    <table className="modern-table lab-submission-table">
      <thead><tr><th scope="col">Học sinh</th><th scope="col">Bài làm</th><th scope="col">Nộp lúc</th><th scope="col">Trạng thái</th><th scope="col"><span className="sr-only">Thao tác</span></th></tr></thead>
      <tbody>{rows.map(({ studentId, studentName, submission }) => {
        const details = predictionDetails(submission?.predictions);
        const completed = Boolean(submission?.completedAt);
        const isGrading = submission?.id === gradingId;
        return <Fragment key={studentId}>
          <tr>
            <td data-label="Học sinh"><div className="lab-student-cell"><span className="lab-student-avatar" aria-hidden="true">{initials(studentName, studentId)}</span><span><strong>{studentName}</strong><small>ID {studentId}</small></span></div></td>
            <td data-label="Bài làm" className={submission ? "" : "lab-muted-cell"}>{submission ? <div className="lab-answer-cell"><strong>{details.answer}</strong>{details.reasoning && <small><b>Lập luận:</b> {details.reasoning}</small>}{details.conclusion && <small><b>Kết luận:</b> {details.conclusion}</small>}</div> : "Chưa bắt đầu"}</td>
            <td data-label="Nộp lúc">{submission?.completedAt ? <time dateTime={submission.completedAt}>{formatDateTime(submission.completedAt)}</time> : "—"}</td>
            <td data-label="Trạng thái"><div className="lab-grade-status"><span className={`lab-status ${completed ? "submitted" : submission ? "progress" : "pending"}`}><span className="lab-status-dot" aria-hidden="true" />{submission ? gradingLabel(submission) : "Chưa bắt đầu"}</span>{completed && submission?.score != null && <strong>{submission.score}/{submission.maxScore ?? maxScore}</strong>}</div></td>
            <td data-label="Chấm điểm" className="lab-grade-action">{completed && submission && <button type="button" className="lab-grade-button" aria-expanded={isGrading} onClick={() => openGrading(submission)}>{submission.gradingStatus === "TEACHER_CONFIRMED" ? "Sửa điểm" : "Chấm bài"}</button>}</td>
          </tr>
          {isGrading && submission && <tr className="lab-grading-row"><td colSpan={5}>
            <form className="lab-grading-form" onSubmit={event => void submitGrade(event, submission.id)}>
              <div className="lab-grading-heading"><div><strong>Chấm bài của {studentName}</strong><small>Thang điểm tối đa: {maxScore}</small></div><button type="button" aria-label="Đóng khung chấm điểm" onClick={() => setGradingId("")}>×</button></div>
              <div className="lab-grading-fields"><label>Điểm<input type="number" min="0" max={maxScore} step="0.1" required value={score} onChange={event => setScore(event.target.value)} /></label><label>Nhận xét<textarea rows={3} maxLength={4000} value={feedback} onChange={event => setFeedback(event.target.value)} placeholder="Nhận xét về dự đoán, lập luận và kết luận của học sinh…" /></label></div>
              {actionError && <p className="lab-grading-error" role="alert">{actionError}</p>}
              <div className="lab-grading-actions"><button type="button" className="lab-reopen-button" disabled={busy} onClick={() => void reopen(submission.id)}>Trả bài làm lại</button><button type="submit" className="prediction-submit-btn" disabled={busy}>{busy ? "Đang lưu…" : "Xác nhận điểm"}</button></div>
            </form>
          </td></tr>}
        </Fragment>;
      })}</tbody>
    </table>
  </div>;
}
