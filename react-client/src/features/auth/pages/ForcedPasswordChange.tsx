import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import axios from "axios";
import { Badge, Box, Button, Callout, Card, Flex, Heading, IconButton, Text, TextField, Theme } from "@radix-ui/themes";
import { EyeClosedIcon, EyeOpenIcon, LockClosedIcon } from "@radix-ui/react-icons";
import { motion, useReducedMotion } from "motion/react";
import { changePassword } from "../../account/api/userApi";
import { logout } from "../api/authApi";
import { clearToken } from "../../../shared/lib/token";
import { useSessionStore } from "../../../shared/auth/sessionStore";
import { userHome } from "../../../shared/auth/permissions";
import "./forced-password-change.css";

export default function ForcedPasswordChange() {
  const navigate = useNavigate();
  const user = useSessionStore(state => state.user);
  const setUser = useSessionStore(state => state.setUser);
  const reducedMotion = useReducedMotion();
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [visible, setVisible] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setError("");
    if (newPassword !== confirmation) { setError("Mật khẩu xác nhận chưa trùng khớp."); return; }
    if (newPassword === currentPassword) { setError("Mật khẩu mới phải khác mật khẩu khởi tạo."); return; }
    if (newPassword.trim().length === 0 || newPassword.length < 8) { setError("Mật khẩu mới phải có ít nhất 8 ký tự."); return; }
    if (new TextEncoder().encode(newPassword).length > 72) { setError("Mật khẩu quá dài. Vui lòng dùng mật khẩu ngắn hơn."); return; }
    setSaving(true);
    try {
      const updated = await changePassword(currentPassword, newPassword);
      setUser(updated);
      navigate(userHome(updated), { replace: true });
    } catch (failure) {
      setError(axios.isAxiosError<{ message?: string }>(failure)
        ? failure.response?.data?.message ?? "Chưa đổi được mật khẩu. Vui lòng thử lại."
        : "Chưa đổi được mật khẩu. Vui lòng thử lại.");
    } finally { setSaving(false); }
  };

  const signOut = () => {
    void logout().catch(() => undefined);
    clearToken();
    setUser(null);
    navigate("/login", { replace: true });
  };

  return <Theme accentColor="indigo" grayColor="slate" radius="large" className="forced-password-page">
    <motion.main initial={reducedMotion ? false : { opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.18 }} className="forced-password-main">
      <Card size="4">
        <Flex align="center" justify="between" gap="3" mb="5">
          <Box className="forced-password-icon"><LockClosedIcon width="22" height="22" /></Box>
          <Badge size="2" variant="soft">Thiết lập tài khoản</Badge>
        </Flex>
        <Heading as="h1" size="6" mb="2">Đổi mật khẩu khởi tạo</Heading>
        <Text as="p" size="2" color="gray" mb="5">Đặt mật khẩu riêng để tiếp tục sử dụng tài khoản.</Text>
        <Box className="forced-password-account" mb="5">
          <Text as="p" size="3" weight="medium">{user?.fullName}</Text>
          <Text as="p" size="2" color="gray">{user?.email}</Text>
        </Box>
        <form onSubmit={handleSubmit}>
          <Flex direction="column" gap="4">
            <Box>
              <Text as="label" htmlFor="initial-password" size="2" weight="medium">Mật khẩu khởi tạo</Text>
              <TextField.Root id="initial-password" mt="2" size="3" type={visible ? "text" : "password"} autoComplete="current-password" required maxLength={120}
                value={currentPassword} onChange={event => setCurrentPassword(event.target.value)} disabled={saving}>
                <TextField.Slot side="right"><IconButton type="button" variant="ghost" size="1" aria-label={visible ? "Ẩn mật khẩu" : "Hiện mật khẩu"} aria-pressed={visible} onClick={() => setVisible(!visible)}>
                  {visible ? <EyeClosedIcon /> : <EyeOpenIcon />}
                </IconButton></TextField.Slot>
              </TextField.Root>
            </Box>
            <Box>
              <Text as="label" htmlFor="new-password" size="2" weight="medium">Mật khẩu mới</Text>
              <TextField.Root id="new-password" mt="2" size="3" type={visible ? "text" : "password"} autoComplete="new-password" required minLength={8} maxLength={120}
                value={newPassword} onChange={event => setNewPassword(event.target.value)} disabled={saving} aria-describedby="new-password-hint" />
              <Text as="p" id="new-password-hint" size="1" color="gray" mt="2">Ít nhất 8 ký tự.</Text>
            </Box>
            <Box>
              <Text as="label" htmlFor="confirm-password" size="2" weight="medium">Xác nhận mật khẩu mới</Text>
              <TextField.Root id="confirm-password" mt="2" size="3" type={visible ? "text" : "password"} autoComplete="new-password" required minLength={8} maxLength={120}
                value={confirmation} onChange={event => setConfirmation(event.target.value)} disabled={saving} />
            </Box>
            {error && <Callout.Root color="red" size="1" role="alert"><Callout.Text>{error}</Callout.Text></Callout.Root>}
            <Button type="submit" size="3" loading={saving}>Lưu mật khẩu và tiếp tục</Button>
            <Button type="button" variant="ghost" color="gray" size="2" disabled={saving} onClick={signOut}>Đăng xuất</Button>
          </Flex>
        </form>
      </Card>
    </motion.main>
  </Theme>;
}