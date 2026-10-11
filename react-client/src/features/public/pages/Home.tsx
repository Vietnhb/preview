import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { motion, useReducedMotion, useScroll, useTransform } from "motion/react";
import { getRegistrationPlans, type LicensePlan } from "../../billing/api/billingApi";
import { useSessionStore } from "../../../shared/auth/sessionStore";
import { useEffectiveTheme } from "../../../shared/theme/themeStore";
import { userHome } from "../../../shared/auth/permissions";
import BrandMark from "../../../shared/ui/BrandMark";
import Aurora from "../../../shared/effects/Aurora";
import DotField from "../../../shared/effects/DotField";
import { Magnetic, Marquee, Reveal, SplitWords, SpotlightCard, TiltCard } from "../../../shared/effects/Motion";
import RotatingText from "../../../shared/effects/reactbits/RotatingText";
import { Circuit, Collision, Pendulum, Wave } from "../components/PhysicsVignettes";
import s from "./Home.module.css";

const money = (value: number) =>
  new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND", maximumFractionDigits: 0 }).format(value);
const count = (value: number) => value.toLocaleString("vi-VN");

const phrases = ["nhìn thấy được.", "chạm vào được.", "thử nghiệm được.", "giải thích được."];
const topics = ["Chuyển động thẳng", "Ném xiên", "Rơi tự do", "Định luật Newton", "Va chạm", "Con lắc đơn", "Dao động", "Sóng cơ", "Điện tích", "Mạch điện", "Mômen lực", "Hạt nhân"];
const AURORA_DARK: [string, string, string] = ["#1d4ed8", "#22d3ee", "#8b5cf6"];
const AURORA_LIGHT: [string, string, string] = ["#3b82f6", "#06b6d4", "#8b5cf6"];

const bento = [
  { key: "pendulum", title: "Dao động", text: "Con lắc, lò xo: chu kỳ, biên độ và năng lượng theo thời gian.", art: <Pendulum /> },
  { key: "wave", title: "Sóng cơ", text: "Sóng truyền trên dây và li độ tại một điểm theo thời gian.", art: <Wave /> },
  { key: "circuit", title: "Điện học", text: "Dòng điện trong mạch, điện tích và điện trường.", art: <Circuit /> },
  { key: "collision", title: "Va chạm", text: "Động lượng được bảo toàn — kiểm tra bằng số liệu từ bộ giải.", art: <Collision /> },
];

const steps = [
  { title: "Dự đoán", text: "Học sinh ghi lại dự đoán và lập luận trước khi chạy thí nghiệm." },
  { title: "Thử nghiệm", text: "Thay đổi điều kiện đầu vào, quan sát chuyển động, đo đại lượng trên đồ thị." },
  { title: "Giải thích", text: "Đối chiếu kết quả với mô hình, nêu giả thiết và giới hạn của lời giải." },
];

const audiences = [
  { role: "Giáo viên", lead: "Từ đề bài đến mô phỏng trong vài phút.", points: ["Nhập đề bằng văn bản, LaTeX hoặc ảnh", "Mô phỏng được đối chiếu với lời giải tham chiếu", "Giao bài theo lớp, xem bài nộp và chấm điểm"] },
  { role: "Học sinh", lead: "Học bằng cách tự thử, không chỉ đọc lời giải.", points: ["Làm bài theo trình tự dự đoán – quan sát – giải thích", "Điều chỉnh thông số và xem đồ thị ngay", "Khám phá kho mô phỏng của cộng đồng"] },
  { role: "Nhà trường", lead: "Quản lý tập trung cho cả tổ Vật lý.", points: ["Tạo tài khoản, lớp học bằng tệp CSV", "Theo dõi mức sử dụng và báo cáo theo lớp", "Một gói năm cho toàn trường"] },
];

function HomePlanCatalog() {
  const [plans, setPlans] = useState<LicensePlan[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    getRegistrationPlans()
      .then((nextPlans) => { if (active) setPlans(nextPlans); })
      .catch(() => { if (active) setError("Không thể tải danh mục gói lúc này."); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);

  if (loading) return <p className={s.state} aria-live="polite">Đang tải danh mục gói…</p>;
  if (error) return <p className={s.state} role="status">{error} Bạn vẫn có thể mở trang đăng ký để thử lại.</p>;
  if (plans.length === 0) return <p className={s.state}>Hiện chưa có gói đăng ký khả dụng.</p>;
  return (
    <div className={s.plans}>
      {plans.map((plan, index) => (
        <Reveal key={plan.code} delay={index * 0.08}>
          <SpotlightCard className={s.plan}>
            <strong className={s.planName}>{plan.name}</strong>
            <p className={s.planDesc}>{plan.description}</p>
            <p className={s.planPrice}>{money(plan.annualPriceVnd)}<span>/ năm</span></p>
            <dl className={s.planFacts}>
              <div><dt>Học sinh</dt><dd>{count(plan.studentQuota)}</dd></div>
              <div><dt>Token AI / tháng</dt><dd>{plan.monthlyTokenQuota === null ? "Không giới hạn" : count(plan.monthlyTokenQuota)}</dd></div>
            </dl>
            <Link className={s.ghost} to="/signup">Chọn gói {plan.name}</Link>
          </SpotlightCard>
        </Reveal>
      ))}
    </div>
  );
}

export default function Home() {
  const user = useSessionStore((state) => state.user);
  const reduced = useReducedMotion();
  const theme = useEffectiveTheme();
  const AURORA = theme === "dark" ? AURORA_DARK : AURORA_LIGHT;
  const { scrollY } = useScroll();
  const tilt = useTransform(scrollY, [0, 500], [14, 0]);
  const scale = useTransform(scrollY, [0, 500], [0.94, 1]);

  return (
    <div className={s.home}>
      <main>
        <section className={s.hero}>
          <Aurora key={theme} colors={AURORA} />
          <DotField gap={28} radius={150} />
          <div className={s.heroGrid} aria-hidden="true" />
          <div className={s.heroCopy}>
            <h1>
              <SplitWords text="Vật lý," className={s.lineOne} delay={0.1} />
              <RotatingText texts={phrases} mainClassName={s.lineTwo} splitLevelClassName={s.rotWord} elementLevelClassName="text-shine" splitBy="words" staggerDuration={0.07} rotationInterval={2800} auto={!reduced} transition={{ type: "spring", damping: 26, stiffness: 320 }} />
            </h1>
            <motion.p className={s.lead} initial={reduced ? false : { opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.6, delay: 0.45 }}>
              Giáo viên nhập đề bài, PhysLive dựng mô phỏng tương tác đã được kiểm chứng.
              Học sinh dự đoán, thử nghiệm và giải thích ngay trên trình duyệt.
            </motion.p>
            <motion.div className={s.ctas} initial={reduced ? false : { opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.6, delay: 0.6 }}>
              <Magnetic>
                <span className="glow-border">
                  {user
                    ? <Link className={s.primary} to={userHome(user)}>Vào không gian làm việc <span aria-hidden="true">→</span></Link>
                    : <Link className={s.primary} to="/signup">Đăng ký cho trường <span aria-hidden="true">→</span></Link>}
                </span>
              </Magnetic>
              <Link className={s.ghost} to="/workspace">Thử mô phỏng</Link>
            </motion.div>
          </div>
          <motion.div
            className={s.stage}
            style={reduced ? undefined : { rotateX: tilt, scale, transformPerspective: 1400 }}
            initial={reduced ? false : { opacity: 0, y: 60 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 1, ease: [0.22, 1, 0.36, 1], delay: 0.5 }}
          >
            <div className={s.stageGlow} aria-hidden="true" />
            <div className="glow-frame" style={{ padding: 32 }}>
              <h2>Mô phỏng từ đề bài của bạn</h2>
              <p>Nhập tình huống, xem mô hình và khám phá kết quả tính toán.</p>
              <Link className={s.primary} to="/workspace">Mở không gian mô phỏng</Link>
            </div>
          </motion.div>
          <Marquee items={topics} className={s.marquee} />
        </section>

        <section className={s.section} aria-labelledby="home-topics">
          <Reveal className={s.sectionHead}>
            <h2 id="home-topics">Mỗi chương một <span className="text-shine">phòng thí nghiệm</span>.</h2>
            <p>Mô phỏng chạy từ số liệu của bộ giải, không phải hoạt hình vẽ tay — đổi một thông số, mọi đại lượng cập nhật theo.</p>
          </Reveal>
          <div className={s.bento}>
            {bento.map((item, index) => (
              <Reveal key={item.key} delay={index * 0.08} className={s[`bento_${item.key}`]}>
                <TiltCard className={s.bentoCard}>
                  <div className={s.bentoArt}>{item.art}</div>
                  <h3>{item.title}</h3>
                  <p>{item.text}</p>
                </TiltCard>
              </Reveal>
            ))}
          </div>
        </section>

        <section className={s.section} aria-labelledby="home-method">
          <Reveal className={s.sectionHead}>
            <h2 id="home-method">Từ dự đoán đến bằng chứng.</h2>
            <p>Học sinh phải cam kết một dự đoán trước khi được xem mô phỏng.</p>
          </Reveal>
          <ol className={s.steps}>
            {steps.map((step, index) => (
              <Reveal as="li" key={step.title} delay={index * 0.12}>
                <SpotlightCard className={s.step}>
                  <span className={s.stepIndex}>{String(index + 1).padStart(2, "0")}</span>
                  <h3>{step.title}</h3>
                  <p>{step.text}</p>
                </SpotlightCard>
              </Reveal>
            ))}
          </ol>
        </section>

        <section className={s.section} aria-labelledby="home-roles">
          <Reveal className={s.sectionHead}>
            <h2 id="home-roles">Một nền tảng, ba vai trò.</h2>
            <p>Tài khoản giáo viên và học sinh do nhà trường cấp, dữ liệu gắn với lớp học thực tế.</p>
          </Reveal>
          <div className={s.audiences}>
            {audiences.map((item, index) => (
              <Reveal key={item.role} delay={index * 0.1}>
                <TiltCard className={s.audience} max={5}>
                  <span className={s.audienceRole}>{item.role}</span>
                  <h3>{item.lead}</h3>
                  <ul>{item.points.map((point) => <li key={point}>{point}</li>)}</ul>
                </TiltCard>
              </Reveal>
            ))}
          </div>
        </section>

        <section className={s.section} aria-labelledby="home-plans">
          <Reveal className={s.sectionHead}>
            <h2 id="home-plans">Gói dành cho nhà trường.</h2>
            <p>Giáo viên và lớp học không giới hạn; token AI tính theo mức sử dụng thực tế.</p>
          </Reveal>
          <HomePlanCatalog />
        </section>

        <section className={s.closing}>
          <Aurora key={`closing-${theme}`} colors={AURORA} intensity={0.8} />
          <Reveal className={s.closingInner}>
            <h2>Đưa phòng thí nghiệm số <br />vào <span className="text-shine">lớp học của bạn</span>.</h2>
            <p>Đăng ký trực tuyến, thanh toán qua VNPAY, nhận tài khoản quản lý ngay sau khi kích hoạt.</p>
            <div className={s.ctas}>
              <Magnetic><span className="glow-border"><Link className={s.primary} to="/signup">Đăng ký cho trường <span aria-hidden="true">→</span></Link></span></Magnetic>
              <Link className={s.ghost} to="/community">Xem kho mô phỏng cộng đồng</Link>
            </div>
          </Reveal>
        </section>
      </main>
      <footer className={s.footer}>
        <div className={s.footerInner}>
          <BrandMark size={26} />
          <span className={s.footerNote}>Mô phỏng Vật lý tương tác cho trường THPT.</span>
          <nav aria-label="Liên kết chân trang">
            <Link to="/about">Giới thiệu</Link>
            <Link to="/terms">Điều khoản</Link>
            <Link to="/community">Cộng đồng</Link>
            <Link to="/signup">Đăng ký cho trường</Link>
          </nav>
        </div>
      </footer>
    </div>
  );
}
