import { useRef, useState, type ChangeEvent } from "react";
import { Badge, Button, Callout, Dialog, Flex, IconButton, Select, Table, Text, TextField } from "@radix-ui/themes";
import { CheckCircledIcon, Cross2Icon, DownloadIcon, FileTextIcon, ReloadIcon, TrashIcon, UploadIcon } from "@radix-ui/react-icons";
import { apiMessage } from "../../utils/apiError";
import { commitImport, credentialsCsv, downloadBlob, downloadImportTemplate, previewImportFile, previewImportRows, type ImportKind, type ImportPreview, type ImportResult } from "./importApi";
import s from "./SchoolBulkImport.module.css";

const importLabels: Record<ImportKind, string> = { USERS: "Tài khoản", CLASSES: "Lớp học", ENROLLMENTS: "Xếp lớp học sinh", TEACHER_ASSIGNMENTS: "Phân công giáo viên" };
const columnLabels: Record<string, string> = { fullName: "Họ và tên", dateOfBirth: "Ngày sinh", email: "Email", initialPassword: "Mật khẩu ban đầu", role: "Vai trò", staffType: "Loại giáo viên", classCode: "Mã lớp", schoolYear: "Năm học", avatarUrl: "Ảnh đại diện (URL)", gradeLevel: "Khối", subject: "Môn học", studentEmail: "Email học sinh", teacherEmail: "Email giáo viên" };
type Props = { schoolId: string; kind: ImportKind; label?: string; disabled?: boolean; onImported?: () => void | Promise<void> };

export default function SchoolBulkImport({ schoolId, kind, label = "Nhập CSV", disabled, onImported }: Readonly<Props>) {
  const fileRef = useRef<HTMLInputElement>(null);
  const busyRef = useRef(false);
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [templateBusy, setTemplateBusy] = useState(false);
  const [preview, setPreview] = useState<ImportPreview | null>(null);
  const [result, setResult] = useState<ImportResult | null>(null);
  const [filename, setFilename] = useState("");
  const [dirty, setDirty] = useState(false);
  const [error, setError] = useState("");
  const close = () => {
    if (busyRef.current) return;
    setOpen(false); setPreview(null); setResult(null); setFilename(""); setDirty(false); setError("");
  };
  const start = () => { if (busyRef.current) return false; busyRef.current = true; setBusy(true); setError(""); return true; };
  const finish = () => { busyRef.current = false; setBusy(false); };
  const chooseFile = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.currentTarget.files?.[0]; event.currentTarget.value = "";
    if (!file || !start()) return;
    setPreview(null); setResult(null); setDirty(false); setFilename(file.name);
    try {
      if (file.size > 1_048_576) throw new Error("Tệp CSV tối đa 1 MB.");
      const next = await previewImportFile(schoolId, kind, file);
      setFilename(file.name); setPreview(next); setDirty(false); setResult(null);
    } catch (err) { setError(apiMessage(err, err instanceof Error ? err.message : "Không thể đọc CSV.")); }
    finally { finish(); }
  };
  const edit = (rowNumber: number, column: string, value: string) => {
    setPreview(current => current ? { ...current, canCommit: false, previewToken: null, rows: current.rows.map(row => row.row === rowNumber ? { ...row, data: { ...row.data, [column]: value, ...(column === "role" ? { staffType: value === "STAFF" ? row.data.staffType || "TEACHER" : "" } : {}) } } : row) } : current);
    setDirty(true); setError("");
  };
  const remove = (rowNumber: number) => {
    setPreview(current => current ? { ...current, canCommit: false, previewToken: null, rows: current.rows.filter(row => row.row !== rowNumber) } : current);
    setDirty(true); setError("");
  };
  const validate = async () => {
    if (!preview?.rows.length || !start()) return;
    try {
      setPreview(await previewImportRows(schoolId, kind, preview.rows.map(({ row, data }) => ({ row, data }))));
      setDirty(false);
    } catch (err) { setError(apiMessage(err, "Không thể kiểm tra dữ liệu.")); }
    finally { finish(); }
  };
  const commit = async () => {
    if (!preview?.canCommit || dirty || !start()) return;
    try {
      const next = await commitImport(schoolId, preview);
      setResult(next); setPreview(null); setDirty(false);
      // The committed result remains available for credential export even if refreshing the page data fails.
      try { await onImported?.(); } catch { setError("Đã nhập thành công. Tải lại danh sách để xem dữ liệu mới."); }
    } catch (err) { setError(apiMessage(err, "Không thể nhập dữ liệu. Hãy kiểm tra lại trước khi thử tiếp.")); setDirty(true); }
    finally { finish(); }
  };
  const template = async () => {
    if (templateBusy) return; setTemplateBusy(true); setError("");
    try { await downloadImportTemplate(schoolId, kind); }
    catch (err) { setError(apiMessage(err, "Không thể tải tệp mẫu.")); }
    finally { setTemplateBusy(false); }
  };
  const selectOptions = (column: string, row: Record<string, string>) => column === "role" ? [{ value: "STAFF", label: "Giáo viên" }, { value: "STUDENT", label: "Học sinh" }]
    : column === "staffType" && row.role === "STAFF" ? [{ value: "TEACHER", label: "Giáo viên" }, { value: "DEPARTMENT_HEAD", label: "Trưởng bộ môn" }]
    : column === "gradeLevel" ? [10, 11, 12].map(value => ({ value: String(value), label: String(value) })) : null;

  return <Dialog.Root open={open} onOpenChange={next => { if (next) setOpen(true); else close(); }}>
    <Dialog.Trigger><Button variant="soft" disabled={disabled}><UploadIcon />{label}</Button></Dialog.Trigger>
    <Dialog.Content maxWidth="1200px" size="3" className={s.dialog} onEscapeKeyDown={event => { if (busyRef.current) event.preventDefault(); }} onPointerDownOutside={event => { if (busyRef.current) event.preventDefault(); }}>
      <Flex justify="between" align="start" gap="3"><div><Dialog.Title>Nhập CSV · {importLabels[kind]}</Dialog.Title><Dialog.Description size="2">Tối đa 200 dòng, UTF-8, 1 MB. Kiểm tra và sửa dữ liệu trước khi xác nhận.</Dialog.Description></div><Dialog.Close><IconButton variant="soft" color="gray" aria-label="Đóng" disabled={busy}><Cross2Icon /></IconButton></Dialog.Close></Flex>
      {error && <Callout.Root color="red" role="alert" mt="4"><Callout.Text>{error}</Callout.Text></Callout.Root>}
      {!result && <>
        <Flex gap="3" wrap="wrap" align="center" mt="4" mb="4"><Button variant="outline" onClick={() => void template()} loading={templateBusy} disabled={templateBusy || busy}><DownloadIcon />Tải mẫu đầy đủ</Button><Button onClick={() => fileRef.current?.click()} loading={busy && !preview} disabled={busy}><UploadIcon />{preview ? "Chọn tệp khác" : "Chọn tệp CSV"}</Button><input ref={fileRef} type="file" accept=".csv,text/csv" hidden aria-label="Chọn CSV" disabled={busy} onChange={event => void chooseFile(event)} />{filename && <Text size="2" color="gray"><FileTextIcon /> {filename}</Text>}</Flex>
        {!preview && <div className={s.guide}><Text as="p" size="2">Tệp mẫu có dòng ví dụ và đúng các cột cần dùng.</Text>{kind === "USERS" ? <Text as="p" size="2" color="gray">Ngày sinh: yyyy-MM-dd. Để trống mật khẩu để tạo tự động. STAFF mặc định là giáo viên; chọn DEPARTMENT_HEAD cho trưởng bộ môn. Mã lớp và năm học là tùy chọn. Họ tên và ngày sinh trùng sẽ có cảnh báo; email phải khác.</Text> : <Text as="p" size="2" color="gray">Mã lớp là tên lớp trong trường. Năm học gồm hai năm liên tiếp, ví dụ 2026-2027. Với xếp lớp hoặc phân công, dùng email tài khoản đã tồn tại trong trường.</Text>}</div>}
        {preview && <>
          <Flex align="center" justify="between" gap="3" wrap="wrap" mb="3"><Flex align="center" gap="2"><Badge color="indigo">{preview.rows.length} dòng</Badge>{dirty ? <Badge color="amber">Cần kiểm tra lại</Badge> : <><Badge color="green">{preview.validRows} hợp lệ</Badge>{preview.invalidRows > 0 && <Badge color="red">{preview.invalidRows} lỗi</Badge>}</>}</Flex><Button variant="soft" onClick={() => void validate()} loading={busy} disabled={busy || !preview.rows.length}><ReloadIcon />Kiểm tra lại</Button></Flex>
          <div className={s.tableScroll}><Table.Root size="2" variant="surface"><Table.Header><Table.Row><Table.ColumnHeaderCell>Dòng</Table.ColumnHeaderCell>{preview.columns.map(column => <Table.ColumnHeaderCell key={column}>{columnLabels[column] ?? column}<Text as="div" size="1" color="gray">{column}</Text></Table.ColumnHeaderCell>)}<Table.ColumnHeaderCell>Kết quả kiểm tra</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Xóa</Table.ColumnHeaderCell></Table.Row></Table.Header><Table.Body>{preview.rows.map(row => <Table.Row key={row.row}><Table.RowHeaderCell>{row.row}</Table.RowHeaderCell>{preview.columns.map(column => {
            const options = selectOptions(column, row.data);
            const label = `${columnLabels[column] ?? column}, dòng ${row.row}`;
            return <Table.Cell key={column} className={s.cell}>{options ? <Select.Root size="2" value={row.data[column] || undefined} disabled={busy} onValueChange={value => edit(row.row, column, value)}><Select.Trigger aria-label={label} placeholder="Chọn…" style={{ width: "100%" }} /><Select.Content>{options.map(option => <Select.Item key={option.value} value={option.value}>{option.label}</Select.Item>)}</Select.Content></Select.Root> : <TextField.Root aria-label={label} disabled={busy || column === "staffType" && row.data.role !== "STAFF"} placeholder={column === "staffType" && row.data.role !== "STAFF" ? "Không áp dụng" : undefined} type={column === "initialPassword" ? "password" : column === "dateOfBirth" ? "date" : "text"} value={row.data[column] ?? ""} onChange={event => edit(row.row, column, event.target.value)} />}</Table.Cell>;
          })}<Table.Cell className={s.validation}>{dirty ? <Text size="2" color="gray">Cần kiểm tra lại</Text> : <>{row.errors.map(message => <Text as="p" color="red" size="2" key={message}>{message}</Text>)}{row.warnings.map(message => <Text as="p" color="amber" size="2" key={message}>{message}</Text>)}{row.matches.map(match => <Text as="p" size="2" color="gray" key={match.id}>{match.fullName} · {match.dateOfBirth} · {match.email}</Text>)}{!row.errors.length && !row.warnings.length && <Badge color="green">Hợp lệ</Badge>}</>}</Table.Cell><Table.Cell><IconButton variant="ghost" color="red" aria-label={`Xóa dòng ${row.row}`} disabled={busy} onClick={() => remove(row.row)}><TrashIcon /></IconButton></Table.Cell></Table.Row>)}</Table.Body></Table.Root></div>
          {!preview.rows.length && <Text as="p" size="2" color="gray" mt="3">Không còn dòng nào. Chọn tệp khác để tiếp tục.</Text>}
          <Flex justify="between" align="center" gap="3" wrap="wrap" mt="4"><Text size="2" color="gray">Toàn bộ lô chỉ được nhập khi mọi dòng hợp lệ.</Text><Button disabled={busy || dirty || !preview.canCommit || !preview.rows.length} loading={busy} onClick={() => void commit()}><CheckCircledIcon />Xác nhận nhập {preview.rows.length} dòng</Button></Flex>
        </>}
      </>}
      {result && <div className={s.success}><CheckCircledIcon width="32" height="32" /><Text as="p" size="4" weight="bold">Đã nhập {result.imported} dòng</Text>{result.credentials.length > 0 && <><Text as="p" size="2" color="gray">Tải danh sách tài khoản và mật khẩu ban đầu trước khi đóng. Mật khẩu không hiển thị lại trong danh sách người dùng.</Text><Button onClick={() => downloadBlob(new Blob([credentialsCsv(result.credentials)], { type: "text/csv;charset=utf-8" }), "tai-khoan-moi.csv")}><DownloadIcon />Tải tài khoản và mật khẩu</Button></>}<Button variant="soft" color="gray" onClick={close}>Đóng</Button></div>}
    </Dialog.Content>
  </Dialog.Root>;
}
