export function lifecycleClass(status: string) {
  if (status === "APPROVED") return "pass";
  if (status === "DRAFT") return "draft";
  return "fail";
}

export function reviewerStatusLabel(status: string) {
  const labels: Record<string, string> = {
    DRAFT: "Bản nháp", APPROVED: "Đã duyệt", RETIRED: "Ngừng sử dụng",
    PENDING: "Chờ duyệt", FEATURED: "Nổi bật", REJECTED: "Từ chối", REMOVED: "Đã gỡ",
    ACTIVE: "Đang hoạt động", ANNOTATING: "Đang gán nhãn", DISAGREEMENT: "Cần phân xử",
    GOLD_READY: "Đã có đáp án chuẩn", ARCHIVED: "Đã lưu trữ", COMPLETED: "Hoàn tất",
    SUCCEEDED: "Hoàn tất", SUCCESS: "Hoàn tất", FAILED: "Thất bại", RUNNING: "Đang chạy", QUEUED: "Đang chờ",
  };
  return labels[status] ?? status;
}



