import { useSessionStore } from "../../../shared/auth/sessionStore";
import AppSidebarLayout, { type SidebarGroup } from "../../../shared/layout/AppSidebar";

/** SCHOOL area. While the licence is unpaid only the billing page is reachable. */
export default function SchoolLayout() {
  const user = useSessionStore(state => state.user);
  const billingOnly = user?.billingRequired === true;
  const groups: SidebarGroup[] = billingOnly
    ? [{ items: [{ to: "/school/billing", label: "Mua / gia hạn gói", icon: "activity" }] }]
    : [
      { items: [{ to: "/school", label: "Tổng quan", icon: "grid", end: true }] },
      { label: "Nhà trường", items: [
        { to: "/school/users", label: "Tài khoản", icon: "users" },
        { to: "/school/classes", label: "Lớp học", icon: "book" },
        { to: "/school/reports", label: "Báo cáo", icon: "chart" },
      ] },
      { label: "Gói dịch vụ", items: [{ to: "/school/billing", label: "Mua / gia hạn gói", icon: "activity" }] },
    ];
  return <AppSidebarLayout id="school" subtitle="Quản lý trường" home={billingOnly ? "/school/billing" : "/school"} groups={groups} contentClassName="admin-shell school-shell" />;
}
