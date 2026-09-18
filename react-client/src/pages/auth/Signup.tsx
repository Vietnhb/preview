import { Link } from "react-router-dom";

export default function Signup() {
  return <main className="main"><h1>Tài khoản do nhà trường cấp</h1>
    <p>Vui lòng liên hệ quản lý trường để được cấp tài khoản giáo viên hoặc học sinh.</p>
    <Link to="/login">Đăng nhập</Link>
  </main>;
}
