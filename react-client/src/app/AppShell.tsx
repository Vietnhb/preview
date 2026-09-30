import type { PropsWithChildren } from "react";
import { Theme } from "@radix-ui/themes";
import { MotionConfig } from "motion/react";
import { useLocation } from "react-router-dom";
import NavBar from "../components/common/NavBar";
import LicenseNotice from "./LicenseNotice";
import { usePhysliveStore } from "../store/usePhysliveStore";
import academic from "./AcademicChrome.module.css";
export default function AppShell({ children }: PropsWithChildren) {
  const { pathname } = useLocation();
  const user = usePhysliveStore(state => state.user);
  const role = user?.role;
  const passwordRequired = user?.mustChangePassword === true;
  const hasRoleTheme = Boolean(role && role !== "STAFF");
  const academicPage = role && role !== "STAFF"
    && ["/admin", "/manager", "/school", "/reviewer", "/assignments", "/community", "/library", "/curriculum", "/profile"].some(
      prefix => pathname === prefix || pathname.startsWith(prefix + "/"),
    );
  const fullPage =
    [
      "/player",
      "/workspace",
      "/assignments/workspace",
      "/models",
      "/lab",
    ].includes(pathname) ||
    ["/signup", "/admin", "/manager", "/school"].some(
      (prefix) => pathname === prefix || pathname.startsWith(prefix + "/"),
    );
  const shellClassName = `${fullPage ? "full-shell" : "shell"} ${academicPage ? academic.chrome : ""}`;
  const content = <>
      {!fullPage && !passwordRequired && <NavBar />}
      {!passwordRequired && <LicenseNotice />}
      {children}
    </>;
  return hasRoleTheme ? (
    <MotionConfig reducedMotion="user" transition={{ duration: 0.18, ease: "easeOut" }}>
      <Theme accentColor="indigo" grayColor="slate" radius="large" scaling="100%" className={shellClassName}>
        {content}
      </Theme>
    </MotionConfig>
  ) : <div className={shellClassName}>{content}</div>;
}
