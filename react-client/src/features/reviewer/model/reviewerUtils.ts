export function lifecycleClass(status: string) {
  if (status === "APPROVED") return "pass";
  if (status === "DRAFT") return "draft";
  return "fail";
}

export function reviewerStatusLabel(status: string) {
  const labels: Record<string, string> = {
    DRAFT: "Bản nháp", APPROVED: "Đang dùng", RETIRED: "Ngừng sử dụng",
    PENDING: "Chờ duyệt", FEATURED: "Nổi bật", REJECTED: "Bị từ chối", REMOVED: "Đã gỡ",
    ACTIVE: "Sẵn sàng gán nhãn", ANNOTATING: "Đang gán nhãn", DISAGREEMENT: "Cần phân xử",
    GOLD_READY: "Đã có đáp án chuẩn", ARCHIVED: "Đã lưu trữ", COMPLETED: "Hoàn tất",
    SUCCEEDED: "Hoàn tất", SUCCESS: "Hoàn tất", FAILED: "Thất bại", RUNNING: "Đang chạy", QUEUED: "Đang chờ",
  };
  return labels[status] ?? status;
}

/** "3 phút trước", "2 ngày trước" — reviewers care about age, not timestamps. */
export function timeAgo(value?: string | null) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const minutes = Math.round((Date.now() - date.getTime()) / 60000);
  if (minutes < 1) return "vừa xong";
  if (minutes < 60) return `${minutes} phút trước`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours} giờ trước`;
  const days = Math.round(hours / 24);
  if (days < 30) return `${days} ngày trước`;
  return date.toLocaleDateString("vi-VN");
}

export function formatDate(value?: string | null) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "—" : date.toLocaleDateString("vi-VN");
}

export function matches(query: string, ...values: (string | null | undefined)[]) {
  const needle = query.trim().toLowerCase();
  return !needle || values.some(value => value?.toLowerCase().includes(needle));
}
