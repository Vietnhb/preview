// Local visual fixture. Every API request is handled here; no database is used.
import { createRoot } from "react-dom/client";
import { MemoryRouter } from "react-router-dom";
import axios, { AxiosError } from "axios";
import AppRoutes from "../../src/app/AppRoutes";
import AppShell from "../../src/app/AppShell";
import axiosClient from "../../src/api/axios";
import { usePhysliveStore } from "../../src/store/usePhysliveStore";
import type { Assignment, LibraryItem, User } from "../../src/types/physlive";
import "../../src/styles/global.css";

const query = new URLSearchParams(location.search);
const role = query.get("role") || "STUDENT";
const school = { id: "school-1", code: "THPT01", name: "THPT Nguyễn Trãi", address: "Hà Nội", active: true, licenseStart: "2026-08-01", licenseEnd: "2027-08-01", monthlyTokenQuota: 1000000 };
const users: User[] = [
  { id: 1, fullName: "Nguyễn Minh Anh", email: "admin@example.test", role: "ADMIN", active: true },
  { id: 2, fullName: "Trần Quang Huy", email: "manager@example.test", role: "MANAGER", active: true },
  { id: 3, fullName: "Lê Thu Hà", email: "reviewer@example.test", role: "REVIEWER", active: true, reviewerCanEdit: query.get("mode") !== "review", reviewerCanReview: query.get("mode") !== "edit" },
  { id: 4, fullName: "Phạm Ngọc Lan", email: "school@example.test", role: "SCHOOL", active: true, institutionId: school.id, schoolId: school.id, schoolName: school.name },
  { id: 5, fullName: "Đỗ Hoàng Nam", email: "staff@example.test", role: "STAFF", active: true, staffType: query.get("staff") === "head" ? "DEPARTMENT_HEAD" : "TEACHER", institutionId: school.id, schoolId: school.id, schoolName: school.name },
  { id: 6, fullName: "Nguyễn Khánh Linh", email: "student@example.test", role: "STUDENT", active: true, institutionId: school.id, schoolId: school.id, schoolName: school.name, lastLogin: "2026-09-30T07:15:00Z" },
  { id: 7, fullName: "Vũ Nhật Minh", email: "student2@example.test", role: "STUDENT", active: false, institutionId: school.id, schoolId: school.id, schoolName: school.name },
];
const actor = users.find(user => user.role === role) || users[5];
actor.mustChangePassword = query.get("pending") === "1";
usePhysliveStore.getState().setUser(actor);
// Isolated dev preview origin, deliberately invalid outside this adapter.
const previousToken = localStorage.getItem("token");
localStorage.setItem("token", "visual-fixture-only");
window.addEventListener("pagehide", () => {
  if (previousToken && previousToken !== "visual-fixture-only") localStorage.setItem("token", previousToken);
  else localStorage.removeItem("token");
}, { once: true });
const catalog = { topics: [{ id: "physics", name: "Vật lý", slug: "physics", enabled: true, modules: [
  { id: "motion", name: "Động học", slug: "motion", levels: [{ id: "grade-10", name: "Lớp 10", lessons: [{ id: "fall", name: "Sự rơi tự do", slug: "free-fall" }, { id: "linear", name: "Chuyển động thẳng", slug: "linear-motion" }] }] },
  { id: "oscillation", name: "Dao động", slug: "oscillation", levels: [{ id: "grade-11", name: "Lớp 11", lessons: [{ id: "pendulum", name: "Con lắc đơn", slug: "pendulum" }] }] },
  { id: "electric", name: "Điện học", slug: "electric", levels: [{ id: "grade-11", name: "Lớp 11", lessons: [{ id: "circuit", name: "Mạch điện", slug: "circuit" }] }] },
] }] };
const items: LibraryItem[] = [
  ["l1", "Sự rơi tự do từ độ cao 20 m", "fall", "Động học", "PUBLIC"],
  ["l2", "Khảo sát chuyển động thẳng đều", "linear", "Động học", "SHARED"],
  ["l3", "Chu kỳ dao động của con lắc đơn", "pendulum", "Dao động", "PUBLIC"],
  ["l4", "Định luật Ohm và mạch điện nối tiếp", "circuit", "Điện học", "SHARED"],
].map(([id, title, lessonId, topic, visibility]) => ({ id, title, lessonId, topic, visibility: visibility as LibraryItem["visibility"], simulationId: "sim-1", specificationId: "spec-1", validationStatus: "PASS", moderationStatus: "APPROVED", sharedByName: "Đỗ Hoàng Nam", schoolId: visibility === "SHARED" ? school.id : null, schoolName: visibility === "SHARED" ? school.name : null, createdAt: "2026-09-29T08:00:00Z" }));
const assignments: Assignment[] = [
  { id: "a1", title: "Khảo sát sự rơi tự do", description: "Thả một vật từ độ cao 20 m. Dự đoán thời gian chạm đất và kiểm tra bằng mô phỏng.", libraryItemId: "l1", libraryItemTitle: items[0].title, classId: "c1", className: "10A1", classGradeLevel: 10, specificationId: "spec-1", status: "ACTIVE", assignedAt: "2026-09-29T08:00:00Z", dueAt: "2026-10-05T16:59:00Z", questions: { prompt: "Dự đoán thời gian vật chạm đất và giải thích cách tính.", activityType: "PREDICT_OBSERVE_EXPLAIN" }, studentIds: [6], maxScore: 10, autoGrade: true, predictionSubmitted: false, submissionCompleted: false },
  { id: "a2", title: "Đồ thị vận tốc theo thời gian", libraryItemId: "l2", classId: "c1", className: "10A1", specificationId: "spec-1", status: "ACTIVE", assignedAt: "2026-09-28T08:00:00Z", dueAt: "2026-10-03T16:59:00Z", questions: { prompt: "So sánh vận tốc tại các thời điểm và rút ra kết luận.", activityType: "PREDICT_OBSERVE_EXPLAIN" }, studentIds: [6], maxScore: 10, predictionSubmitted: true, predictions: { answerText: "Vận tốc tăng đều.", reasoning: "Gia tốc không đổi." }, submissionCompleted: false },
  { id: "a3", title: "Tìm hiểu chuyển động thẳng đều", libraryItemId: "l2", classId: "c1", className: "10A1", specificationId: "spec-1", status: "ACTIVE", assignedAt: "2026-09-20T08:00:00Z", dueAt: "2026-09-27T16:59:00Z", questions: { prompt: "Khảo sát quãng đường theo thời gian.", activityType: "MEASUREMENT" }, studentIds: [6], maxScore: 10, predictionSubmitted: true, submissionCompleted: true, completedAt: "2026-09-26T08:00:00Z", gradingStatus: "TEACHER_CONFIRMED", score: 9, feedback: "Nhận xét đúng. Cần ghi rõ đơn vị ở bảng đo." },
];
const time = Array.from({ length: 101 }, (_, index) => index / 50);
const simulation = { simulationId: "sim-1", simulationRunId: "run-1", specificationId: "spec-1", schemaId: "free-fall", ready: true, validationPassed: true, computationTimeMs: 8, adjustableParams: { h: 20, g: 9.8 }, time, positions: { x: time.map(() => 0), y: time.map(t => 20 - 4.9 * t * t) }, velocities: { vx: time.map(() => 0), vy: time.map(t => -9.8 * t) }, accelerations: { ay: time.map(() => -9.8) }, values: {}, visualization: { scene: "free-fall", controls: [], series: [{ key: "y", source: "positions.y", label: "Độ cao", symbol: "h", unit: "m", color: "#20534d" }], presentation: { layout: "dataPlane", actors: [{ id: "object", x: "positions.x", y: "positions.y", label: "Vật" }] } }, validation: { passed: true, tolerance: 0.01, checkpoints: [] } };
const classReport = [{ id: "c1", name: "10A1", gradeLevel: 10, schoolYear: "2026-2027", teachers: 1, students: 32 }, { id: "c2", name: "11A2", gradeLevel: 11, schoolYear: "2026-2027", teachers: 2, students: 35 }];
const version = { id: "v1", schemaId: "free-fall", name: "Sự rơi tự do", topic: "Động học", version: "1.0.0", lifecycleStatus: "APPROVED", createdAt: "2026-09-30T10:00:00Z", solverId: "free_fall_numerical", outputDefinition: { referenceSolverId: "free_fall_reference" }, definition: {} };
const plans = [
  { code: "STARTER", name: "Starter", description: "Triển khai cơ bản", annualPriceVnd: 30000000, studentQuota: 500, monthlyTokenQuota: 100000 },
  { code: "PROFESSIONAL", name: "Professional", description: "Nhiều khối lớp", annualPriceVnd: 50000000, studentQuota: 1000, monthlyTokenQuota: 300000 },
  { code: "ENTERPRISE", name: "Enterprise", description: "Quy mô toàn trường", annualPriceVnd: 200000000, studentQuota: 5000, monthlyTokenQuota: null },
];
const data: Record<string, unknown> = {
  "/auth/plans": plans,
"/school/billing": { schoolId: school.id, schoolName: school.name, planCode: "PROFESSIONAL", annualPriceVnd: 50000000, studentQuota: 1000, studentsUsed: 67, monthlyTokenQuota: 300000, tokensUsed: 124500, licenseStart: school.licenseStart, licenseEnd: school.licenseEnd, nextPlanCode: null, payments: [], planChoices: [{ planCode: "STARTER", allowed: false, purpose: null, reason: "Không được hạ gói đang hoạt động." }, { planCode: "PROFESSIONAL", allowed: true, purpose: "RENEWAL", reason: null }, { planCode: "ENTERPRISE", allowed: true, purpose: "UPGRADE", reason: null }] },
  "/user/me": actor,
  "/user/me/license": { active: true, inGraceMode: false, showRenewalBanner: false, daysUntilExpiry: 305, canPerformWriteOperations: true, licenseEnd: school.licenseEnd },
  "/admin/users": users,
  "/admin/schools": [school],
  "/admin/payment-notifications": [],
  "/admin/metrics/validation": { total: 124, failed: 3, failureRate: 0.024 },
  "/admin/curriculum": catalog,
  "/curriculum": catalog,
  "/library": items,
  "/library/community": items,
  "/library/personal": [],
  "/assignments/mine/student": assignments,
  "/student/classes": [{ id: "c1", name: "10A1", gradeLevel: 10, schoolYear: "2026-2027", subject: "Vật lý", schoolId: school.id, schoolName: school.name, teachers: [{ id: 5, fullName: "Đỗ Hoàng Nam" }], classmateCount: 32 }],
  "/student/action-logs": {},
  [`/schools/${school.id}/users`]: users.filter(user => user.schoolId === school.id),
  [`/schools/${school.id}/assignments`]: { content: assignments.map(item => ({ ...item, teacherId: 5, teacherName: users[4].fullName, schoolYear: "2026-2027", studentCount: item.studentIds?.length ?? 0 })), totalElements: 3, totalPages: 1, number: 0 },
  [`/schools/${school.id}/library`]: [{ ...items[1], sharedById: 8, moderationStatus: "PENDING" }],
  [`/schools/${school.id}/reports/summary`]: { schoolId: school.id, schoolName: school.name, students: 67, teachers: 3, managers: 1, activeClasses: 2, enrolledStudents: 67, usedTokens: 124500, tokenQuota: 1000000, licenseEnd: school.licenseEnd },
  [`/schools/${school.id}/reports/classes`]: classReport,
  [`/schools/${school.id}/reports/token-audit`]: [],
  [`/schools/${school.id}/classes`]: classReport.map(item => ({ ...item, subject: "Vật lý", teacherCount: item.teachers, studentCount: item.students })),
  [`/schools/${school.id}/classes/c1`]: { ...classReport[0], schoolId: school.id, subject: "Vật lý", teacherCount: 1, studentCount: 1, teachers: [{ id: 5, fullName: "Đỗ Hoàng Nam", email: "staff@example.test" }], students: [{ id: 6, fullName: "Nguyễn Khánh Linh", email: "student@example.test" }] },
  "/reviewer/ambiguities": [{ id: "q1", specificationId: "spec-1", question: "Gia tốc rơi tự do được chọn bằng bao nhiêu?", fieldPath: "quantities.g", code: "MISSING_GRAVITY", options: ["9,8 m/s²", "10 m/s²"], problemText: "Thả một vật từ độ cao 20 m. Tính thời gian chạm đất.", topic: "Sự rơi tự do", quantities: [{ symbol: "h", value: 20, unit: "m" }], relations: [] }],
  "/reviewer/schemas": [version],
  "/reviewer/solvers": [version],
  "/reviewer/solver-implementations": { numerical: ["free_fall_numerical"], reference: ["free_fall_reference"] },
  "/reviewer/module-releases": [{ id: "m1", topic: "Động học", moduleName: "Chuyển động thẳng", schemaId: "linear-motion", schemaVersion: "1.0.0", lifecycleStatus: "DRAFT" }],
  "/reviewer/benchmarks": [{ id: "b1", problemText: "Một vật rơi tự do từ độ cao 20 m.", topic: "Rơi tự do", gradeScope: "10", sourceCategory: "SGK", status: "ANNOTATING", annotationCount: 1, canAnnotate: true, canAdjudicate: false, annotations: [], goldSpecification: null }],
  "/evaluations/history": { items: [], page: 0, size: 10, totalElements: 0, totalPages: 0 },
  "/reviewer/library": [{ ...items[2], moderationStatus: "PENDING" }],
};
axiosClient.defaults.adapter = async config => {
  const path = new URL(config.url || "", location.origin).pathname.replace(/^\/api(?=\/)/, "");
  let response = data[path];
  if (config.method !== "get" && path !== "/student/action-logs") response = undefined;
  if (config.method === "put" && path === "/user/me/password") {
    actor.mustChangePassword = false;
    response = actor;
  }
  if (config.method === "post" && path === "/auth/logout") response = {};
  if (/^\/assignments\/[^/]+\/simulation$/.test(path) || path.startsWith("/simulations/shared/")) response = simulation;
  if (/^\/assignments\/[^/]+\/predictions$/.test(path)) {
    const assignmentId = path.split("/")[2];
    const predictions = JSON.parse(config.data).predictions;
    const assignment = assignments.find(item => item.id === assignmentId);
    if (assignment) { assignment.predictionSubmitted = true; assignment.predictions = predictions; }
    response = { id: "submission-1", assignmentId, studentId: 6, studentName: actor.fullName, predictions, submittedAt: new Date().toISOString(), score: null, gradingStatus: "PENDING" };
  }
  if (response === undefined) throw new AxiosError(`Fixture missing: ${config.method} ${path}`, "ERR_BAD_REQUEST", config, undefined, { data: { message: "Endpoint not included in visual fixture." }, status: 404, statusText: "Not found", headers: {}, config });
  return { data: structuredClone(response), status: 200, statusText: "OK", headers: {}, config };
};
axios.defaults.adapter = axiosClient.defaults.adapter;
const paths: Record<string, string> = { ADMIN: "/admin/users", MANAGER: "/manager", REVIEWER: "/reviewer", SCHOOL: "/school", STAFF: "/workspace", STUDENT: "/assignments" };
createRoot(document.getElementById("root")!).render(<MemoryRouter initialEntries={[query.get("route") || paths[role]]}><AppShell><AppRoutes authReady /></AppShell></MemoryRouter>);
