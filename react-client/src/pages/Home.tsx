import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import axios from "axios";
import { getToken } from "../utils/token";
import { getMe } from "../api/userApi";
import type { User } from "../types/user";
import "../styles/home.css";

function Home() {
  const token = getToken();
  const navigate = useNavigate();
  const [user, setUser] = useState<User | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!token) return;
    void getMe()
      .then((data) => { setUser(data); })
      .catch((err: unknown) => {
        if (axios.isAxiosError<{ message?: string }>(err)) {
          setError(err.response?.data?.message ?? "Không thể tải thông tin người dùng");
          return;
        }
        setError("Không thể tải thông tin người dùng");
      });
  }, [token]);

  return (
    <div className="home-page">
      {/* Simple Top Navigation */}
      <nav className="home-navbar">
        <div className="home-nav-container">
          <div className="home-nav-brand">
            <img className="home-nav-icon" src="/favicon.ico" alt="" />
            <span className="home-nav-title">PhysLive</span>
          </div>
          <div className="home-nav-links">
            <button onClick={() => navigate("/workspace")} className="home-nav-link">
              Workspace
            </button>
            {token ? (
              <>
                <button onClick={() => navigate("/library")} className="home-nav-link">
                  Thư viện
                </button>
                <div className="home-nav-user">
                  <span className="home-nav-avatar">
                    {user?.fullName.charAt(0).toUpperCase() || "U"}
                  </span>
                  <span className="home-nav-name">{user?.fullName}</span>
                </div>
              </>
            ) : (
              <>
                <button onClick={() => navigate("/login")} className="home-nav-link">
                  Đăng nhập
                </button>
                <button onClick={() => navigate("/signup")} className="home-nav-btn-primary">
                  Đăng ký
                </button>
              </>
            )}
          </div>
        </div>
      </nav>
      
      {/* Hero Section */}
      <section className="hero-section">
        <div className="hero-container">
          <div className="hero-content">
            <div className="hero-badge">
              <span className="hero-badge-icon">⚛️</span>
              <span>PhysLive Studio</span>
            </div>
            <h1 className="hero-title">
              Mô phỏng vật lý <br />
              <span className="hero-highlight">Trực quan & Tương tác</span>
            </h1>
            <p className="hero-description">
              Nền tảng mô phỏng vật lý chuyên nghiệp dành cho giáo viên. 
              Tạo bài giảng sinh động với đồ thị, công thức và chuyển động thực tế.
            </p>
            
            <div className="hero-actions">
              <button 
                className="btn btn-primary"
                onClick={() => navigate("/workspace")}
              >
                <span className="btn-icon">▶</span>
                Bắt đầu mô phỏng
              </button>
              {!token && (
                <button 
                  className="btn btn-secondary"
                  onClick={() => navigate("/login")}
                >
                  Đăng nhập
                </button>
              )}
            </div>

            {token && user && (
              <div className="hero-user-info">
                <div className="user-avatar">
                  {user.fullName.charAt(0).toUpperCase()}
                </div>
                <div className="user-details">
                  <strong>{user.fullName}</strong>
                  <span className="user-role-badge">{user.role}</span>
                </div>
              </div>
            )}
          </div>

          <div className="hero-visual">
            <div className="visual-card">
              <div className="visual-formula">
                <span className="formula-label">Phương trình chuyển động</span>
                <div className="formula-equation">
                  x(t) = x₀ + v₀t + ½at²
                </div>
              </div>
              <div className="visual-graph">
                <svg viewBox="0 0 200 120" className="mini-graph">
                  <defs>
                    <linearGradient id="graphGradient" x1="0%" y1="0%" x2="0%" y2="100%">
                      <stop offset="0%" stopColor="#2563eb" stopOpacity="0.3"/>
                      <stop offset="100%" stopColor="#2563eb" stopOpacity="0"/>
                    </linearGradient>
                  </defs>
                  {/* Grid */}
                  <line x1="20" y1="20" x2="20" y2="100" stroke="#e5e7eb" strokeWidth="1"/>
                  <line x1="20" y1="100" x2="180" y2="100" stroke="#e5e7eb" strokeWidth="1"/>
                  {/* Curve */}
                  <path 
                    d="M 20 100 Q 60 80, 100 50 T 180 20" 
                    fill="url(#graphGradient)" 
                    stroke="#2563eb" 
                    strokeWidth="2"
                  />
                  {/* Points */}
                  <circle cx="20" cy="100" r="3" fill="#2563eb"/>
                  <circle cx="100" cy="50" r="3" fill="#2563eb"/>
                  <circle cx="180" cy="20" r="3" fill="#2563eb"/>
                </svg>
              </div>
              <div className="visual-metrics">
                <div className="metric">
                  <span className="metric-label">Vận tốc</span>
                  <span className="metric-value">12.5 m/s</span>
                </div>
                <div className="metric">
                  <span className="metric-label">Gia tốc</span>
                  <span className="metric-value">2.0 m/s²</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Features Section */}
      <section className="features-section">
        <div className="features-container">
          <div className="section-header">
            <h2>Tính năng nổi bật</h2>
            <p>Công cụ hoàn chỉnh cho giảng dạy vật lý hiện đại</p>
          </div>

          <div className="features-grid">
            <div className="feature-card">
              <div className="feature-icon">📊</div>
              <h3>Workspace 3D</h3>
              <p>Giao diện 3 panel chuyên nghiệp với canvas mô phỏng, thư viện và bảng điều khiển tham số.</p>
              <button 
                className="feature-link"
                onClick={() => navigate("/workspace")}
              >
                Khám phá →
              </button>
            </div>

            <div className="feature-card">
              <div className="feature-icon">⚡</div>
              <h3>Real-time Simulation</h3>
              <p>Mô phỏng chuyển động thời gian thực với điều chỉnh tham số tức thì, tốc độ phát linh hoạt.</p>
            </div>

            <div className="feature-card">
              <div className="feature-icon">📐</div>
              <h3>Công thức & Đồ thị</h3>
              <p>Hiển thị phương trình toán học, vẽ đồ thị tự động và đối chiếu kết quả giải tích.</p>
            </div>

            <div className="feature-card">
              <div className="feature-icon">🎯</div>
              <h3>Thư viện Mô phỏng</h3>
              <p>Quản lý bài giảng theo folder, lưu trữ và tái sử dụng các mô phỏng đã tạo.</p>
            </div>

            <div className="feature-card">
              <div className="feature-icon">🔧</div>
              <h3>Tùy chỉnh Tham số</h3>
              <p>Slider trực quan cho vận tốc, gia tốc, khối lượng, lực... Thay đổi và quan sát ngay.</p>
            </div>

            <div className="feature-card">
              <div className="feature-icon">📱</div>
              <h3>Responsive Design</h3>
              <p>Giao diện tối ưu cho desktop, tablet và mobile. Giảng dạy linh hoạt mọi lúc mọi nơi.</p>
            </div>
          </div>
        </div>
      </section>

      {/* Simulation Types Section */}
      <section className="simulations-section">
        <div className="simulations-container">
          <div className="section-header">
            <h2>Các dạng mô phỏng</h2>
            <p>Hỗ trợ đa dạng chủ đề vật lý từ cơ bản đến nâng cao</p>
          </div>

          <div className="simulations-grid">
            <div className="simulation-card">
              <div className="simulation-preview">
                <span className="simulation-icon">→</span>
              </div>
              <h3>Chuyển động thẳng</h3>
              <p>Chuyển động đều, biến đổi đều, phương trình động học cơ bản.</p>
              <ul className="simulation-tags">
                <li>x = x₀ + v₀t + ½at²</li>
                <li>v = v₀ + at</li>
              </ul>
            </div>

            <div className="simulation-card">
              <div className="simulation-preview">
                <span className="simulation-icon">↗</span>
              </div>
              <h3>Ném xiên</h3>
              <p>Chuyển động parabol, phạm vi xa, thời gian bay, độ cao cực đại.</p>
              <ul className="simulation-tags">
                <li>Quỹ đạo 2D</li>
                <li>Góc ném tối ưu</li>
              </ul>
            </div>

            <div className="simulation-card">
              <div className="simulation-preview">
                <span className="simulation-icon">⚛</span>
              </div>
              <h3>Va chạm</h3>
              <p>Va chạm đàn hồi, không đàn hồi, bảo toàn động lượng và năng lượng.</p>
              <ul className="simulation-tags">
                <li>m₁v₁ + m₂v₂</li>
                <li>Hệ số phục hồi</li>
              </ul>
            </div>

            <div className="simulation-card">
              <div className="simulation-preview">
                <span className="simulation-icon">⚡</span>
              </div>
              <h3>Mạch điện</h3>
              <p>Mạch RC, RL, RLC, sạc và xả tụ điện, định luật Ohm.</p>
              <ul className="simulation-tags">
                <li>V = IR</li>
                <li>τ = RC</li>
              </ul>
            </div>
          </div>
        </div>
      </section>

      {/* CTA Section */}
      <section className="cta-section">
        <div className="cta-container">
          <div className="cta-content">
            <h2>Sẵn sàng nâng tầm bài giảng?</h2>
            <p>
              Tham gia cùng hàng trăm giáo viên đang sử dụng PhysLive để tạo ra 
              những bài học sinh động và dễ hiểu hơn cho học sinh.
            </p>
            <div className="cta-actions">
              {!token ? (
                <>
                  <button 
                    className="btn btn-primary btn-large"
                    onClick={() => navigate("/signup")}
                  >
                    Đăng ký miễn phí
                  </button>
                  <button 
                    className="btn btn-secondary btn-large"
                    onClick={() => navigate("/login")}
                  >
                    Đăng nhập
                  </button>
                </>
              ) : (
                <>
                  <button 
                    className="btn btn-primary btn-large"
                    onClick={() => navigate("/workspace")}
                  >
                    <span className="btn-icon">▶</span>
                    Mở Workspace
                  </button>
                  {user?.role === "ADMIN" && (
                    <button 
                      className="btn btn-secondary btn-large"
                      onClick={() => navigate("/admin")}
                    >
                      Quản trị hệ thống
                    </button>
                  )}
                </>
              )}
            </div>
          </div>

          <div className="cta-stats">
            <div className="stat-card">
              <div className="stat-value">500+</div>
              <div className="stat-label">Giáo viên</div>
            </div>
            <div className="stat-card">
              <div className="stat-value">2,000+</div>
              <div className="stat-label">Mô phỏng</div>
            </div>
            <div className="stat-card">
              <div className="stat-value">10,000+</div>
              <div className="stat-label">Học sinh</div>
            </div>
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer className="home-footer">
        <div className="footer-container">
          <div className="footer-brand">
            <div className="footer-logo">
              <span className="footer-logo-icon">⚛️</span>
              <span>PhysLive</span>
            </div>
            <p>Nền tảng mô phỏng vật lý cho giáo viên</p>
          </div>

          <div className="footer-links">
            <div className="footer-column">
              <h4>Sản phẩm</h4>
              <a href="#" onClick={(e) => { e.preventDefault(); navigate("/workspace"); }}>Workspace</a>
              <a href="#" onClick={(e) => { e.preventDefault(); }}>Thư viện</a>
              <a href="#" onClick={(e) => { e.preventDefault(); }}>Tài liệu</a>
            </div>

            <div className="footer-column">
              <h4>Hỗ trợ</h4>
              <a href="#" onClick={(e) => e.preventDefault()}>Hướng dẫn</a>
              <a href="#" onClick={(e) => e.preventDefault()}>FAQ</a>
              <a href="#" onClick={(e) => e.preventDefault()}>Liên hệ</a>
            </div>

            <div className="footer-column">
              <h4>Pháp lý</h4>
              <a href="#" onClick={(e) => e.preventDefault()}>Điều khoản</a>
              <a href="#" onClick={(e) => e.preventDefault()}>Bảo mật</a>
              <a href="#" onClick={(e) => e.preventDefault()}>Cookie</a>
            </div>
          </div>
        </div>

        <div className="footer-bottom">
          <p>© 2026 PhysLive Studio. All rights reserved.</p>
          <p>Built with ❤️ for Physics Education</p>
        </div>
      </footer>

      {error && (
        <div className="error-toast">
          <span className="error-icon">⚠️</span>
          <span>{error}</span>
          <button onClick={() => setError("")}>✕</button>
        </div>
      )}
    </div>
  );
}

export default Home;
