import { useEffect, useState, type ReactNode } from "react";
import {
  motion,
  useReducedMotion,
  useScroll,
  useTransform,
} from "motion/react";
import { Link } from "react-router-dom";
import { getRegistrationPlans, type LicensePlan } from "../api/authApi";
import { usePhysliveStore } from "../store/usePhysliveStore";
import ProjectileExperiment from "../components/simulation/ProjectileExperiment";
import s from "./Home.module.css";

const money = (value: number) =>
  new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
    maximumFractionDigits: 0,
  }).format(value);

const count = (value: number) => value.toLocaleString("vi-VN");

function Reveal({
  children,
  className = "",
}: Readonly<{ children: ReactNode; className?: string }>) {
  const prefersReducedMotion = Boolean(useReducedMotion());
  return (
    <motion.div
      className={className}
      initial={prefersReducedMotion ? false : { opacity: 0, y: 20 }}
      whileInView={prefersReducedMotion ? undefined : { opacity: 1, y: 0 }}
      viewport={{ once: true, amount: 0.16 }}
      transition={{ duration: 0.5, ease: "easeOut" }}
    >
      {children}
    </motion.div>
  );
}

function HomePlanCatalog() {
  const [plans, setPlans] = useState<LicensePlan[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    getRegistrationPlans()
      .then((nextPlans) => {
        if (active) setPlans(nextPlans);
      })
      .catch(() => {
        if (active) setError("Không thể tải danh mục gói lúc này.");
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  return (
    <div className={s.catalog} aria-live="polite">
      {loading && (
        <p className={s.state}>Đang tải danh mục gói từ cấu hình admin…</p>
      )}
      {!loading && error && (
        <p className={s.error} role="status">
          {error} Bạn vẫn có thể mở trang đăng ký để thử lại.
        </p>
      )}
      {!loading && !error && plans.length === 0 && (
        <p className={s.state}>Hiện chưa có gói đăng ký khả dụng.</p>
      )}
      {!loading && !error && plans.length > 0 && (
        <div className={s.planList}>
          {plans.map((plan) => (
            <article className={s.planRow} key={plan.code}>
              <div>
                <span className={s.code}>{plan.code}</span>
                <h3>{plan.name}</h3>
                <p>{plan.description}</p>
              </div>
              <dl>
                <div>
                  <dt>Giá năm</dt>
                  <dd>{money(plan.annualPriceVnd)}</dd>
                </div>
                <div>
                  <dt>Học sinh</dt>
                  <dd>{count(plan.studentQuota)}</dd>
                </div>
                <div>
                  <dt>Token AI</dt>
                  <dd>
                    {plan.monthlyTokenQuota === null
                      ? "Không giới hạn"
                      : count(plan.monthlyTokenQuota)}
                  </dd>
                </div>
              </dl>
            </article>
          ))}
        </div>
      )}
    </div>
  );
}

export default function Home() {
  const user = usePhysliveStore((state) => state.user);
  const reduced = useReducedMotion();
  const { scrollYProgress } = useScroll();
  const progress = useTransform(scrollYProgress, [0, 1], [0, 1]);

  return (
    <div className={s.home}>
      {!reduced && (
        <motion.div
          className={s.progress}
          style={{ scaleX: progress }}
          aria-hidden="true"
        />
      )}
      <main className={s.container}>
        <section className={s.hero}>
          <div className={s.heroCopy}>
            <div>
              <p className={s.kicker}>PHÒNG THÍ NGHIỆM SỐ</p>
              <h1>
                Vật lý,
                <br />
                <em>nhìn thấy được.</em>
              </h1>
            </div>
            <div className={s.intro}>
              <p>
                Thay đổi điều kiện. <br />
                Quan sát chuyển động. <br />
                Hiểu điều đang xảy ra.
              </p>
              <a className={s.primary} href="#projectile-experiment">
                Thử một thí nghiệm <span aria-hidden="true">↘</span>
              </a>
              <Link className={s.textLink} to="/signup">
                Dành cho nhà trường ↗
              </Link>
              {user && (
                <Link
                  className={s.textLink}
                  to={user.role === "STUDENT" ? "/assignments" : "/workspace"}
                >
                  Tiếp tục học tập →
                </Link>
              )}
            </div>
          </div>
          <div className={s.experiment}>
            <ProjectileExperiment />
          </div>
        </section>
        <section className={s.story}>
          <p className={s.kicker}>02 / TỪ DỰ ĐOÁN ĐẾN BẰNG CHỨNG</p>
          <Reveal className={s.storyCopy}>
            <h2>
              Một thay đổi nhỏ.
              <br />
              Một quỹ đạo khác.
            </h2>
            <p>
              Giữ nguyên vận tốc. Chọn Đối chiếu để lưu quỹ đạo, rồi thay đổi
              góc ném. Vật bay cao hơn có luôn bay xa hơn?
            </p>
            <a className={s.textLink} href="#projectile-experiment">
              Kiểm tra dự đoán của bạn ↑
            </a>
          </Reveal>
        </section>
        <section className={s.school}>
          <div>
            <p className={s.kicker}>03 / DẠY VÀ HỌC</p>
            <h2>
              Cho câu hỏi
              <br />
              một nơi để thử.
            </h2>
            <Link className={s.textLink} to="/workspace">
              Mở Workspace thực tế ↗
            </Link>
          </div>
          <ol className={s.tasks}>
            <li>
              <span>01</span>
              <div>
                <h3>Dự đoán</h3>
                <p>Đặt câu hỏi về một đại lượng trước khi chạy thí nghiệm.</p>
              </div>
            </li>
            <li>
              <span>02</span>
              <div>
                <h3>Thử nghiệm</h3>
                <p>
                  Thay đổi điều kiện đầu vào, quan sát chuyển động và ghi lại số
                  đo.
                </p>
              </div>
            </li>
            <li>
              <span>03</span>
              <div>
                <h3>Giải thích</h3>
                <p>
                  Đối chiếu kết quả với mô hình. Nêu giả thiết và giới hạn của
                  lời giải.
                </p>
              </div>
            </li>
          </ol>
        </section>
        <section className={s.plans} aria-labelledby="home-plans-title">
          <div className={s.planHeading}>
            <div>
              <p className={s.kicker}>04 / DÀNH CHO NHÀ TRƯỜNG</p>
              <h2 id="home-plans-title">
                Không gian cho
                <br />
                những khám phá mới.
              </h2>
            </div>
            <Link className={s.primary} to="/signup">
              Chọn gói cho trường ↗
            </Link>
          </div>
          <HomePlanCatalog />
        </section>
      </main>
      <footer className={s.footer}>
        <div>
          <strong>PhysLive</strong>
          <span>Vật lý, nhìn thấy được.</span>
        </div>
        <nav aria-label="Liên kết chân trang">
          <Link to="/about">Giới thiệu</Link>
          <Link to="/terms">Điều khoản</Link>
          <Link to="/signup">Đăng ký cho trường</Link>
        </nav>
      </footer>
    </div>
  );
}
