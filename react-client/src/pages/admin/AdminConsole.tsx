import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { usePhysliveStore } from "../../store/usePhysliveStore";
import { adminUsers, validationMetrics } from "../../api/adminApi";
import type { User } from "../../types/physlive";
import LearningIcon from "../../components/common/LearningIcon";
import "../../styles/admin-console.css";

type AdminTab = "dashboard" | "users" | "feedback" | "messages";
type AdminIconName = "grid" | "users" | "message" | "activity" | "book" | "shield" | "refresh" | "check" | "search";

const navigation: Array<{ id: AdminTab; label: string; icon: AdminIconName }> = [
  { id: "dashboard", label: "Tổng quan", icon: "grid" },
  { id: "users", label: "Người dùng", icon: "users" },
  { id: "feedback", label: "Phản hồi", icon: "message" },
  { id: "messages", label: "Tin nhắn", icon: "message" },
];

const roleLabels: Record<string, string> = {
  ADMIN: "Quản trị viên",
  TEACHER: "Giáo viên",
  STUDENT: "Học sinh",
  REVIEWER: "Thẩm định viên",
};

export default function AdminConsole() {
  const [activeTab, setActiveTab] = useState<AdminTab>("dashboard");
  const user = usePhysliveStore(state => state.user);
  const navigate = useNavigate();

  if (!user || user.role !== "ADMIN") {
    return (
      <div className="admin-shell admin-shell-denied">
        <section className="admin-access-card">
          <div className="admin-access-icon"><LearningIcon name="shield" /></div>
          <p className="admin-eyebrow">PhysLive Admin</p>
          <h1>Không thể truy cập</h1>
          <p>Bạn cần quyền quản trị viên để mở khu vực này.</p>
          <button type="button" className="admin-primary-button" onClick={() => navigate("/")}>Về trang chủ</button>
        </section>
      </div>
    );
  }

  const activePage = navigation.find(item => item.id === activeTab) ?? navigation[0];
  const initials = user.fullName?.trim().slice(0, 1).toUpperCase() || "A";

  return (
    <div className="admin-shell">
      <aside className="admin-sidebar">
        <button type="button" className="admin-brand" onClick={() => navigate("/")} aria-label="Về trang chủ PhysLive">
          <img src="/favicon.ico" alt="" aria-hidden="true" />
          <span className="admin-brand-copy">
            <strong>PhysLive</strong>
            <small>Quản trị hệ thống</small>
          </span>
        </button>

        <p className="admin-sidebar-label">Không gian quản trị</p>
        <nav className="admin-nav" aria-label="Điều hướng quản trị">
          {navigation.map(item => (
            <button
              key={item.id}
              type="button"
              className={`admin-nav-item ${activeTab === item.id ? "active" : ""}`}
              aria-current={activeTab === item.id ? "page" : undefined}
              onClick={() => setActiveTab(item.id)}
            >
              <LearningIcon name={item.icon} />
              <span>{item.label}</span>
            </button>
          ))}
        </nav>

        <div className="admin-sidebar-footer">
          <div className="admin-sidebar-user">
            <span className="admin-user-avatar">{initials}</span>
            <span className="admin-sidebar-user-copy">
              <strong>{user.fullName || "Quản trị viên"}</strong>
              <small>{roleLabels[user.role] ?? user.role}</small>
            </span>
          </div>
          <button type="button" className="admin-back-button" onClick={() => navigate("/")}>
            <LearningIcon name="back" />
            <span>Về trang chủ</span>
          </button>
        </div>
      </aside>

      <main className="admin-main">
        <header className="admin-topbar">
          <div>
            <span className="admin-topbar-kicker">PhysLive / Quản trị</span>
            <strong>{activePage.label}</strong>
          </div>
          <div className="admin-topbar-user">
            <span className="admin-user-avatar">{initials}</span>
            <span>{user.fullName || user.email}</span>
          </div>
        </header>

        {activeTab === "dashboard" && <DashboardView />}
        {activeTab === "users" && <UsersView />}
        {activeTab === "feedback" && <FeedbackView />}
        {activeTab === "messages" && <MessagesView />}
      </main>
    </div>
  );
}

function PageHeader({ title, description }: { title: string; description: string }) {
  return (
    <header className="admin-content-header">
      <p className="admin-eyebrow">PhysLive Admin</p>
      <h1 className="admin-content-title">{title}</h1>
      <p className="admin-content-subtitle">{description}</p>
    </header>
  );
}

function StatCard({ label, value, icon, tone, caption }: {
  label: string;
  value: string | number;
  icon: AdminIconName;
  tone: "blue" | "green" | "orange" | "purple";
  caption?: string;
}) {
  return (
    <article className="admin-stat-card">
      <div className={`admin-stat-icon ${tone}`}><LearningIcon name={icon} /></div>
      <p>{label}</p>
      <strong>{value}</strong>
      {caption && <small>{caption}</small>}
    </article>
  );
}

function DashboardView() {
  const [users, setUsers] = useState<User[]>([]);
  const [metrics, setMetrics] = useState<{ total: number; failed: number; failureRate: number } | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    const fetchData = async () => {
      try {
        setLoading(true);
        const [usersData, metricsData] = await Promise.all([adminUsers(), validationMetrics()]);
        setUsers(usersData);
        setMetrics(metricsData);
        setError("");
      } catch {
        setError("Không thể tải dữ liệu tổng quan.");
      } finally {
        setLoading(false);
      }
    };
    void fetchData();
  }, []);

  const totalUsers = users.length;
  const activeUsers = users.filter(item => item.active).length;
  const teachers = users.filter(item => item.role === "TEACHER").length;
  const validationRate = metrics ? Math.max(0, Math.min(100, (1 - metrics.failureRate) * 100)) : 0;
  const validationSuccess = metrics ? metrics.total - metrics.failed : 0;

  if (loading) return <LoadingState label="Đang tải tổng quan..." />;

  return (
    <div className="admin-content">
      <PageHeader title="Tổng quan" description="Theo dõi người dùng và chất lượng mô phỏng trong PhysLive." />
      {error && <div className="admin-error-banner" role="alert">{error}</div>}

      <section className="admin-stats-grid" aria-label="Chỉ số tổng quan">
        <StatCard label="Tổng người dùng" value={totalUsers} icon="users" tone="blue" />
        <StatCard label="Đang hoạt động" value={activeUsers} icon="activity" tone="green" />
        <StatCard label="Giáo viên" value={teachers} icon="book" tone="orange" />
        <StatCard label="Tỷ lệ kiểm tra đạt" value={`${validationRate.toFixed(1)}%`} icon="shield" tone="purple" />
      </section>

      <section className="admin-dashboard-grid">
        <article className="admin-panel admin-health-panel">
          <div className="admin-panel-heading">
            <div>
              <p className="admin-eyebrow">Hệ thống</p>
              <h2>Chất lượng validation</h2>
            </div>
            <div className="admin-panel-icon green"><LearningIcon name="check" /></div>
          </div>
          <div className="admin-health-value">{validationRate.toFixed(1)}%</div>
          <div className="admin-health-track" aria-label={`Tỷ lệ thành công ${validationRate.toFixed(1)}%`}>
            <span style={{ width: `${validationRate}%` }} />
          </div>
          <div className="admin-health-meta">
            <span>{validationSuccess} lượt đạt</span>
            <span>{metrics?.failed ?? 0} lượt lỗi</span>
          </div>
        </article>

        <article className="admin-panel">
          <div className="admin-panel-heading">
            <div>
              <p className="admin-eyebrow">Phân bổ</p>
              <h2>Người dùng</h2>
            </div>
            <div className="admin-panel-icon blue"><LearningIcon name="users" /></div>
          </div>
          <div className="admin-breakdown-list">
            <div><span><i className="admin-dot blue" />Giáo viên</span><strong>{teachers}</strong></div>
            <div><span><i className="admin-dot orange" />Học sinh</span><strong>{users.filter(item => item.role === "STUDENT").length}</strong></div>
            <div><span><i className="admin-dot green" />Đang hoạt động</span><strong>{activeUsers}</strong></div>
          </div>
        </article>
      </section>
    </div>
  );
}

function UsersView() {
  const [searchQuery, setSearchQuery] = useState("");
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const fetchUsers = async () => {
    try {
      setLoading(true);
      setUsers(await adminUsers());
      setError("");
    } catch {
      setError("Không thể tải danh sách người dùng.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void fetchUsers(); }, []);

  const query = searchQuery.trim().toLowerCase();
  const filteredUsers = users.filter(item =>
    item.email.toLowerCase().includes(query) || (item.fullName || "").toLowerCase().includes(query),
  );

  return (
    <div className="admin-content">
      <PageHeader title="Người dùng" description="Xem danh sách tài khoản và trạng thái hoạt động." />

      <div className="admin-toolbar">
        <label className="admin-search-field">
          <LearningIcon name="search" />
          <span className="sr-only">Tìm người dùng</span>
          <input
            type="search"
            placeholder="Tìm theo tên hoặc email..."
            value={searchQuery}
            onChange={event => setSearchQuery(event.target.value)}
          />
        </label>
        <button type="button" className="admin-icon-button" aria-label="Làm mới danh sách" onClick={() => void fetchUsers()} disabled={loading}>
          <LearningIcon name="refresh" />
        </button>
      </div>

      {error && <div className="admin-error-banner" role="alert">{error}</div>}

      <section className="admin-panel admin-users-panel">
        <div className="admin-panel-heading admin-panel-heading-inline">
          <div>
            <p className="admin-eyebrow">Danh sách tài khoản</p>
            <h2>{filteredUsers.length} người dùng</h2>
          </div>
          <span className="admin-muted-label">Cập nhật theo yêu cầu</span>
        </div>
        {loading ? <LoadingState label="Đang tải người dùng..." compact /> : (
          <div className="admin-table-scroll">
            <table className="admin-table">
              <thead>
                <tr><th>Người dùng</th><th>Vai trò</th><th>Trạng thái</th></tr>
              </thead>
              <tbody>
                {filteredUsers.length === 0 ? (
                  <tr><td colSpan={3} className="admin-empty-table">{query ? "Không tìm thấy người dùng phù hợp." : "Chưa có người dùng."}</td></tr>
                ) : filteredUsers.map(item => (
                  <tr key={item.id}>
                    <td>
                      <div className="admin-table-user">
                        {item.avatarUrl ? <img src={item.avatarUrl} alt="" /> : <span className="admin-user-avatar">{(item.fullName || item.email).slice(0, 1).toUpperCase()}</span>}
                        <span><strong>{item.fullName || "Chưa cập nhật tên"}</strong><small>{item.email}</small></span>
                      </div>
                    </td>
                    <td><span className={`admin-role-badge ${item.role.toLowerCase()}`}><LearningIcon name={item.role === "ADMIN" ? "shield" : item.role === "TEACHER" ? "book" : "users"} />{roleLabels[item.role] ?? item.role}</span></td>
                    <td><span className={`admin-status ${item.active ? "active" : "inactive"}`}><i />{item.active ? "Đang hoạt động" : "Đã khóa"}</span></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}

function EmptyAdminView({ title, description, icon }: { title: string; description: string; icon: AdminIconName }) {
  return (
    <div className="admin-content">
      <PageHeader title={title} description={description} />
      <section className="admin-empty-panel">
        <div className="admin-empty-icon"><LearningIcon name={icon} /></div>
        <h2>Khu vực đang được hoàn thiện</h2>
        <p>Chức năng này sẽ được bổ sung trong phiên bản tiếp theo.</p>
      </section>
    </div>
  );
}

function FeedbackView() {
  return <EmptyAdminView title="Phản hồi" description="Theo dõi góp ý để cải thiện trải nghiệm học tập." icon="message" />;
}

function MessagesView() {
  return <EmptyAdminView title="Tin nhắn" description="Quản lý trao đổi giữa PhysLive và người dùng." icon="message" />;
}

function LoadingState({ label, compact = false }: { label: string; compact?: boolean }) {
  return <div className={`admin-loading ${compact ? "compact" : ""}`}><span className="admin-loading-spinner" /><p>{label}</p></div>;
}
