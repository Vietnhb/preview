import { useEffect, useState } from "react";
import { Navigate, Outlet, useNavigate, useOutletContext, useParams, useSearchParams } from "react-router-dom";
import { Card, Heading, Text } from "@radix-ui/themes";
import api from "../../../shared/api/client";
import { useSessionStore } from "../../../shared/auth/sessionStore";
import AppSidebarLayout, { type SidebarGroup } from "../../../shared/layout/AppSidebar";
import { reviewerAccess, type ReviewerAccess, type ReviewerView } from "../model/reviewerAccess";
import { ReviewerIcon } from "../components/ReviewerKit";
import { ReviewerOverview } from "../components/ReviewerOverview";
import { ContentModeration } from "../components/ContentModeration";
import { TopicsWorkspace } from "../components/TopicsWorkspace";
import { BenchmarksTab } from "../components/BenchmarksTab";
import { ComplaintsDesk } from "../components/ComplaintsDesk";
import { complaintsForReview } from "../../support/api/supportApi";
import "../styles/reviewer.css";
import "../../support/styles/complaints.css";

/** Older links used ?view= / ?tab=; send them to the matching route. */
const LEGACY: Record<string, string> = { overview: "", moderation: "moderation", library: "moderation", topics: "topics", schemas: "topics", modules: "topics", benchmarks: "benchmarks" };
const pathOf = (view: ReviewerView, extra?: Record<string, string>) =>
  view === "overview" ? "/reviewer" : `/reviewer/${view}${view === "topics" && extra?.section ? `/${extra.section}` : ""}`;

type ReviewerContext = { access: ReviewerAccess };
const useReviewer = () => useOutletContext<ReviewerContext>();

/** REVIEWER area (also open to MANAGER). The sidebar shows only what the account was granted. */
export default function ReviewerLayout() {
  const user = useSessionStore(state => state.user);
  const [params] = useSearchParams();
  const access = reviewerAccess(user);
  const [pending, setPending] = useState(0);
  useEffect(() => {
    if (!access.canReview) return;
    let active = true;
    const load = () => void api.get<unknown[]>("/reviewer/library", { params: { status: "PENDING" } }).then(response => { if (active) setPending(response.data.length); }).catch(() => undefined);
    load(); const timer = window.setInterval(load, 60000);
    return () => { active = false; window.clearInterval(timer); };
  }, [access.canReview]);

  // Complaints are open to every reviewer account, whichever permission it holds.
  const canHandleComplaints = access.canEdit || access.canReview;
  const [openComplaints, setOpenComplaints] = useState(0);
  useEffect(() => {
    if (!canHandleComplaints) return;
    let active = true;
    const load = () => void complaintsForReview().then(items => { if (active) setOpenComplaints(items.filter(item => item.status !== "RESOLVED").length); }).catch(() => undefined);
    load(); const timer = window.setInterval(load, 60000);
    return () => { active = false; window.clearInterval(timer); };
  }, [canHandleComplaints]);

  const legacy = params.get("view") ?? params.get("tab");
  if (legacy !== null && legacy in LEGACY) return <Navigate to={`/reviewer${LEGACY[legacy] ? `/${LEGACY[legacy]}` : ""}`} replace />;

  const groups: SidebarGroup[] = [{ items: [{ to: "/reviewer", label: "Việc cần làm", icon: "grid", end: true }] }];
  if (access.canReview) groups.push({ label: "Kiểm duyệt nội dung", items: [{ to: "/reviewer/moderation", label: "Mô phỏng chờ duyệt", icon: "shield", badge: pending }] });
  if (canHandleComplaints) groups.push({ label: "Hỗ trợ giáo viên", items: [{ to: "/reviewer/complaints", label: "Khiếu nại mô phỏng", icon: "message", badge: openComplaints }] });
  if (access.canEdit) groups.push({ label: "Biên soạn dữ liệu chuẩn", items: [
    { to: "/reviewer/topics", label: "Chủ đề vật lý", icon: "atom" },
    { to: "/reviewer/benchmarks", label: "Đề kiểm thử AI", icon: "chart" },
  ] });
  if (access.isManager) groups.push({ label: "Vận hành", items: [{ to: "/manager", label: "Về trang vận hành", icon: "back" }] });

  return <AppSidebarLayout id="reviewer" subtitle="Kiểm duyệt" home="/reviewer" groups={groups} contentClassName="reviewer-academic reviewer-page">
    {!access.canEdit && !access.canReview
      ? <div className="reviewer-blocked"><Card size="4"><span className="reviewer-empty-icon"><ReviewerIcon name="lock" size={28} /></span>
          <Heading as="h1" size="5" mt="3">Tài khoản chưa được cấp quyền</Heading>
          <Text as="p" color="gray" mt="2">Tài khoản kiểm duyệt cần ít nhất một quyền: <strong>kiểm duyệt nội dung</strong> hoặc <strong>biên soạn dữ liệu chuẩn</strong>. Hãy liên hệ bộ phận vận hành (Manager) để được cấp quyền.</Text>
        </Card></div>
      : <Outlet context={{ access } satisfies ReviewerContext} />}
  </AppSidebarLayout>;
}

export function ReviewerHomePage() {
  const { access } = useReviewer();
  const navigate = useNavigate();
  const name = useSessionStore(state => state.user?.fullName);
  return <ReviewerOverview access={access} name={name} onOpen={(view, extra) => navigate(pathOf(view, extra))} />;
}

export function ReviewerModerationPage() {
  const { access } = useReviewer();
  return access.canReview ? <ContentModeration /> : <Navigate to="/reviewer" replace />;
}

export function ReviewerTopicsPage() {
  const { access } = useReviewer();
  const { section } = useParams();
  const navigate = useNavigate();
  if (!access.canEdit) return <Navigate to="/reviewer" replace />;
  return <TopicsWorkspace section={section ?? null} onSection={next => navigate(`/reviewer/topics/${next}`)} />;
}

export function ReviewerComplaintsPage() {
  const { access } = useReviewer();
  return access.canEdit || access.canReview ? <ComplaintsDesk /> : <Navigate to="/reviewer" replace />;
}

export function ReviewerBenchmarksPage() {
  const { access } = useReviewer();
  return access.canEdit ? <BenchmarksTab /> : <Navigate to="/reviewer" replace />;
}
