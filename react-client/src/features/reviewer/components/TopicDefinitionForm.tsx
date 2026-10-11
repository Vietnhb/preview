import { useState } from "react";
import { Text, TextArea } from "@radix-ui/themes";
import { AdvancedJson, RowsEditor } from "./JsonEditor";
import { objectRows } from "../model/jsonRows";
import { CapabilitiesEditor } from "./CapabilitiesEditor";

type Definition = Record<string, unknown>;

const SECTION_LABELS: Record<string, string> = {
  relationTypes: "Loại quan hệ", laws: "Định luật", unitCatalog: "Đơn vị",
  applicationRequirements: "Yêu cầu áp dụng", curriculum: "Nội dung chương trình", conceptVocabulary: "Từ vựng khái niệm",
  simulationCapability: "Năng lực mô phỏng", visualCapability: "Năng lực hiển thị",
};

/** Visual editor for the parts of a topic pack a physics expert edits most;
 *  everything else stays reachable through the advanced JSON view. */
export function TopicDefinitionForm({ value, onChange }: Readonly<{ value: Definition; onChange: (next: Definition) => void }>) {
  const set = (key: string, next: unknown) => onChange({ ...value, [key]: next });
  const limitations = Array.isArray(value.limitations) ? (value.limitations as unknown[]).map(String).join("\n") : "";
  const others = Object.keys(SECTION_LABELS).filter(key => value[key] !== undefined).map(key => {
    const section = value[key];
    return `${SECTION_LABELS[key]}${Array.isArray(section) ? ` (${section.length})` : ""}`;
  });
  return <div className="reviewer-form-sections">
    <label className="reviewer-field"><span>Mô tả chủ đề</span><TextArea size="2" rows={3} placeholder="Chủ đề này bao gồm những hiện tượng nào?" value={typeof value.description === "string" ? value.description : ""} onChange={event => set("description", event.target.value)} /></label>
    <section className="reviewer-form-section"><Text as="div" size="2" weight="bold">Đối tượng vật lý</Text><Text as="p" size="1" color="gray" mb="2">Những vật/hệ thường gặp trong đề bài của chủ đề (xe, lò xo, điện tích…).</Text>
      <RowsEditor rows={objectRows(value.objectTypes)} onChange={rows => set("objectTypes", rows)} addLabel="Thêm đối tượng" empty="Chưa có đối tượng nào."
        columns={[{ key: "label", label: "Tên hiển thị", placeholder: "vật chuyển động", width: "28%" }, { key: "description", label: "Mô tả", placeholder: "Xe, người, bóng…" }, { key: "type", label: "Mã kỹ thuật", placeholder: "moving_body", width: "22%" }]} />
    </section>
    <section className="reviewer-form-section"><Text as="div" size="2" weight="bold">Đại lượng</Text><Text as="p" size="1" color="gray" mb="2">Các đại lượng AI được phép trích xuất, kèm đơn vị hợp lệ (cách nhau bởi dấu phẩy).</Text>
      <RowsEditor rows={objectRows(value.quantityDefinitions)} onChange={rows => set("quantityDefinitions", rows)} addLabel="Thêm đại lượng" empty="Chưa có đại lượng nào."
        columns={[{ key: "label", label: "Tên", placeholder: "tốc độ ban đầu", width: "24%" }, { key: "symbol", label: "Kí hiệu", placeholder: "v₀", width: "10%" }, { key: "allowedUnits", label: "Đơn vị", placeholder: "m/s, km/h", type: "list", width: "16%" }, { key: "description", label: "Mô tả" }, { key: "key", label: "Mã kỹ thuật", placeholder: "initial_speed", width: "20%" }]} />
    </section>
    <section className="reviewer-form-section"><Text as="div" size="2" weight="bold">Bài toán mô phỏng và công thức</Text><Text as="p" size="1" color="gray" mb="2">Mỗi bài toán có bộ công thức giải số (RK4) và, nếu có, nghiệm giải tích để đối chiếu. Bấm vào một bài toán để sửa.</Text>
      <CapabilitiesEditor capabilities={objectRows(value.capabilities)} objectTypes={objectRows(value.objectTypes).map(item => String(item.type ?? "")).filter(Boolean)} onChange={next => set("capabilities", next)} />
    </section>
    <LinesField label="Giới hạn của chủ đề (mỗi dòng một ý)" placeholder="Bỏ qua sức cản không khí…" value={limitations} onChange={lines => set("limitations", lines)} />
    {others.length > 0 && <Text as="p" size="1" color="gray">Phần khác giữ nguyên: {others.join(", ")}. Chỉnh trong mục nâng cao nếu cần.</Text>}
    <AdvancedJson value={value} onChange={onChange} />
  </div>;
}

function LinesField({ label, placeholder, value, onChange }: Readonly<{ label: string; placeholder?: string; value: string; onChange: (lines: string[]) => void }>) {
  const [text, setText] = useState(value);
  const clean = (raw: string) => raw.split("\n").map(line => line.trim()).filter(Boolean);
  // Follow outside edits (advanced JSON) without fighting the line the user is typing.
  const shown = clean(text).join("\n") === value ? text : value;
  return <label className="reviewer-field"><span>{label}</span><TextArea size="2" rows={3} placeholder={placeholder} value={shown} onChange={event => { setText(event.target.value); onChange(clean(event.target.value)); }} /></label>;
}
