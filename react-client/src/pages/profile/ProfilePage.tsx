import { useEffect, useRef, useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import axios from "axios";
import { changePassword, updateAvatar, updateProfile } from "../../api/userApi";
import type { User } from "../../types/physlive";
import { usePhysliveStore } from "../../store/usePhysliveStore";
import LearningIcon from "../../components/common/LearningIcon";
import "../../styles/profile.css";

function ProfileContent({ user }: Readonly<{ user: User }>) {
  const setUser = usePhysliveStore((state) => state.setUser);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [fullName, setFullName] = useState(user.fullName);
  const [dateOfBirth, setDateOfBirth] = useState(user.dateOfBirth ?? "");
  const [avatarUrl, setAvatarUrl] = useState(user.avatarUrl ?? "");
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [savingProfile, setSavingProfile] = useState(false);
  const [savingPassword, setSavingPassword] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  const apiError = (err: unknown, fallback: string) => axios.isAxiosError<{ message?: string }>(err) ? err.response?.data?.message ?? fallback : fallback;
  const handleProfileSubmit = async (event: FormEvent) => {
    event.preventDefault(); setError(""); setMessage(""); setSavingProfile(true);
    try { const updated = await updateProfile({ fullName, dateOfBirth: dateOfBirth || undefined }); setUser(updated); setMessage("Cập nhật thông tin thành công."); }
    catch (err: unknown) { setError(apiError(err, "Không thể cập nhật thông tin.")); }
    finally { setSavingProfile(false); }
  };
  const handlePasswordSubmit = async (event: FormEvent) => {
    event.preventDefault(); setError(""); setMessage("");
    if (newPassword !== confirmPassword) { setError("Mật khẩu xác nhận không khớp."); return; }
    if (newPassword.length < 8) { setError("Mật khẩu mới phải có ít nhất 8 ký tự."); return; }
    setSavingPassword(true);
    try { await changePassword(currentPassword, newPassword); setCurrentPassword(""); setNewPassword(""); setConfirmPassword(""); setMessage("Đổi mật khẩu thành công."); }
    catch (err: unknown) { setError(apiError(err, "Không thể đổi mật khẩu.")); }
    finally { setSavingPassword(false); }
  };
  const handleAvatar = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;
    if (!file.type.startsWith("image/") || file.size > 1_500_000) { setError("Avatar phải là ảnh và dung lượng tối đa 1.5 MB."); return; }
    const reader = new FileReader();
    reader.onload = async () => {
      if (typeof reader.result !== "string") return;
      setUploading(true); setError("");
      try { const updated = await updateAvatar(reader.result); setAvatarUrl(updated.avatarUrl ?? reader.result); setUser(updated); setMessage("Cập nhật ảnh đại diện thành công."); }
      catch (err: unknown) { setError(apiError(err, "Không thể cập nhật ảnh đại diện.")); }
      finally { setUploading(false); }
    };
    reader.readAsDataURL(file);
  };

  return <div className="profile-page">
    <div className="profile-container">
      <div className="profile-back"><Link to="/"><LearningIcon name="back" />Quay lại PhysLive</Link></div>
      <div className="profile-grid">
        <aside className="profile-sidebar">
          <section className="profile-card profile-identity-card">
            <div className="profile-cover" />
            <div className="profile-identity-content">
              <button type="button" className="profile-avatar-button" onClick={() => fileInputRef.current?.click()} disabled={uploading} title="Đổi ảnh đại diện">
                {avatarUrl ? <img src={avatarUrl} alt={fullName} /> : <span>{fullName.slice(0, 1).toUpperCase() || "U"}</span>}
                <span className="profile-avatar-overlay">{uploading ? "…" : "Đổi"}</span>
              </button>
              <input ref={fileInputRef} type="file" accept="image/*" hidden onChange={handleAvatar} />
              <h2>{fullName || "User"}</h2><p>{user.email}</p>
              <span className="profile-role"><LearningIcon name="settings" />{user.role}</span>
            </div>
          </section>
          <section className="profile-card profile-note"><LearningIcon name="bulb" /><div><strong>Hồ sơ PhysLive</strong><p>Thông tin của bạn được dùng để cá nhân hóa workspace và lịch sử mô phỏng.</p></div></section>
        </aside>
        <main className="profile-main">
          <section className="profile-card profile-section"><div className="profile-section-heading"><div><h1>Thông tin cá nhân</h1><p>Cập nhật hồ sơ công khai của bạn.</p></div><LearningIcon name="settings" /></div>
            <form onSubmit={handleProfileSubmit} className="profile-form"><label>Họ và tên<input value={fullName} onChange={(event) => setFullName(event.target.value)} required maxLength={120} /></label><label>Ngày sinh<input type="date" value={dateOfBirth} onChange={(event) => setDateOfBirth(event.target.value)} /></label><label>Email<input value={user.email} readOnly /></label><button type="submit" disabled={savingProfile}>{savingProfile ? "Đang lưu…" : "Lưu thay đổi"}</button></form>
          </section>
          <section className="profile-card profile-section"><div className="profile-section-heading"><div><h2>Bảo mật & tài khoản</h2><p>Giữ tài khoản của bạn an toàn.</p></div><LearningIcon name="settings" /></div>
            <form onSubmit={handlePasswordSubmit} className="profile-form profile-password-form"><label>Mật khẩu hiện tại<input type="password" value={currentPassword} onChange={(event) => setCurrentPassword(event.target.value)} required /></label><label>Mật khẩu mới<input type="password" value={newPassword} onChange={(event) => setNewPassword(event.target.value)} minLength={8} required /></label><label>Xác nhận mật khẩu<input type="password" value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} minLength={8} required /></label><button type="submit" className="profile-outline-button" disabled={savingPassword}>{savingPassword ? "Đang cập nhật…" : "Đổi mật khẩu"}</button></form>
          </section>
          {(message || error) && <div className={error ? "profile-alert error" : "profile-alert success"}>{error || message}</div>}
          <div className="profile-account-meta"><div><span>Vai trò</span><strong>{user.role}</strong></div><div><span>Mã người dùng</span><strong>#{user.id}</strong></div></div>
        </main>
      </div>
    </div>
  </div>;
}

export default function ProfilePage() {
  const navigate = useNavigate();
  const user = usePhysliveStore((state) => state.user);
  useEffect(() => { if (!user) navigate("/login", { replace: true }); }, [user, navigate]);
  if (!user) return <div className="profile-loading">Đang tải hồ sơ…</div>;
  return <ProfileContent key={user.id} user={user} />;
}
