import type { User } from "../../../shared/auth/types";
import { hasPermission } from "../../../shared/auth/permissions";

/** What the signed-in account may do inside the reviewer console.
 *  MANAGER has both; a REVIEWER gets what the manager granted on the account. */
export type ReviewerAccess = { canEdit: boolean; canReview: boolean; isManager: boolean };

export function reviewerAccess(user: User | null | undefined): ReviewerAccess {
  const isManager = user?.role === "MANAGER";
  return {
    isManager,
    canEdit: isManager || (user?.role === "REVIEWER" && hasPermission(user, "CONTENT_EDIT")),
    canReview: isManager || (user?.role === "REVIEWER" && hasPermission(user, "CONTENT_REVIEW")),
  };
}

export type ReviewerView = "overview" | "moderation" | "topics" | "benchmarks";

export type ReviewerNavItem = { id: ReviewerView; label: string; hint: string; icon: "home" | "library" | "schema" | "benchmark" };
export type ReviewerNavGroup = { id: string; label: string; items: ReviewerNavItem[] };

export function reviewerNavigation(access: ReviewerAccess): ReviewerNavGroup[] {
  const groups: ReviewerNavGroup[] = [{ id: "start", label: "", items: [{ id: "overview", label: "Việc cần làm", hint: "Tổng quan công việc hôm nay", icon: "home" }] }];
  if (access.canReview) groups.push({ id: "review", label: "Kiểm duyệt nội dung", items: [
    { id: "moderation", label: "Mô phỏng chờ duyệt", hint: "Giáo viên chia sẻ lên thư viện công khai", icon: "library" },
  ] });
  if (access.canEdit) groups.push({ id: "edit", label: "Biên soạn dữ liệu chuẩn", items: [
    { id: "topics", label: "Chủ đề vật lý", hint: "Cấu trúc chủ đề, bộ giải, gói phát hành", icon: "schema" },
    { id: "benchmarks", label: "Đề kiểm thử AI", hint: "Đáp án chuẩn để đo độ chính xác", icon: "benchmark" },
  ] });
  return groups;
}

export function allowedViews(access: ReviewerAccess): ReviewerView[] {
  return reviewerNavigation(access).flatMap(group => group.items.map(item => item.id));
}
