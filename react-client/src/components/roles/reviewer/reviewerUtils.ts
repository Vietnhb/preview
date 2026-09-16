export function lifecycleClass(status: string) {
  if (status === "APPROVED") return "pass";
  if (status === "DRAFT") return "draft";
  return "fail";
}



