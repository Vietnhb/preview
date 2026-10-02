import { useEffect, useState, type FormEvent } from "react";
import LearningIcon from "../../../shared/ui/LearningIcon";
import { Badge, Button as ThemeButton, Callout, Checkbox, Dialog, Table, Text, TextField } from "@radix-ui/themes";
import { adminSchools, createSchool, updateSchool, type ManagedSchool } from "../api/schoolApi";
import { apiMessage } from "../../../shared/lib/apiError";
import { PageHeader, Panel, Loading, ErrorNotice, Button, FormField } from "../../../shared/ui/ManagementUI";

type SchoolForm = { code: string; name: string; address: string; active: boolean; licenseStart: string; licenseEnd: string; monthlyTokenQuota: string };

const blankSchool: SchoolForm = { code: "", name: "", address: "", active: true, licenseStart: "", licenseEnd: "", monthlyTokenQuota: "0" };

export function SchoolsView() {
  const [items, setItems] = useState<ManagedSchool[]>([]); const [form, setForm] = useState<SchoolForm>(blankSchool); const [editing, setEditing] = useState<string | null>(null); const [showForm, setShowForm] = useState(false); const [loading, setLoading] = useState(true); const [busy, setBusy] = useState(false); const [error, setError] = useState("");
  const load = async () => { setLoading(true); try { setItems(await adminSchools()); setError(""); } catch (e) { setError(apiMessage(e, "Không thể tải danh sách trường học.")); } finally { setLoading(false); } }; useEffect(() => { void load(); }, []);
  const submit = async (event: FormEvent) => { event.preventDefault(); setBusy(true); try { const payload = { ...form, licenseStart: form.licenseStart || null, licenseEnd: form.licenseEnd || null, monthlyTokenQuota: form.monthlyTokenQuota === "" ? null : Number(form.monthlyTokenQuota) }; if (editing) { await updateSchool(editing, payload); } else { await createSchool(payload); } setShowForm(false); setEditing(null); setForm(blankSchool); await load(); } catch (e) { setError(apiMessage(e, "Không thể lưu trường học.")); } finally { setBusy(false); } };
  return <div className="admin-content">
    <PageHeader title="Trường học" description="" action={<Button primary onClick={() => { setEditing(null); setForm(blankSchool); setShowForm(true); }}><LearningIcon name="plus" />Thêm trường</Button>} />
    {error && <ErrorNotice error={error} onRetry={() => void load()} />}
    <Dialog.Root open={showForm} onOpenChange={setShowForm}><Dialog.Content maxWidth="620px" aria-describedby={undefined}><Dialog.Title>{editing ? "Cập nhật trường" : "Thêm trường"}</Dialog.Title>
      {error && <Callout.Root color="red" size="1" mb="4" role="alert"><Callout.Text>{error}</Callout.Text></Callout.Root>}
      <form className="admin-form-grid" onSubmit={submit}>
        <FormField label="Mã trường" htmlFor="managed-school-code"><TextField.Root id="managed-school-code" size="3" required value={form.code} disabled={Boolean(editing)} onChange={event => setForm({ ...form, code: event.target.value })} /></FormField>
        <FormField label="Tên trường" htmlFor="managed-school-name"><TextField.Root id="managed-school-name" size="3" required value={form.name} onChange={event => setForm({ ...form, name: event.target.value })} /></FormField>
        <FormField label="Địa chỉ" htmlFor="managed-school-address"><TextField.Root id="managed-school-address" size="3" value={form.address} onChange={event => setForm({ ...form, address: event.target.value })} /></FormField>
        <FormField label="Ngày bắt đầu license" htmlFor="managed-school-start"><TextField.Root id="managed-school-start" size="3" type="date" value={form.licenseStart} required={Boolean(form.licenseEnd)} onChange={event => setForm({ ...form, licenseStart: event.target.value })} /></FormField>
        <FormField label="Ngày hết hạn license" htmlFor="managed-school-end"><TextField.Root id="managed-school-end" size="3" type="date" min={form.licenseStart} value={form.licenseEnd} required={Boolean(form.licenseStart)} onChange={event => setForm({ ...form, licenseEnd: event.target.value })} /></FormField>
        <FormField label="Token AI/tháng (trống = không giới hạn)" htmlFor="managed-school-quota"><TextField.Root id="managed-school-quota" size="3" type="number" min="0" step="1" value={form.monthlyTokenQuota} onChange={event => setForm({ ...form, monthlyTokenQuota: event.target.value })} /></FormField>
        <Text as="label" size="2" className="admin-checkbox"><Checkbox checked={form.active} onCheckedChange={checked => setForm({ ...form, active: checked === true })} />Đang hoạt động</Text>
        <div className="admin-form-actions"><Button onClick={() => setShowForm(false)}>Hủy</Button><ThemeButton type="submit" size="3" loading={busy} disabled={busy}>{busy ? "Đang lưu…" : "Lưu trường"}</ThemeButton></div>
      </form>
    </Dialog.Content></Dialog.Root>
    {loading ? <Loading /> : <Panel title={`${items.length} trường học`}><Table.Root size="2" variant="ghost"><Table.Header><Table.Row><Table.ColumnHeaderCell>Mã</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Tên trường</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Địa chỉ</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Trạng thái</Table.ColumnHeaderCell><Table.ColumnHeaderCell /></Table.Row></Table.Header><Table.Body>{items.map(item => <Table.Row key={item.id}><Table.Cell><Text weight="medium">{item.code}</Text></Table.Cell><Table.Cell>{item.name}</Table.Cell><Table.Cell>{item.address || "—"}</Table.Cell><Table.Cell><Badge color={item.active ? "cyan" : "gray"} size="2">{item.active ? "Hoạt động" : "Đã tắt"}</Badge></Table.Cell><Table.Cell><Button onClick={() => { setEditing(item.id); setForm({ code: item.code, name: item.name, address: item.address ?? "", active: item.active, licenseStart: item.licenseStart ?? "", licenseEnd: item.licenseEnd ?? "", monthlyTokenQuota: item.monthlyTokenQuota == null ? "" : String(item.monthlyTokenQuota) }); setShowForm(true); }}>Sửa</Button></Table.Cell></Table.Row>)}</Table.Body></Table.Root></Panel>}
  </div>;
}