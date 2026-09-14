import type { Simulation, Specification } from "../types/physlive";

export type LessonKind = "motion" | "projectile" | "forces" | "spring" | "collision" | "circuit";
export type LearningSeries = { key: string; label: string; symbol: string; unit: string; color: string; data: number[] };
export type LearningControl = { key: string; label: string; symbol: string; unit: string; min: number; max: number; step: number };

export function lessonKind(schemaId: string): LessonKind {
  const schema = schemaId.toLowerCase();
  if (/circuit|rc_|rl_/.test(schema)) return "circuit";
  if (/projectile|2d/.test(schema)) return "projectile";
  if (/spring|oscillation/.test(schema)) return "spring";
  if (schema.includes("collision")) return "collision";
  if (/dynamic|force/.test(schema)) return "forces";
  return "motion";
}

export const lessonCopy: Record<LessonKind, { title: string; topic: string; goal: string; formula: string; explanation: string; prompt: string; answer: string }> = {
  motion: {
    title: "Chuyển động trên đường thẳng", topic: "Chuyển động học",
    goal: "Khám phá mối liên hệ giữa vị trí, vận tốc và gia tốc.",
    formula: "x(t) = x₀ + v₀t + ½at²",
    explanation: "Khi gia tốc không đổi, vận tốc thay đổi đều theo thời gian. Độ dốc của đồ thị vị trí cho biết vận tốc; độ dốc của đồ thị vận tốc cho biết gia tốc.",
    prompt: "Nếu gia tốc bằng 0, đồ thị vận tốc sẽ có hình dạng gì?",
    answer: "Đường nằm ngang: vận tốc không đổi. Nếu vận tốc cũng bằng 0, vật đứng yên; nếu khác 0, vị trí thay đổi đều theo thời gian.",
  },
  projectile: {
    title: "Khám phá chuyển động ném", topic: "Chuyển động hai chiều",
    goal: "Quan sát vận tốc theo hai phương trên cùng một quỹ đạo.",
    formula: "x = x₀ + v₀ cos(θ)t; y = y₀ + v₀ sin(θ)t − ½gt²",
    explanation: "Khi bỏ qua sức cản không khí, vận tốc ngang không đổi. Trọng lực làm vận tốc thẳng đứng giảm theo thời gian. Mô hình toán học có thể tiếp tục xuống dưới y = 0; mặt đất chưa được xử lý như một va chạm.",
    prompt: "Ở đỉnh quỹ đạo, vật có hoàn toàn dừng lại không?",
    answer: "Không nhất thiết. Tại đỉnh, vận tốc thẳng đứng bằng 0 nhưng thành phần ngang vẫn còn. Vật vẫn chuyển động ngang nếu vận tốc ngang khác 0.",
  },
  forces: {
    title: "Lực làm thay đổi chuyển động", topic: "Động lực học",
    goal: "Thay đổi lực hoặc khối lượng để quan sát gia tốc.",
    formula: "ΣF = ma",
    explanation: "Gia tốc cùng hướng với hợp lực. Với cùng hợp lực, khối lượng càng lớn thì gia tốc càng nhỏ. Mô hình này dùng ma sát theo chiều quy ước cố định; cần thận trọng khi vận tốc đổi dấu.",
    prompt: "Giữ nguyên hợp lực và tăng gấp đôi khối lượng, gia tốc thay đổi thế nào?",
    answer: "Gia tốc giảm còn một nửa vì a = ΣF/m. Điều này đúng khi hợp lực thực sự giữ nguyên, bao gồm cả lực ma sát nếu có.",
  },
  spring: {
    title: "Nhịp chuyển động của lò xo", topic: "Dao động điều hòa",
    goal: "Liên hệ li độ, vận tốc và vị trí cân bằng.",
    formula: "x(t) = A cos(ωt + φ); ω = √(k/m)",
    explanation: "Trong mô hình lò xo lý tưởng, gia tốc luôn hướng về vị trí cân bằng. Biên độ quyết định li độ cực đại; độ cứng và khối lượng quyết định nhịp dao động.",
    prompt: "Vật chuyển động nhanh nhất ở biên hay tại vị trí cân bằng?",
    answer: "Tại vị trí cân bằng. Ở hai biên, vận tốc bằng 0 và độ lớn gia tốc đạt cực đại. Hãy đối chiếu đồ thị li độ và vận tốc tại cùng thời điểm.",
  },
  collision: {
    title: "Hai vật tương tác khi va chạm", topic: "Động lượng",
    goal: "So sánh vận tốc của hai vật trước và sau sự kiện.",
    formula: "m₁v₁ + m₂v₂ = m₁v₁′ + m₂v₂′",
    explanation: "Mô hình đang dùng va chạm đàn hồi một chiều. Thời điểm đổi vận tốc được đặt tại t = 1 s; vị trí tiếp xúc chưa được tự động phát hiện. Dùng đồ thị để so sánh trạng thái trước và sau sự kiện.",
    prompt: "Với hai vật có cùng khối lượng, vận tốc sau va chạm đàn hồi sẽ thế nào?",
    answer: "Hai vật trao đổi vận tốc trong va chạm đàn hồi một chiều. Động lượng và động năng toàn hệ được bảo toàn.",
  },
  circuit: {
    title: "Điện áp thay đổi trong mạch RC", topic: "Mạch điện",
    goal: "Quan sát quá trình tích điện hoặc phóng điện của tụ.",
    formula: "τ = RC; nạp: U꜀ = U₀(1 − e⁻ᵗ/τ); xả: U꜀ = U₀e⁻ᵗ/τ",
    explanation: "Hằng số thời gian τ = RC quyết định tốc độ thay đổi điện áp trên tụ. Tăng R hoặc C làm quá trình diễn ra chậm hơn. Chiều dòng điện phụ thuộc vào quá trình nạp hay xả.",
    prompt: "Nếu tăng điện trở R và giữ nguyên C, tụ sẽ thay đổi điện áp nhanh hơn hay chậm hơn?",
    answer: "Chậm hơn vì hằng số thời gian τ = RC tăng. Sau một khoảng τ, tụ nạp đạt khoảng 63,2% điện áp nguồn, hoặc tụ xả còn khoảng 36,8% điện áp ban đầu.",
  },
};

export function controlValue(control: LearningControl, simulation: Simulation, specification?: Specification): number {
  if (Number.isFinite(simulation.parameters?.[control.key])) return simulation.parameters[control.key];
  const quantity = specification?.quantities.find(q => q.name.toLowerCase() === control.key || q.symbol?.toLowerCase() === control.key);
  if (quantity && Number.isFinite(quantity.normalizedValue)) return quantity.normalizedValue;
  // Initial samples describe the actual run when overrides are absent.
  if (control.key === "initial_velocity") {
    const x = simulation.velocities?.x?.[0] ?? simulation.values?.vx?.[0];
    const y = simulation.velocities?.y?.[0] ?? simulation.values?.vy?.[0] ?? 0;
    if (Number.isFinite(x)) return lessonKind(simulation.schemaId) === "projectile" ? Math.hypot(x, y) : x;
  }
  if (control.key === "acceleration") {
    const value = simulation.accelerations?.x?.[0] ?? simulation.values?.ax?.[0];
    if (Number.isFinite(value)) return value;
  }
  return Number.NaN;
}

export function learningSeries(simulation: Simulation): LearningSeries[] {
  const read = (path: string): number[] => {
    const [group, key] = path.split(".");
    const source = (simulation as unknown as Record<string, Record<string, number[]>>)[group];
    return source?.[key] ?? [];
  };
  const candidates: LearningSeries[] = (simulation.visualization?.series ?? []).map(item => ({ ...item, data: read(item.source) }));
  return candidates.filter(s => s.data.length === simulation.time.length && s.data.length > 0 && s.data.every(Number.isFinite));
}

export function indexAtTime(times: number[], time: number): number {
  let low = 0, high = times.length - 1;
  while (low < high) {
    const middle = Math.ceil((low + high) / 2);
    if (times[middle] <= time) low = middle; else high = middle - 1;
  }
  return low;
}

export function interpolateAtTime(times: number[], values: number[], time: number): number {
  if (!values || !values.length) return 0;
  if (!times || times.length < 2 || time <= times[0]) return values[0] ?? 0;
  const lastIndex = times.length - 1;
  if (time >= times[lastIndex]) return values[lastIndex] ?? 0;

  let low = 0, high = lastIndex;
  while (low <= high) {
    const mid = Math.floor((low + high) / 2);
    if (times[mid] <= time) {
      if (mid === lastIndex || times[mid + 1] > time) {
        low = mid;
        break;
      }
      low = mid + 1;
    } else {
      high = mid - 1;
    }
  }

  const i = Math.min(Math.max(0, low), lastIndex - 1);
  const t0 = times[i], t1 = times[i + 1];
  const dt = t1 - t0;
  if (dt <= 1e-9) return values[i] ?? 0;
  const ratio = (time - t0) / dt;
  const v0 = values[i] ?? 0, v1 = values[i + 1] ?? v0;
  return v0 + ratio * (v1 - v0);
}

export const numberLabel = (value: number | undefined, digits = 2) => typeof value === "number" && Number.isFinite(value)
  ? new Intl.NumberFormat("vi-VN", { maximumFractionDigits: digits }).format(value) : "—";
