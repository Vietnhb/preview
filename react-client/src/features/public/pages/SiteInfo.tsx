import { Link } from "react-router-dom";

type SiteInfoProps = { kind: "about" | "terms" };

const content = {
  about: {
    kicker: "Giới thiệu",
    title: "PhysLive là phòng thí nghiệm Vật lý trên trình duyệt",
    lead: "PhysLive là nền tảng mô phỏng vật lý trực quan, hỗ trợ giáo viên và người học xây dựng, kiểm chứng và khám phá các thí nghiệm tương tác.",
    sections: [
      { title: "Giáo viên", text: "Nhập đề bài bằng văn bản, LaTeX hoặc ảnh. Hệ thống nhận diện đại lượng, dựng mô phỏng và đối chiếu với lời giải tham chiếu trước khi đưa vào thư viện." },
      { title: "Học sinh", text: "Làm bài theo trình tự dự đoán, quan sát, giải thích. Điều chỉnh thông số và đọc đồ thị trực tiếp thay vì chỉ xem đáp án." },
      { title: "Nhà trường", text: "Cấp tài khoản, xếp lớp bằng tệp CSV, theo dõi mức sử dụng và gia hạn gói năm cho toàn trường." },
    ],
  },
  terms: {
    kicker: "Điều khoản",
    title: "Điều khoản sử dụng",
    lead: "Vui lòng sử dụng PhysLive đúng mục đích học tập, tôn trọng dữ liệu mô phỏng và thông tin tài khoản của người dùng khác.",
    sections: [
      { title: "Tài khoản", text: "Tài khoản giáo viên và học sinh do nhà trường cấp. Không chia sẻ mật khẩu; liên hệ quản lý trường khi cần đặt lại." },
      { title: "Nội dung chia sẻ", text: "Mô phỏng chia sẻ lên cộng đồng được kiểm duyệt trước khi hiển thị công khai và có thể bị gỡ nếu không phù hợp." }
    ],
  },
} as const;

export default function SiteInfo({ kind }: Readonly<SiteInfoProps>) {
  const page = content[kind];
  return (
    <main className="site-info-page">
      <article className="site-info-card">
        <span className="site-info-kicker">{page.kicker}</span>
        <h1>{page.title}</h1>
        <p>{page.lead}</p>
        <dl className="site-info-list">
          {page.sections.map((section) => (
            <div key={section.title}>
              <dt>{section.title}</dt>
              <dd>{section.text}</dd>
            </div>
          ))}
        </dl>
        <div className="site-info-actions">
          <Link to="/">Về trang chủ</Link>
          <Link to={kind === "about" ? "/terms" : "/about"}>{kind === "about" ? "Điều khoản sử dụng" : "Giới thiệu PhysLive"}</Link>
        </div>
      </article>
    </main>
  );
}
