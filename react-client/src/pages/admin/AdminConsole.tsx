import { useState, useEffect } from "react";
import { usePhysliveStore } from "../../store/usePhysliveStore";
import { adminUsers, validationMetrics } from "../../api/adminApi";
import type { User } from "../../types/physlive";
import "../../styles/admin-console.css";

export default function AdminConsole() {
  const [activeTab, setActiveTab] = useState("users");
  const user = usePhysliveStore(s => s.user);

  if (!user || user.role !== "ADMIN") {
    return (
      <div className="admin-shell">
        <div className="access-denied">
          <h1>Truy cập bị từ chối</h1>
          <p>Bạn cần quyền quản trị viên để truy cập trang này.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="admin-shell">
      {/* Sidebar */}
      <aside className="admin-sidebar">
        <div className="admin-sidebar-header">
          <div className="admin-panel-icon">⚙</div>
          <span className="admin-panel-title">Admin Panel</span>
        </div>
        
        <nav className="admin-nav">
          <button
            className={`admin-nav-item ${activeTab === "dashboard" ? "active" : ""}`}
            onClick={() => setActiveTab("dashboard")}
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <rect x="3" y="3" width="7" height="7"/>
              <rect x="14" y="3" width="7" height="7"/>
              <rect x="14" y="14" width="7" height="7"/>
              <rect x="3" y="14" width="7" height="7"/>
            </svg>
            Dashboard
          </button>
          
          <button
            className={`admin-nav-item ${activeTab === "users" ? "active" : ""}`}
            onClick={() => setActiveTab("users")}
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
              <circle cx="9" cy="7" r="4"/>
              <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
              <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
            </svg>
            Users
          </button>
          
          <button
            className={`admin-nav-item ${activeTab === "feedback" ? "active" : ""}`}
            onClick={() => setActiveTab("feedback")}
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
            </svg>
            Feedback
          </button>
          
          <button
            className={`admin-nav-item ${activeTab === "messages" ? "active" : ""}`}
            onClick={() => setActiveTab("messages")}
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"/>
            </svg>
            Messages
          </button>
        </nav>
        
        <div className="admin-sidebar-footer">
          <button className="admin-back-btn">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M9 10l-5 5 5 5"/>
              <path d="M20 4v7a4 4 0 0 1-4 4H4"/>
            </svg>
            Back to Site
          </button>
        </div>
      </aside>

      {/* Main Content */}
      <main className="admin-main">
        {activeTab === "dashboard" && <DashboardView />}
        {activeTab === "users" && <UsersView />}
        {activeTab === "feedback" && <FeedbackView />}
        {activeTab === "messages" && <MessagesView />}
      </main>
    </div>
  );
}

function DashboardView() {
  const [users, setUsers] = useState<User[]>([]);
  const [metrics, setMetrics] = useState<{ total: number; failed: number; failureRate: number } | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchData = async () => {
      try {
        setLoading(true);
        const [usersData, metricsData] = await Promise.all([
          adminUsers(),
          validationMetrics()
        ]);
        setUsers(usersData);
        setMetrics(metricsData);
      } catch (err) {
        console.error("Failed to fetch dashboard data:", err);
      } finally {
        setLoading(false);
      }
    };
    
    fetchData();
  }, []);

  const stats = {
    totalUsers: users.length,
    activeUsers: users.filter(u => u.active).length,
    teachers: users.filter(u => u.role === "TEACHER").length,
    students: users.filter(u => u.role === "STUDENT").length,
    validationTotal: metrics?.total || 0,
    validationFailed: metrics?.failed || 0,
    validationSuccess: metrics ? metrics.total - metrics.failed : 0,
    validationRate: metrics ? ((1 - metrics.failureRate) * 100).toFixed(1) : "0"
  };

  if (loading) {
    return (
      <div className="admin-content">
        <div className="admin-loading">
          <div className="loading-spinner"></div>
          <p>Loading dashboard...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="admin-content">
      <header className="admin-content-header">
        <div>
          <h1 className="admin-content-title">Dashboard</h1>
          <p className="admin-content-subtitle">Overview of system statistics</p>
        </div>
      </header>
      
      <div className="admin-stats-grid">
        <div className="admin-stat-card">
          <div className="stat-label">Total Users</div>
          <div className="stat-value">{stats.totalUsers}</div>
        </div>
        <div className="admin-stat-card">
          <div className="stat-label">Active Users</div>
          <div className="stat-value">{stats.activeUsers}</div>
        </div>
        <div className="admin-stat-card">
          <div className="stat-label">Teachers</div>
          <div className="stat-value">{stats.teachers}</div>
        </div>
        <div className="admin-stat-card">
          <div className="stat-label">Students</div>
          <div className="stat-value">{stats.students}</div>
        </div>
        <div className="admin-stat-card">
          <div className="stat-label">Validation Runs</div>
          <div className="stat-value">{stats.validationTotal}</div>
        </div>
        <div className="admin-stat-card">
          <div className="stat-label">Success Rate</div>
          <div className="stat-value">{stats.validationRate}%</div>
        </div>
        <div className="admin-stat-card">
          <div className="stat-label">Successful</div>
          <div className="stat-value">{stats.validationSuccess}</div>
        </div>
        <div className="admin-stat-card">
          <div className="stat-label">Failed</div>
          <div className="stat-value">{stats.validationFailed}</div>
        </div>
      </div>
      
      <div className="admin-section">
        <h2 className="admin-section-title">System Health</h2>
        <div className="health-cards">
          <div className="health-card">
            <div className="health-icon success">✓</div>
            <div className="health-info">
              <div className="health-title">Validation Quality</div>
              <div className="health-value">{stats.validationRate}%</div>
              <div className="health-desc">System validation success rate</div>
            </div>
          </div>
          <div className="health-card">
            <div className="health-icon info">👥</div>
            <div className="health-info">
              <div className="health-title">User Base</div>
              <div className="health-value">{stats.totalUsers}</div>
              <div className="health-desc">{stats.teachers} teachers, {stats.students} students</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function UsersView() {
  const [searchQuery, setSearchQuery] = useState("");
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  
  // Fetch users from API
  useEffect(() => {
    const fetchUsers = async () => {
      try {
        setLoading(true);
        const data = await adminUsers();
        setUsers(data);
        setError(null);
      } catch (err) {
        console.error("Failed to fetch users:", err);
        setError("Không thể tải danh sách người dùng");
      } finally {
        setLoading(false);
      }
    };
    
    fetchUsers();
  }, []);
  
  // Filter users based on search
  const filteredUsers = users.filter(user => {
    const searchLower = searchQuery.toLowerCase();
    return (
      user.email.toLowerCase().includes(searchLower) ||
      (user.fullName && user.fullName.toLowerCase().includes(searchLower))
    );
  });

  // Calculate stats
  const stats = {
    totalUsers: users.length,
    admins: users.filter(u => u.role === "ADMIN").length,
    regularUsers: users.filter(u => u.role === "STUDENT" || u.role === "TEACHER").length,
    teachers: users.filter(u => u.role === "TEACHER").length,
    students: users.filter(u => u.role === "STUDENT").length,
    bannedUsers: users.filter(u => !u.active).length,
    activeUsers: users.filter(u => u.active).length,
  };

  const handleRefresh = async () => {
    try {
      setLoading(true);
      const data = await adminUsers();
      setUsers(data);
      setError(null);
    } catch (err) {
      console.error("Failed to refresh users:", err);
      setError("Không thể làm mới danh sách");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="admin-content">
      <header className="admin-content-header">
        <div>
          <div className="header-title-row">
            <h1 className="admin-content-title">User Management</h1>
            <span className="admin-role-badge">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
              </svg>
              Admin
            </span>
          </div>
          <p className="admin-content-subtitle">Manage all users and their roles</p>
        </div>
      </header>

      {/* Search Bar */}
      <div className="admin-search-bar">
        <svg className="search-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <circle cx="11" cy="11" r="8"/>
          <path d="m21 21-4.35-4.35"/>
        </svg>
        <input
          type="text"
          placeholder="Search users by email or name..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          className="admin-search-input"
        />
        <button className="search-refresh-btn" onClick={handleRefresh} disabled={loading}>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M21.5 2v6h-6M2.5 22v-6h6M2 11.5a10 10 0 0 1 18.8-4.3M22 12.5a10 10 0 0 1-18.8 4.2"/>
          </svg>
        </button>
      </div>

      {error && (
        <div className="admin-error-banner">
          {error}
        </div>
      )}

      {/* Stats Cards */}
      <div className="admin-stats-row">
        <div className="stat-mini-card">
          <div className="stat-mini-label">Total Users</div>
          <div className="stat-mini-value">{stats.totalUsers}</div>
        </div>
        <div className="stat-mini-card">
          <div className="stat-mini-label">Admins</div>
          <div className="stat-mini-value">{stats.admins}</div>
        </div>
        <div className="stat-mini-card">
          <div className="stat-mini-label">Teachers</div>
          <div className="stat-mini-value">{stats.teachers}</div>
        </div>
        <div className="stat-mini-card">
          <div className="stat-mini-label">Students</div>
          <div className="stat-mini-value">{stats.students}</div>
        </div>
        <div className="stat-mini-card stat-mini-highlight">
          <div className="stat-mini-label">Active Users</div>
          <div className="stat-mini-value">{stats.activeUsers}</div>
          <div className="stat-mini-subtitle">Currently enabled</div>
        </div>
      </div>

      {/* Users Table */}
      {loading ? (
        <div className="admin-loading">
          <div className="loading-spinner"></div>
          <p>Loading users...</p>
        </div>
      ) : (
        <div className="admin-table-container">
          <table className="admin-table">
            <thead>
              <tr>
                <th>Email</th>
                <th>Full Name</th>
                <th>Role</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {filteredUsers.length === 0 ? (
                <tr>
                  <td colSpan={5} style={{ textAlign: "center", padding: "40px", color: "#64748b" }}>
                    {searchQuery ? "No users found matching your search" : "No users available"}
                  </td>
                </tr>
              ) : (
                filteredUsers.map(user => (
                  <tr key={user.id}>
                    <td className="cell-email">{user.email}</td>
                    <td className="cell-name">{user.fullName || "—"}</td>
                    <td>
                      <span className={`role-badge role-${user.role.toLowerCase()}`}>
                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                          {user.role === "ADMIN" ? (
                            <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
                          ) : user.role === "TEACHER" ? (
                            <path d="M22 10v6M2 10l10-5 10 5-10 5z M2 10v6c0 2.21 4.47 4 10 4s10-1.79 10-4"/>
                          ) : (
                            <><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></>
                          )}
                        </svg>
                        {user.role}
                      </span>
                    </td>
                    <td>
                      <span className={`status-badge ${user.active ? "active" : "banned"}`}>
                        {user.active ? "Active" : "Inactive"}
                      </span>
                    </td>
                    <td>
                      <button className="action-menu-btn" title="Actions">
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                          <circle cx="12" cy="12" r="1"/>
                          <circle cx="12" cy="5" r="1"/>
                          <circle cx="12" cy="19" r="1"/>
                        </svg>
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function FeedbackView() {
  return (
    <div className="admin-content">
      <header className="admin-content-header">
        <div>
          <h1 className="admin-content-title">Feedback Management</h1>
          <p className="admin-content-subtitle">Review and respond to user feedback</p>
        </div>
      </header>
      <div className="admin-placeholder">
        <p>Feedback management coming soon</p>
      </div>
    </div>
  );
}

function MessagesView() {
  return (
    <div className="admin-content">
      <header className="admin-content-header">
        <div>
          <h1 className="admin-content-title">Messages</h1>
          <p className="admin-content-subtitle">Manage user conversations</p>
        </div>
      </header>
      <div className="admin-placeholder">
        <p>Messages management coming soon</p>
      </div>
    </div>
  );
}
