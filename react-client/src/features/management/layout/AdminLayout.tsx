import { useEffect, useState } from "react";
import { Avatar, Button, DropdownMenu, IconButton } from "@radix-ui/themes";
import { paymentNotifications, type PaymentNotification } from "../../billing/api/billingApi";
import { NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import LearningIcon from "../../../shared/ui/LearningIcon";
import { useSessionStore } from "../../../shared/auth/sessionStore";
import { clearToken } from "../../../shared/lib/token";
import { getRoleLabel } from "../../../shared/auth/roles";
import BrandMark from "../../../shared/ui/BrandMark";
import { ActivePill } from "../../../shared/effects/Motion";

type AdminNavItem = { label: string; path: string; icon: "grid" | "users" | "message" | "book" | "activity" };

const navigation: AdminNavItem[] = [
  { label: "Tổng quan", path: "/manager", icon: "grid" },
  { label: "Người dùng", path: "/manager/users", icon: "users" },
  { label: "Phản hồi", path: "/manager/feedback", icon: "message" },
  { label: "Tin nhắn", path: "/manager/messages", icon: "message" },
  { label: "Trường học", path: "/manager/schools", icon: "book" },
  { label: "Gói dịch vụ", path: "/manager/plans", icon: "book" },
  { label: "Thanh toán", path: "/manager/payments", icon: "activity" },
  { label: "Chương trình học", path: "/manager/curriculum", icon: "book" },
  { label: "Kiểm định", path: "/manager/validation", icon: "activity" },
  { label: "Kiểm duyệt vật lý", path: "/reviewer", icon: "book" },
  { label: "Mô phỏng", path: "/workspace", icon: "grid" },
];

const pageMeta: Record<string, { title: string; description: string }> = {
  "/manager": { title: "Tổng quan", description: "Theo dõi và vận hành nền tảng PhysLive" },
  "/manager/users": { title: "Quản lý người dùng", description: "Quản lý tài khoản và phân quyền người dùng" },
  "/manager/feedback": { title: "Phản hồi", description: "Tiếp nhận phản hồi từ người dùng" },
  "/manager/messages": { title: "Tin nhắn", description: "Trao đổi và hỗ trợ người dùng" },
  "/manager/schools": { title: "Trường học", description: "Quản lý trường học và tài khoản liên quan" },
  "/manager/plans": { title: "Gói dịch vụ", description: "Quản lý giá và hạn mức của trường" },
  "/manager/payments": { title: "Thanh toán", description: "Theo dõi và đối soát thanh toán" },
  "/manager/curriculum": { title: "Chương trình học", description: "Quản lý nội dung chương trình học" },
  "/manager/validation": { title: "Kiểm định", description: "Theo dõi kết quả kiểm định mô phỏng" },
};

export default function AdminLayout() {
  const user = useSessionStore(state => state.user);
  const visibleNavigation = navigation;
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const [profileOpen, setProfileOpen] = useState(false);
  const [notifications, setNotifications] = useState<PaymentNotification[]>([]);
  const [notificationError, setNotificationError] = useState(false);
  useEffect(() => {
    let active = true;
    const load = () => void paymentNotifications().then(items => { if (active) { setNotifications(items); setNotificationError(false); } }).catch(() => { if (active) setNotificationError(true); });
    load(); const timer = window.setInterval(load, 30000);
    return () => { active = false; window.clearInterval(timer); };
  }, []);
  const meta = pageMeta[pathname] ?? pageMeta["/manager"];
  const initials = user?.fullName?.trim().slice(0, 1).toUpperCase() || "A";

  const logout = () => {
    clearToken();
    useSessionStore.getState().setUser(null);
    navigate("/login", { replace: true });
  };

  return <div className="admin-shell">
    <aside className="admin-sidebar">
      <NavLink to="/manager" className="admin-brand" aria-label="Mở khu vực MANAGER">
        <BrandMark size={32} wordmark={false} />
        <span className="admin-brand-copy"><strong>PhysLive</strong><small>Vận hành hệ thống</small></span>
      </NavLink>
      <nav className="admin-nav" aria-label="Điều hướng MANAGER">
        {visibleNavigation.map(item => <NavLink key={item.path} to={item.path} end={item.path === "/manager"} className={({ isActive }) => `admin-nav-item ${isActive ? "active" : ""}`}>
          {({ isActive }) => <>{isActive && <ActivePill id="admin-nav-pill" />}<LearningIcon name={item.icon} /><span>{item.label}</span></>}
        </NavLink>)}
      </nav>
      <div className="admin-sidebar-footer">
        <div className="admin-sidebar-user"><Avatar size="2" radius="full" src={user?.avatarUrl || undefined} fallback={initials} /><span className="admin-sidebar-user-copy"><strong>{user?.fullName || "MANAGER"}</strong><small>{user?.email || ""}</small></span></div>
        <Button variant="ghost" color="gray" onClick={() => navigate("/")}><LearningIcon name="back" />Về trang chủ</Button>
      </div>
    </aside>
    <main className="admin-main">
      <header className="admin-topbar">
        <div className="admin-page-topbar-copy">
          <div className="admin-page-title-line"><strong>{meta.title}</strong><span className="admin-role-pill"><LearningIcon name="shield" />{getRoleLabel(user?.role ?? "")}</span></div>

        </div>
        <div className="admin-topbar-actions">
          <DropdownMenu.Root><DropdownMenu.Trigger><IconButton variant="soft" color="gray" aria-label="Thông báo trường đăng ký gói"><LearningIcon name="bell" /></IconButton></DropdownMenu.Trigger><DropdownMenu.Content align="end" sideOffset={8}>
            <DropdownMenu.Label>Đăng ký & thanh toán của trường</DropdownMenu.Label><DropdownMenu.Separator />
            {notificationError ? <p>Không thể tải thông báo.</p> : notifications.length === 0 ? <p>Chưa có đăng ký đã thanh toán.</p> : notifications.map(item => <DropdownMenu.Item key={item.id} onSelect={() => navigate("/manager/schools")}><strong>{item.schoolName}</strong><span>{item.planCode} · {item.amountVnd.toLocaleString("vi-VN")} ₫{item.status === "REQUIRES_REVIEW" ? " · Cần đối soát" : ""}</span><small>{new Date(item.paidAt).toLocaleString("vi-VN")}</small></DropdownMenu.Item>)}
          </DropdownMenu.Content></DropdownMenu.Root>
          <div className="admin-profile-wrap">
            <DropdownMenu.Root open={profileOpen} onOpenChange={setProfileOpen}>
              <DropdownMenu.Trigger><Button variant="ghost" color="gray"><Avatar size="2" radius="full" src={user?.avatarUrl || undefined} fallback={initials} /><span>{user?.email || "MANAGER"}</span></Button></DropdownMenu.Trigger>
              <DropdownMenu.Content align="end"><DropdownMenu.Label>{getRoleLabel(user?.role ?? "")}</DropdownMenu.Label><DropdownMenu.Item onSelect={() => navigate("/profile")}>Hồ sơ cá nhân</DropdownMenu.Item><DropdownMenu.Item onSelect={() => navigate("/")}>Về trang chủ</DropdownMenu.Item><DropdownMenu.Separator /><DropdownMenu.Item color="red" onSelect={logout}>Đăng xuất</DropdownMenu.Item></DropdownMenu.Content>
            </DropdownMenu.Root>
          </div>
        </div>
      </header>
      <nav className="admin-mobile-nav" aria-label="Điều hướng MANAGER mobile">
        {visibleNavigation.map(item => <NavLink key={item.path} to={item.path} end={item.path === "/manager"} className={({ isActive }) => `admin-nav-item ${isActive ? "active" : ""}`}>
          {({ isActive }) => <>{isActive && <ActivePill id="admin-nav-pill-mobile" />}<LearningIcon name={item.icon} /><span>{item.label}</span></>}
        </NavLink>)}
      </nav>
      <Outlet />
    </main>
  </div>;
}