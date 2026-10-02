import type { PropsWithChildren } from "react";
import { Theme } from "@radix-ui/themes";
import { MotionConfig } from "motion/react";
import { useLocation } from "react-router-dom";
import NavBar from "../shared/layout/NavBar";
import LicenseNotice from "./LicenseNotice";
import { useSessionStore } from "../shared/auth/sessionStore";
import { useEffectiveTheme } from "../shared/theme/themeStore";
import academic from "../shared/layout/AcademicChrome.module.css";

export default function AppShell({ children }: PropsWithChildren) {
  const { pathname } = useLocation();
  const user = useSessionStore(state => state.user);
  const role = user?.role;
  const appearance = useEffectiveTheme();
  const passwordRequired = user?.mustChangePassword === true;
  const hasRoleTheme = Boolean(role && role !== "STAFF");
  const academicPage = role && role !== "STAFF"
    && ["/admin", "/manager", "/school", "/reviewer", "/student", "/assignments", "/community", "/library", "/curriculum", "/profile"].some(
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
    ["/signup", "/admin", "/manager", "/school", "/reviewer", "/student", "/lab"].some(
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
      <Theme appearance={appearance} accentColor="indigo" grayColor="slate" radius="large" scaling="100%" className={shellClassName}>
        {content}
      </Theme>
    </MotionConfig>
  ) : <div className={shellClassName}>{content}</div>;
}