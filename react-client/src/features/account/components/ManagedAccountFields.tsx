import { useRef, useState, type ChangeEvent, type FormEvent } from "react";
import { Avatar, Button, Callout, Checkbox, Dialog, Flex, Text, TextField } from "@radix-ui/themes";
import { UploadIcon } from "@radix-ui/react-icons";
import { resetManagedUserPassword } from "../api/userApi";
import type { User } from "../../../shared/auth/types";
import { apiMessage } from "../../../shared/lib/apiError";
import { missingPermission, type AccountDetails } from "../model/accountDetailsModel";
import { PERMISSION_HINTS, PERMISSION_LABELS, permissionsForRole, type PermissionCode } from "../../../shared/auth/permissions";

export function AccountProfileFields({ value, onChange, name, disabled, idPrefix }: Readonly<{
  value: AccountDetails; onChange: (value: Partial<AccountDetails>) => void; name: string; disabled?: boolean; idPrefix: string;
}>) {
  const fileInput = useRef<HTMLInputElement>(null);
  const [uploadError, setUploadError] = useState("");
  const [reading, setReading] = useState(false);
  const today = new Date();
  const maximumDate = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}-${String(today.getDate()).padStart(2, "0")}`;
  const chooseImage = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.currentTarget.files?.[0];
    event.currentTarget.value = "";
    if (!file) return;
    setUploadError("");
    if (!["image/jpeg", "image/png", "image/webp"].includes(file.type) || file.size > 1_572_864) {
      setUploadError("Chọn ảnh JPG, PNG hoặc WebP tối đa 1,5 MB."); return;
    }
    setReading(true);
    const reader = new FileReader();
    reader.onload = () => { if (typeof reader.result === "string") onChange({ avatarUrl: reader.result }); setReading(false); };
    reader.onerror = () => { setUploadError("Không thể đọc ảnh. Vui lòng chọn lại."); setReading(false); };
    reader.readAsDataURL(file);
  };
  return <>
    <div style={{ display: "grid", gap: 8 }}>
      <Text as="label" size="2" weight="medium" htmlFor={`${idPrefix}-dob`}>Ngày sinh</Text>
      <TextField.Root id={`${idPrefix}-dob`} name="dateOfBirth" size="3" type="date" max={maximumDate} value={value.dateOfBirth} disabled={disabled} onChange={event => onChange({ dateOfBirth: event.target.value })} />
    </div>
    <div style={{ display: "grid", gap: 10, gridColumn: "1 / -1" }}>
      <Text as="label" size="2" weight="medium" htmlFor={`${idPrefix}-avatar`}>Ảnh đại diện</Text>
      <Flex align="center" gap="3" wrap="wrap">
        <Avatar size="4" radius="full" src={value.avatarUrl || undefined} fallback={name.trim().slice(0, 1).toUpperCase() || "U"} />
        <Button type="button" variant="surface" disabled={disabled || reading} loading={reading} onClick={() => fileInput.current?.click()}><UploadIcon />Tải ảnh</Button>
        {value.avatarUrl && <Button type="button" variant="ghost" color="gray" disabled={disabled || reading} onClick={() => onChange({ avatarUrl: "" })}>Xóa ảnh</Button>}
        <input ref={fileInput} type="file" accept="image/jpeg,image/png,image/webp" hidden disabled={disabled || reading} onChange={chooseImage} />
      </Flex>
      <TextField.Root id={`${idPrefix}-avatar`} name="avatarUrl" size="3" type="url" placeholder="https://…" value={value.avatarUrl.startsWith("data:") ? "" : value.avatarUrl} disabled={disabled || reading} onChange={event => { setUploadError(""); onChange({ avatarUrl: event.target.value }); }} aria-label="URL ảnh đại diện" />
      <Text size="1" color="gray">JPG, PNG, WebP · tối đa 1,5 MB</Text>
      {uploadError && <Callout.Root size="1" color="red" role="alert"><Callout.Text>{uploadError}</Callout.Text></Callout.Root>}
    </div>
  </>;
}

export function AccountPermissionFields({ role, value, onChange, disabled, idPrefix }: Readonly<{
  role: string; value: AccountDetails; onChange: (value: Partial<AccountDetails>) => void; disabled?: boolean; idPrefix: string;
}>) {
  const available = permissionsForRole(role);
  if (available.length === 0) return null;
  const toggle = (code: PermissionCode, checked: boolean) =>
    onChange({ permissions: checked ? [...value.permissions.filter(item => item !== code), code] : value.permissions.filter(item => item !== code) });
  return <fieldset id={`${idPrefix}-permissions`} style={{ display: "grid", gap: 12, gridColumn: "1 / -1", border: "1px solid var(--gray-5)", borderRadius: 10, padding: 16, margin: 0 }}>
    <Text asChild size="2" weight="medium"><legend>{role === "STAFF" ? "Quyền trong trường" : "Quyền reviewer"}</legend></Text>
    {available.map(code => <Text as="label" size="2" key={code}><Flex gap="2" align="center"><Checkbox checked={value.permissions.includes(code)} disabled={disabled} onCheckedChange={checked => toggle(code, checked === true)} /><span><strong>{PERMISSION_LABELS[code]}</strong> — {PERMISSION_HINTS[code]}</span></Flex></Text>)}
    <Text size="1" color="gray">{role === "STAFF" ? "Một tài khoản có thể vừa là giáo viên vừa là tổ trưởng bộ môn." : "Một reviewer có thể biên soạn, kiểm duyệt hoặc cả hai."}</Text>
    {missingPermission(value, role) && <Text size="2" color="red" role="alert">Cần chọn ít nhất một quyền.</Text>}
  </fieldset>;
}

export function ResetManagedPasswordDialog({ user, schoolId, onClose, onUpdated }: Readonly<{
  user: User | null; schoolId?: string; onClose: () => void; onUpdated: (user: User) => void;
}>) {
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const busyRef = useRef(false);
  const close = () => { if (busyRef.current) return; setPassword(""); setConfirmation(""); setError(""); onClose(); };
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!user || busyRef.current) return;
    if (password !== confirmation) { setError("Hai mật khẩu chưa trùng khớp."); return; }
    busyRef.current = true; setBusy(true); setError("");
    try { const updated = await resetManagedUserPassword(user.id, password, schoolId); onUpdated(updated); }
    catch (err) { setError(apiMessage(err, "Không thể đặt lại mật khẩu.")); return; }
    finally { busyRef.current = false; setBusy(false); }
    close();
  };
  return <Dialog.Root open={Boolean(user)} onOpenChange={open => { if (!open) close(); }}><Dialog.Content maxWidth="440px" onEscapeKeyDown={event => { if (busyRef.current) event.preventDefault(); }} onInteractOutside={event => { if (busyRef.current) event.preventDefault(); }}>
    <Dialog.Title>Đặt lại mật khẩu</Dialog.Title>
    <Dialog.Description size="2" mb="4">{user?.email} phải đổi mật khẩu khi đăng nhập lần tiếp theo.</Dialog.Description>
    <form onSubmit={submit} style={{ display: "grid", gap: 16 }}>
      <Text as="label" size="2" weight="medium">Mật khẩu tạm thời<TextField.Root type="password" size="3" autoComplete="new-password" required minLength={8} maxLength={120} value={password} disabled={busy} onChange={event => setPassword(event.target.value)} mt="2" /></Text>
      <Text as="label" size="2" weight="medium">Nhập lại mật khẩu<TextField.Root type="password" size="3" autoComplete="new-password" required minLength={8} maxLength={120} value={confirmation} disabled={busy} onChange={event => setConfirmation(event.target.value)} mt="2" /></Text>
      {error && <Callout.Root size="1" color="red" role="alert"><Callout.Text>{error}</Callout.Text></Callout.Root>}
      <Flex justify="end" gap="3"><Button type="button" variant="soft" color="gray" disabled={busy} onClick={close}>Hủy</Button><Button type="submit" disabled={busy} loading={busy}>Đặt lại mật khẩu</Button></Flex>
    </form>
  </Dialog.Content></Dialog.Root>;
}