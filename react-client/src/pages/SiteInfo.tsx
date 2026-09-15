type SiteInfoProps = { kind: "about" | "terms" };

export default function SiteInfo({ kind }: SiteInfoProps) {
  const isAbout = kind === "about";
  return (
    <main className="site-info-page">
      <section className="site-info-card">
        <span className="site-info-kicker">PhysLive</span>
        <h1>{isAbout ? "Giới thiệu" : "Điều khoản sử dụng"}</h1>
        <p>
          {isAbout
            ? "PhysLive là nền tảng mô phỏng vật lý trực quan, hỗ trợ giáo viên và người học xây dựng, kiểm chứng và khám phá các thí nghiệm tương tác."
            : "Vui lòng sử dụng PhysLive đúng mục đích học tập, tôn trọng dữ liệu mô phỏng và thông tin tài khoản của người dùng khác."}
        </p>
      </section>
    </main>
  );
}
