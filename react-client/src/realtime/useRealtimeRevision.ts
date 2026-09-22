import { useEffect, useRef, useState } from "react";
import { useLocation } from "react-router-dom";
import { API_URL } from "../config/api";
import { getToken } from "../utils/token";

const clientId = globalThis.crypto?.randomUUID?.() ?? `client-${Date.now()}-${Math.random()}`;

type ChangeEvent = { revision: number; path: string; clientId: string | null };

const relevant = (page: string, api: string) => {
  if (page.startsWith("/admin")) return true;
  if (page.startsWith("/school"))
    return /\/(schools|school|assignments|student|user|auth\/payments|simulations|problems)/.test(api);
  if (page.startsWith("/reviewer"))
    return /\/(reviewer|schemas|evaluations|library|simulations|problems)/.test(api);
  if (page.startsWith("/assignments")) return /\/(assignments|schools|user)/.test(api);
  if (page === "/library" || page === "/community") return api.includes("/library");
  if (page === "/curriculum") return api.includes("/curriculum");
  if (page === "/profile") return /\/(user|admin\/users|schools)/.test(api);
  if (["/workspace", "/lab", "/models", "/player"].includes(page))
    return /\/(simulations|problems|library|curriculum|assignments)/.test(api);
  if (page === "/") return /\/(library|curriculum|simulations)/.test(api);
  return false;
};

export const realtimeClientId = clientId;

export function useRealtimeRevision(enabled: boolean) {
  const { pathname } = useLocation();
  const [revision, setRevision] = useState(0);
  const pageRef = useRef(pathname);
  const lastServerRevision = useRef<number | null>(null);
  pageRef.current = pathname;

  useEffect(() => {
    if (!enabled) return;
    const controller = new AbortController();
    let retryMs = 1_000;

    const refresh = (apiPath?: string) => {
      if (!apiPath || relevant(pageRef.current, apiPath)) setRevision(value => value + 1);
    };

    const consume = async () => {
      while (!controller.signal.aborted) {
        try {
          const token = getToken();
          if (!token) return;
          const response = await fetch(`${API_URL}/realtime/events`, {
            headers: { Accept: "text/event-stream", Authorization: `Bearer ${token}` },
            cache: "no-store",
            signal: controller.signal,
          });
          if (!response.ok || !response.body) throw new Error(`Realtime stream returned ${response.status}`);
          retryMs = 1_000;
          const reader = response.body.getReader();
          const decoder = new TextDecoder();
          let buffer = "";
          while (!controller.signal.aborted) {
            const { done, value } = await reader.read();
            if (done) break;
            buffer += decoder.decode(value, { stream: true }).replaceAll("\r", "");
            let boundary = buffer.indexOf("\n\n");
            while (boundary >= 0) {
              const block = buffer.slice(0, boundary);
              buffer = buffer.slice(boundary + 2);
              const event = block.split("\n").find(line => line.startsWith("event:"))?.slice(6).trim();
              const data = block.split("\n").filter(line => line.startsWith("data:"))
                .map(line => line.slice(5).trimStart()).join("\n");
              if (event === "ready") {
                const serverRevision = Number(data);
                if (lastServerRevision.current !== null && serverRevision !== lastServerRevision.current) refresh();
                lastServerRevision.current = serverRevision;
              } else if (event === "change") {
                const change = JSON.parse(data) as ChangeEvent;
                lastServerRevision.current = change.revision;
                if (change.clientId !== clientId) refresh(change.path);
              }
              boundary = buffer.indexOf("\n\n");
            }
          }
        } catch (error) {
          if (controller.signal.aborted) return;
          console.warn("Realtime connection interrupted; reconnecting.", error);
        }
        await new Promise(resolve => globalThis.setTimeout(resolve, retryMs));
        retryMs = Math.min(retryMs * 2, 15_000);
      }
    };
    void consume();
    return () => controller.abort();
  }, [enabled]);

  return revision;
}
