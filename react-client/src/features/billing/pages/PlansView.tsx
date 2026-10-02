import { useEffect, useState, type FormEvent } from "react";
import { Badge, Checkbox, Table, Text, TextField } from "@radix-ui/themes";
import { adminPlans, saveManagedPlan, type ManagedPlan } from "../api/billingApi";
import { apiMessage } from "../../../shared/lib/apiError";
import { PageHeader, Panel, Loading, ErrorNotice, Button, FormField } from "../../../shared/ui/ManagementUI";

export function PlansView() {
  const [items, setItems] = useState<ManagedPlan[]>([]);
  const [form, setForm] = useState<ManagedPlan | null>(null);
  const [editing, setEditing] = useState(false);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const load = async () => { setLoading(true); try { setItems(await adminPlans()); setError(""); } catch (err) { setError(apiMessage(err, "Không thể tải danh mục gói.")); } finally { setLoading(false); } };
  useEffect(() => { void load(); }, []);
  const submit = async (event: FormEvent) => {
    event.preventDefault(); if (!form || busy) return; setBusy(true); setError("");
    try { await saveManagedPlan(form, editing); setForm(null); await load(); }
    catch (err) { setError(apiMessage(err, "Không thể lưu gói.")); } finally { setBusy(false); }
  };
  return <div className="admin-content"><PageHeader title="Danh mục gói" description="Giá và quota công khai của PhysLive." action={<Button primary onClick={() => { setEditing(false); setForm({ code: "", name: "", description: "", annualPriceVnd: 0, studentQuota: 0, monthlyTokenQuota: 0, active: false }); }}>Thêm gói</Button>} />
    {error && <ErrorNotice error={error} onRetry={() => void load()} />}
    {form && <Panel title={editing ? "Cập nhật gói" : "Thêm gói"} description="Thay đổi áp dụng cho báo giá mới. Các giao dịch đã tạo giữ số tiền và quota đã chốt."><form className="admin-form-grid" onSubmit={submit}>
      <FormField label="Mã gói" htmlFor="managed-plan-code"><TextField.Root id="managed-plan-code" size="3" required maxLength={40} pattern={"[A-Z0-9_\\-]+"} readOnly={editing} value={form.code} onChange={e => setForm({ ...form, code: e.target.value.toUpperCase() })} /></FormField>
      <FormField label="Tên gói" htmlFor="managed-plan-name"><TextField.Root id="managed-plan-name" size="3" required maxLength={255} value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} /></FormField>
      <FormField label="Mô tả" htmlFor="managed-plan-description"><TextField.Root id="managed-plan-description" size="3" required maxLength={255} value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} /></FormField>
      <FormField label="Giá / năm (VND)" htmlFor="managed-plan-price"><TextField.Root id="managed-plan-price" size="3" type="number" required min="1" step="1" value={form.annualPriceVnd || ""} onChange={e => setForm({ ...form, annualPriceVnd: Number(e.target.value) })} /></FormField>
      <FormField label="Quota học sinh" htmlFor="managed-plan-students"><TextField.Root id="managed-plan-students" size="3" type="number" required min="1" step="1" value={form.studentQuota || ""} onChange={e => setForm({ ...form, studentQuota: Number(e.target.value) })} /></FormField>
      <FormField label="Token AI / tháng (trống = không giới hạn)" htmlFor="managed-plan-tokens"><TextField.Root id="managed-plan-tokens" size="3" type="number" min="0" step="1" value={form.monthlyTokenQuota ?? ""} onChange={e => setForm({ ...form, monthlyTokenQuota: e.target.value === "" ? null : Number(e.target.value) })} /></FormField>
      <Text as="label" className="admin-checkbox"><Checkbox checked={form.active} onCheckedChange={checked => setForm({ ...form, active: checked === true })} /> Cho phép đăng ký</Text>
      <div className="admin-form-actions"><Button disabled={busy} onClick={() => setForm(null)}>Hủy</Button><Button primary type="submit" disabled={busy}>{busy ? "Đang lưu…" : "Lưu gói"}</Button></div>
    </form></Panel>}
    {loading ? <Loading /> : <Panel title="Gói đăng ký"><div className="admin-table-scroll"><Table.Root size="2" variant="ghost" className="admin-table"><Table.Header><Table.Row><Table.ColumnHeaderCell>Mã</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Tên</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Giá / năm</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Học sinh</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Token / tháng</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Trạng thái</Table.ColumnHeaderCell><Table.ColumnHeaderCell /></Table.Row></Table.Header><Table.Body>{items.map(plan => <Table.Row key={plan.code}><Table.Cell>{plan.code}</Table.Cell><Table.Cell>{plan.name}</Table.Cell><Table.Cell>{plan.annualPriceVnd.toLocaleString("vi-VN")} ₫</Table.Cell><Table.Cell>{plan.studentQuota.toLocaleString("vi-VN")}</Table.Cell><Table.Cell>{plan.monthlyTokenQuota == null ? "Không giới hạn" : plan.monthlyTokenQuota.toLocaleString("vi-VN")}</Table.Cell><Table.Cell><Badge color={plan.active ? "cyan" : "gray"} size="2">{plan.active ? "Đang bán" : "Ẩn"}</Badge></Table.Cell><Table.Cell><Button onClick={() => { setEditing(true); setForm({ ...plan }); }}>Sửa</Button></Table.Cell></Table.Row>)}</Table.Body></Table.Root></div></Panel>}
  </div>;
}