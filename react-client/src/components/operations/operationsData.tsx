import { useEffect, useRef, useState } from "react";
import axios from "axios";
import api from "../../api/axios";

export function message(error: unknown) {
  return axios.isAxiosError<{ message?: string }>(error)
    ? error.response?.data?.message || "Không thể kết nối máy chủ. Vui lòng thử lại."
    : error instanceof Error ? error.message : "Thao tác chưa hoàn tất.";
}
export function useResource<T>(url: string) {
  const [revision, setRevision] = useState(0);
  const key = `${url}:${revision}`;
  const [result, setResult] = useState<{ key: string; data?: T; error?: string }>({ key: "" });
  useEffect(() => {
    const controller = new AbortController();
    void api.get<T>(url, { signal: controller.signal }).then(r => {
      if (!controller.signal.aborted) setResult({ key, data: r.data });
    }).catch(error => { if (!controller.signal.aborted) setResult({ key, error: message(error) }); });
    return () => controller.abort();
  }, [url, key]);
  return { data: result.data, error: result.key === key ? result.error : undefined, loading: result.key !== key,
    refresh: () => setRevision(value => value + 1) };
}
export function useAction() {
  const lock = useRef(false);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const run = async (action: () => Promise<unknown>, success = "Đã lưu thay đổi.") => {
    if (lock.current) return false;
    lock.current = true; setBusy(true); setError(""); setNotice("");
    try { await action(); setNotice(success); return true; }
    catch (e) { setError(message(e)); return false; }
    finally { lock.current = false; setBusy(false); }
  };
  return { busy, run, feedback: <>{error && <div className="ops-alert" role="alert">{error}</div>}{notice && <div className="ops-success" role="status">{notice}</div>}</> };
}
export function parseObject(value: string) {
  const parsed: unknown = JSON.parse(value);
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) throw new Error("JSON phải là một object.");
  return parsed;
}
export function downloadJson(value: unknown, name: string) {
  const url = URL.createObjectURL(new Blob([JSON.stringify(value, null, 2)], { type: "application/json" }));
  const link = document.createElement("a"); link.href = url; link.download = name; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
}
