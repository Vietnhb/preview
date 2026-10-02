import { Link } from "react-router-dom";
import { motion, useReducedMotion } from "motion/react";
import BrandMark from "../../../shared/ui/BrandMark";
import Aurora from "../../../shared/effects/Aurora";
import DotField from "../../../shared/effects/DotField";
import { useEffectiveTheme } from "../../../shared/theme/themeStore";
import { Magnetic, Marquee, Reveal, ScrollLine, ScrollProgress, SplitWords, SpotlightCard, useScrollSpy } from "../../../shared/effects/Motion";
import s from "./SiteInfo.module.css";

type SiteInfoProps = { kind: "about" | "terms" };

const AURORA_DARK: [string, string, string] = ["#1d4ed8", "#22d3ee", "#8b5cf6"];
const AURORA_LIGHT: [string, string, string] = ["#3b82f6", "#06b6d4", "#8b5cf6"];

const roles = [
  { role: "Giáo viên", lead: "Từ đề bài đến mô phỏng.", text: "Nhập đề bằng văn bản, LaTeX hoặc ảnh. Hệ thống nhận diện đại lượng, dựng mô phỏng và đối chiếu với lời giải tham chiếu trước khi đưa vào thư viện." },
  { role: "Học sinh", lead: "Học bằng cách tự thử.", text: "Làm bài theo trình tự dự đoán, quan sát, giải thích. Điều chỉnh thông số và đọc đồ thị trực tiếp thay vì chỉ xem đáp án." },
  { role: "Nhà trường", lead: "Quản lý tập trung.", text: "Cấp tài khoản, xếp lớp bằng tệp CSV, theo dõi mức sử dụng và gia hạn gói năm cho toàn trường." },
];

const pipeline = [
  { title: "Nhập đề bài", text: "Giáo viên dán đề bằng văn bản, LaTeX hoặc tải ảnh chụp. Không cần viết mã hay vẽ hình." },
  { title: "AI đọc đề, chuyên gia xác nhận", text: "Hệ thống trích xuất đối tượng và đại lượng. Dữ kiện nào chưa chắc chắn sẽ được hỏi lại thay vì tự đoán." },
  { title: "Đối chiếu lời giải tham chiếu", text: "Kết quả của bộ giải số được so với công thức chính xác trước khi mô phỏng được phép sử dụng." },
  { title: "Giao bài và khám phá", text: "Học sinh cam kết một dự đoán, chạy mô phỏng, đo trên đồ thị rồi giải thích sự khác biệt." },
];

const principles = [
  { title: "Số liệu trước, hình ảnh sau", text: "Mọi chuyển động trên màn hình đến từ kết quả của bộ giải, không phải hoạt hình vẽ tay." },
  { title: "Con người kiểm duyệt", text: "Nội dung chia sẻ công khai và dữ liệu chuẩn của từng chủ đề đều qua chuyên gia Vật lý duyệt." },
  { title: "Bám sát chương trình THPT", text: "Chủ đề được tổ chức theo Vật lí 10, 11 và 12, dùng đúng kí hiệu và đơn vị trong sách giáo khoa." },
];

const topics = ["Động học", "Động lực học", "Năng lượng", "Động lượng", "Dao động", "Sóng", "Điện trường", "Dòng điện", "Từ trường", "Khí lí tưởng", "Hạt nhân", "Lượng tử"];

const terms = [
  { id: "pham-vi", title: "Phạm vi áp dụng", body: ["Điều khoản này áp dụng cho mọi người dùng PhysLive: quản lý trường, giáo viên, học sinh và chuyên gia kiểm duyệt.", "Khi đăng nhập và sử dụng dịch vụ, bạn đồng ý tuân theo các điều khoản dưới đây."] },
  { id: "tai-khoan", title: "Tài khoản", body: ["Tài khoản giáo viên và học sinh do nhà trường cấp; tài khoản quản lý trường được tạo sau khi nhà trường đăng ký gói.", "Bạn chịu trách nhiệm giữ bí mật mật khẩu. Mật khẩu được cấp lần đầu phải được đổi trước khi sử dụng.", "Khi cần đặt lại mật khẩu, hãy liên hệ quản lý trường của bạn."] },
  { id: "su-dung", title: "Sử dụng đúng mục đích", body: ["PhysLive phục vụ việc dạy và học Vật lý. Không dùng dịch vụ để phát tán nội dung không liên quan, gây hại hoặc vi phạm pháp luật.", "Không can thiệp vào hệ thống, không truy cập dữ liệu của lớp học hoặc trường khác."] },
  { id: "noi-dung", title: "Nội dung chia sẻ", body: ["Mô phỏng do giáo viên tạo thuộc thư viện cá nhân cho tới khi được chia sẻ trong trường hoặc lên cộng đồng.", "Nội dung chia sẻ công khai được kiểm duyệt trước khi hiển thị và có thể bị từ chối hoặc gỡ kèm lý do nếu sai kiến thức hoặc không phù hợp."] },
  { id: "ai", title: "Kết quả do AI hỗ trợ", body: ["AI hỗ trợ đọc đề và dựng mô phỏng. Kết quả được đối chiếu với lời giải tham chiếu, nhưng giáo viên vẫn là người quyết định nội dung dùng trong lớp.", "Hạn mức token AI tính theo mức sử dụng thực tế của trường trong từng tháng."] },
  { id: "thanh-toan", title: "Gói dịch vụ và thanh toán", body: ["Nhà trường đăng ký gói theo năm và thanh toán trực tuyến. Tài khoản quản lý được kích hoạt sau khi thanh toán thành công.", "Khi gói hết hạn, nhà trường cần gia hạn để tiếp tục sử dụng đầy đủ tính năng."] },
  { id: "du-lieu", title: "Dữ liệu học tập", body: ["Bài nộp, điểm và nhận xét của học sinh chỉ hiển thị cho học sinh đó và giáo viên phụ trách lớp. Quản lý trường xem báo cáo tổng hợp.", "Dữ liệu học tập được dùng để vận hành lớp học và lập báo cáo cho nhà trường."] },
  { id: "thay-doi", title: "Thay đổi điều khoản", body: ["Điều khoản có thể được cập nhật khi dịch vụ thay đổi. Bản mới nhất luôn được đăng tại trang này.", "Nếu có câu hỏi, hãy gửi phản hồi qua mục hỗ trợ trong ứng dụng hoặc liên hệ quản lý trường."] },
];

function Hero({ title, shine, lead }: Readonly<{ title: string; shine: string; lead: string }>) {
  const reduced = useReducedMotion();
  const theme = useEffectiveTheme();
  return (
    <section className={s.hero}>
      <Aurora key={theme} colors={theme === "dark" ? AURORA_DARK : AURORA_LIGHT} intensity={0.8} />
      <DotField gap={28} radius={140} />
      <div className={s.heroGrid} aria-hidden="true" />
      <div className={s.heroCopy}>
        <h1><SplitWords text={title} delay={0.05} /> <SplitWords text={shine} delay={0.25} wordClassName="text-shine" /></h1>
        <motion.p className={s.lead} initial={reduced ? false : { opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.6, delay: 0.45 }}>{lead}</motion.p>
      </div>
    </section>
  );
}

function About() {
  return <>
    <Hero title="Phòng thí nghiệm" shine="Vật lý trên trình duyệt." lead="PhysLive biến đề bài thành mô phỏng tương tác đã được kiểm chứng, để giáo viên giảng bằng thí nghiệm và học sinh học bằng cách tự thử." />
    <Marquee items={topics} className={s.marquee} />

    <section className={s.section} aria-labelledby="about-roles">
      <Reveal className={s.sectionHead}><h2 id="about-roles">Một nền tảng, ba vai trò.</h2></Reveal>
      <div className={s.cards}>
        {roles.map((item, index) => <Reveal key={item.role} delay={index * 0.08}>
          <SpotlightCard className={s.card}><span className={s.chip}>{item.role}</span><h3>{item.lead}</h3><p>{item.text}</p></SpotlightCard>
        </Reveal>)}
      </div>
    </section>

    <section className={s.section} aria-labelledby="about-how">
      <Reveal className={s.sectionHead}><h2 id="about-how">Từ đề bài đến bằng chứng.</h2><p>Bốn bước, mỗi bước đều có điểm kiểm tra trước khi sang bước tiếp theo.</p></Reveal>
      <div className={s.timeline}>
        <ScrollLine className={s.timelineLine} />
        <ol>
        {pipeline.map((step, index) => <Reveal as="li" key={step.title} delay={index * 0.05} className={s.timelineItem}>
          <span className={s.timelineDot}>{index + 1}</span>
          <div><h3>{step.title}</h3><p>{step.text}</p></div>
        </Reveal>)}
        </ol>
      </div>
    </section>

    <section className={s.section} aria-labelledby="about-principles">
      <Reveal className={s.sectionHead}><h2 id="about-principles">Điều chúng tôi cam kết.</h2></Reveal>
      <div className={s.cards}>
        {principles.map((item, index) => <Reveal key={item.title} delay={index * 0.08}>
          <SpotlightCard className={s.card}><span className={s.cardIndex}>{String(index + 1).padStart(2, "0")}</span><h3>{item.title}</h3><p>{item.text}</p></SpotlightCard>
        </Reveal>)}
      </div>
    </section>

    <section className={s.closing}>
      <Reveal className={s.closingInner}>
        <h2>Sẵn sàng thử trong <span className="text-shine">lớp học của bạn</span>?</h2>
        <div className={s.ctas}>
          <Magnetic><span className="glow-border"><Link className={s.primary} to="/signup">Đăng ký cho trường <span aria-hidden="true">→</span></Link></span></Magnetic>
          <Link className={s.ghost} to="/community">Xem kho mô phỏng</Link>
        </div>
      </Reveal>
    </section>
  </>;
}

function Terms() {
  const active = useScrollSpy(terms.map(item => item.id));
  return <>
    <Hero title="Điều khoản" shine="sử dụng." lead="Những nguyên tắc để PhysLive là không gian học tập an toàn và đáng tin cậy cho nhà trường, giáo viên và học sinh." />
    <div className={s.legal}>
      <nav className={s.toc} aria-label="Mục lục điều khoản">
        <span className={s.tocTitle}>Mục lục</span>
        {terms.map((item, index) => <a key={item.id} href={`#${item.id}`} className={s.tocLink} aria-current={active === item.id ? "true" : undefined}>
          {active === item.id && <motion.span layoutId="terms-toc-pill" className={s.tocPill} aria-hidden="true" transition={{ type: "spring", stiffness: 420, damping: 36 }} />}
          <span className={s.tocIndex}>{String(index + 1).padStart(2, "0")}</span><span>{item.title}</span>
        </a>)}
      </nav>
      <div className={s.articles}>
        {terms.map((item, index) => <Reveal as="section" key={item.id} className={s.article}>
          <span className={s.articleIndex} aria-hidden="true">{String(index + 1).padStart(2, "0")}</span>
          <div><h2 id={item.id}>{item.title}</h2>{item.body.map(paragraph => <p key={paragraph}>{paragraph}</p>)}</div>
        </Reveal>)}
      </div>
    </div>
  </>;
}

export default function SiteInfo({ kind }: Readonly<SiteInfoProps>) {
  return (
    <div className={s.page}>
      <ScrollProgress />
      <main>{kind === "about" ? <About /> : <Terms />}</main>
      <footer className={s.footer}>
        <div className={s.footerInner}>
          <BrandMark size={26} />
          <span className={s.footerNote}>Mô phỏng Vật lý tương tác cho trường THPT.</span>
          <nav aria-label="Liên kết chân trang">
            <Link to="/">Trang chủ</Link>
            <Link to="/about" aria-current={kind === "about" ? "page" : undefined}>Giới thiệu</Link>
            <Link to="/terms" aria-current={kind === "terms" ? "page" : undefined}>Điều khoản</Link>
            <Link to="/community">Cộng đồng</Link>
          </nav>
        </div>
      </footer>
    </div>
  );
}
