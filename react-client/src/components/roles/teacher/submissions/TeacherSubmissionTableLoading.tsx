export function TeacherSubmissionTableLoading() {
  return (
    <div className="lab-submissions-loading" aria-live="polite" aria-busy="true">
      <span className="lab-loading-spinner" aria-hidden="true" />
      <strong>Đang tải bài nộp…</strong>
      <span>Danh sách học sinh sẽ hiển thị ngay khi dữ liệu sẵn sàng.</span>
    </div>
  );
}


