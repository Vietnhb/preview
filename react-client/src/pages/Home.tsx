import { Link, useNavigate } from "react-router-dom";
import { getToken } from "../utils/token";
import { usePhysliveStore } from "../store/usePhysliveStore";
import LearningIcon from "../components/common/LearningIcon";
import "../styles/home.css";

function Home() {
  const navigate = useNavigate();
  const token = getToken();
  const user = usePhysliveStore((state) => state.user);
  const isStudent = user?.role === "STUDENT";

  return (
    <div className="home-page">
      <main>
        <section className="home-hero">
          <div className="home-container home-hero-grid">
            <div className="home-hero-copy">
              <span className="home-eyebrow">PhysLive</span>
              <h1>Vật lý, nhìn thấy được.</h1>
              <p>
                {isStudent
                  ? "Làm bài được giao, gửi dự đoán và quan sát mô phỏng sau khi mở khóa."
                  : "Tạo mô phỏng, thay đổi thông số và quan sát kết quả ngay trong Workspace."}
              </p>
              <div className="home-actions">
                <button
                  type="button"
                  className="home-button home-button-primary"
                  onClick={() =>
                    navigate(
                      user?.role === "STUDENT" ? "/assignments" : "/workspace",
                    )
                  }
                >
                  {user?.role === "STUDENT" ? "Xem bài tập" : "Mở Workspace"}{" "}
                  <LearningIcon name="arrow" />
                </button>
                {user?.role === "STUDENT" && (
                  <button
                    type="button"
                    className="home-button home-button-secondary"
                    onClick={() => navigate("/community")}
                  >
                    Kho cộng đồng
                  </button>
                )}
                {!token && (
                  <button
                    type="button"
                    className="home-button home-button-secondary"
                    onClick={() => navigate("/login")}
                  >
                    Đăng nhập
                  </button>
                )}
              </div>
              {user && (
                <p className="home-welcome">Xin chào, {user.fullName}.</p>
              )}
            </div>

            <div
              className="home-simulation-card"
              aria-label="Minh họa mô phỏng chuyển động"
            >
              <div className="home-card-heading">
                <span>{isStudent ? "Mô phỏng lớp học" : "Workspace"}</span>
                <span className="home-status">
                  <i /> Đang chạy
                </span>
              </div>
              <div className="home-simulation-stage">
                <svg viewBox="0 0 440 220" aria-label="Đường đi của vật thể">
                  <line
                    x1="38"
                    y1="184"
                    x2="404"
                    y2="184"
                    className="home-axis"
                  />
                  <line
                    x1="38"
                    y1="30"
                    x2="38"
                    y2="184"
                    className="home-axis"
                  />
                  <path
                    d="M48 174 C126 160 176 118 230 112 S330 74 392 45"
                    className="home-trajectory"
                  />
                  <circle cx="230" cy="112" r="7" className="home-point" />
                </svg>
              </div>
              <div className="home-card-values">
                <div>
                  <span>Vận tốc</span>
                  <strong>12.5 m/s</strong>
                </div>
                <div>
                  <span>Gia tốc</span>
                  <strong>2.0 m/s²</strong>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section className="home-features">
          <div className="home-container">
            <div className="home-section-heading">
              <span className="home-eyebrow">Bắt đầu từ một thí nghiệm</span>
              <h2>Các công cụ ở cùng một nơi</h2>
            </div>
            <div className="home-feature-grid">
              {isStudent ? (
                <>
                  <article className="home-feature-card">
                    <LearningIcon name="book" />
                    <h3>Bài tập được giao</h3>
                    <p>Xem các mô phỏng và câu hỏi giáo viên gửi cho bạn.</p>
                  </article>
                  <article className="home-feature-card">
                    <LearningIcon name="check" />
                    <h3>Dự đoán trước</h3>
                    <p>Gửi câu trả lời trước khi xem kết quả mô phỏng.</p>
                  </article>
                  <article className="home-feature-card">
                    <LearningIcon name="folder" />
                    <h3>Tài nguyên lớp học</h3>
                    <p>Chạy các mô phỏng đã được chia sẻ để tự luyện tập.</p>
                  </article>
                </>
              ) : (
                <>
                  <article className="home-feature-card">
                    <LearningIcon name="grid" />
                    <h3>Tạo mô phỏng</h3>
                    <p>
                      Chọn một bài toán và bắt đầu từ các thông số cần thiết.
                    </p>
                  </article>
                  <article className="home-feature-card">
                    <LearningIcon name="sliders" />
                    <h3>Chỉnh và quan sát</h3>
                    <p>
                      Thay đổi giá trị, chạy thử và xem chuyển động thay đổi ra
                      sao.
                    </p>
                  </article>
                  <article className="home-feature-card">
                    <LearningIcon name="book" />
                    <h3>Lưu để dùng tiếp</h3>
                    <p>Lưu bài làm trong Workspace để mở lại khi cần.</p>
                  </article>
                </>
              )}
            </div>
          </div>
        </section>
      </main>

      <footer className="home-footer">
        <div className="home-container home-footer-inner">
          <span className="home-footer-brand">
            <img src="/favicon.ico" alt="" /> PhysLive
          </span>
          <nav aria-label="Liên kết chân trang">
            <Link to="/about">Giới thiệu</Link>
            <Link to="/terms">Điều khoản</Link>
          </nav>
        </div>
      </footer>
    </div>
  );
}

export default Home;
