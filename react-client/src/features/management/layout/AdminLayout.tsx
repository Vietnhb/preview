import { useEffect, useState } from "react";
import { DropdownMenu, IconButton } from "@radix-ui/themes";
import { useNavigate } from "react-router-dom";
import { paymentNotifications, type PaymentNotification } from "../../billing/api/billingApi";
import LearningIcon from "../../../shared/ui/LearningIcon";
import AppSidebarLayout, { type SidebarGroup } from "../../../shared/layout/AppSidebar";

/** MANAGER area: platform operations. Every menu item is its own route under /manager. */
export default function AdminLayout() {
  const navigate = useNavigate();
  const [notifications, setNotifications] = useState<PaymentNotification[]>([]);
  const [notificationError, setNotificationError] = useState(false);
  useEffect(() => {
    let active = true;
    const load = () => void paymentNotifications().then(items => { if (active) { setNotifications(items); setNotificationError(false); } }).catch(() => { if (active) setNotificationError(true); });
    load(); const timer = window.setInterval(load, 30000);
    return () => { active = false; window.clearInterval(timer); };
  }, []);
  const toReconcile = notifications.filter(item => item.status === "REQUIRES_REVIEW").length;

  const groups: SidebarGroup[] = [
    { items: [{ to: "/manager", label: "Tổng quan", icon: "grid", end: true }] },
    { label: "Khách hàng", items: [
      { to: "/manager/schools", label: "Trường học", icon: "book" },
      { to: "/manager/users", label: "Người dùng", icon: "users", title: "Quản lý người dùng" },
      { to: "/manager/plans", label: "Gói dịch vụ", icon: "sliders" },
      { to: "/manager/payments", label: "Thanh toán", icon: "chart", badge: toReconcile },
    ] },
    { label: "Hỗ trợ", items: [
      { to: "/manager/feedback", label: "Phản hồi", icon: "message" },
      { to: "/manager/messages", label: "Tin nhắn", icon: "bell" },
    ] },
    { label: "Nội dung", items: [
      { to: "/manager/curriculum", label: "Chương trình học", icon: "file" },
      { to: "/manager/validation", label: "Kiểm định", icon: "activity" },
      { to: "/reviewer", label: "Kiểm duyệt vật lý", icon: "shield" },
      { to: "/workspace", label: "Mô phỏng", icon: "atom" },
    ] },
  ];

  const bell = <DropdownMenu.Root><DropdownMenu.Trigger><IconButton variant="ghost" color="gray" aria-label="Thông báo trường đăng ký gói"><LearningIcon name="bell" /></IconButton></DropdownMenu.Trigger><DropdownMenu.Content align="end" sideOffset={8}>
    <DropdownMenu.Label>Đăng ký & thanh toán của trường</DropdownMenu.Label><DropdownMenu.Separator />
    {notificationError ? <p>Không thể tải thông báo.</p> : notifications.length === 0 ? <p>Chưa có đăng ký đã thanh toán.</p> : notifications.map(item => <DropdownMenu.Item key={item.id} onSelect={() => navigate("/manager/schools")}><strong>{item.schoolName}</strong><span>{item.planCode} · {item.amountVnd.toLocaleString("vi-VN")} ₫{item.status === "REQUIRES_REVIEW" ? " · Cần đối soát" : ""}</span><small>{new Date(item.paidAt).toLocaleString("vi-VN")}</small></DropdownMenu.Item>)}
  </DropdownMenu.Content></DropdownMenu.Root>;

  return <AppSidebarLayout id="manager" subtitle="Vận hành" home="/manager" groups={groups} actions={bell} contentClassName="admin-shell" />;
}
