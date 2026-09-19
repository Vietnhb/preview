import type { PropsWithChildren } from "react";
import { useLocation } from "react-router-dom";
import NavBar from "../components/common/NavBar";
import LicenseNotice from "./LicenseNotice";
export default function AppShell({ children }: PropsWithChildren) {
  const { pathname } = useLocation();
  const fullPage =
    [
      "/player",
      "/workspace",
      "/assignments/workspace",
      "/models",
      "/lab",
    ].includes(pathname) ||
    ["/signup", "/admin", "/school"].some(
      (prefix) => pathname === prefix || pathname.startsWith(prefix + "/"),
    );
  return (
    <div
      className={
        fullPage
          ? "full-shell"
          : pathname === "/reviewer"
            ? "shell reviewer-shell"
            : "shell"
      }
    >
      {!fullPage && <NavBar />}
      <LicenseNotice />
      {children}
    </div>
  );
}
