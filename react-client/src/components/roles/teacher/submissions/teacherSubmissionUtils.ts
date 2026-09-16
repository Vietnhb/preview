export function formatDate(value: string | undefined) {
  if (!value) return "Chưa đặt hạn";
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? "Chưa đặt hạn"
    : date.toLocaleDateString("vi-VN");
}

export function formatShortDate(value: string | undefined) {
  if (!value) return "Chưa đặt hạn";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "Chưa đặt hạn";
  return `${String(date.getDate()).padStart(2, "0")} Th${String(date.getMonth() + 1).padStart(2, "0")}`;
}

export function formatDateTime(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "—" : date.toLocaleString("vi-VN");
}

export function predictionText(predictions: unknown) {
  if (typeof predictions === "string") return predictions;
  if (predictions === null || predictions === undefined)
    return "Chưa có dự đoán";
  return JSON.stringify(predictions) ?? "Chưa có dự đoán";
}

export function initials(name: string, studentId: number) {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return `HS${studentId}`.slice(0, 3).toUpperCase();
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase();
}
