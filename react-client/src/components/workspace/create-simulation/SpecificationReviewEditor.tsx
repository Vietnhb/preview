import { useState } from "react";
import type { Ambiguity, EndCondition, Specification } from "../../../types/physlive";

type SpecificationPayload = Pick<Specification, "objects" | "quantities" | "relations" | "endCondition">;
type SpecificationDraft = {
  objects: unknown[];
  quantities: unknown[];
  relations: unknown[];
  endCondition: Record<string, unknown> | null;
};
type RecordCollection = "objects" | "quantities" | "relations";

const quantityNames: Record<string, string> = {
  acceleration: "Gia tốc", amplitude: "Biên độ", capacitance: "Điện dung", charge: "Điện tích",
  current: "Cường độ dòng điện", distance: "Quãng đường", duration: "Thời gian", force: "Lực",
  friction_coefficient: "Hệ số ma sát", gravitational_acceleration: "Gia tốc trọng trường", height: "Độ cao",
  initial_height: "Độ cao ban đầu", initial_position: "Vị trí ban đầu", initial_velocity: "Vận tốc ban đầu",
  launch_angle: "Góc phóng", mass: "Khối lượng", mass_1: "Khối lượng vật 1", mass_2: "Khối lượng vật 2",
  net_force: "Hợp lực", phase: "Pha ban đầu", position: "Vị trí", radius: "Bán kính", resistance: "Điện trở",
  spring_constant: "Độ cứng lò xo", velocity: "Vận tốc", velocity_1: "Vận tốc vật 1", velocity_2: "Vận tốc vật 2",
  voltage: "Điện áp", work: "Công", energy: "Năng lượng", time: "Thời gian",
};

const friendlyKeys: Record<string, string> = {
  id: "Mã tham chiếu", label: "Tên hiển thị", name: "Tên", title: "Tiêu đề", type: "Loại",
  subject: "Vật thể chịu tác động", object: "Vật thể liên quan", value: "Giá trị", unit: "Đơn vị",
  sourceText: "Trích đoạn đề bài", confidence: "Độ tin cậy nhận diện", originalValue: "Giá trị trong đề bài",
  originalUnit: "Đơn vị trong đề bài", symbol: "Ký hiệu", normalizedValue: "Giá trị sử dụng",
  normalizedUnit: "Đơn vị sử dụng", entities: "Các vật thể", quantity: "Đại lượng", operator: "Điều kiện so sánh",
  duration: "Thời lượng", maxTime: "Thời gian tối đa", count: "Số chu kỳ", event: "Sự kiện",
};

const endConditionNames: Record<string, string> = {
  time_limit: "Sau một khoảng thời gian",
  threshold: "Khi đạt một giá trị",
  event: "Khi xảy ra sự kiện",
  cycle_count: "Sau một số chu kỳ",
  manual: "Khi giáo viên dừng",
};

const objectTypeNames: Record<string, string> = {
  circuit: "Mạch điện", capacitor: "Tụ điện", resistor: "Điện trở", battery: "Nguồn điện",
  body: "Vật thể", mass: "Vật có khối lượng", projectile: "Vật được phóng", cart: "Xe chuyển động",
  spring: "Lò xo", block: "Khối vật", object: "Đối tượng vật lý",
};

const endConditionTypes = Object.keys(endConditionNames);
const operators = [">=", "<=", ">", "<", "=="] as const;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T;
}

function makeDraft(specification: Specification): SpecificationDraft {
  return {
    objects: clone(specification.objects ?? []),
    quantities: clone(specification.quantities ?? []).map(item => {
      if (!isRecord(item)) return item;
      return {
        ...item,
        normalizedValue: item.normalizedValue ?? item.value,
        normalizedUnit: item.normalizedUnit ?? item.originalUnit,
      };
    }),
    relations: clone(specification.relations ?? []),
    endCondition: isRecord(specification.endCondition) ? clone(specification.endCondition) : null,
  };
}

function humanizeKey(key: string): string {
  if (friendlyKeys[key]) return friendlyKeys[key];
  return key.replace(/([a-z])([A-Z])/g, "$1 $2").replace(/[_-]+/g, " ").replace(/^./, character => character.toLocaleUpperCase());
}

function displayValue(value: unknown): string {
  if (value === null || value === undefined || value === "") return "Chưa có thông tin";
  if (typeof value === "boolean") return value ? "Có" : "Không";
  if (Array.isArray(value)) return value.map(displayValue).join(", ");
  if (isRecord(value)) return `${Object.keys(value).length} mục thông tin`;
  return String(value);
}

function textValue(value: unknown): string {
  return value === null || value === undefined ? "" : String(value);
}

function numberValue(value: unknown): string {
  return (typeof value === "number" || typeof value === "string") && value !== "" && Number.isFinite(Number(value)) ? String(value) : "";
}

function isFiniteInput(value: unknown): boolean {
  return value !== "" && value !== null && value !== undefined && Number.isFinite(Number(value));
}

function updateAt(items: unknown[], index: number, patch: Record<string, unknown>): unknown[] {
  return items.map((item, itemIndex) => itemIndex === index && isRecord(item) ? { ...item, ...patch } : item);
}

function firstExistingKey(record: Record<string, unknown>, candidates: string[], fallback: string): string {
  return candidates.find(key => Object.hasOwn(record, key)) ?? fallback;
}

function entityName(value: unknown, objects: unknown[]): string {
  const reference = textValue(value);
  const match = objects.find(item => isRecord(item) && [item.id, item.name, item.label].some(candidate => textValue(candidate) === reference));
  if (isRecord(match)) return textValue(match.label ?? match.name ?? reference);
  return reference ? "Vật thể chưa nhận diện" : "Chưa xác định";
}

function optionValue(record: Record<string, unknown>): string {
  return textValue(record.id ?? record.name ?? record.label);
}

function optionLabel(record: Record<string, unknown>, index: number): string {
  return textValue(record.label ?? record.name ?? record.title) || `Vật thể ${index + 1}`;
}

function relationTypeLabel(type: unknown): string {
  const value = textValue(type);
  const translations: Record<string, string> = {
    initial_condition: "có điều kiện ban đầu là", initial_state: "có trạng thái ban đầu",
    causes: "chịu tác động bởi", affects: "tác động đến", connected_to: "được nối với",
    contains: "chứa", acts_on: "tác dụng lên", requested_simulation_duration: "mô phỏng trong",
    simulation_duration: "mô phỏng trong", duration: "mô phỏng trong",
  };
  return translations[value] ?? "có liên hệ với";
}

function validateDraft(draft: SpecificationDraft): string {
  if (!Array.isArray(draft.objects) || !Array.isArray(draft.quantities) || !Array.isArray(draft.relations)) {
    return "Thông tin mô hình chưa đúng định dạng. Hãy tải lại đề bài hoặc liên hệ quản trị viên.";
  }
  for (const [index, item] of draft.quantities.entries()) {
    if (!isRecord(item)) return `Đại lượng thứ ${index + 1} chưa đúng định dạng.`;
    if (!textValue(item.name).trim()) return `Hãy bổ sung tên cho đại lượng thứ ${index + 1}.`;
    const value = item.normalizedValue;
    if (value === "" || value === null || value === undefined || !Number.isFinite(Number(value))) {
      return `Hãy nhập một giá trị hợp lệ cho “${quantityNames[textValue(item.name)] ?? humanizeKey(textValue(item.name))}”.`;
    }
    if (!textValue(item.normalizedUnit).trim()) {
      return `Hãy bổ sung đơn vị cho “${quantityNames[textValue(item.name)] ?? humanizeKey(textValue(item.name))}”.`;
    }
  }
  if (draft.endCondition) {
    const type = textValue(draft.endCondition.type);
    if (!endConditionTypes.includes(type)) return "Hãy chọn cách kết thúc mô phỏng hợp lệ.";
    const requiredNumericField = type === "time_limit" ? "duration" : type === "threshold" ? "value" : type === "cycle_count" ? "count" : null;
    if (requiredNumericField && !isFiniteInput(draft.endCondition[requiredNumericField])) {
      return "Hãy nhập điều kiện kết thúc hợp lệ.";
    }
    if (type === "time_limit" && Number(draft.endCondition.duration) <= 0) return "Thời lượng mô phỏng phải lớn hơn 0.";
    if ((type === "threshold" || type === "cycle_count") && !textValue(draft.endCondition.quantity).trim()) return "Hãy chọn đại lượng cho điều kiện dừng.";
    if (type === "threshold" && !operators.includes(textValue(draft.endCondition.operator) as typeof operators[number])) return "Hãy chọn điều kiện so sánh hợp lệ.";
    if (type === "cycle_count" && (Number(draft.endCondition.count) <= 0 || !Number.isInteger(Number(draft.endCondition.count)))) return "Số chu kỳ phải là số nguyên lớn hơn 0.";
    if (type === "event") {
      const event = isRecord(draft.endCondition.event) ? draft.endCondition.event : {};
      if (!Array.isArray(event.entities) || event.entities.length === 0) return "Hãy chọn ít nhất một vật thể cho sự kiện kết thúc.";
      if (event.type === "contact" && (!textValue(event.quantity).trim() || !operators.includes(textValue(event.operator) as typeof operators[number]) || !isFiniteInput(event.value))) {
        return "Điều kiện tiếp xúc cần có đại lượng, phép so sánh và giá trị.";
      }
      if (!["contact", "collision"].includes(textValue(event.type))) return "Hãy chọn một sự kiện kết thúc hợp lệ.";
    }
    if (["manual", "threshold", "event", "cycle_count"].includes(type) && draft.endCondition.maxTime !== undefined && draft.endCondition.maxTime !== "" && (!Number.isFinite(Number(draft.endCondition.maxTime)) || Number(draft.endCondition.maxTime) <= 0)) {
      return "Thời gian an toàn tối đa phải lớn hơn 0.";
    }
  }
  return "";
}

type SpecificationReviewEditorProps = {
  specification: Specification;
  loading: boolean;
  ambiguities: Ambiguity[];
  onSave: (value: SpecificationPayload) => void;
  onConfirm: (value: SpecificationPayload) => void;
};

export function SpecificationReviewEditor({ specification, loading, ambiguities, onSave, onConfirm }: Readonly<SpecificationReviewEditorProps>) {
  const [draft, setDraft] = useState<SpecificationDraft>(() => makeDraft(specification));
  const [error, setError] = useState("");
  const hasAmbiguities = ambiguities.length > 0;
  const needsAttention = hasAmbiguities || specification.confirmationState === "UNRESOLVED";

  const updateCollection = (collection: RecordCollection, index: number, patch: Record<string, unknown>) => {
    setDraft(current => ({ ...current, [collection]: updateAt(current[collection], index, patch) }));
    setError("");
  };

  const updateEndCondition = (patch: Record<string, unknown>) => {
    setDraft(current => ({
      ...current,
      endCondition: { ...(current.endCondition ?? { type: "time_limit", duration: 10 }), ...patch },
    }));
    setError("");
  };

  const changeEndConditionType = (type: string) => {
    const defaults: Record<string, Record<string, unknown>> = {
      time_limit: { duration: 10 },
      threshold: { quantity: "", operator: ">=", value: 0, maxTime: 10 },
      event: { event: { type: "contact", entities: [] }, maxTime: 10 },
      cycle_count: { quantity: "", count: 1, maxTime: 10 },
      manual: { maxTime: 10 },
    };
    setDraft(current => ({
      ...current,
      endCondition: { ...current.endCondition, ...defaults[type], type },
    }));
    setError("");
  };

  const submit = (action: (value: SpecificationPayload) => void) => {
    const validationError = validateDraft(draft);
    if (validationError) {
      setError(validationError);
      return;
    }
    action({
      objects: draft.objects,
      quantities: draft.quantities as Specification["quantities"],
      relations: draft.relations,
      endCondition: draft.endCondition as EndCondition | null,
    });
    setError("");
  };

  const conditionType = textValue(draft.endCondition?.type || "time_limit");
  const eventCondition = isRecord(draft.endCondition?.event) ? draft.endCondition.event : {};
  const selectedEventEntities = Array.isArray(eventCondition.entities) ? eventCondition.entities.map(textValue) : [];

  return <section className="create-chat-specification-review" aria-label="Kiểm tra thông tin mô phỏng">
    <header className="create-chat-specification-heading">
      <div>
        <span className="create-chat-specification-eyebrow">BƯỚC KIỂM TRA</span>
        <strong>Thông tin mô phỏng</strong>
        <span>AI đã tách dữ kiện từ đề bài. Thầy cô có thể kiểm tra và chỉnh lại trước khi tạo mô phỏng.</span>
      </div>
      <span className={`create-chat-specification-status ${needsAttention ? "warning" : "ready"}`}>
        {needsAttention ? "Cần làm rõ" : "Đã nhận diện"}
      </span>
    </header>

    {hasAmbiguities && <p className="create-chat-specification-guidance">Hãy trả lời câu hỏi của AI phía trên để chốt cách hiểu trước khi chạy mô phỏng.</p>}

    <fieldset className="create-chat-specification-form" disabled={loading}>
      <section className="create-chat-review-section">
        <div className="create-chat-review-section-heading">
          <div><span className="create-chat-review-step">01</span><div><h3>Vật thể trong tình huống</h3><p>Những đối tượng AI nhận ra từ đề bài</p></div></div>
          <span className="create-chat-review-count">{draft.objects.length} vật thể</span>
        </div>
        {draft.objects.length ? <div className="create-chat-review-object-grid">
          {draft.objects.map((item, index) => {
            if (!isRecord(item)) return <article className="create-chat-review-object" key={`object-${index}`}><span>Thông tin vật thể {index + 1}</span><p>{displayValue(item)}</p></article>;
            const labelKey = firstExistingKey(item, ["label", "name", "title"], "label");
            const typeKey = firstExistingKey(item, ["type", "category", "kind"], "type");
            const displayName = textValue(item[labelKey]) || `Vật thể ${index + 1}`;
            const typeName = textValue(item[typeKey]);
            const knownKeys = new Set([labelKey, typeKey, "id"]);
            const extraEntries = Object.entries(item).filter(([key]) => !knownKeys.has(key));
            return <article className="create-chat-review-object" key={textValue(item.id) || `object-${index}`}>
              <div className="create-chat-review-object-top"><span className="create-chat-review-object-mark" aria-hidden="true">{index + 1}</span><strong>{displayName}</strong>{typeName && <span className="create-chat-review-object-type">{objectTypeNames[typeName] ?? "Vật thể mô phỏng"}</span>}</div>
              <div className="create-chat-review-fields two-columns">
                <label><span>Tên vật thể</span><input value={textValue(item[labelKey])} onChange={event => updateCollection("objects", index, { [labelKey]: event.target.value })} /></label>
              </div>
              {extraEntries.length > 0 && <details className="create-chat-review-details"><summary>Thông tin bổ sung ({extraEntries.length})</summary><dl>{extraEntries.map(([key, value]) => <div key={key}><dt>{humanizeKey(key)}</dt><dd>{displayValue(value)}</dd></div>)}</dl></details>}
            </article>;
          })}
        </div> : <p className="create-chat-review-empty">Đề bài chưa có vật thể riêng biệt.</p>}
      </section>

      <section className="create-chat-review-section">
        <div className="create-chat-review-section-heading">
          <div><span className="create-chat-review-step">02</span><div><h3>Các đại lượng vật lý</h3><p>Kiểm tra giá trị và đơn vị được dùng để tính</p></div></div>
          <span className="create-chat-review-count">{draft.quantities.length} đại lượng</span>
        </div>
        {draft.quantities.length ? <div className="create-chat-review-quantity-grid">
          {draft.quantities.map((item, index) => {
            if (!isRecord(item)) return <article className="create-chat-review-quantity" key={`quantity-${index}`}><strong>Đại lượng {index + 1}</strong><p>{displayValue(item)}</p></article>;
            const name = textValue(item.name);
            const label = quantityNames[name] ?? humanizeKey(name || `Đại lượng ${index + 1}`);
            const sourceText = item.sourceText;
            return <article className="create-chat-review-quantity" key={`${name}-${index}`}>
              <div className="create-chat-review-quantity-title"><div><strong>{label}</strong>{Boolean(item.symbol) && <span className="create-chat-review-symbol">{textValue(item.symbol)}</span>}</div><span className="create-chat-review-quantity-number">{String(index + 1).padStart(2, "0")}</span></div>
              <div className="create-chat-review-value-fields">
                <label><span>Giá trị</span><input type="number" inputMode="decimal" step="any" value={numberValue(item.normalizedValue)} onChange={event => updateCollection("quantities", index, { normalizedValue: event.target.value === "" ? "" : Number(event.target.value) })} /></label>
                <label><span>Đơn vị</span><input value={textValue(item.normalizedUnit)} onChange={event => updateCollection("quantities", index, { normalizedUnit: event.target.value })} /></label>
              </div>
              {(Boolean(sourceText) || item.originalValue !== undefined) && <details className="create-chat-review-details"><summary>Đối chiếu với đề bài</summary><p>{sourceText ? `“${textValue(sourceText)}”` : `${displayValue(item.originalValue)} ${textValue(item.originalUnit)}`}</p></details>}
            </article>;
          })}
        </div> : <p className="create-chat-review-empty">Chưa nhận diện được đại lượng. Hãy bổ sung dữ kiện trong đề bài rồi yêu cầu AI phân tích lại.</p>}
      </section>

      <section className="create-chat-review-section">
        <div className="create-chat-review-section-heading">
          <div><span className="create-chat-review-step">03</span><div><h3>Mối liên hệ</h3><p>Các tác động hoặc điều kiện AI hiểu từ đề bài</p></div></div>
          <span className="create-chat-review-count">{draft.relations.length} mối liên hệ</span>
        </div>
        {draft.relations.length ? <div className="create-chat-review-relation-list">
          {draft.relations.map((item, index) => {
            if (!isRecord(item)) return <article className="create-chat-review-relation" key={`relation-${index}`}><span>{displayValue(item)}</span></article>;
            const relationType = item.type ?? item.relation ?? item.name;
            const subject = entityName(item.subject ?? item.from, draft.objects);
            const relatedObject = entityName(item.object ?? item.target, draft.objects);
            const relationTypeKey = textValue(relationType);
            const relationLabel = relationTypeLabel(relationType);
            const valueText = item.value === undefined || item.value === null ? "" : `${displayValue(item.value)}${item.unit ? ` ${textValue(item.unit)}` : ""}`;
            const isDurationRelation = ["duration", "requested_simulation_duration", "simulation_duration"].includes(relationTypeKey);
            const summary = textValue(item.sourceText)
              ? `Theo đề bài: ${textValue(item.sourceText)}`
              : isDurationRelation
                ? "Thời lượng mô phỏng"
                : item.subject || item.from || item.object || item.target
                  ? `${subject} ${relationLabel} ${relatedObject}`
                  : "Điều kiện được nhận diện";
            const fieldKeys = Object.keys(item).filter(key => !["id", "sourceText"].includes(key));
            const subjectKey = Object.hasOwn(item, "from") ? "from" : "subject";
            const objectKey = Object.hasOwn(item, "target") ? "target" : "object";
            return <details className="create-chat-review-relation" key={textValue(item.id) || `relation-${index}`}>
              <summary><span className="create-chat-review-relation-mark" aria-hidden="true">↔</span><span><strong>{summary}</strong>{valueText && <small>{isDurationRelation ? `Thời lượng: ${valueText}` : `Giá trị liên quan: ${valueText}`}</small>}</span><span className="create-chat-review-edit-hint">Chỉnh sửa</span></summary>
              <div className="create-chat-review-fields two-columns">
                <label><span>Giá trị liên quan</span><input value={textValue(item.value)} onChange={event => updateCollection("relations", index, { value: typeof item.value === "number" ? (event.target.value === "" ? "" : Number(event.target.value)) : event.target.value })} /></label>
                <label><span>Vật thể chịu tác động</span><select value={textValue(item[subjectKey])} onChange={event => updateCollection("relations", index, { [subjectKey]: event.target.value })}><option value="">Chọn vật thể</option>{draft.objects.filter(isRecord).map((object, objectIndex) => <option value={optionValue(object)} key={optionValue(object) || objectIndex}>{optionLabel(object, objectIndex)}</option>)}</select></label>
                <label><span>Vật thể liên quan</span><select value={textValue(item[objectKey])} onChange={event => updateCollection("relations", index, { [objectKey]: event.target.value })}><option value="">Chọn vật thể</option>{draft.objects.filter(isRecord).map((object, objectIndex) => <option value={optionValue(object)} key={optionValue(object) || objectIndex}>{optionLabel(object, objectIndex)}</option>)}</select></label>
                {item.unit !== undefined && <label><span>Đơn vị</span><input value={textValue(item.unit)} onChange={event => updateCollection("relations", index, { unit: event.target.value })} /></label>}
              </div>
              {Boolean(item.sourceText) && <p className="create-chat-review-source">Trích từ đề bài: “{textValue(item.sourceText)}”</p>}
              {fieldKeys.some(key => !["type", "relation", "name", "subject", "from", "object", "target", "value", "unit"].includes(key)) && <p className="create-chat-review-preserved">Các chi tiết khác được giữ nguyên khi lưu.</p>}
            </details>;
          })}
        </div> : <p className="create-chat-review-empty">Đề bài chưa nêu mối liên hệ bổ sung.</p>}
      </section>

      <section className="create-chat-review-section create-chat-review-end-condition">
        <div className="create-chat-review-section-heading">
          <div><span className="create-chat-review-step">04</span><div><h3>Khi nào dừng mô phỏng?</h3><p>Chọn điểm kết thúc để kết quả tập trung vào yêu cầu của bài</p></div></div>
        </div>
        <label className="create-chat-review-condition-choice"><span>Mô phỏng sẽ dừng</span><select value={endConditionNames[conditionType] ? conditionType : "time_limit"} onChange={event => changeEndConditionType(event.target.value)}>
          {endConditionTypes.map(type => <option value={type} key={type}>{endConditionNames[type]}</option>)}
        </select></label>
        {conditionType === "time_limit" && <div className="create-chat-review-condition-fields"><label><span>Thời lượng</span><div className="create-chat-review-input-with-unit"><input type="number" min="0.01" step="any" value={numberValue(draft.endCondition?.duration)} onChange={event => updateEndCondition({ duration: event.target.value === "" ? "" : Number(event.target.value) })} /><span>giây</span></div></label></div>}
        {conditionType === "threshold" && <div className="create-chat-review-condition-fields three-columns">
          <label><span>Đại lượng</span><select value={textValue(draft.endCondition?.quantity)} onChange={event => updateEndCondition({ quantity: event.target.value })}><option value="">Chọn đại lượng</option>{draft.quantities.filter(isRecord).map((quantity, index) => <option value={textValue(quantity.name)} key={`${textValue(quantity.name)}-${index}`}>{quantityNames[textValue(quantity.name)] ?? humanizeKey(textValue(quantity.name))}</option>)}</select></label>
          <label><span>Điều kiện</span><select value={textValue(draft.endCondition?.operator || ">=")} onChange={event => updateEndCondition({ operator: event.target.value })}>{operators.map(operator => <option key={operator}>{operator}</option>)}</select></label>
          <label><span>Giá trị cần đạt</span><input type="number" step="any" value={numberValue(draft.endCondition?.value)} onChange={event => updateEndCondition({ value: event.target.value === "" ? "" : Number(event.target.value) })} /></label>
        </div>}
        {conditionType === "event" && <div className="create-chat-review-event-form">
          <label className="create-chat-review-event-type"><span>Kết thúc khi</span><select value={textValue(eventCondition.type || "contact")} onChange={event => updateEndCondition({ event: { ...eventCondition, type: event.target.value } })}><option value="contact">Các vật thể tiếp xúc</option><option value="collision">Các vật thể va chạm</option></select></label>
          <div className="create-chat-review-entity-picker"><span>Chọn vật thể liên quan</span>{draft.objects.filter(isRecord).length ? <div>{draft.objects.filter(isRecord).map((object, index) => {
            const value = optionValue(object);
            return <label key={value || index}><input type="checkbox" checked={selectedEventEntities.includes(value)} onChange={event => {
              const entities = event.target.checked ? [...selectedEventEntities, value] : selectedEventEntities.filter(entity => entity !== value);
              updateEndCondition({ event: { ...eventCondition, entities } });
            }} /><span>{optionLabel(object, index)}</span></label>;
          })}</div> : <p>Chưa có vật thể để chọn.</p>}</div>
          {eventCondition.type === "contact" && <div className="create-chat-review-condition-fields three-columns">
            <label><span>Đại lượng theo dõi</span><select value={textValue(eventCondition.quantity)} onChange={event => updateEndCondition({ event: { ...eventCondition, quantity: event.target.value } })}><option value="">Chọn đại lượng</option>{draft.quantities.filter(isRecord).map((quantity, index) => <option value={textValue(quantity.name)} key={`${textValue(quantity.name)}-${index}`}>{quantityNames[textValue(quantity.name)] ?? humanizeKey(textValue(quantity.name))}</option>)}</select></label>
            <label><span>Điều kiện</span><select value={textValue(eventCondition.operator || ">=")} onChange={event => updateEndCondition({ event: { ...eventCondition, operator: event.target.value } })}>{operators.map(operator => <option key={operator}>{operator}</option>)}</select></label>
            <label><span>Giá trị tiếp xúc</span><input type="number" step="any" value={numberValue(eventCondition.value)} onChange={event => updateEndCondition({ event: { ...eventCondition, value: event.target.value === "" ? "" : Number(event.target.value) } })} /></label>
          </div>}
        </div>}
        {conditionType === "cycle_count" && <div className="create-chat-review-condition-fields two-columns"><label><span>Đại lượng chu kỳ</span><select value={textValue(draft.endCondition?.quantity)} onChange={event => updateEndCondition({ quantity: event.target.value })}><option value="">Chọn đại lượng</option>{draft.quantities.filter(isRecord).map((quantity, index) => <option value={textValue(quantity.name)} key={`${textValue(quantity.name)}-${index}`}>{quantityNames[textValue(quantity.name)] ?? humanizeKey(textValue(quantity.name))}</option>)}</select></label><label><span>Số chu kỳ</span><input type="number" min="1" step="1" value={numberValue(draft.endCondition?.count)} onChange={event => updateEndCondition({ count: event.target.value === "" ? "" : Number(event.target.value) })} /></label></div>}
        {(conditionType === "manual" || conditionType === "threshold" || conditionType === "event" || conditionType === "cycle_count") && <div className="create-chat-review-condition-limit"><label><span>Thời gian an toàn tối đa</span><div className="create-chat-review-input-with-unit"><input type="number" min="0.01" step="any" value={numberValue(draft.endCondition?.maxTime)} onChange={event => updateEndCondition({ maxTime: event.target.value === "" ? undefined : Number(event.target.value) })} /><span>giây</span></div></label><small>Giúp mô phỏng tự dừng nếu chưa chạm điều kiện.</small></div>}
      </section>
    </fieldset>

    {error && <p className="create-chat-specification-error" role="alert">{error}</p>}
    <div className="create-chat-specification-actions">
      <button type="button" className="create-chat-secondary-action" onClick={() => submit(onSave)} disabled={loading}>Lưu thông tin</button>
      {!hasAmbiguities && <button type="button" className="create-chat-specification-confirm" onClick={() => submit(onConfirm)} disabled={loading}>Xác nhận và tạo mô phỏng</button>}
    </div>
  </section>;
}
