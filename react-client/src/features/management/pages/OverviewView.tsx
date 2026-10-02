import { useEffect, useState } from "react";
import LearningIcon from "../../../shared/ui/LearningIcon";
import { Badge, Heading, Progress, Text } from "@radix-ui/themes";
import { adminUsers } from "../../account/api/userApi";
import { validationMetrics } from "../api/operationsApi";
import type { User } from "../../../shared/auth/types";
import { apiMessage } from "../../../shared/lib/apiError";
import { PageHeader, Panel, Loading, ErrorNotice, Button, Stat } from "../../../shared/ui/ManagementUI";

export function OverviewView() {
  const [users, setUsers] = useState<User[]>([]);
  const [metrics, setMetrics] = useState<{ total: number; failed: number; failureRate: number } | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const load = async () => { setLoading(true); try { const [nextUsers, nextMetrics] = await Promise.all([adminUsers(), validationMetrics()]); setUsers(nextUsers); setMetrics(nextMetrics); setError(""); } catch (e) { setError(apiMessage(e, "Không thể tải tổng quan.")); } finally { setLoading(false); } };
  useEffect(() => { void load(); }, []);
  const passed = metrics ? metrics.total - metrics.failed : 0;
  const rate = metrics?.total ? (passed / metrics.total) * 100 : 0;
  if (loading) return <div className="admin-content"><Loading /></div>;
  return <div className="admin-content"><PageHeader title="Tổng quan" description="Theo dõi người dùng và chất lượng mô phỏng trong PhysLive." action={<Button onClick={() => void load()}><LearningIcon name="refresh" /> Làm mới</Button>} />{error && <ErrorNotice error={error} onRetry={() => void load()} />}
    <div className="admin-stats-grid"><Stat label="Tổng người dùng" value={users.length} icon="users" tone="blue" /><Stat label="Đang hoạt động" value={users.filter(item => item.active !== false).length} icon="activity" tone="green" /><Stat label="Giáo viên" value={users.filter(item => item.role === "STAFF").length} icon="book" tone="orange" /><Stat label="Kiểm định đạt" value={`${rate.toFixed(1)}%`} icon="shield" tone="purple" /></div>
    <div className="admin-dashboard-grid"><Panel title="Kết quả kiểm định"><Heading as="h3" size="8" color="indigo" className="admin-health-value">{rate.toFixed(1)}%</Heading><Progress value={Math.max(0, Math.min(100, rate))} color="indigo" aria-label="Tỷ lệ kiểm định đạt" /><div className="admin-health-meta"><Badge color="green" size="2">{passed} lượt đạt</Badge><Badge color="amber" size="2">{metrics?.failed ?? 0} lượt lỗi</Badge></div></Panel><Panel title="Phân bổ tài khoản"><div className="admin-breakdown-list"><div><Text>Giáo viên</Text><Badge color="gray" size="2">{users.filter(item => item.role === "STAFF").length}</Badge></div><div><Text>Học sinh</Text><Badge color="gray" size="2">{users.filter(item => item.role === "STUDENT").length}</Badge></div><div><Text>Người kiểm duyệt</Text><Badge color="gray" size="2">{users.filter(item => item.role === "REVIEWER").length}</Badge></div></div></Panel></div>
  </div>;
}

