import type { SupportStatus } from "../api/supportApi";

/** How a complaint's state is worded for the people following it. */
export const COMPLAINT_STATUS: Record<SupportStatus, string> = { OPEN: "Chờ xử lý", READ: "Đang xem xét", RESOLVED: "Đã giải quyết" };
