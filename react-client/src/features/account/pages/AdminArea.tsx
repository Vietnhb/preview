import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Badge, Button, Card, Heading, Text } from "@radix-ui/themes";
import { adminUsers } from "../api/userApi";
import { apiMessage } from "../../../shared/lib/apiError";
import { ROLE_LABELS, type RoleType } from "../../../shared/auth/roles";
import type { User } from "../../../shared/auth/types";
import { useSessionStore } from "../../../shared/auth/sessionStore";
import AppSidebarLayout, { type SidebarGroup } from "../../../shared/layout/AppSidebar";
import CountUp from "../../../shared/effects/reactbits/CountUp";
import "../styles/admin-directory.css";

const groups: SidebarGroup[] = [
  { items: [{ to: "/admin", label: "Tổng quan", icon: "grid", end: true }] },
  { label: "Quản trị", items: [{ to: "/admin/users", label: "Tài khoản người dùng", icon: "users" }] },
];

/** ADMIN area: sees every account, manages MANAGER accounts only. */
export default function AdminAreaLayout() {
  return <AppSidebarLayout id="admin" subtitle="Quản trị hệ thống" home="/admin" groups={groups} />;
}

const ROLE_ORDER: RoleType[] = ["ADMIN", "MANAGER", "REVIEWER", "SCHOOL", "STAFF", "STUDENT"];

export function AdminOverviewPage() {
  const name = useSessionStore(state => state.user?.fullName);
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  useEffect(() => {
    let active = true;
    adminUsers().then(items => { if (active) { setUsers(items); setError(""); } })
      .catch(reason => { if (active) setError(apiMessage(reason, "Không thể tải danh sách tài khoản.")); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);
  const managers = users.filter(item => item.role === "MANAGER");
  const locked = users.filter(item => item.active === false).length;
  const stats = [
    { label: "Tổng tài khoản", value: users.length },
    { label: "Tài khoản vận hành (MANAGER)", value: managers.length },
    { label: "Đang hoạt động", value: users.length - locked },
    { label: "Tạm khóa", value: locked },
  ];
  const firstName = name?.trim().split(/\s+/).at(-1);

  return <div className="directory-content admin-home">
    <div className="directory-heading"><div><Heading as="h1" size="7">{firstName ? `Chào ${firstName}` : "Tổng quan"}</Heading><Text as="p" size="2" color="gray" mt="1">Bạn quản lý tài khoản vận hành (MANAGER) và xem được toàn bộ tài khoản trong hệ thống.</Text></div>
      <Button asChild size="3"><Link to="/admin/users">Mở danh sách tài khoản</Link></Button></div>
    {error && <div className="ops-alert" role="alert">{error}</div>}
    <div className="directory-stats">{stats.map(stat => <Card key={stat.label} size="3" className="directory-stat"><Text as="p" size="2" color="gray">{stat.label}</Text><Heading as="p" size="8" mt="2">{loading ? "—" : <CountUp to={stat.value} duration={1} />}</Heading></Card>)}</div>
    <div className="admin-home__grid">
      <Card size="3"><Heading as="h2" size="4" mb="3">Tài khoản theo vai trò</Heading>
        <ul className="admin-home__roles">{ROLE_ORDER.map(role => {
          const count = users.filter(item => item.role === role).length;
          const share = users.length ? (count / users.length) * 100 : 0;
          return <li key={role}><span>{ROLE_LABELS[role]}</span><span className="admin-home__bar" aria-hidden="true"><span style={{ width: `${share}%` }} /></span><strong>{loading ? "—" : count}</strong></li>;
        })}</ul>
      </Card>
      <Card size="3"><Heading as="h2" size="4" mb="3">Tài khoản vận hành</Heading>
        {loading ? <Text size="2" color="gray">Đang tải…</Text> : managers.length === 0 ? <Text size="2" color="gray">Chưa có tài khoản MANAGER nào. Tạo tài khoản đầu tiên trong mục Tài khoản người dùng.</Text>
          : <ul className="admin-home__managers">{managers.slice(0, 6).map(item => <li key={item.id}><span><strong>{item.fullName || item.email}</strong><small>{item.email}</small></span><Badge color={item.active === false ? "gray" : "green"} variant="soft">{item.active === false ? "Tạm khóa" : "Hoạt động"}</Badge></li>)}</ul>}
      </Card>
    </div>
  </div>;
}
