import type { LibraryItem } from "../../../shared/types/physlive";

/** Short review state of a shared item, shown in the share menu and on the library row. */
export function shareStatus(item: LibraryItem) {
  if (item.visibility === "PERSONAL") return "Đang chọn";
  if (item.moderationStatus === "PENDING") return "Chờ duyệt";
  if (item.moderationStatus === "REJECTED") return "Bị từ chối";
  return "Đã duyệt";
}
